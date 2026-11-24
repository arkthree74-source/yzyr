package com.dsh.toolbox.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 雨课堂 reader. Everything runs against the same web endpoints the mobile page uses,
 * authenticated by the WebView cookies, so no password handling happens here.
 *
 * <p>Flow mirroring what a student does by hand: open the course, look at 「未完成」,
 * and read the title + deadline of every entry that is still open.
 */
public final class YktSync {

    /** 「未完成」entry type ids → short labels shown on the card. */
    private static final int TYPE_EXERCISE = 6;
    private static final int TYPE_INTERACT = 4;
    private static final int TYPE_QUIZ = 5;
    private static final int TYPE_MATERIAL = 3;
    private static final int TYPE_COURSEWARE = 7;
    private static final int TYPE_CLASS_ACTIVITY = 8;
    private static final int TYPE_UNKNOWN_9 = 9;

    /**
     * Everything the course lists as 「未完成」 counts as work — including courseware and
     * reading material that the teacher opened as an assignment. What we do drop are the
     * in-class pop-ups (弹题/课堂活动) and other live-class items, which teachers fire
     * during a lecture and which the student cannot "hand in" later.
     */
    private static boolean isWork(int type) {
        return type != TYPE_CLASS_ACTIVITY && type != TYPE_UNKNOWN_9;
    }

    private YktSync() {
    }

