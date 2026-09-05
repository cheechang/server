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
 * Agent 结构化回答消息（用户→机器人）。消息类型 201。payload.content 为 JSON 字符串：{"qid":"uuid","answers":[{"id":"q1","selected":["是","否"],"custom":"可选补充"}]}。
 */
public class AgentAnswerMessageContent extends MessageContent {
    private final JSONObject json = new JSONObject();

    public AgentAnswerMessageContent() {
        json.put("qid", ""); json.put("answers", new JSONArray());
    }

    public AgentAnswerMessageContent content(String content) {
        parseInto(content);
        return this;
    }

    public AgentAnswerMessageContent(String qid, JSONArray answers) {
        json.put("qid", qid);
        json.put("answers", answers);
    }

    public AgentAnswerMessageContent qid(String qid) { json.put("qid", qid); return this; }
    public AgentAnswerMessageContent answers(JSONArray answers) { json.put("answers", answers); return this; }
    public AgentAnswerMessageContent addAnswer(JSONObject answer) {
        JSONArray arr = (JSONArray) json.get("answers");
        if (arr == null) { arr = new JSONArray(); json.put("answers", arr); }
        arr.add(answer);
        return this;
    }
    public String getQid() { return str("qid"); }
    public JSONArray getAnswers() { return arr("answers"); }

    protected String digest() {
        JSONArray answers = getAnswers();
        if (answers == null || answers.isEmpty()) return "（已作答）";
        StringBuilder sb = new StringBuilder();
        for (Object o : answers) {
            if (!(o instanceof JSONObject)) continue;
            JSONObject a = (JSONObject) o;
            JSONArray selected = a.get("selected") instanceof JSONArray ? (JSONArray) a.get("selected") : null;
            String custom = a.get("custom") == null ? "" : String.valueOf(a.get("custom"));
            if (selected != null && !selected.isEmpty()) {
                if (sb.length() > 0) sb.append('；');
                sb.append(selected.toString().replace("[", "").replace("]", "").replace("\"", ""));
            } else if (!custom.isEmpty()) {
                if (sb.length() > 0) sb.append('；');
                sb.append(custom);
            }
        }
        return sb.length() > 0 ? "已选择：" + sb : "（已作答）";
    }

    public JSONObject getContentJson() { return json; }

    @Override
    public int getContentType() { return ProtoConstants.ContentType.Agent_Answer; }

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
