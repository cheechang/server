package cn.wildfirechat.oplog;

import cn.wildfirechat.common.ErrorCode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.GeneratedMessage;
import com.xiaoleilu.loServer.handler.Request;
import io.moquette.BrokerConstants;
import io.moquette.server.config.IConfig;
import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import win.liyufan.im.DBUtil;
import win.liyufan.im.IMTopic;
import win.liyufan.im.Utility;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 操作日志记录器<br>
 * 记录四类操作者的写操作：用户(长连接topic)、机器人、频道、管理员(HTTP API)。
 * 日志先进入内存队列，由单守护线程批量落库，记录失败不影响业务主流程。
 */
public class OpLogRecorder {
    private static final Logger LOG = LoggerFactory.getLogger(OpLogRecorder.class);

    public static final int OPERATOR_USER = 0;
    public static final int OPERATOR_ROBOT = 1;
    public static final int OPERATOR_CHANNEL = 2;
    public static final int OPERATOR_ADMIN = 3;

    private static final int MAX_BODY_LENGTH = 4096;
    private static final int BATCH_SIZE = 200;
    private static final String INSERT_SQL = "insert into t_oplog (_operator_type, _operator_id, _client_id, _operation, _target, _data, _ip, _result, _dt) values(?,?,?,?,?,?,?,?,?)";

    private static boolean enabled = true;
    private static boolean recordBody = true;
    private static int keepDays = 90;
    private static final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

    //用户侧写操作topic白名单，只有列表中的topic才记录。CONNECT/DISCONNECT是伪topic，用于记录用户连上/断开
    private static final Set<String> includeTopics = new HashSet<>(Arrays.asList(
        //连接事件
        "CONNECT", "DISCONNECT",
        //用户设置/资料
        IMTopic.ModifyMyInfoTopic,
        //群组
        IMTopic.CreateGroupTopic, IMTopic.AddGroupMemberTopic, IMTopic.KickoffGroupMemberTopic,
        IMTopic.QuitGroupTopic, IMTopic.DismissGroupTopic, IMTopic.ModifyGroupInfoTopic,
        IMTopic.ModifyGroupAliasTopic, IMTopic.ModifyGroupMemberAliasTopic, IMTopic.ModifyGroupMemberExtraTopic,
        IMTopic.TransferGroupTopic, IMTopic.SetGroupManagerTopic,
        //好友/黑名单
        IMTopic.AddFriendRequestTopic, IMTopic.HandleFriendRequestTopic,
        IMTopic.DeleteFriendTopic, IMTopic.BlackListUserTopic, IMTopic.SetFriendAliasTopic,
        //聊天室
        IMTopic.JoinChatroomTopic, IMTopic.QuitChatroomTopic,
        //频道
        IMTopic.CreateChannelTopic, IMTopic.ModifyChannelInfoTopic, IMTopic.TransferChannelInfoTopic,
        IMTopic.DestroyChannelInfoTopic, IMTopic.ChannelListenTopic,
        //账号/客户端管理
        IMTopic.DestroyUserTopic, IMTopic.KickoffPCClientTopic
    ));

