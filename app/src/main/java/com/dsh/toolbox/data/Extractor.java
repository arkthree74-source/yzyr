package com.dsh.toolbox.data;

import android.text.Html;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Turns whatever the WebView page produced (network JSON payloads plus DOM text)
 * into a list of homework items. The rules are intentionally permissive: the goal
 * is to surface candidates so the user can confirm them, not to be exact.
 */
public final class Extractor {

    private static final String[] TITLE_KEYS = {
            "title", "workName", "taskName", "homeworkName", "examName", "name", "content", "subject"
    };
    private static final String[] TIME_KEYS = {
            "endTime", "end_time", "deadline", "deadLine", "deadlineTime", "expireTime",
            "endDate", "limitTime", "finishTime", "closeTime", "endTimeStr", "expireTimeStr"
    };
    private static final String[] COURSE_KEYS = {
            "courseName", "coursename", "course_name", "className", "courseTitle", "course"
    };
    private static final String[] URL_KEYS = {"url", "link", "href", "jumpUrl", "detailUrl"};
    private static final String[] STATUS_KEYS = {
            "status", "state", "finishStatus", "submitStatus", "workStatus", "statusName"
    };

    private static final Pattern DATE_HINT = Pattern.compile(
            "(20\\d{2}\\s*[-/.年]\\s*\\d{1,2}\\s*[-/.月]\\s*\\d{1,2}|\\d{1,2}\\s*[-/.月]\\s*\\d{1,2}\\s*[日号]?)");
    private static final Pattern KEYWORD = Pattern.compile("作业|任务|测验|考试|实验|报告|论文|练习|课件|章节|视频|讨论");

    /** Leading date/time noise such as "9月09日 星期三16：32" or "2026-09-23 23:59". */
    private static final Pattern LEAD_TIME = Pattern.compile(
            "^\\s*(20\\d{2}\\s*[-/.年]\\s*\\d{1,2}\\s*[-/.月]\\s*\\d{1,2}\\s*[日号]?"
                    + "|\\d{1,2}\\s*[-/.月]\\s*\\d{1,2}\\s*[日号]?"
                    + "|\\d{1,2}:\\d{2}\\s*(上午|下午)?"
                    + "|(星期|周)[一二三四五六日天])\\s*");
    /** Trailing boilerplate once the real title has been read. */
    private static final Pattern TAIL_NOISE = Pattern.compile(
            "(请在\\s*[「\"'（(]|截止时间|截止日期|剩余时间|去完成|点击查看)");
    /** Class / term labels that identify a course rather than a homework item. */
    private static final Pattern COURSE_NOISE = Pattern.compile(
            "(\\[\\s*\\d{4}\\s*[-~]\\s*\\d{4}\\s*\\]|\\d{4}\\s*[春秋]\\s*[-—]|学期|开课时间|教学班|行政班|课程简介)");
    /** A full date+time that ends the title part of a list row. */
    private static final Pattern INLINE_DATE = Pattern.compile(
            "20\\d{2}\\s*[-/.]\\s*\\d{1,2}\\s*[-/.]\\s*\\d{1,2}");
    /** Course codes such as "大学物理实验(2-1)" are courses, not homework. */
    private static final Pattern COURSE_CODE_TAIL = Pattern.compile(
            "\\(\\s*\\d+\\s*[-–~]\\s*\\d+\\s*\\)\\s*$|（\\s*\\d+\\s*[-–~]\\s*\\d+\\s*）\\s*$");

    public static class Stats {
        public int jsonBodies = 0;
        public int domNodes = 0;
        public int textHits = 0;
        public String page = "";
    }

    private Extractor() {
    }

