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
 * Agent 任务进度卡片消息（机器人→用户）。消息类型 208，同 flowId 的消息由机器人 updateMessage 原地更新。
 * payload.content 为 JSON 字符串：
 * {"ver":2,"flowId":"<run-instance>","updatedAt":1787470162762,
 *  "tasks":[{"id":"t-1","label":"...","kind":"subagent|job|step|tool|phase|...",
 *            "status":"running|done|failed|cancelled|waiting","detail":"...","reason":"...",
 *            "progress":60,"updatedAt":1787470162762}]}
 */
public class AgentTaskProgressMessageContent extends MessageContent {
    private final JSONObject json;

    public AgentTaskProgressMessageContent() {
        this.json = new JSONObject();
    }

    public AgentTaskProgressMessageContent(String flowId, JSONArray tasks) {
        this();
        json.put("ver", 2L);
        json.put("flowId", flowId);
        json.put("tasks", tasks);
    }

    public AgentTaskProgressMessageContent content(String content) { parseInto(content); return this; }

    public AgentTaskProgressMessageContent ver(long ver) { json.put("ver", ver); return this; }
    public AgentTaskProgressMessageContent flowId(String flowId) { json.put("flowId", flowId); return this; }
    public AgentTaskProgressMessageContent tasks(JSONArray tasks) { json.put("tasks", tasks); return this; }
    public AgentTaskProgressMessageContent addTask(JSONObject task) {
        JSONArray arr = (JSONArray) json.get("tasks");
        if (arr == null) { arr = new JSONArray(); json.put("tasks", arr); }
        arr.add(task);
        return this;
    }
    public AgentTaskProgressMessageContent updatedAt(long updatedAt) { json.put("updatedAt", updatedAt); return this; }

    public Long getVer() { return num("ver"); }
    public String getFlowId() { return str("flowId"); }
    public JSONArray getTasks() { return arr("tasks"); }
    public JSONObject getContentJson() { return json; }

    @Override
    public int getContentType() { return ProtoConstants.ContentType.Agent_Task_Progress; }

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
        JSONArray tasks = getTasks();
        if (tasks == null || tasks.isEmpty()) return "🧩 任务：无";
        int running = 0, failed = 0;
        for (Object o : tasks) {
            if (!(o instanceof JSONObject)) continue;
            String status = String.valueOf(((JSONObject) o).getOrDefault("status", ""));
            if ("running".equals(status)) running++;
            else if ("failed".equals(status)) failed++;
        }
        if (running > 0) return "🧩 任务 " + tasks.size() + "（" + running + " 运行中）";
        return failed == tasks.size() ? "🧩 任务 " + tasks.size() + "（全部失败）" : "🧩 任务 " + tasks.size() + "（全部完成）";
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