    //HTTP侧写操作path白名单（精确匹配），只有列表中的path才记录
    private static final Set<String> includePaths = new HashSet<>(Arrays.asList(
        //admin 消息
        "/admin/message/send", "/admin/message/recall", "/admin/message/update", "/admin/message/delete",
        "/admin/message/broadcast", "/admin/message/multicast", "/admin/message/publish", "/admin/message/import",
        "/admin/message/recall_broadcast", "/admin/message/recall_multicast",
        "/admin/message/delete_broadcast", "/admin/message/delete_multicast",
        "/admin/message/clear_by_user", "/admin/message/conv_read", "/admin/message/delivery",
        "/admin/conversation/delete",
        //admin 用户
        "/admin/user/create", "/admin/user/update", "/admin/user/destroy",
        "/admin/user/update_block_status", "/admin/user/kickoff_client", "/admin/user/put_setting",
        //admin 群组
        "/admin/group/create", "/admin/group/del", "/admin/group/modify", "/admin/group/transfer",
        "/admin/group/member/add", "/admin/group/member/del", "/admin/group/member/quit",
        "/admin/group/member/set_alias", "/admin/group/member/set_extra",
        "/admin/group/manager/set", "/admin/group/manager/mute", "/admin/group/manager/allow",
        "/admin/group/join_request/add",
        //admin 好友/黑名单
        "/admin/friend/send_request", "/admin/friend/handle_send_request", "/admin/friend/set_alias", "/admin/friend/set_extra",
        "/admin/blacklist/status",
        //admin 频道/聊天室
        "/admin/channel/create", "/admin/channel/destroy", "/admin/channel/subscribe", "/admin/channel/batch_subscribe",
        "/admin/chatroom/create", "/admin/chatroom/del", "/admin/chatroom/mute_all",
        "/admin/chatroom/set_black_status", "/admin/chatroom/set_manager",
        //admin 会议
        "/admin/conference/create", "/admin/conference/destroy", "/admin/conference/recording",
        "/admin/conference/rtp_forward", "/admin/conference/stop_rtp_forward",
        "/admin/conference/user_event", "/admin/conference/user_request",
        //admin 其它
        "/admin/robot/create", "/admin/device/create", "/admin/domain/create", "/admin/domain/destroy",
        "/admin/sensitive/add", "/admin/sensitive/del", "/admin/system/put_setting",
        "/admin/moments/feed/post", "/admin/mesh/group_sync",
        //robot
        "/robot/message/send", "/robot/message/recall", "/robot/message/reply", "/robot/message/update",
        "/robot/group/create", "/robot/group/del", "/robot/group/modify", "/robot/group/transfer",
        "/robot/group/member/add", "/robot/group/member/del", "/robot/group/member/quit",
        "/robot/group/member/set_alias", "/robot/group/member/set_extra",
        "/robot/group/manager/set", "/robot/group/manager/mute", "/robot/group/manager/allow",
        "/robot/update_profile", "/robot/set_callback", "/robot/delete_callback", "/robot/set_online",
        "/robot/conference/request",
        "/robot/moments/feed/post", "/robot/moments/feed/recall", "/robot/moments/feed/update",
        "/robot/moments/comment/post", "/robot/moments/comment/recall",
        "/robot/moments/profiles/list/push", "/robot/moments/profiles/value/push",
        //channel
        "/channel/message/send", "/channel/message/recall", "/channel/message/republish",
        "/channel/update_profile", "/channel/subscribe"
    ));

    private static final BlockingQueue<OpLogEntry> queue = new LinkedBlockingQueue<>(100000);
    private static volatile long dropCount = 0;

    private static class OpLogEntry {
        int operatorType;
        String operatorId;
        String clientId;
        String operation;
        String target;
        String data;
        String ip;
        int result;
        long dt;
    }

    public static void init(IConfig config) {
        try {
            enabled = Boolean.parseBoolean(config.getProperty(BrokerConstants.OPLOG_ENABLE, "false"));
            recordBody = Boolean.parseBoolean(config.getProperty(BrokerConstants.OPLOG_RECORD_BODY, "true"));
            try {
                keepDays = Integer.parseInt(config.getProperty(BrokerConstants.OPLOG_KEEP_DAYS, "90"));
            } catch (NumberFormatException ignored) {
            }

            String extraTopics = config.getProperty(BrokerConstants.OPLOG_INCLUDE_TOPICS);
            if (!StringUtil.isNullOrEmpty(extraTopics)) {
                for (String topic : extraTopics.split(",")) {
                    if (!StringUtil.isNullOrEmpty(topic.trim())) {
                        includeTopics.add(topic.trim());
                    }
                }
            }
            String removedTopics = config.getProperty(BrokerConstants.OPLOG_EXCLUDE_TOPICS);
            if (!StringUtil.isNullOrEmpty(removedTopics)) {
                for (String topic : removedTopics.split(",")) {
                    if (!StringUtil.isNullOrEmpty(topic.trim())) {
                        includeTopics.remove(topic.trim());
                    }
                }
            }
            String extraPaths = config.getProperty(BrokerConstants.OPLOG_INCLUDE_PATHS);
            if (!StringUtil.isNullOrEmpty(extraPaths)) {
                for (String path : extraPaths.split(",")) {
                    if (!StringUtil.isNullOrEmpty(path.trim())) {
                        includePaths.add(path.trim());
                    }
                }
            }
            String removedPaths = config.getProperty(BrokerConstants.OPLOG_EXCLUDE_PATHS);
            if (!StringUtil.isNullOrEmpty(removedPaths)) {
                for (String path : removedPaths.split(",")) {
                    if (!StringUtil.isNullOrEmpty(path.trim())) {
                        includePaths.remove(path.trim());
                    }
                }
            }
        } catch (Exception e) {
            Utility.printExecption(LOG, e);
        }

        if (!enabled) {
            LOG.info("OpLog recorder is disabled");
            return;
        }

        Thread worker = new Thread(OpLogRecorder::drainLoop, "oplog-recorder");
        worker.setDaemon(true);
        worker.start();

        if (keepDays > 0) {
            Thread cleaner = new Thread(OpLogRecorder::cleanLoop, "oplog-cleaner");
            cleaner.setDaemon(true);
            cleaner.start();
        }
        LOG.info("OpLog recorder started, recordBody={}, keepDays={}", recordBody, keepDays);
    }

