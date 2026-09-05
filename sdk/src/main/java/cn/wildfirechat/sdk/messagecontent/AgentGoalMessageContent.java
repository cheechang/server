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
 * Agent 目标进度卡片消息（机器人→用户）。消息类型 206。
 * payload.content 为 JSON 字符串，ver:2 通用载荷（旧客户端可回退 v1 字段）：
 * {"ver":2,"gid":"...","title":"...","state":"active|paused|blocked|complete|cancelled","stage":"...",
 *  "progress":35,"milestones":[{"label":"...","done":true}],"updatedAt":1787470162762,
 *  "objective":"...","phase":"...","roundsStarted":3}
 */
public class AgentGoalMessageContent extends MessageContent {
    private final JSONObject json;

    public AgentGoalMessageContent() {
        this.json = new JSONObject();
    }

    public AgentGoalMessageContent content(String content) { parseInto(content); return this; }

    public AgentGoalMessageContent ver(long ver) { json.put("ver", ver); return this; }
    public AgentGoalMessageContent gid(String gid) { json.put("gid", gid); return this; }
    public AgentGoalMessageContent title(String title) { json.put("title", title); return this; }
    public AgentGoalMessageContent objective(String objective) { json.put("objective", objective); return this; }
    public AgentGoalMessageContent state(String state) { json.put("state", state); return this; }
    public AgentGoalMessageContent phase(String phase) { json.put("phase", phase); return this; }
    public AgentGoalMessageContent stage(String stage) { json.put("stage", stage); return this; }
    public AgentGoalMessageContent progress(long progress) { json.put("progress", progress); return this; }
    public AgentGoalMessageContent milestones(JSONArray milestones) { json.put("milestones", milestones); return this; }
    public AgentGoalMessageContent roundsStarted(long roundsStarted) { json.put("roundsStarted", roundsStarted); return this; }
    public AgentGoalMessageContent updatedAt(long updatedAt) { json.put("updatedAt", updatedAt); return this; }
    public AgentGoalMessageContent agent(JSONObject agent) { json.put("agent", agent); return this; }

    public Long getVer() { return num("ver"); }
    public String getGid() { return str("gid"); }
    public String getTitle() { String t = str("title"); return t.isEmpty() ? str("objective") : t; }
    public String getState() { String s = str("state"); return s.isEmpty() ? str("phase") : s; }
    public String getStage() { return str("stage"); }
    public Long getProgress() { return num("progress"); }
    public JSONArray getMilestones() { return arr("milestones"); }
    public JSONObject getAgent() { Object v = json.get("agent"); return v instanceof JSONObject ? (JSONObject) v : null; }
    public JSONObject getContentJson() { return json; }

    @Override
    public int getContentType() { return ProtoConstants.ContentType.Agent_Goal; }

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
        String title = getTitle();
        String state = getState();
        return "🎯 " + (title.isEmpty() ? "目标" : title) + (state.isEmpty() ? "" : "（" + state + "）");
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
    private Long num(String key) { Object v = json.get(key); return v == null ? null : (v instanceof Number ? ((Number) v).longValue() : Long.valueOf(String.valueOf(v))); }
    private JSONArray arr(String key) { Object v = json.get(key); return v instanceof JSONArray ? (JSONArray) v : null; }
}
