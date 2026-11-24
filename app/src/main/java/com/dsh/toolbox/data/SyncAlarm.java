package com.dsh.toolbox.data;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * 定时同步的「硬」闹钟。
 *
 * <p>WorkManager 的周期任务在国产 ROM 上经常被省电策略推迟（实测 vivo 上 6 小时的周期被排到 12
 * 小时之后才跑）。所以另外用 AlarmManager 按用户选的频率叫一次：闹钟到点后由
 * {@link SyncAlarmReceiver} 拉起前台服务真正去同步，醒来概率比纯 JobScheduler 高得多。</p>
 *
 * <p>频率来自 {@link SyncSettings#hours(Context)}；选「不自动同步」时这里会撤掉闹钟。</p>
 */
public final class SyncAlarm {

    public static final String ACTION_SYNC = "com.dsh.toolbox.action.SYNC_ALARM";

    private static final int REQUEST = 4201;

    private SyncAlarm() {
    }

    /** 按当前设置排下一次；关掉自动同步时撤掉已有闹钟。 */
    public static void schedule(Context ctx) {
        try {
            long interval = SyncSettings.intervalMs(ctx);
            if (interval <= 0) {
                cancel(ctx);
                return;
            }
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            long at = System.currentTimeMillis() + interval;
            PendingIntent pi = pending(ctx);
            if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) {
                // 允许精确闹钟时用精确版：偏差最小，国产 ROM 也更不敢随便推迟。
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, at, pi);
            }
            Store.get(ctx).prefs().edit().putLong("next_sync", at).apply();
        } catch (Throwable ignored) {
        }
    }

    /** 撤掉闹钟（改成「不自动同步」时用）。 */
    public static void cancel(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(pending(ctx));
        } catch (Throwable ignored) {
        }
        Store.get(ctx).prefs().edit().putLong("next_sync", 0L).apply();
    }

    /** 距离下次同步还有多少毫秒，没排过返回 0。 */
    public static long nextIn(Context ctx) {
        long at = Store.get(ctx).prefs().getLong("next_sync", 0L);
        if (at <= 0) return 0L;
        long left = at - System.currentTimeMillis();
        return Math.max(0L, left);
    }

    static PendingIntent pending(Context ctx) {
        Intent i = new Intent(ctx, SyncAlarmReceiver.class);
        i.setAction(ACTION_SYNC);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(ctx, REQUEST, i, flags);
    }
}