package com.dsh.toolbox.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 同步记录：每次同步存一条（时间、各平台条数、有没有登录失效）。
 *
 * <p>用户的原话是「这样大家可以看一下是否真的被同步了」——所以这里记的是**每次**同步的结果，
 * 而不是只记最后一次。</p>
 */
public final class SyncHistory {

    public static final class Entry {
        public long at;
        public int kept;
        public boolean sessionLost;
        /** 形如 {yuketang=30, zhihuishu=14}，按抓取顺序。 */
        public String detail = "";
        /** 形如「智慧树 0（有 3 门课的任务不在监控范围）」的补充说明。 */
        public String note = "";

        public String time() {
            return new SimpleDateFormat("M/d HH:mm", Locale.CHINA).format(new Date(at));
        }

        public String line() {
            StringBuilder b = new StringBuilder(time()).append("  ·  ").append(kept).append(" 条");
            if (detail.length() > 0) b.append("  ·  ").append(detail);
            if (note.length() > 0) b.append("\n      ").append(note);
            if (sessionLost) b.append("  ·  需要重新登录");
            return b.toString();
        }
    }

    private static final String FILE = "sync_history.json";
    private static final int MAX = 60;

    private SyncHistory() {
    }

    public static synchronized void add(Context ctx, SyncAll.Report r) {
        try {
            JSONArray arr = read(ctx);
            JSONObject o = new JSONObject();
            o.put("at", System.currentTimeMillis());
            o.put("kept", r.kept);
            o.put("lost", r.sessionLost);
            JSONObject d = new JSONObject();
            for (String k : r.perPlatform.keySet()) d.put(k, r.perPlatform.get(k));
            o.put("detail", d.toString());
            JSONObject n = new JSONObject();
            for (String k : r.notes.keySet()) {
                String v = r.notes.get(k);
                if (v != null && !v.isEmpty()) n.put(k, v);
            }
            o.put("notes", n.toString());
            JSONArray out = new JSONArray();
            out.put(o);
            for (int i = 0; i < arr.length() && out.length() < MAX; i++) out.put(arr.get(i));
            write(ctx, out);
        } catch (Throwable ignored) {
        }
    }

    public static synchronized List<Entry> list(Context ctx) {
        List<Entry> out = new ArrayList<>();
        JSONArray arr = read(ctx);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Entry e = new Entry();
            e.at = o.optLong("at");
            e.kept = o.optInt("kept");
            e.sessionLost = o.optBoolean("lost");
            e.detail = pretty(o.optString("detail", ""));
            e.note = prettyNotes(o.optString("notes", ""));
            out.add(e);
        }
        return out;
    }

    public static synchronized void clear(Context ctx) {
        try {
            new File(ctx.getFilesDir(), FILE).delete();
        } catch (Throwable ignored) {
        }
    }

    /** {"yuketang":30,"chaoxing":3} → "雨课堂 30 · 超星学习通 3" */
    private static String pretty(String json) {
        if (json == null || json.length() < 3) return "";
        try {
            JSONObject o = new JSONObject(json);
            StringBuilder b = new StringBuilder();
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (b.length() > 0) b.append(" · ");
                b.append(Sources.nameOf(k)).append(' ').append(o.optInt(k));
            }
            return b.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /** {"zhihuishu":"有 3 门课的任务不在监控范围"} → "智慧树：有 3 门课的任务不在监控范围" */
    private static String prettyNotes(String json) {
        if (json == null || json.length() < 3) return "";
        try {
            JSONObject o = new JSONObject(json);
            StringBuilder b = new StringBuilder();
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                String v = o.optString(k, "");
                if (v.isEmpty()) continue;
                if (b.length() > 0) b.append("；");
                b.append(Sources.nameOf(k)).append("：").append(v);
            }
            return b.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    private static JSONArray read(Context ctx) {
        try {
            File f = new File(ctx.getFilesDir(), FILE);
            if (!f.exists()) return new JSONArray();
            byte[] buf = new byte[(int) f.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                int off = 0;
                while (off < buf.length) {
                    int n = in.read(buf, off, buf.length - off);
                    if (n <= 0) break;
                    off += n;
                }
            }
            return new JSONArray(new String(buf, "UTF-8"));
        } catch (Throwable t) {
            return new JSONArray();
        }
    }

    private static void write(Context ctx, JSONArray arr) {
        try {
            YktSync.write(ctx, FILE, arr.toString());
        } catch (Throwable ignored) {
        }
    }
}