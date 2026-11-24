package com.dsh.toolbox.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** One block on the timetable: a course occupying dayOfWeek x [startSection, endSection]. */
public class Timetable {

    public String name = "";
    public String teacher = "";
    public String room = "";
    public int day;          // 1 = Monday ... 7 = Sunday
    public int start;        // 1-based section index
    public int end;          // inclusive
    public int[] weeks = new int[0];

    public String timeLabel() {
        return "第" + start + (end > start ? "-" + end : "") + "节";
    }

    public String weekLabel() {
        if (weeks.length == 0) return "";
        if (weeks.length == 1) return "第" + weeks[0] + "周";
        int min = weeks[0];
        int max = weeks[weeks.length - 1];
        boolean cont = true;
        for (int i = 1; i < weeks.length; i++) if (weeks[i] != weeks[i - 1] + 1) cont = false;
        if (cont) {
            StringBuilder sb = new StringBuilder(min + "-" + max + "周");
            boolean allOdd = true;
            boolean allEven = true;
            for (int w : weeks) {
                if (w % 2 == 0) allOdd = false;
                else allEven = false;
            }
            if (allOdd && weeks.length > 1) sb.append("(单)");
            else if (allEven && weeks.length > 1) sb.append("(双)");
            return sb.toString();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < weeks.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(weeks[i]);
        }
        return sb.append("周").toString();
    }

    public boolean inWeek(int week) {
        if (weeks.length == 0) return true;
        for (int w : weeks) if (w == week) return true;
        return false;
    }

    public String key() {
        return day + "|" + start + "|" + end + "|" + name + "|" + room;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("name", name);
            o.put("teacher", teacher);
            o.put("room", room);
            o.put("day", day);
            o.put("start", start);
            o.put("end", end);
            JSONArray a = new JSONArray();
            for (int w : weeks) a.put(w);
            o.put("weeks", a);
        } catch (JSONException ignored) {
        }
        return o;
    }

    public static Timetable fromJson(JSONObject o) {
        Timetable t = new Timetable();
        t.name = o.optString("name");
        t.teacher = o.optString("teacher");
        t.room = o.optString("room");
        t.day = o.optInt("day");
        t.start = o.optInt("start");
        t.end = o.optInt("end", t.start);
        JSONArray a = o.optJSONArray("weeks");
        if (a != null && a.length() > 0) {
            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) list.add(a.optInt(i));
            t.weeks = new int[list.size()];
            for (int i = 0; i < list.size(); i++) t.weeks[i] = list.get(i);
        }
        return t;
    }
}