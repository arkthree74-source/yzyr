package com.dsh.toolbox.data;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Cached course lists plus the user's per-platform course selection. */
public final class CourseStore {

    private CourseStore() {
    }

    private static File file(Context ctx, String source) {
        return new File(ctx.getFilesDir(), "courses_" + source + ".json");
    }

    public static List<Course> load(Context ctx, String source) {
        List<Course> out = new ArrayList<>();
        File f = file(ctx, source);
        if (!f.exists()) return out;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int read = in.read(buf);
            if (read <= 0) return out;
            JSONArray arr = new JSONArray(new String(buf, 0, read, StandardCharsets.UTF_8));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) out.add(Course.fromJson(o));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void save(Context ctx, List<Course> courses) {
        try {
            JSONArray arr = new JSONArray();
            for (Course c : courses) arr.put(c.toJson());
            try (FileOutputStream out = new FileOutputStream(file(ctx, courses.isEmpty()
                    ? "none" : courses.get(0).source))) {
                out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    public static void save(Context ctx, String source, List<Course> courses) {
        try {
            JSONArray arr = new JSONArray();
            for (Course c : courses) arr.put(c.toJson());
            try (FileOutputStream out = new FileOutputStream(file(ctx, source))) {
                out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    /** Merge freshly fetched courses into the cache, keeping existing selection flags. */
    public static List<Course> merge(Context ctx, String source, List<Course> fresh) {
        List<Course> old = load(ctx, source);
        List<Course> out = new ArrayList<>(fresh);
        for (Course c : out) {
            for (Course o : old) {
                if (o.key().equals(c.key())) {
                    c.selected = o.selected;
                    break;
                }
            }
        }
        save(ctx, source, out);
        return out;
    }

    public static List<Course> selected(Context ctx, String source) {
        List<Course> out = new ArrayList<>();
        for (Course c : load(ctx, source)) if (c.selected) out.add(c);
        return out;
    }

    public static int selectedCount(Context ctx, String source) {
        return selected(ctx, source).size();
    }

    public static boolean anySelected(Context ctx) {
        for (String s : Sources.ALL_KEYS) {
            if (selectedCount(ctx, s) > 0) return true;
        }
        return false;
    }

    public static boolean anySelected(Context ctx, String source) {
        return selectedCount(ctx, source) > 0;
    }
}