    /**
     * 用户topic是否需要记录操作日志
     */
    public static boolean isUserTopicRecordable(String topic) {
        return enabled && !StringUtil.isNullOrEmpty(topic) && includeTopics.contains(topic);
    }

    /**
     * 记录用户连接/断开等无请求体的事件，结果固定为成功
     */
    public static void recordUserEvent(String topic, String clientID, String fromUser) {
        recordUser(topic, clientID, fromUser, ErrorCode.ERROR_CODE_SUCCESS, null);
    }

    /**
     * 记录用户长连接写操作，在IMHandler中action执行完成后调用
     * @param data 解析后的请求对象，protobuf对象以文本格式记录
     */
    public static void recordUser(String topic, String clientID, String fromUser, ErrorCode errorCode, Object data) {
        if (!enabled || StringUtil.isNullOrEmpty(topic) || !includeTopics.contains(topic)) {
            return;
        }
        OpLogEntry entry = new OpLogEntry();
        entry.operatorType = OPERATOR_USER;
        entry.operatorId = fromUser == null ? "" : fromUser;
        entry.clientId = clientID;
        entry.operation = topic;
        entry.result = errorCode == null ? 0 : errorCode.getCode();
        entry.dt = System.currentTimeMillis();
        if (recordBody && data != null) {
            entry.data = toDataString(data);
        }
        offer(entry);
    }

    private static String toDataString(Object data) {
        String str;
        if (data instanceof GeneratedMessage) {
            //protobuf对象用文本格式输出，可读且不会泄露内部字段
            str = data.toString();
        } else if (data instanceof byte[]) {
            str = "<binary " + ((byte[]) data).length + " bytes>";
        } else if (data instanceof String) {
            str = (String) data;
        } else {
            try {
                str = gson.toJson(data);
            } catch (Exception e) {
                str = String.valueOf(data);
            }
        }
        if (str != null && str.length() > MAX_BODY_LENGTH) {
            str = str.substring(0, MAX_BODY_LENGTH);
        }
        return str;
    }

    /**
     * 记录admin/robot/channel的HTTP API写操作，在Response.send()时调用（此时结果已知）
     */
    public static void recordHttp(Request request, Object content) {
        if (!enabled || request == null) {
            return;
        }
        try {
            String path = request.getPath();
            if (StringUtil.isNullOrEmpty(path)) {
                return;
            }

            OpLogEntry entry = new OpLogEntry();
            if (path.startsWith("/admin")) {
                entry.operatorType = OPERATOR_ADMIN;
                entry.operatorId = "admin";
                entry.ip = request.getIp();
            } else if (path.startsWith("/robot")) {
                entry.operatorType = OPERATOR_ROBOT;
                entry.operatorId = getHeader(request, "rid");
                if (StringUtil.isNullOrEmpty(entry.operatorId)) {
                    return;
                }
            } else if (path.startsWith("/channel")) {
                entry.operatorType = OPERATOR_CHANNEL;
                entry.operatorId = getHeader(request, "cid");
                if (StringUtil.isNullOrEmpty(entry.operatorId)) {
                    return;
                }
            } else {
                return;
            }

            if (Request.METHOD_GET.equalsIgnoreCase(request.getMethod())) {
                return;
            }
            if (!includePaths.contains(path)) {
                return;
            }

            entry.operation = path;
            entry.result = parseResultCode(content);
            entry.dt = System.currentTimeMillis();

            String body = request.getBody();
            if (!StringUtil.isNullOrEmpty(body)) {
                entry.target = extractTarget(body);
                if (recordBody) {
                    entry.data = body.length() > MAX_BODY_LENGTH ? body.substring(0, MAX_BODY_LENGTH) : body;
                }
            }
            offer(entry);
        } catch (Exception e) {
            Utility.printExecption(LOG, e);
        }
    }

