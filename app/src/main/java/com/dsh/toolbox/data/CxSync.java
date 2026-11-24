package com.dsh.toolbox.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 学习通 (chaoxing) reader.
 *
 * <p>The 学习规划助手 endpoint {@code /stat2/learning/plan/recommended-course-list} returns the
 * whole course list together with {@code nearTaskList} per course and a flattened
 * {@code allNearTasks}; each task carries {@code name}, {@code courseName}, {@code endTime}
 * (milliseconds) and {@code url}. That single call is enough for our need.</p>
 */
public final class CxSync {

    private CxSync() {
    }

    public static List<Course> courses(Context ctx) throws Exception {
        CxClient.init(ctx);
        String body = CxClient.plan();
        write(ctx, "cx_plan.json", body);
        JSONObject root = new JSONObject(body);
        JSONObject data = root.optJSONObject("data");
        List<Course> out = new ArrayList<>();
        if (data == null) return out;
        collect(data.optJSONArray("courseList"), out);
        collect(data.optJSONArray("otherCourseList"), out);
        return out;
    }

    private static void collect(JSONArray arr, List<Course> out) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String name = o.optString("courseName");
            if (name.isEmpty()) continue;
            Course c = new Course();
            c.source = Sources.CX;
            c.courseId = o.optLong("courseId");
            c.classroomId = o.optLong("clazzId");
            c.name = name;
            c.classroom = o.optString("className");
            c.term = termOf(name);
            out.add(c);
        }
    }

    /** 「大学物理(2-2)2026年秋季」→ 2026秋 ; falls back to the leading 20xx年 when unparseable. */
    static String termOf(String courseName) {
        if (courseName == null) return "";
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("(20\\d{2})\\s*年\\s*(春|夏|秋|冬)").matcher(courseName);
        if (m.find()) return m.group(1) + ("冬".equals(m.group(2)) ? "秋" : m.group(2));
        m = java.util.regex.Pattern.compile("(20\\d{2})").matcher(courseName);
        if (m.find()) return m.group(1);
        return "";
    }

    public static String courseUrl(Course c) {
        if (c == null) return CxClient.HOST + "/stat2-vue/studyPlanAssistant";
        return "https://mooc1.chaoxing.com/mooc-ans/course/" + c.courseId + "/" + c.classroomId;
    }

    /** Pull the near-task list, keep what belongs to a monitored course, and drop overdue rows. */
    public static int syncSelected(Context ctx, StringBuilder log) {
        CxClient.init(ctx);
        List<Course> picked = CourseStore.selected(ctx, Sources.CX);
        Set<String> keep = new HashSet<>();
        for (Course c : picked) keep.add(c.name);
        if (log != null) log.append("cx selected=").append(picked.size()).append('\n');
        if (keep.isEmpty()) {
            Store.get(ctx).purgeUntracked(Sources.CX, keep);
            Store.get(ctx).setLastSync(Sources.CX, System.currentTimeMillis());
            return 0;
        }

        String body;
        try {
            body = CxClient.plan();
        } catch (Throwable t) {
            if (log != null) log.append("cx FAILED ").append(t).append('\n');
            return 0;
        }
        if (body == null || body.isEmpty()) return 0;
        write(ctx, "cx_plan.json", body);

        long now = System.currentTimeMillis();
        List<Homework> fresh = new ArrayList<>();
        int raw = 0;
        try {
            JSONObject root = new JSONObject(body);
            int code = root.optInt("code", 0);
            JSONObject data = root.optJSONObject("data");
            if (code != 0 || data == null) {
                // 登录过期：code 非 0 或 data 缺失，保留旧数据不清空
                if (log != null) log.append("cx not logged in? code=").append(code)
                        .append(" msg=").append(root.optString("msg")).append('\n');
                return 0;
            }
            JSONArray tasks = data.optJSONArray("allNearTasks");
            if (tasks != null) {
                for (int i = 0; i < tasks.length(); i++) {
                    JSONObject o = tasks.optJSONObject(i);
                    if (o == null) continue;
                    String course = o.optString("courseName");
                    if (!keep.contains(course)) continue;
                    raw++;
                    String title = tidy(o.optString("name"));
                    if (title.isEmpty()) continue;
                    long due = o.optLong("endTime", 0);
                    if (due <= 0 || due < now) continue;
                    Homework h = new Homework();
                    String id = o.optString("id");
                    h.id = "cx:" + (id.isEmpty() ? (course + "|" + title).hashCode() : id);
                    h.source = Sources.CX;
                    h.course = course;
                    h.title = title;
                    h.dueAt = due;
                    h.dueText = o.optString("timeRange");
                    h.url = o.optString("url");
                    h.kind = "exam".equals(o.optString("eventType")) ? "测验" : "作业";
                    fresh.add(h);
                }
            }
        } catch (Exception e) {
            if (log != null) log.append("cx parse FAILED ").append(e).append('\n');
            return 0;
        }

        int total = 0;
        Set<String> done = new HashSet<>();
        for (Homework h : fresh) {
            if (!done.add(h.course)) continue;
            List<Homework> forCourse = new ArrayList<>();
            for (Homework x : fresh) if (x.course.equals(h.course)) forCourse.add(x);
            total += Store.get(ctx).replaceCourse(Sources.CX, h.course, forCourse);
        }
        for (String course : keep) {
            if (!done.contains(course)) Store.get(ctx).replaceCourse(Sources.CX, course, new ArrayList<>());
        }
        if (!keep.isEmpty()) Store.get(ctx).purgeUntracked(Sources.CX, keep);
        Store.get(ctx).setLastSync(Sources.CX, System.currentTimeMillis());
        if (log != null) log.append("cx raw=").append(raw).append(" kept=").append(total).append('\n');
        return total;
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