    public static List<Course> courses() throws Exception {
        String body = YktClient.learningList();
        if (body == null) throw new IllegalStateException("empty response");
        JSONObject root = new JSONObject(body);
        JSONArray arr = root.optJSONArray("data");
        List<Course> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Course c = new Course();
            c.source = Sources.YKT;
            c.courseId = o.optLong("course_id");
            c.classroomId = o.optLong("classroom_id");
            c.name = o.optString("course_name");
            c.classroom = o.optString("classroom_name");
            c.term = Course.termOf(c.classroom);
            long uni = o.optLong("university_id", 0);
            if (uni > 0) YktClient.universityId = String.valueOf(uni);
            JSONObject t = o.optJSONObject("teacher");
            if (t != null) c.teacher = t.optString("name");
            if (c.courseId == 0 || c.classroomId == 0 || c.name.isEmpty()) continue;
            out.add(c);
        }
        return out;
    }

    public static String courseUrl(Course c) {
        return "https://www.yuketang.cn/m/v2/course/normalcourse/logs/" + c.courseId + "/" + c.classroomId;
    }

    /**
     * Pull the still-open work of every course the user ticked.
     *
     * @param log optional diagnostics sink
     */
    public static int syncSelected(Context ctx, StringBuilder log) {
        List<Course> picked = CourseStore.selected(ctx, Sources.YKT);
        if (log != null) log.append("selected courses=").append(picked.size()).append('\n');
        int total = 0;
        int failed = 0;
        java.util.Set<String> keep = new java.util.HashSet<>();
        for (Course c : picked) {
            keep.add(c.name);
            if (c.skuId <= 0) c.skuId = lookupSku(c, log);
            if (c.skuId <= 0) {
                failed++;
                if (log != null) log.append("  ").append(c.name).append(" no sku, skipped\n");
                continue;
            }
            List<Homework> items = fetchTodo(ctx, c, log);
            if (items == null) {
                // 网络或登录态出问题：保留旧数据，绝不当作“这门课没有作业”清空
                failed++;
                continue;
            }
            total += items.size();
            Store.get(ctx).replaceCourse(Sources.YKT, c.name, items);
            if (log != null) {
                log.append("  ").append(c.name).append(": kept=").append(items.size()).append('\n');
            }
        }
        // 时间戳照常记：哪怕只有几门课失败，也算“这次跑过了”，否则 catchUp 会认为一直没同步过，
        // 每次打开应用都立刻重扫 37 门课（实测就是这么烧流量的）。
        Store.get(ctx).setLastSync(Sources.YKT, System.currentTimeMillis());
        CourseStore.save(ctx, Sources.YKT, CourseStore.load(ctx, Sources.YKT));
        if (failed > 0) {
            if (log != null) log.append("ykt incomplete failed=").append(failed)
                    .append(", skipped purge to keep cached rows\n");
            return total;
        }
        int purged = Store.get(ctx).purgeUntracked(Sources.YKT, keep);
        if (log != null) log.append("purged stale rows=").append(purged).append('\n');
        return total;
    }

    /** sku id only shows up in the classroom payload, so cache it on the course. */
    private static long lookupSku(Course c, StringBuilder log) {
        try {
            String body = YktClient.classroomLogs(c.courseId, c.classroomId);
            if (body == null) return 0;
            JSONObject data = new JSONObject(body).optJSONObject("data");
            if (data == null) return 0;
            JSONObject classRoom = data.optJSONObject("classroom");
            if (classRoom == null) return 0;
            long uni = classRoom.optLong("university_id", 0);
            if (uni > 0) YktClient.universityId = String.valueOf(uni);
            return classRoom.optLong("sku_id", 0);
        } catch (Exception e) {
            if (log != null) log.append("  sku lookup failed ").append(e).append('\n');
            return 0;
        }
    }

    private static List<Homework> fetchTodo(Context ctx, Course course, StringBuilder log) {
        List<Homework> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        try {
            String body = YktClient.todoList(course.classroomId, course.skuId);
            if (body == null) return null;
            write(ctx, "todo_" + course.classroomId + ".json", body);
            JSONObject root = new JSONObject(body);
            JSONObject data = root.optJSONObject("data");
            if (data == null) {
                if (log != null) log.append("  ").append(course.name).append(" no data: ")
                        .append(body.length() > 200 ? body.substring(0, 200) : body).append('\n');
                return null;
            }
            JSONObject count = data.optJSONObject("count");
            if (log != null && count != null) {
                log.append("  count ").append(course.name).append(' ').append(count.toString()).append('\n');
            }
            JSONArray arr = data.optJSONArray("result");
            int dropped = 0;
            if (arr == null) return out;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                int type = o.optInt("type", 0);
                if (!isWork(type)) {
                    dropped++;
                    continue;
                }
                String title = tidy(o.optString("name"));
                if (title.isEmpty()) continue;
                long due = o.optLong("score_deadline_ts", 0);
                if (due <= 0) due = DeadlineParser.parse(o.optString("score_deadline"));
                // 没有截止时间的（很古早的历史数据）不爬；已逾期的照收，交给界面分组
                if (due <= 0) {
                    dropped++;
                    continue;
                }
                Homework h = new Homework();
                long leaf = o.optLong("leaf_id", 0);
                h.id = "ykt:" + (leaf > 0 ? leaf : title.hashCode());
                h.source = Sources.YKT;
                h.course = course.name;
                h.title = title;
                h.dueAt = due;
                h.dueText = o.optString("score_deadline");
                h.url = o.optString("link_url", courseUrl(course));
                h.kind = kindOf(o.optInt("type", 0));
                h.done = false;
                // 开放型（自选）作业过了截止时间也能补交 → 不进「已逾期」组
                h.lateOk = o.optBoolean("is_open_type", false) && due < now;
                out.add(h);
            }
            if (log != null) {
                log.append("  ").append(course.name).append(" raw=").append(arr.length())
                        .append(" dropped=").append(dropped).append('\n');
            }
        } catch (Exception e) {
            if (log != null) log.append("  ").append(course.name).append(" FAILED ").append(e).append('\n');
            return null;
        }
        return out;
    }

    static String kindOf(int type) {
        switch (type) {
            case TYPE_EXERCISE:
                return "作业";
            case TYPE_INTERACT:
                return "互动作业";
            case TYPE_QUIZ:
                return "测验";
            case TYPE_MATERIAL:
                return "资料";
            case TYPE_COURSEWARE:
                return "课件作业";
            case TYPE_CLASS_ACTIVITY:
                return "课堂";
            default:
                return "";
        }
    }

    static String tidy(String s) {
        if (s == null) return "";
        String t = s.replace('\n', ' ').replace('\r', ' ').trim();
        while (t.contains("  ")) t = t.replace("  ", " ");
        return t.length() > 120 ? t.substring(0, 120) : t;
    }

    public static void write(Context ctx, String name, String content) {
        try (FileOutputStream out = new FileOutputStream(new File(ctx.getFilesDir(), name))) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }
}