    private static String getHeader(Request request, String name) {
        String value = request.getHeader(name);
        if (StringUtil.isNullOrEmpty(value)) {
            value = request.getHeader(name.substring(0, 1).toUpperCase() + name.substring(1));
        }
        return value;
    }

    private static int parseResultCode(Object content) {
        if (content instanceof ByteBuf) {
            try {
                String json = ((ByteBuf) content).toString(CharsetUtil.UTF_8);
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                if (obj.has("code")) {
                    return obj.get("code").getAsInt();
                }
            } catch (Exception ignored) {
            }
            return -1;
        }
        return 0;
    }

    //尽力从请求体中提取主要操作目标
    private static final String[] TARGET_KEYS = {"targetId", "groupId", "userId", "channelId", "target", "toUser", "cid"};

    private static String extractTarget(String body) {
        try {
            JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
            for (String key : TARGET_KEYS) {
                if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
                    return obj.get(key).getAsString();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void offer(OpLogEntry entry) {
        if (!queue.offer(entry)) {
            dropCount++;
            if (dropCount % 1000 == 1) {
                LOG.warn("OpLog queue is full, dropped {} logs", dropCount);
            }
        }
    }

    private static void drainLoop() {
        List<OpLogEntry> batch = new ArrayList<>(BATCH_SIZE);
        while (true) {
            try {
                OpLogEntry first = queue.poll(1, TimeUnit.SECONDS);
                if (first == null) {
                    continue;
                }
                batch.add(first);
                queue.drainTo(batch, BATCH_SIZE - 1);
                persist(batch);
                batch.clear();
            } catch (InterruptedException e) {
                LOG.warn("OpLog recorder interrupted");
                return;
            } catch (Exception e) {
                batch.clear();
                Utility.printExecption(LOG, e);
            }
        }
    }

    //定时清理过期操作日志，每天执行一次
    private static void cleanLoop() {
        while (true) {
            try {
                Thread.sleep(24 * 60 * 60 * 1000);
            } catch (InterruptedException e) {
                return;
            }
            Connection connection = null;
            PreparedStatement statement = null;
            try {
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DATE, -keepDays);
                connection = DBUtil.getConnection();
                statement = connection.prepareStatement("delete from t_oplog where _dt < ?");
                statement.setTimestamp(1, new Timestamp(cal.getTimeInMillis()));
                int count = statement.executeUpdate();
                if (count > 0) {
                    LOG.info("Cleaned {} expired oplogs", count);
                }
            } catch (Exception e) {
                Utility.printExecption(LOG, e);
            } finally {
                DBUtil.closeDB(connection, statement);
            }
        }
    }

    private static void persist(List<OpLogEntry> logs) {
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = DBUtil.getConnection();
            statement = connection.prepareStatement(INSERT_SQL);
            for (OpLogEntry entry : logs) {
                statement.setInt(1, entry.operatorType);
                statement.setString(2, entry.operatorId);
                statement.setString(3, entry.clientId);
                statement.setString(4, entry.operation);
                statement.setString(5, entry.target);
                statement.setString(6, entry.data);
                statement.setString(7, entry.ip);
                statement.setInt(8, entry.result);
                statement.setTimestamp(9, new Timestamp(entry.dt));
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (Exception e) {
            Utility.printExecption(LOG, e);
        } finally {
            DBUtil.closeDB(connection, statement);
        }
    }
}
