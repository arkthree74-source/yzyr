package com.dsh.toolbox.data;

import org.json.JSONObject;

/** One course/classroom the user is enrolled in on some platform. */
public class Course {

    public String source = "";
    public long courseId;
    public long classroomId;
    public long skuId;
    public String name = "";
    public String classroom = "";
    public String teacher = "";
    public String term = "";
    public boolean selected;

    public String key() {
        return source + ":" + courseId + ":" + classroomId;
    }

    /** "2026秋" style prefix pulled out of the classroom name. */
    public static String termOf(String classroom) {
        if (classroom == null) return "";
        String s = classroom.trim();
        int dash = s.indexOf('-');
        if (dash > 0 && dash <= 8) return s.substring(0, dash);
        return "";
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("source", source);
            o.put("courseId", courseId);
            o.put("classroomId", classroomId);
            o.put("skuId", skuId);
            o.put("name", name);
            o.put("classroom", classroom);
            o.put("teacher", teacher);
            o.put("term", term);
            o.put("selected", selected);
        } catch (Exception ignored) {
        }
        return o;
    }

    public static Course fromJson(JSONObject o) {
        Course c = new Course();
        c.source = o.optString("source");
        c.courseId = o.optLong("courseId");
        c.classroomId = o.optLong("classroomId");
        c.skuId = o.optLong("skuId");
        c.name = o.optString("name");
        c.classroom = o.optString("classroom");
        c.teacher = o.optString("teacher");
        c.term = o.optString("term");
        c.selected = o.optBoolean("selected");
        return c;
    }

    public String subtitle() {
        StringBuilder sb = new StringBuilder();
        if (!teacher.isEmpty()) sb.append(teacher);
        if (!classroom.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(classroom);
        }
        return sb.toString();
    }
}