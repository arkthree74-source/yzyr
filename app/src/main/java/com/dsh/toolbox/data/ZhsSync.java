package com.dsh.toolbox.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
        // 这个接口的分页行为各校不同：有的默认只给一页（10~20 门），
        // 所以先按默认拿一次，再按 pageNum/pageSize 逐页补，最后按 courseId 去重。
        List<JSONObject> items = new ArrayList<>();
        java.util.Set<String> ids = new java.util.HashSet<>();
        StringBuilder trace = new StringBuilder();

        String body = ZhsClient.courseList();
        int first = harvest(body, items, ids, trace, "default");
        for (int page = 1; page <= 5 && first >= 10; page++) {
            String pageBody = ZhsClient.courseList("{\"pageNum\":" + page + ",\"pageSize\":100}");
            int before = items.size();
            int got = harvest(pageBody, items, ids, trace, "page" + page);
            if (got == 0 || items.size() == before) break;  // 这一页没有新东西，收工
        }
        if (ctx != null) {
            YktSync.write(ctx, "zhs_courses.json", trace + "\nunique=" + items.size() + "\n\n" + body);
        }

        List<Course> out = new ArrayList<>();
        for (JSONObject o : items) {
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

    /** 把一次响应里的课程塞进 items（按 courseId 去重），返回本次新增条数。 */
    private static int harvest(String body, List<JSONObject> items, java.util.Set<String> ids,
                              StringBuilder trace, String label) {
        if (body == null) {
            trace.append(label).append(": null\n");
            return 0;
        }
        int added = 0;
        try {
            JSONObject root = new JSONObject(body);
            JSONObject data = root.optJSONObject("data");
            JSONArray arr = data == null ? null : data.optJSONArray("list");
            if (arr == null) {
                trace.append(label).append(": code=").append(root.optInt("code"))
                        .append(" msg=").append(root.optString("msg")).append('\n');
                return 0;
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String id = o.optString("courseId");
                String name = o.optString("courseName");
                if (name.isEmpty()) continue;
                if (!ids.add(id.isEmpty() ? "n:" + name : id)) continue;
                items.add(o);
                added++;
            }
            trace.append(label).append(": list=").append(arr.length())
                    .append(" added=").append(added);
            if (data != null) {
                trace.append(" total=").append(data.optInt("totalCount", -1))
                        .append(" pages=").append(data.optInt("totalPage", -1));
            }
            trace.append('\n');
        } catch (Throwable t) {
            trace.append(label).append(": parse failed ").append(t).append('\n');
        }
        return added;
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
        return syncSelected(ctx, log, null);
    }

    /** 最近一次同步的结果说明，给界面显示「为什么是 0 条」用。 */
    public static volatile String lastNote = "";

    /** 同 {@link #syncSelected(Context, StringBuilder)}，额外汇报进度。 */
    public static int syncSelected(Context ctx, StringBuilder log, SyncProgress prog) {
        ZhsClient.init(ctx);
        List<Course> picked = CourseStore.selected(ctx, Sources.ZHS);
        Set<String> keep = new HashSet<>();
        for (Course c : picked) keep.add(c.name);
        if (log != null) log.append("zhs selected=").append(picked.size()).append('\n');
        if (keep.isEmpty()) {
            lastNote = "没有选课";
            Store.get(ctx).purgeUntracked(Sources.ZHS, keep);
            Store.get(ctx).setLastSync(Sources.ZHS, System.currentTimeMillis());
            return 0;
        }
        if (prog != null) {
            prog.plan(1);
            prog.note("智慧树 · 正在取未完成列表");
        }

        // 智慧树的分页接口对 pageSize 敏感：大值会被服务端忽略甚至返回空列表，
        // 所以按 20 条一页翻，最多翻 10 页（200 条）足够覆盖一个学期的作业。
        JSONArray arr = new JSONArray();
        int rawTotal = 0;
        try {
            StringBuilder notes = new StringBuilder();
            for (int page = 1; page <= 10; page++) {
                String body = ZhsClient.todoList(page, 20);
                if (body == null) {
                    if (notes.length() == 0) notes.append("接口无响应");
                    break;
                }
                if (page == 1) write(ctx, "zhs_todo.json", body);
                JSONObject root = new JSONObject(body);
                int code = root.optInt("code", 0);
                JSONObject data = root.optJSONObject("data");
                if (code != 200 || data == null) {
                    notes.append("接口返回 code=").append(code)
                         .append("（").append(root.optString("msg", "")).append("）");
                    if (arr.length() == 0) {
                        lastNote = notes.toString();
                        if (log != null) log.append("zhs FAILED ").append(notes).append('\n');
                        return 0; // 登录过期 / 后端报错：保留旧数据
                    }
                    break;
                }
                int totalCount = data.optInt("totalCount", 0);
                JSONArray list = data.optJSONArray("list");
                if (list == null || list.length() == 0) break;
                for (int i = 0; i < list.length(); i++) {
                    JSONObject o = list.optJSONObject(i);
                    if (o != null) arr.put(o);
                }
                rawTotal = Math.max(rawTotal, totalCount);
                int got = arr.length();
                if (got >= totalCount || got >= 200) break;
            }
            if (log != null) log.append("zhs raw=").append(arr.length()).append('\n');
        } catch (Throwable t) {
            if (log != null) log.append("zhs FAILED ").append(t).append('\n');
            lastNote = "请求异常";
            return 0;
        } finally {
            if (prog != null) prog.step("智慧树 · 解析");
        }
        if (arr.length() == 0 && rawTotal == 0) {
            lastNote = "平台上没有未完成任务";
            Store.get(ctx).setLastSync(Sources.ZHS, System.currentTimeMillis());
            return 0;
        }

        int total = 0;
        long now = System.currentTimeMillis();
        List<Homework> fresh = new ArrayList<>();
        Set<String> skipped = new LinkedHashSet<>();
        StringBuilder skippedList = new StringBuilder();
        try {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    String course = o.optString("courseName");
                    if (course == null || course.trim().isEmpty()) {
                        // 共享课（courseType=4）把课程信息塞在 content 对象里
                        JSONObject content = o.optJSONObject("content");
                        if (content != null) course = content.optString("courseName");
                    }
                    String owner = matchCourse(course, keep);
                    if (owner == null) {
                        if (skipped.add(course)) skippedList.append(course).append(" / ");
                        continue;
                    }
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
        if (total == 0) {
            lastNote = fresh.isEmpty()
                    ? (skipped.isEmpty() ? "平台上没有未完成任务"
                                         : "有 " + skipped.size() + " 门课的任务不在监控范围")
                    : "任务都缺少截止时间";
            if (log != null) log.append("zhs note: ").append(lastNote).append('\n');
        } else {
            lastNote = "";
        }
        if (log != null) {
            log.append("zhs kept=").append(total).append('\n');
            if (skippedList.length() > 0) {
                log.append("zhs skipped by course (").append(skipped.size()).append("): ")
                   .append(skippedList).append('\n');
            }
        }
        return total;
    }

    /**
     * 智慧树的课程名在「课程列表」和「未完成任务」里不一定一字不差
     * （括号全半角、多余空格、"（线上）" 后缀等），所以做三层匹配：
     * 完全相同 → 归一化后相同 → 互相包含。
     */
    static String matchCourse(String taskCourse, Set<String> keep) {
        if (taskCourse == null) return null;
        String t = taskCourse.trim();
        if (t.isEmpty()) return null;
        if (keep.contains(t)) return t;
        String nt = normName(t);
        for (String k : keep) {
            if (normName(k).equals(nt)) return k;
        }
        if (nt.length() >= 4) {
            for (String k : keep) {
                String nk = normName(k);
                if (nk.length() >= 4 && (nk.contains(nt) || nt.contains(nk))) return k;
            }
        }
        return null;
    }

    static String normName(String s) {
        if (s == null) return "";
        String t = s.replace('（', '(').replace('）', ')')
                .replace('【', '[').replace('】', ']')
                .replace("\u3000", " ")
                .replaceAll("\\s+", "")
                .trim();
        return t;
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