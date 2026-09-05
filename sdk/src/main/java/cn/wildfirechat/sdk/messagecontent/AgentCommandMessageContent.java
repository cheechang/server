/*
 * Copyright (c) 2026 WildFireChat. All rights reserved.
 */

package cn.wildfirechat.sdk.messagecontent;

import cn.wildfirechat.pojos.MessagePayload;
import cn.wildfirechat.proto.ProtoConstants;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Agent 命令消息（用户→机器人，透明消息）。消息类型 207。
 * 客户端不渲染、不进消息流。payload.content 为 JSON 字符串：
 * {"op":"query|set|interrupt|ping","cmd":"/model deepseek-official/...","seq":1,
 *  "agent":{"provider":"dsh","agentId":"...","label":"...","model":"..."}}
 */
public class AgentCommandMessageContent extends MessageContent {
    private final JSONObject json;

    public AgentCommandMessageContent() {
        this.json = new JSONObject();
    }

    public AgentCommandMessageContent(String op, String cmd) {
        this();
        json.put("op", op);
        json.put("cmd", cmd);
    }

    public AgentCommandMessageContent content(String content) { parseInto(content); return this; }

    public AgentCommandMessageContent op(String op) { json.put("op", op); return this; }
    public AgentCommandMessageContent cmd(String cmd) { json.put("cmd", cmd); return this; }
    public AgentCommandMessageContent seq(long seq) { json.put("seq", seq); return this; }
    public AgentCommandMessageContent agent(JSONObject agent) { json.put("agent", agent); return this; }

    public String getOp() { return str("op"); }
    public String getCmd() { return str("cmd"); }
    public Long getSeq() { Object v = json.get("seq"); return v == null ? null : (v instanceof Number ? ((Number) v).longValue() : Long.valueOf(String.valueOf(v))); }
    public JSONObject getAgent() { Object v = json.get("agent"); return v instanceof JSONObject ? (JSONObject) v : null; }
    public JSONObject getContentJson() { return json; }

    @Override
    public int getContentType() { return ProtoConstants.ContentType.Agent_Command; }

    @Override
    public int getPersistFlag() { return ProtoConstants.PersistFlag.Transparent; }

    @Override
    public MessagePayload encode() {
        MessagePayload payload = super.encode();
        payload.setContent(json.toString());
        payload.setSearchableContent("");
        return payload;
    }

    @Override
    public void decode(MessagePayload payload) {
        super.decode(payload);
        parseInto(payload.getContent());
    }

    private void parseInto(String content) {
        if (content == null || content.isEmpty()) return;
        try {
            Object o = new JSONParser().parse(content);
            if (o instanceof JSONObject) {
                json.clear();
                json.putAll((JSONObject) o);
            }
        } catch (ParseException ignored) { }
    }

    private String str(String key) { Object v = json.get(key); return v == null ? "" : String.valueOf(v); }
}
