package com.dsh.toolbox.data;

import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DeadlineParser {

    private static final Pattern FULL = Pattern.compile(
            "(20\\d{2})\\s*-\\s*(\\d{1,2})\\s*-\\s*(\\d{1,2})(?:\\s+|T)?(?:(\\d{1,2})\\s*:\\s*(\\d{2}))?");
    private static final Pattern SHORT = Pattern.compile(
            "(?<!\\d)(\\d{1,2})\\s*-\\s*(\\d{1,2})(?:\\s+|T)?(?:(\\d{1,2})\\s*:\\s*(\\d{2}))?");
    private static final Pattern RELATIVE = Pattern.compile(
            "(今天|明天|后天)(?:\\s*(\\d{1,2})\\s*:\\s*(\\d{2}))?");

    private DeadlineParser() {
    }

    public static long parse(String raw) {
        if (raw == null) return 0L;
        String s = raw.trim();
        if (s.isEmpty()) return 0L;
        s = s.replace('：', ':').replace('年', '-').replace('月', '-')
                .replace("日", " ").replace("号", " ")
                .replace('/', '-').replace('.', '-');
        long now = System.currentTimeMillis();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);

        Matcher m = FULL.matcher(s);
        if (m.find()) {
            return build(c, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)), num(m.group(4), 23), num(m.group(5), 59));
        }

        m = RELATIVE.matcher(s);
        if (m.find()) {
            int add = "今天".equals(m.group(1)) ? 0 : ("明天".equals(m.group(1)) ? 1 : 2);
            Calendar d = (Calendar) c.clone();
            d.add(Calendar.DAY_OF_YEAR, add);
            d.set(Calendar.HOUR_OF_DAY, num(m.group(2), 23));
            d.set(Calendar.MINUTE, num(m.group(3), 59));
            d.set(Calendar.SECOND, 0);
            d.set(Calendar.MILLISECOND, 0);
            return d.getTimeInMillis();
        }

        m = SHORT.matcher(s);
        if (m.find()) {
            int month = Integer.parseInt(m.group(1));
            int day = Integer.parseInt(m.group(2));
            if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                long t = build(c, c.get(Calendar.YEAR), month, day, num(m.group(3), 23), num(m.group(4), 59));
                // if the parsed date is far in the past, it is probably next year
                if (t < now - 60L * 24 * 3600 * 1000) {
                    t = build(c, c.get(Calendar.YEAR) + 1, month, day, num(m.group(3), 23), num(m.group(4), 59));
                }
                return t;
            }
        }
        return 0L;
    }

    private static int num(String v, int def) {
        if (v == null || v.isEmpty()) return def;
        try {
            return Integer.parseInt(v);
        } catch (Exception e) {
            return def;
        }
    }

    private static long build(Calendar base, int y, int mo, int d, int h, int mi) {
        Calendar c = (Calendar) base.clone();
        c.set(Calendar.YEAR, y);
        c.set(Calendar.MONTH, mo - 1);
        c.set(Calendar.DAY_OF_MONTH, d);
        c.set(Calendar.HOUR_OF_DAY, h);
        c.set(Calendar.MINUTE, mi);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}