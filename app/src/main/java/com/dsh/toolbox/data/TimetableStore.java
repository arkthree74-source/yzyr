package com.dsh.toolbox.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Persists the imported timetable in filesDir/timetable.json. */
public final class TimetableStore {

    private static final String FILE = "timetable.json";

    private TimetableStore() {
    }

    private static File file(Context c) {
        return new File(c.getFilesDir(), FILE);
    }

    public static synchronized List<Timetable> load(Context c) {
        List<Timetable> out = new ArrayList<>();
        File f = file(c);
        if (!f.exists()) return out;
        try {
            String raw = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) out.add(Timetable.fromJson(o));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static synchronized void save(Context c, List<Timetable> list, String term) {
        JSONArray arr = new JSONArray();
        for (Timetable t : list) arr.put(t.toJson());
        try (FileOutputStream out = new FileOutputStream(file(c))) {
            out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
        Store.get(c).prefs().edit()
                .putLong("timetable_at", System.currentTimeMillis())
                .putString("timetable_term", term == null ? "" : term)
                .apply();
    }

    /** Remembers "startSection -> 08:00-09:35" so the grid can label 上课时间. */
    public static synchronized void saveSectionTimes(Context c, java.util.Map<Integer, String> times) {
        JSONObject o = new JSONObject();
        try {
            if (times != null) {
                for (java.util.Map.Entry<Integer, String> e : times.entrySet()) {
                    o.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
        } catch (Exception ignored) {
        }
        Store.get(c).prefs().edit().putString("timetable_times", o.toString()).apply();
    }

    public static synchronized java.util.Map<Integer, String> sectionTimes(Context c) {
        java.util.Map<Integer, String> out = new java.util.LinkedHashMap<>();
        try {
            String raw = Store.get(c).prefs().getString("timetable_times", "{}");
            JSONObject o = new JSONObject(raw);
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                try {
                    out.put(Integer.parseInt(k), o.optString(k));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static long updatedAt(Context c) {
        return Store.get(c).prefs().getLong("timetable_at", 0);
    }

    public static String term(Context c) {
        return Store.get(c).prefs().getString("timetable_term", "");
    }

    public static final long DAY_MS = 86400000L;

    /** Start (Sunday 00:00) of week 1. Users can adjust it; otherwise it is guessed. */
    public static long start(Context c) {
        long s = Store.get(c).prefs().getLong("timetable_start", 0L);
        if (s > 0) return sundayOf(s);
        return guessStart(term(c));
    }

    public static void setStart(Context c, long millis) {
        Store.get(c).prefs().edit().putLong("timetable_start", sundayOf(millis)).apply();
    }

    /** Sunday 00:00 of the week that contains {@code millis}. */
    public static long sundayOf(long millis) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(millis);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        int dow = cal.get(java.util.Calendar.DAY_OF_WEEK); // Sunday = 1
        return cal.getTimeInMillis() - (dow - 1) * DAY_MS;
    }

    /** 1-based week number for {@code millis}; the school counts Sunday as the first day. */
    public static int weekOf(long millis, long week1Start) {
        long diff = sundayOf(millis) - sundayOf(week1Start);
        long days = Math.round(diff / (double) DAY_MS);
        return (int) (days / 7) + 1;
    }

    public static long weekStart(long week1Start, int week) {
        return sundayOf(week1Start) + (week - 1) * 7 * DAY_MS;
    }

    /**
     * Autumnal term starts on the first Sunday of September, spring term on the first
     * Sunday on/after Feb 20 — close enough to the 校历 for a first guess.
     */
    private static long guessStart(String term) {
        java.util.Calendar now = java.util.Calendar.getInstance();
        int month = now.get(java.util.Calendar.MONTH) + 1;
        int year = now.get(java.util.Calendar.YEAR);
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(20\\d{2})").matcher(term == null ? "" : term);
        if (m.find()) {
            try {
                year = Integer.parseInt(m.group(1));
            } catch (Exception ignored) {
            }
        }
        boolean spring = month >= 2 && month <= 7;
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.clear();
        if (spring) {
            cal.set(year, java.util.Calendar.FEBRUARY, 20);
        } else {
            cal.set(year, java.util.Calendar.SEPTEMBER, 1);
        }
        long t = cal.getTimeInMillis();
        return sundayOf(t + 6 * DAY_MS); // first Sunday on/after that day
    }

    public static void clear(Context c) {
        File f = file(c);
        if (f.exists()) //noinspection ResultOfMethodCallIgnored
            f.delete();
        Store.get(c).prefs().edit().remove("timetable_at").remove("timetable_term").apply();
    }
}