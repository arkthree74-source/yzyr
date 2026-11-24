package com.dsh.toolbox.data;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * Background refresh schedule.
 *
 * <p>频率取自 {@link SyncSettings#hours(Context)}（默认 6 小时，可选 1 小时到一周，0 = 关闭）。
 * WorkManager 只当兜底（国产 ROM 常常推迟它），真正靠 {@link SyncAlarm} 的闹钟 + 前台服务。</p>
 */
public final class SyncScheduler {

    /** 应用打开时，距上次同步超过这么久就立刻补一次。 */
    private static final long CATCH_UP_MS = 2L * 60L * 60L * 1000L;

    private static final String UNIQUE = "homework-periodic-sync";

    private SyncScheduler() {
    }

    /** 按设置排任务：关掉时同时撤掉周期任务与闹钟。 */
    public static void ensureScheduled(Context ctx) {
        int hours = SyncSettings.hours(ctx);
        try {
            if (hours <= 0) {
                WorkManager.getInstance(ctx).cancelUniqueWork(UNIQUE);
                SyncAlarm.cancel(ctx);
                return;
            }
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();
            PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                    SyncWorker.class, hours, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .setInitialDelay(10, TimeUnit.MINUTES)
                    .build();
            // UPDATE so existing installs pick up a changed interval instead of keeping the old one.
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                    UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, request);
        } catch (Throwable ignored) {
        }
        // 闹钟是主力（WorkManager 常被省电策略推迟），已经有近期闹钟就不要往后推。
        long left = SyncAlarm.nextIn(ctx);
        long interval = SyncSettings.intervalMs(ctx);
        if (left <= 0 || left > interval) SyncAlarm.schedule(ctx);
    }

    /** 改了频率之后重排：周期任务要重建，闹钟直接按新间隔排。 */
    public static void reschedule(Context ctx) {
        ensureScheduled(ctx);
        SyncAlarm.schedule(ctx);
    }

    /** Fire a sync right now if the cached data is stale (called when the app comes to front). */
    public static void catchUp(Context ctx) {
        try {
            if (!SyncSettings.auto(ctx)) return;
            long last = 0;
            for (String s : Sources.ALL_KEYS) {
                last = Math.max(last, Store.get(ctx).lastSync(s));
            }
            if (last > 0 && System.currentTimeMillis() - last < CATCH_UP_MS) return;
            if (!CourseStore.anySelected(ctx)) return;
            // 前台拉起服务：此时应用可见，不存在「后台启动前台服务」的限制。
            SyncAlarmReceiver.start(ctx);
        } catch (Throwable ignored) {
        }
    }
}