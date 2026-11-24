package com.dsh.toolbox.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 智慧树 reader.
 *
 * <p>The mobile site exposes one call that lists every unfinished task across all courses
 * (「待办任务 → 待完成」), which is exactly what we need, plus a course list for the picker.
 */
public final class ZhsSync {

    private ZhsSync() {
    }

    public static List<Course> courses(Context ctx) throws Exception {
        ZhsClient.init(ctx);
        String body = ZhsClient.courseList();
        if (body == null) throw new IllegalStateException("empty response");
        JSONObject root = new JSONObject(body);
        JSONObject data = root.optJSONObject("data");
        List<Course> out = new ArrayList<>();
        if (data == null) return out;
        JSONArray arr = data.optJSONArray("list");
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Course c = new Course();
            c.source = Sources.ZHS;
            c.courseId = o.optString("courseId").hashCode();
            c.name = o.optString("courseName");
            c.classroom = o.optString("termName");
            c.term = termOf(o.optString("termName"));
            JSONArray teachers = o.optJSONArray("teachers");
            if (teachers != null && teachers.length() > 0) {
                JSONObject t = teachers.optJSONObject(0);
                if (t != null) c.teacher = t.optString("teacherName");
            }
            if (c.name.isEmpty()) continue;
            out.add(c);
        }
        return out;
    }

    static String termOf(String termName) {
        if (termName == null || termName.isEmpty()) return "";
        String digits = termName.replaceAll("[^0-9]", "");
        if (digits.length() >= 4) {
            String year = digits.substring(0, 4);
            if (termName.contains("秋冬") || termName.contains("秋")) return year + "秋";
            if (termName.contains("春夏") || termName.contains("春")) return year + "春";
            return year;
        }
        return termName;
    }

    public static String courseUrl(Course c) {
        return "https://hike-teaching-center.polymas.com/custom-stu-hike/studyTask";
    }

    /** Pull every open task, keep the ones that belong to a monitored course. */
    public static int syncSelected(Context ctx, StringBuilder log) {
        ZhsClient.init(ctx);
        List<Course> picked = CourseStore.selected(ctx, Sources.ZHS);
        Set<String> keep = new HashSet<>();
        for (Course c : picked) keep.add(c.name);
        if (log != null) log.append("zhs selected=").append(picked.size()).append('\n');
        if (keep.isEmpty()) {
            Store.get(ctx).purgeUntracked(Sources.ZHS, keep);
            Store.get(ctx).setLastSync(Sources.ZHS, System.currentTimeMillis());
            return 0;
        }

        String body;
        try {
            body = ZhsClient.todoList(1, 100);
        } catch (Throwable t) {
            if (log != null) log.append("zhs FAILED ").append(t).append('\n');
            return 0;
        }
        if (body == null) return 0;
        write(ctx, "zhs_todo.json", body);

        int total = 0;
        long now = System.currentTimeMillis();
        List<Homework> fresh = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(body);
            int code = root.optInt("code", 0);
            JSONObject data = root.optJSONObject("data");
            if (code != 200 || data == null) {
                // 登录过期时接口会返回 code!=200 且 data 为 null：保留旧数据，不清空
                if (log != null) log.append("zhs not logged in? code=").append(code).append('\n');
                return 0;
            }
            JSONArray arr = data.optJSONArray("list");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    String course = o.optString("courseName");
                    if (!keep.contains(course)) continue;
                    String title = tidy(o.optString("title"));
                    if (title.isEmpty()) continue;
                    long due = parseDate(o.optString("endTime"));
                    if (due <= 0) continue;
                    Homework h = new Homework();
                    String taskId = o.optString("activityTaskId");
                    h.id = "zhs:" + (taskId.isEmpty() ? (course + "|" + title).hashCode() : taskId);
                    h.source = Sources.ZHS;
                    h.course = course;
                    h.title = title;
                    h.dueAt = due;
                    h.dueText = o.optString("endTime");
                    h.url = courseUrl(null);
                    int type = o.optInt("homeworkType", 1);
                    h.kind = type == 2 ? "测验" : "作业";
                    h.done = false;
                    h.lateOk = o.optBoolean("longTermOpen", false) && due < now;
                    fresh.add(h);
                }
            }
        } catch (Exception e) {
            if (log != null) log.append("zhs parse FAILED ").append(e).append('\n');
            return 0; // 解析失败不动缓存
        }

        // replace per course so finished tasks disappear
        Set<String> done = new HashSet<>();
        for (Homework h : fresh) {
            if (done.add(h.course)) {
                List<Homework> forCourse = new ArrayList<>();
                for (Homework x : fresh) if (x.course.equals(h.course)) forCourse.add(x);
                total += Store.get(ctx).replaceCourse(Sources.ZHS, h.course, forCourse);
            }
        }
        for (String course : keep) {
            if (!done.contains(course)) Store.get(ctx).replaceCourse(Sources.ZHS, course, new ArrayList<>());
        }
        Store.get(ctx).setLastSync(Sources.ZHS, System.currentTimeMillis());
        if (log != null) log.append("zhs kept=").append(total).append('\n');
        return total;
    }

    static long parseDate(String s) {
        if (s == null) return 0;
        String v = s.trim();
        if (v.isEmpty() || "null".equals(v)) return 0;
        String[] patterns = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd"};
        for (String p : patterns) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(p, Locale.CHINA);
                java.util.Date d = f.parse(v);
                if (d != null) return d.getTime();
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    static String tidy(String s) {
        if (s == null) return "";
        String t = s.replace('\n', ' ').replace('\r', ' ').trim();
        while (t.contains("  ")) t = t.replace("  ", " ");
        return t.length() > 120 ? t.substring(0, 120) : t;
    }

    static void write(Context ctx, String name, String content) {
        YktSync.write(ctx, name, content);
    }
}