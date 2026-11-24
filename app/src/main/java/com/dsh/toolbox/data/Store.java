package com.dsh.toolbox.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class Store {

    private static Store instance;

    private final Context app;
    private final List<Homework> items = new ArrayList<>();
    private boolean loaded = false;

    private Store(Context c) {
        this.app = c.getApplicationContext();
    }

    public static synchronized Store get(Context c) {
        if (instance == null) instance = new Store(c);
        instance.ensureLoaded();
        return instance;
    }

    private File file() {
        return new File(app.getFilesDir(), "homework.json");
    }

    private synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        File f = file();
        if (!f.exists()) return;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int read = in.read(buf);
            if (read <= 0) return;
            JSONArray arr = new JSONArray(new String(buf, 0, read, StandardCharsets.UTF_8));
            for (int i = 0; i < arr.length(); i++) {
                items.add(Homework.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
    }

    private synchronized void persist() {
        try {
            JSONArray arr = new JSONArray();
            for (Homework h : items) arr.put(h.toJson());
            File tmp = new File(app.getFilesDir(), "homework.json.tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
            }
            File f = file();
            if (f.exists() && !f.delete()) {
                // keep going, fall back to direct write below
            }
            if (!tmp.renameTo(f)) {
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
                }
                tmp.delete();
            }
        } catch (Exception ignored) {
        }
    }

    public synchronized List<Homework> all() {
        return new ArrayList<>(items);
    }

    public synchronized int size() {
        return items.size();
    }

    public synchronized Homework byId(String id) {
        for (Homework h : items) if (h.id.equals(id)) return h;
        return null;
    }

    /**
     * Merge scraped items. Existing entries keep their done flag.
     *
     * @return number of newly added items
     */
    public synchronized int merge(List<Homework> incoming) {
        int added = 0;
        for (Homework in : incoming) {
            Homework existing = find(in);
            if (existing != null) {
                if (!in.course.isEmpty()) existing.course = in.course;
                if (!in.url.isEmpty()) existing.url = in.url;
                if (in.dueAt > 0 && existing.dueAt == 0) existing.dueAt = in.dueAt;
                if (!in.dueText.isEmpty()) existing.dueText = in.dueText;
                existing.fetchedAt = System.currentTimeMillis();
            } else {
                if (in.id == null || in.id.isEmpty()) {
                    in.id = UUID.randomUUID().toString();
                }
                in.fetchedAt = System.currentTimeMillis();
                items.add(in);
                added++;
            }
        }
        persist();
        return added;
    }

    private Homework find(Homework in) {
        for (Homework h : items) {
            if (!h.source.equals(in.source)) continue;
            if (h.dueAt == in.dueAt && h.key().equals(in.key())) return h;
            if (h.displayKey().equals(in.displayKey())) return h;
        }
        return null;
    }

    public synchronized void add(Homework h) {
        if (h.id == null || h.id.isEmpty()) h.id = UUID.randomUUID().toString();
        h.fetchedAt = System.currentTimeMillis();
        items.add(h);
        persist();
    }

    public synchronized void setDone(String id, boolean done) {
        Homework h = byId(id);
        if (h != null) {
            h.done = done;
            persist();
        }
    }

    public synchronized void remove(String id) {
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).id.equals(id)) items.remove(i);
        }
        persist();
    }

    public synchronized void clearSource(String source) {
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).source.equals(source)) items.remove(i);
        }
        persist();
    }

    public synchronized void clearAll() {
        items.clear();
        persist();
    }

    /**
     * Replace everything we know about one course with a fresh snapshot, so that
     * work the student has already finished stops showing up.
     *
     * @return number of items kept
     */
    public synchronized int replaceCourse(String source, String course, List<Homework> fresh) {
        for (int i = items.size() - 1; i >= 0; i--) {
            Homework h = items.get(i);
            if (h.source.equals(source) && h.course.equals(course)) items.remove(i);
        }
        long now = System.currentTimeMillis();
        for (Homework h : fresh) {
            if (h.id == null || h.id.isEmpty()) h.id = UUID.randomUUID().toString();
            h.fetchedAt = now;
            items.add(h);
        }
        persist();
        return fresh.size();
    }

    /**
     * Drop stale rows for a source: anything without a course name (left over from the
     * old "extract this page" mode) or for a course the user no longer monitors.
     *
     * @return number of removed rows
     */
    public synchronized int purgeUntracked(String source, java.util.Set<String> keepCourses) {
        int removed = 0;
        for (int i = items.size() - 1; i >= 0; i--) {
            Homework h = items.get(i);
            if (!h.source.equals(source)) continue;
            if (h.course == null || h.course.isEmpty() || !keepCourses.contains(h.course)) {
                items.remove(i);
                removed++;
            }
        }
        if (removed > 0) persist();
        return removed;
    }

    public synchronized void sortByDue() {
        Collections.sort(items, new Comparator<Homework>() {
            @Override
            public int compare(Homework a, Homework b) {
                if (a.dueAt == 0 && b.dueAt == 0) return 0;
                if (a.dueAt == 0) return 1;
                if (b.dueAt == 0) return -1;
                return Long.compare(a.dueAt, b.dueAt);
            }
        });
    }

    // ---------- per-source meta ----------

    public SharedPreferences prefs() {
        return app.getSharedPreferences("hw", Context.MODE_PRIVATE);
    }

    public String syncUrl(String source) {
        return prefs().getString("sync_" + source, "");
    }

    public void setSyncUrl(String source, String url) {
        prefs().edit().putString("sync_" + source, url).apply();
    }

    public long lastSync(String source) {
        return prefs().getLong("last_" + source, 0L);
    }

    public void setLastSync(String source, long t) {
        prefs().edit().putLong("last_" + source, t).apply();
    }
}