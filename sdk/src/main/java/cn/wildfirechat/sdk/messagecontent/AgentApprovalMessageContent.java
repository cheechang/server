/*
 * Copyright (c) 2026 WildFireChat. All rights reserved.
 */

package cn.wildfirechat.sdk.messagecontent;

import cn.wildfirechat.pojos.MessagePayload;
import cn.wildfirechat.proto.ProtoConstants;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Agent 工具审批卡片消息（机器人→用户）。消息类型 202。payload.content 为 JSON 字符串：{"aid":"uuid","toolName":"bash","reason":"运行 rm -rf ./dist","state":"pending"}，state ∈ pending/approved/rejected/expired。
 */
public class AgentApprovalMessageContent extends MessageContent {
    private final JSONObject json = new JSONObject();

    public AgentApprovalMessageContent() {
        json.put("aid", ""); json.put("state", "pending");
    }

    public AgentApprovalMessageContent content(String content) {
        parseInto(content);
        return this;
    }

    public AgentApprovalMessageContent(String aid, String toolName) {
        json.put("aid", aid);
        json.put("toolName", toolName);
        json.put("state", "pending");
    }

    public AgentApprovalMessageContent(String aid, String toolName, String reason) {
        this(aid, toolName);
        json.put("reason", reason);
    }

    public AgentApprovalMessageContent aid(String aid) { json.put("aid", aid); return this; }
    public AgentApprovalMessageContent toolName(String toolName) { json.put("toolName", toolName); return this; }
    public AgentApprovalMessageContent reason(String reason) { json.put("reason", reason); return this; }
    public AgentApprovalMessageContent state(String state) { json.put("state", state); return this; }
    public String getAid() { return str("aid"); }
    public String getToolName() { return str("toolName"); }
    public String getReason() { return str("reason"); }
    public String getState() { return str("state"); }

    protected String digest() {
        String reason = getReason();
        return reason.isEmpty() ? "🔐 工具审批：" + getToolName() : "🔐 工具审批：" + getToolName() + "（" + reason + "）";
    }

    public JSONObject getContentJson() { return json; }

    @Override
    public int getContentType() { return ProtoConstants.ContentType.Agent_Approval; }

    @Override
    public int getPersistFlag() { return ProtoConstants.PersistFlag.Persist; }

    @Override
    public MessagePayload encode() {
        MessagePayload payload = super.encode();
        payload.setContent(json.toString());
        payload.setSearchableContent(digest());
        return payload;
    }

    @Override
    public void decode(MessagePayload payload) {
        super.decode(payload);
        parseInto(payload.getContent());
    }

    protected void parseInto(String content) {
        if (content == null || content.isEmpty()) return;
        try {
            Object o = new JSONParser().parse(content);
            if (o instanceof JSONObject) {
                JSONObject parsed = (JSONObject) o;
                json.clear();
                json.putAll(parsed);
            }
        } catch (ParseException ignored) { }
    }

    protected String str(String key) { Object v = json.get(key); return v == null ? "" : String.valueOf(v); }
    protected Long num(String key) { Object v = json.get(key); return v == null ? null : (v instanceof Number ? ((Number) v).longValue() : Long.valueOf(String.valueOf(v))); }
    protected JSONArray arr(String key) { Object v = json.get(key); return v instanceof JSONArray ? (JSONArray) v : null; }
}
