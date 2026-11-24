package com.dsh.toolbox.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the 教务系统「学生个人课表」page into {@link Timetable} blocks.
 *
 * <p>Currently tuned for 强智科技 jwxt (中国石油大学 jwxt.upc.edu.cn/jsxsd): the table has a
 * header row of 星期日..星期六 followed by one row per 大节, and every cell holds
 * {@code <font title="教师">} / {@code <font title="周次(节次)">} / {@code <font title="教室">}
 * runs separated by dashed lines. A generic 正方-style fallback is kept for other schools.
 */
public final class JwParser {

    private static final Pattern TR = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern CELL = Pattern.compile("<t[hd]([^>]*)>(.*?)</t[hd]>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern FONT = Pattern.compile("<font([^>]*)>(.*?)</font>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE = Pattern.compile("title\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME = Pattern.compile("name\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEG_SPLIT = Pattern.compile("-{4,}");
    private static final Pattern ROW_ANY = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern SECTION_PAIR =
            Pattern.compile("\\((\\d{1,2})\\s*[,，]\\s*(\\d{1,2})\\s*小节\\)");
    /** "(01,02小节)" / "(03,04,05小节)" — 强智 mixes 2- and 3-section 大节. */
    private static final Pattern SECTION_LIST =
            Pattern.compile("\\(([\\d\\s,，]+?)\\s*小节\\)");
    private static final Pattern TIME_RANGE =
            Pattern.compile("(\\d{1,2}:\\d{2})\\s*[-~—]\\s*(\\d{1,2}:\\d{2})");
    private static final Pattern ROWSPAN = Pattern.compile("rowspan\\s*=\\s*[\"']?(\\d+)", Pattern.CASE_INSENSITIVE);

    private JwParser() {
    }

    public static List<Timetable> parse(String html) {
        if (html == null || html.isEmpty()) return new ArrayList<>();
        String table = tableById(html, "timetable");
        if (table.isEmpty()) table = pickTable(html);
        if (table.isEmpty()) return new ArrayList<>();
        if (table.contains("kbcontent")) return parseQzt(table);
        return parseGeneric(table);
    }

    // ---------------- 强智科技 layout ----------------

    private static List<Timetable> parseQzt(String table) {
        Map<String, Timetable> merged = new LinkedHashMap<>();
        Matcher rm = TR.matcher(table);
        int bodyIndex = 0;
        int[] dayOfCol = null;

        while (rm.find()) {
            String rowHtml = rm.group(1);
            List<String> cells = new ArrayList<>();
            List<String> cellAttrs = new ArrayList<>();
            Matcher cm = CELL.matcher(rowHtml);
            while (cm.find()) {
                cellAttrs.add(cm.group(1));
                cells.add(cm.group(2));
            }
            if (cells.isEmpty()) continue;

            String joined = strip(cells.get(0));
            if (dayOfCol == null && containsDayName(rowHtml)) {
                dayOfCol = new int[cells.size()];
                for (int c = 0; c < cells.size(); c++) dayOfCol[c] = dayOf(cells.get(c));
                continue;
            }
            if (dayOfCol == null) dayOfCol = new int[]{0, 7, 1, 2, 3, 4, 5, 6};

            bodyIndex++;
            int start;
            int end;
            int[] range = sectionsOf(joined);
            if (range != null) {
                start = range[0];
                end = range[1];
            } else {
                Matcher pair = SECTION_PAIR.matcher(joined);
                if (pair.find()) {
                    start = Integer.parseInt(pair.group(1));
                    end = Integer.parseInt(pair.group(2));
                } else {
                    start = bodyIndex * 2 - 1;
                    end = start + 1;
                }
            }

            for (int c = 1; c < cells.size(); c++) {
                int day = c < dayOfCol.length ? dayOfCol[c] : c;
                if (day <= 0) continue;
                for (String seg : SEG_SPLIT.split(cells.get(c))) {
                    Timetable t = segment(seg, day, start, end);
                    if (t == null) continue;
                    String key = t.day + "|" + t.start + "|" + t.end + "|" + t.name;
                    Timetable old = merged.get(key);
                    if (old == null) {
                        merged.put(key, t);
                    } else {
                        old.weeks = union(old.weeks, t.weeks);
                        if (old.room.isEmpty()) old.room = t.room;
                        if (old.teacher.isEmpty()) old.teacher = t.teacher;
                    }
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    /** "(03,04,05小节)" -> {3,5}; null when the row header does not list 小节. */
    private static int[] sectionsOf(String joined) {
        Matcher m = SECTION_LIST.matcher(joined);
        if (!m.find()) return null;
        int min = Integer.MAX_VALUE;
        int max = 0;
        for (String p : m.group(1).split("[,，\\s]+")) {
            if (p.isEmpty()) continue;
            try {
                int n = Integer.parseInt(p);
                min = Math.min(min, n);
                max = Math.max(max, n);
            } catch (NumberFormatException ignored) {
            }
        }
        if (max <= 0) return null;
        return new int[]{Math.max(1, min), max};
    }

    /**
     * Reads the 大节 header rows into "startSection -> 08:00-09:35" so the grid can show
     * class times next to the section numbers.
     */
    public static Map<Integer, String> sectionTimes(String html) {
        Map<Integer, String> out = new LinkedHashMap<>();
        if (html == null || html.isEmpty()) return out;
        String table = tableById(html, "timetable");
        if (table.isEmpty()) table = pickTable(html);
        if (table.isEmpty()) return out;
        Matcher rm = TR.matcher(table);
        while (rm.find()) {
            String rowHtml = rm.group(1);
            Matcher cm = CELL.matcher(rowHtml);
            if (!cm.find()) continue;
            String first = strip(cm.group(2));
            if (!first.contains("小节")) continue;
            int[] range = sectionsOf(first);
            if (range == null) continue;
            Matcher tm = TIME_RANGE.matcher(first);
            if (!tm.find()) continue;
            out.put(range[0], tm.group(1) + "-" + tm.group(2));
        }
        return out;
    }

    private static Timetable segment(String seg, int day, int start, int end) {
        String name = "";
        String teacher = "";
        String room = "";
        String weeks = "";
        Matcher fm = FONT.matcher(seg);
        while (fm.find()) {
            String attrs = fm.group(1);
            String text = strip(fm.group(2));
            if (text.isEmpty()) continue;
            String title = attr(TITLE, attrs);
            String nm = attr(NAME, attrs);
            if ("wkxx".equals(nm) || "ktmcstr".equals(nm) || "bzstr".equals(nm)
                    || "xsks".equals(nm) || "tzdbh".equals(nm) || "jxlmc".equals(nm)) continue;
            if ("周次(节次)".equals(title)) {
                if (weeks.isEmpty()) weeks = text;
            } else if ("教师".equals(title)) {
                if (teacher.isEmpty()) teacher = text;
            } else if ("教室".equals(title)) {
                if (room.isEmpty()) room = text;
            } else if (title.isEmpty() && name.isEmpty()) {
                name = text;
            }
        }
        if (name.isEmpty()) return null;
        Timetable t = new Timetable();
        t.name = name.length() > 40 ? name.substring(0, 40) : name;
        t.teacher = teacher;
        t.room = room;
        t.day = day;
        t.start = start;
        t.end = end;
        t.weeks = weeksOf(weeks);
        return t;
    }

    private static String attr(Pattern p, String attrs) {
        Matcher m = p.matcher(attrs);
        return m.find() ? m.group(1).trim() : "";
    }

    // ---------------- generic (正方) fallback ----------------

    private static List<Timetable> parseGeneric(String table) {
        List<Timetable> out = new ArrayList<>();
        List<List<String>> grid = new ArrayList<>();
        List<List<String>> attrs = new ArrayList<>();
        Matcher rm = TR.matcher(table);
        while (rm.find()) {
            List<String> cells = new ArrayList<>();
            List<String> at = new ArrayList<>();
            Matcher cm = CELL.matcher(rm.group(1));
            while (cm.find()) {
                at.add(cm.group(1));
                cells.add(cm.group(2));
            }
            grid.add(cells);
            attrs.add(at);
        }
        if (grid.isEmpty()) return out;

        int firstBody = 0;
        int[] dayOfCol = new int[40];
        boolean haveHeader = false;
        for (int r = 0; r < grid.size(); r++) {
            List<String> row = grid.get(r);
            boolean header = false;
            for (int c = 0; c < row.size(); c++) {
                int d = dayOf(row.get(c));
                if (d > 0) {
                    dayOfCol[c] = d;
                    header = true;
                }
            }
            if (header && !haveHeader) {
                haveHeader = true;
                continue;
            }
            firstBody = r;
            break;
        }
        if (!haveHeader) {
            for (int c = 2; c <= 8; c++) dayOfCol[c] = c - 1;
        }

        List<Set<Integer>> used = new ArrayList<>();
        for (int i = 0; i < grid.size() + 40; i++) used.add(new LinkedHashSet<>());
        for (int r = firstBody; r < grid.size(); r++) {
            int col = 0;
            for (int c = 0; c < grid.get(r).size(); c++) {
                while (used.get(r).contains(col)) col++;
                Matcher rsM = ROWSPAN.matcher(attrs.get(r).get(c));
                int rs = rsM.find() ? Integer.parseInt(rsM.group(1)) : 1;
                for (int dr = 0; dr < rs; dr++) if (r + dr < used.size()) used.get(r + dr).add(col);
                String raw = strip(grid.get(r).get(c));
                if (!raw.isEmpty() && !raw.contains("节次") && raw.length() > 2) {
                    int day = dayOfCol[Math.min(col, dayOfCol.length - 1)];
                    if (day <= 0) day = col;
                    out.add(buildGeneric(raw, day, r - firstBody + 1, r - firstBody + rs));
                }
                col++;
            }
        }
        return out;
    }

    private static Timetable buildGeneric(String raw, int day, int start, int end) {
        String[] lines = raw.split("\n");
        Timetable t = new Timetable();
        t.name = lines.length > 0 ? lines[0].trim() : raw;
        if (t.name.isEmpty()) return null;
        String weeks = "";
        for (int i = 1; i < lines.length; i++) {
            String s = lines[i].trim();
            if (s.isEmpty()) continue;
            if (s.contains("周") && weeks.isEmpty()) weeks = s;
            else if (t.room.isEmpty() && (s.contains("教") || s.contains("楼") || s.contains("室"))) t.room = s;
            else if (t.teacher.isEmpty() && s.length() <= 12) t.teacher = s;
        }
        t.day = day;
        t.start = start;
        t.end = Math.max(start, end);
        t.weeks = weeksOf(weeks);
        return t;
    }

    // ---------------- helpers ----------------

    private static String tableById(String html, String id) {
        Matcher m = Pattern.compile("<table[^>]*id\\s*=\\s*[\"']" + Pattern.quote(id) + "[\"'][^>]*>(.*?)</table>",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(html);
        return m.find() ? m.group(0) : "";
    }

    private static String pickTable(String html) {
        String best = "";
        int bestScore = 0;
        Matcher m = Pattern.compile("<table[^>]*>(.*?)</table>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(html);
        while (m.find()) {
            String body = m.group();
            int score = count(body, "周") * 2 + count(body, "星期") * 3 + count(body, "节");
            if (score > bestScore) {
                bestScore = score;
                best = body;
            }
        }
        return bestScore > 6 ? best : "";
    }

    private static int count(String s, String needle) {
        int n = 0;
        int i = 0;
        while ((i = s.indexOf(needle, i)) >= 0) {
            n++;
            i += needle.length();
        }
        return n;
    }

    private static boolean containsDayName(String html) {
        return html.contains("星期一") || html.contains("周一") || html.contains("Sunday")
                || html.contains("星期日");
    }

    private static int dayOf(String cellHtml) {
        String s = strip(cellHtml);
        if (s.isEmpty() || s.length() > 8) return 0;
        boolean week = s.contains("星期") || s.contains("周");
        if (!week) return 0;
        if (s.contains("一")) return 1;
        if (s.contains("二")) return 2;
        if (s.contains("三")) return 3;
        if (s.contains("四")) return 4;
        if (s.contains("五")) return 5;
        if (s.contains("六")) return 6;
        if (s.contains("日") || s.contains("天")) return 7;
        return 0;
    }

    /** HTML -> plain text preserving line breaks. */
    private static String strip(String html) {
        if (html == null) return "";
        String s = html.replaceAll("(?i)<br\\s*/?>", "\n");
        s = s.replaceAll("(?i)</(div|p|li|tr|td|th)>", "\n");
        s = s.replaceAll("<[^>]*>", "");
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'");
        s = s.replace('\u00a0', ' ');
        StringBuilder out = new StringBuilder();
        for (String line : s.split("\n")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (out.length() > 0) out.append('\n');
            out.append(t);
        }
        return out.toString();
    }

    /** "1-17(周)[01-02...周]", "1,4,7-9(周)", "3,6" -> sorted week list. */
    private static int[] weeksOf(String line) {
        if (line == null || line.isEmpty()) return new int[0];
        String s = line;
        int bracket = s.indexOf('[');
        if (bracket > 0) s = s.substring(0, bracket);
        s = s.replace("(周)", "").replace("周", "").replace(" ", "");
        Set<Integer> set = new LinkedHashSet<>();
        boolean odd = s.contains("单");
        boolean even = s.contains("双");
        s = s.replace("单", "").replace("双", "");
        for (String part : s.split("[,，]")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            Matcher range = Pattern.compile("(\\d{1,2})-(\\d{1,2})").matcher(p);
            if (range.find()) {
                int a = Integer.parseInt(range.group(1));
                int b = Integer.parseInt(range.group(2));
                for (int w = a; w <= b && w <= 30; w++) {
                    if (odd && w % 2 == 0) continue;
                    if (even && w % 2 == 1) continue;
                    set.add(w);
                }
            } else {
                try {
                    set.add(Integer.parseInt(p));
                } catch (Exception ignored) {
                }
            }
        }
        int[] arr = new int[set.size()];
        int i = 0;
        for (int w : set) arr[i++] = w;
        Arrays.sort(arr);
        return arr;
    }

    private static int[] union(int[] a, int[] b) {
        Set<Integer> set = new LinkedHashSet<>();
        for (int w : a) set.add(w);
        for (int w : b) set.add(w);
        int[] arr = new int[set.size()];
        int i = 0;
        for (int w : set) arr[i++] = w;
        Arrays.sort(arr);
        return arr;
    }
}