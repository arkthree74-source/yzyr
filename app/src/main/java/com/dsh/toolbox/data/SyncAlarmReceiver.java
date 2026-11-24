package com.dsh.toolbox.data;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

/**
 * 闹钟到点（或开机、应用更新）时拉起同步。
 *
 * <p>先排下一次闹钟，再去同步：即使这次同步被系统杀掉，6 小时后还有下一次，不会彻底停摆。</p>
 */
public class SyncAlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent == null || intent.getAction() == null ? "" : intent.getAction();
        boolean wake = SyncAlarm.ACTION_SYNC.equals(action)
                || Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action);
        if (!wake) return;

        boolean auto = SyncSettings.auto(ctx);
        if (auto) SyncAlarm.schedule(ctx);
        else SyncAlarm.cancel(ctx);
        // 开了「后台保活」的设备，开机后要把常驻服务重新拉起来（否则通知栏那条会消失）。
        if (auto && KeepAliveService.on(ctx)) KeepAliveService.enable(ctx);
        if (!auto) return;
        if (!CourseStore.anySelected(ctx)) return;
        start(ctx);
    }

    /** 优先用前台服务（最稳）；被系统拦住就退回 WorkManager。 */
    static void start(Context ctx) {
        SyncService.note(ctx, "alarm fired -> start service");
        try {
            Intent svc = new Intent(ctx, SyncService.class);
            androidx.core.content.ContextCompat.startForegroundService(ctx, svc);
        } catch (Throwable t) {
            SyncService.note(ctx, "foreground service refused: " + t);
            try {
                OneTimeWorkRequest once = new OneTimeWorkRequest.Builder(SyncWorker.class).build();
                WorkManager.getInstance(ctx)
                        .enqueueUniqueWork("homework-now-sync", ExistingWorkPolicy.KEEP, once);
                SyncService.note(ctx, "fell back to WorkManager");
            } catch (Throwable ignored) {
            }
        }
    }
}