package com.dsh.toolbox.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 自动同步的设置项（频率等）。放在这里统一读，闹钟 / WorkManager / 设置页都看这一份。
 */
public final class SyncSettings {

    /** 可选频率（小时）。0 = 不自动同步。 */
    public static final int[] OPTIONS = {0, 1, 2, 4, 6, 12, 24, 48, 168};

    public static final int DEFAULT_HOURS = 6;

    private static final String KEY_HOURS = "sync_hours";

    private SyncSettings() {
    }

    public static int hours(Context ctx) {
        return prefs(ctx).getInt(KEY_HOURS, DEFAULT_HOURS);
    }

    /** 保存并立刻重排（关掉时会把闹钟与周期任务一起撤掉）。 */
    public static void setHours(Context ctx, int hours) {
        prefs(ctx).edit().putInt(KEY_HOURS, hours).apply();
        SyncScheduler.reschedule(ctx);
    }

    public static boolean auto(Context ctx) {
        return hours(ctx) > 0;
    }

    public static long intervalMs(Context ctx) {
        return hours(ctx) * 60L * 60L * 1000L;
    }

    /** 「每 6 小时一次」这类文案。 */
    public static String label(Context ctx) {
        return label(hours(ctx));
    }

    public static String label(int hours) {
        if (hours <= 0) return "不自动同步";
        if (hours == 24) return "每天一次";
        if (hours == 48) return "每两天一次";
        if (hours == 168) return "每周一次";
        return "每 " + hours + " 小时一次";
    }

    private static SharedPreferences prefs(Context ctx) {
        return Store.get(ctx).prefs();
    }
}