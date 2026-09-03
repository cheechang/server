/*
 * This file is part of the Wildfire Chat package.
 * (c) Heavyrain2012 <heavyrain.lee@gmail.com>
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */

package com.xiaoleilu.loServer.action.robot;


import cn.wildfirechat.common.APIPath;
import cn.wildfirechat.common.ErrorCode;
import cn.wildfirechat.pojos.Conversation;
import cn.wildfirechat.pojos.InputConversationUserSetting;
import cn.wildfirechat.proto.ProtoConstants;
import cn.wildfirechat.proto.WFCMessage;
import com.xiaoleilu.loServer.RestResult;
import com.xiaoleilu.loServer.annotation.HttpMethod;
import com.xiaoleilu.loServer.annotation.Route;
import com.xiaoleilu.loServer.handler.Request;
import com.xiaoleilu.loServer.handler.Response;
import io.moquette.persistence.ServerAPIHelper;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.util.internal.StringUtil;
import win.liyufan.im.IMTopic;
import win.liyufan.im.UserSettingScope;

import java.util.ArrayList;
import java.util.List;

@Route(APIPath.Robot_Conversation_Set_User_Setting)
@HttpMethod("POST")
public class ConversationUserSettingAction extends RobotAction {

    @Override
    public boolean isTransactionAction() {
        return true;
    }

    @Override
    public boolean action(Request request, Response response) {
        if (request.getNettyRequest() instanceof FullHttpRequest) {
            InputConversationUserSetting input = getRequestBody(request.getNettyRequest(), InputConversationUserSetting.class);
            if (input != null && input.isValide()
                && (input.getConversation().getType() == ProtoConstants.ConversationType.ConversationType_Private
                    || input.getConversation().getType() == ProtoConstants.ConversationType.ConversationType_Group)) {
                Conversation conversation = input.getConversation();
                WFCMessage.ModifyUserSettingReq modifyUserSettingReq = WFCMessage.ModifyUserSettingReq.newBuilder()
                    .setScope(UserSettingScope.kUserSettingScope_Conversation_User_Setting)
                    .setKey(conversation.getType() + "-" + conversation.getLine() + "-" + conversation.getTarget() + "_" + input.getType() + "_" + robot.getUid())
                    .setValue(StringUtil.isNullOrEmpty(input.getValue()) ? "" : input.getValue())
                    .build();

                if (conversation.getType() == ProtoConstants.ConversationType.ConversationType_Group) {
                    WFCMessage.GroupInfo groupInfo = messagesStore.getGroupInfo(conversation.getTarget());
                    if (groupInfo == null || groupInfo.getDeleted() > 0) {
                        sendResponse(response, ErrorCode.ERROR_CODE_NOT_EXIST, null);
                        return true;
                    }

                    List<WFCMessage.GroupMember> members = new ArrayList<>();
                    ErrorCode errorCode = messagesStore.getGroupMembers(null, conversation.getTarget(), 0, members);
                    if (errorCode == ErrorCode.ERROR_CODE_SUCCESS) {
                        for (WFCMessage.GroupMember member : members) {
                            if (member.getType() == ProtoConstants.GroupMemberType.GroupMemberType_Removed
                                || member.getMemberId().equals(robot.getUid())) {
                                continue;
                            }
                            ServerAPIHelper.sendRequest(member.getMemberId(), null, IMTopic.PutUserSettingTopic, modifyUserSettingReq.toByteArray(), null, ProtoConstants.RequestSourceType.Request_From_Robot);
                        }
                    }
                } else {
                    if (!conversation.getTarget().equals(robot.getUid())) {
                        ServerAPIHelper.sendRequest(conversation.getTarget(), null, IMTopic.PutUserSettingTopic, modifyUserSettingReq.toByteArray(), null, ProtoConstants.RequestSourceType.Request_From_Robot);
                    }
                }

                sendResponse(response, null, null);
            } else {
                setResponseContent(RestResult.resultOf(ErrorCode.INVALID_PARAMETER), response);
            }
        }
        return true;
    }
}
