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
 * Agent 提问卡片消息（机器人→用户）。消息类型 200（200-209 官方预留 AI 交互段，与客户端/服务端一致）。
 * payload.content 为 JSON 字符串：
 * {"qid":"uuid","questions":[{"id":"q1","header":"...","question":"...","detail":"...",
 *  "options":[{"label":"是","description":"..."}],"multiSelect":false,
 *  "intent":{"kind":"plan-review","approve":"批准"}},...],"state":"pending"}
 * state ∈ pending/answered/expired（由机器人侧 updateMessage 更新）。
 */
public class AgentQuestionMessageContent extends MessageContent {
    private final JSONObject json;

    public AgentQuestionMessageContent() {
        this.json = new JSONObject();
        json.put("state", "pending");
    }

    public AgentQuestionMessageContent(String qid, JSONArray questions) {
        this();
        json.put("qid", qid);
        json.put("questions", questions);
    }

    public AgentQuestionMessageContent content(String content) {
        parseInto(content);
        return this;
    }

    public AgentQuestionMessageContent qid(String qid) { json.put("qid", qid); return this; }
    public AgentQuestionMessageContent questions(JSONArray questions) { json.put("questions", questions); return this; }
    public AgentQuestionMessageContent addQuestion(JSONObject question) {
        JSONArray arr = (JSONArray) json.get("questions");
        if (arr == null) { arr = new JSONArray(); json.put("questions", arr); }
        arr.add(question);
        return this;
    }
    public AgentQuestionMessageContent state(String state) { json.put("state", state); return this; }

    public String getQid() { return str("qid"); }
    public JSONArray getQuestions() { return arr("questions"); }
    public String getState() { return str("state"); }
    public JSONObject getContentJson() { return json; }

    @Override
    public int getContentType() { return ProtoConstants.ContentType.Agent_Question; }

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

    private String digest() {
        JSONArray questions = getQuestions();
        JSONObject first = (questions != null && !questions.isEmpty()) ? (JSONObject) questions.get(0) : null;
        if (first == null) return "🤔 需要你确认";
        String header = String.valueOf(first.getOrDefault("header", ""));
        String question = String.valueOf(first.getOrDefault("question", ""));
        return "🤔 " + (header.isEmpty() ? "" : "【" + header + "】") + question;
    }

    private void parseInto(String content) {
        if (content == null || content.isEmpty()) return;
        try {
            Object o = new JSONParser().parse(content);
            if (o instanceof JSONObject) {
                JSONObject parsed = (JSONObject) o;
                json.clear();
                json.putAll(parsed);
                if (!json.containsKey("state")) json.put("state", "pending");
            }
        } catch (ParseException ignored) { }
    }

    private String str(String key) { Object v = json.get(key); return v == null ? "" : String.valueOf(v); }
    private JSONArray arr(String key) { Object v = json.get(key); return v instanceof JSONArray ? (JSONArray) v : null; }
}
