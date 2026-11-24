package com.dsh.toolbox.data;

import org.json.JSONObject;

import java.util.Locale;

public class Homework {

    public String id = "";
    public String source = "";
    public String course = "";
    public String title = "";
    public long dueAt = 0L;
    public String dueText = "";
    public String url = "";
    public boolean done = false;
    public long fetchedAt = 0L;
    /** 作业 / 互动 / 资料 / 课件, empty when unknown. */
    public String kind = "";
    /** 已过截止时间但平台仍允许补交（雨课堂开放型作业、智慧树长期开放）。 */
    public boolean lateOk = false;

    public Homework() {
    }

    public static String normalize(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\s\\p{Punct}，。：；！？、（）《》【】·]", "").toLowerCase(Locale.ROOT);
    }

    public String key() {
        return source + "|" + normalize(title) + "|" + dueAt;
    }

    public String displayKey() {
        return source + "|" + normalize(title);
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("source", source);
            o.put("course", course);
            o.put("title", title);
            o.put("dueAt", dueAt);
            o.put("dueText", dueText);
            o.put("url", url);
            o.put("done", done);
            o.put("fetchedAt", fetchedAt);
            o.put("kind", kind);
            o.put("lateOk", lateOk);
        } catch (Exception ignored) {
        }
        return o;
    }

    public static Homework fromJson(JSONObject o) {
        Homework h = new Homework();
        h.id = o.optString("id", "");
        h.source = o.optString("source", "");
        h.course = o.optString("course", "");
        h.title = o.optString("title", "");
        h.dueAt = o.optLong("dueAt", 0L);
        h.dueText = o.optString("dueText", "");
        h.url = o.optString("url", "");
        h.done = o.optBoolean("done", false);
        h.fetchedAt = o.optLong("fetchedAt", 0L);
        h.kind = o.optString("kind", "");
        h.lateOk = o.optBoolean("lateOk", false);
        return h;
    }
}