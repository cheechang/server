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
 * Agent 审批结果消息（用户→机器人）。消息类型 203。payload.content 为 JSON 字符串：{"aid":"uuid","action":"approve"}，action ∈ approve/reject。
 */
public class AgentApprovalResultMessageContent extends MessageContent {
    private final JSONObject json = new JSONObject();

    public AgentApprovalResultMessageContent() {
        json.put("aid", ""); json.put("action", "approve");
    }

    public AgentApprovalResultMessageContent content(String content) {
        parseInto(content);
        return this;
    }

    public AgentApprovalResultMessageContent(String aid, String action) {
        json.put("aid", aid);
        json.put("action", action);
    }
    public AgentApprovalResultMessageContent aid(String aid) { json.put("aid", aid); return this; }
    public AgentApprovalResultMessageContent action(String action) { json.put("action", action); return this; }
    public String getAid() { return str("aid"); }
    public String getAction() { return str("action"); }

    protected String digest() { return "approve".equals(getAction()) ? "（已同意）" : "（已拒绝）"; }

    public JSONObject getContentJson() { return json; }

    @Override
    public int getContentType() { return ProtoConstants.ContentType.Agent_Approval_Result; }

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