    public static List<Homework> parse(String source, String pageUrl, String captureJson,
                                       String domJson, Stats stats) {
        List<Homework> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (stats != null) stats.page = pageUrl == null ? "" : pageUrl;

        try {
            JSONArray arr = new JSONArray(captureJson == null || captureJson.isEmpty() ? "[]" : captureJson);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String text = o.optString("t", "");
                if (text == null || text.isEmpty()) continue;
                String trimmed = text.trim();
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                    try {
                        Object value = new JSONTokener(trimmed).nextValue();
                        if (stats != null) stats.jsonBodies++;
                        walk(value, source, pageUrl, out, 0);
                        continue;
                    } catch (Exception ignored) {
                        // not valid JSON, fall through to text scanning
                    }
                }
                scanText(trimmed, source, pageUrl, out, stats);
            }
        } catch (Exception ignored) {
        }

        try {
            JSONArray dom = new JSONArray(domJson == null || domJson.isEmpty() ? "[]" : domJson);
            for (int i = 0; i < dom.length() && out.size() < 400; i++) {
                JSONObject o = dom.optJSONObject(i);
                if (o == null) continue;
                String t = clean(o.optString("t", ""));
                if (t.length() < 3) continue;
                if (isBoilerplate(t)) continue;
                String body = tidy(t);
                if (!usable(body)) continue;
                long due = DeadlineParser.parse(t);
                String href = o.optString("h", "");
                out.add(make(source, "", body, due,
                        href.isEmpty() ? pageUrl : href, now));
                if (stats != null) stats.domNodes++;
            }
        } catch (Exception ignored) {
        }

        return dedupe(out);
    }

    private static void walk(Object value, String source, String pageUrl, List<Homework> out, int depth) {
        if (value == null || depth > 12 || out.size() > 500) return;
        if (value instanceof JSONObject) {
            JSONObject o = (JSONObject) value;
            String title = anyString(o, TITLE_KEYS);
            String course = anyString(o, COURSE_KEYS);
            String url = anyString(o, URL_KEYS);
            long due = anyTime(o, TIME_KEYS);
            boolean hasStatus = anyString(o, STATUS_KEYS) != null;

            if (title != null) {
                String t = tidy(clean(title));
                if (t.length() >= 2 && t.length() <= 90 && !isBoilerplate(t)
                        && !COURSE_NOISE.matcher(t).find()
                        && !COURSE_CODE_TAIL.matcher(t).find()
                        && (KEYWORD.matcher(t).find() || hasWorkKey(o))
                        && (due > 0 || hasStatus)) {
                    out.add(make(source, course == null ? "" : tidy(clean(course)), t, due,
                            url == null ? pageUrl : url, System.currentTimeMillis()));
                }
            }

            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                Object child = o.opt(k);
                if (child instanceof JSONObject || child instanceof JSONArray) {
                    walk(child, source, pageUrl, out, depth + 1);
                }
            }
        } else if (value instanceof JSONArray) {
            JSONArray a = (JSONArray) value;
            for (int i = 0; i < a.length(); i++) {
                walk(a.opt(i), source, pageUrl, out, depth + 1);
            }
        }
    }

    private static void scanText(String text, String source, String pageUrl, List<Homework> out, Stats stats) {
        String plain = text;
        if (plain.contains("<") && plain.contains(">")) {
            try {
                plain = Html.fromHtml(plain, Html.FROM_HTML_MODE_LEGACY).toString();
            } catch (Exception e) {
                plain = plain.replaceAll("<[^>]+>", " ");
            }
        }
        String[] lines = plain.split("[\\r\\n]+");
        for (String raw : lines) {
            if (out.size() > 400) return;
            String line = raw.replaceAll("[ \\t\\u00a0]{2,}", " ").trim();
            if (line.length() < 4 || line.length() > 140) continue;
            if (!KEYWORD.matcher(line).find()) continue;
            boolean hasDate = DATE_HINT.matcher(line).find();
            boolean hasState = line.contains("截止") || line.contains("未完成")
                    || line.contains("待提交") || line.contains("未提交");
            if (!hasDate && !hasState) continue;
            if (isBoilerplate(line)) continue;
            String body = tidy(line);
            if (!usable(body)) continue;
            out.add(make(source, "", body, DeadlineParser.parse(line), pageUrl, System.currentTimeMillis()));
            if (stats != null) stats.textHits++;
        }
    }

    private static Homework make(String source, String course, String title, long dueAt,
                                 String url, long now) {
        Homework h = new Homework();
        h.source = source;
        h.course = course == null ? "" : course;
        h.title = title;
        h.dueAt = dueAt;
        h.url = url == null ? "" : url;
        h.fetchedAt = now;
        return h;
    }

    private static List<Homework> dedupe(List<Homework> in) {
        Map<String, Homework> map = new LinkedHashMap<>();
        for (Homework h : in) {
            String key = h.displayKey();
            Homework cur = map.get(key);
            if (cur == null) {
                map.put(key, h);
            } else if (cur.dueAt == 0 && h.dueAt > 0) {
                cur.dueAt = h.dueAt;
                if (!h.course.isEmpty()) cur.course = h.course;
                if (!h.url.isEmpty()) cur.url = h.url;
            } else if (cur.course.isEmpty() && !h.course.isEmpty()) {
                cur.course = h.course;
            }
        }
        return new ArrayList<>(map.values());
    }

    /** True when the object carries a homework-ish identifier field. */
    private static boolean hasWorkKey(JSONObject o) {
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next().toLowerCase(java.util.Locale.ROOT);
            if (k.contains("homework") || k.contains("workid") || k.contains("work_id")
                    || k.contains("taskid") || k.contains("task_id")
                    || k.contains("examid") || k.contains("exam_id")
                    || k.contains("quiz") || k.contains("exercise")) {
                Object v = o.opt(k);
                if (v != null && !String.valueOf(v).isEmpty() && !"0".equals(String.valueOf(v))) return true;
            }
        }
        return false;
    }

    /** Removes the date/time prefix and the "please finish before ..." boilerplate. */
    private static String tidy(String s) {
        String t = clean(s);
        for (int i = 0; i < 4; i++) {
            java.util.regex.Matcher m = LEAD_TIME.matcher(t);
            if (!m.find()) break;
            t = t.substring(m.end()).trim();
        }
        java.util.regex.Matcher tail = TAIL_NOISE.matcher(t);
        if (tail.find() && tail.start() > 4) t = t.substring(0, tail.start()).trim();
        java.util.regex.Matcher d = INLINE_DATE.matcher(t);
        if (d.find() && d.start() > 6) t = t.substring(0, d.start()).trim();
        t = t.replaceAll("[\\s·|、,，;；]+$", "").trim();
        if (t.length() > 60) t = t.substring(0, 60).trim();
        return t;
    }

    /** Whether a DOM / text candidate really looks like a homework entry. */
    private static boolean usable(String t) {
        if (t == null) return false;
        String s = t.trim();
        if (s.length() < 4) return false;
        if (isBoilerplate(s)) return false;
        if (!KEYWORD.matcher(s).find()) return false;
        if (COURSE_NOISE.matcher(s).find()) return false;
        if (COURSE_CODE_TAIL.matcher(s).find()) return false;
        if (s.endsWith("班") || s.endsWith("课")) return false;
        if (s.contains("：") && s.length() < 8) return false;
        return true;
    }

    private static boolean isBoilerplate(String t) {
        if (t == null) return true;
        String s = t.trim();
        if (s.isEmpty()) return true;
        String[] bad = {"登录", "注册", "首页", "我的", "退出", "帮助", "关于我们", "客服"};
        if (s.length() <= 4) {
            for (String b : bad) if (s.equals(b)) return true;
        }
        return s.startsWith("http") && s.length() < 12;
    }

    private static String anyString(JSONObject o, String[] keys) {
        for (String k : keys) {
            String v = o.optString(k, "");
            if (v != null && !v.isEmpty() && !"null".equalsIgnoreCase(v)) return v;
        }
        return null;
    }

    private static long anyTime(JSONObject o, String[] keys) {
        for (String k : keys) {
            if (!o.has(k)) continue;
            Object v = o.opt(k);
            if (v instanceof Number) {
                long n = ((Number) v).longValue();
                if (n > 100000000000L) return n;
                if (n > 1000000000L) return n * 1000L;
                continue;
            }
            if (v instanceof String) {
                String s = (String) v;
                long parsed = DeadlineParser.parse(s);
                if (parsed > 0) return parsed;
                try {
                    long n = Long.parseLong(s.trim());
                    if (n > 100000000000L) return n;
                    if (n > 1000000000L) return n * 1000L;
                } catch (Exception ignored) {
                }
            }
        }
        return 0L;
    }

    private static String clean(String s) {
        if (s == null) return "";
        String t = s.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
        while (t.startsWith("·") || t.startsWith("-") || t.startsWith("|")) {
            t = t.substring(1).trim();
        }
        return t;
    }
}