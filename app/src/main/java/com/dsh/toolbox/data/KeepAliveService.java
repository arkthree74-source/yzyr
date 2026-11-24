package com.dsh.toolbox.data;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import com.dsh.toolbox.R;

/**
 * 可选常驻服务：一条低优先级通知换「进程基本不被杀」。
 *
 * <p>国产 ROM 把 App 杀掉之后，闹钟广播不一定叫得醒它。开启这个开关后进程一直活着，6 小时闹钟、
 * 开机自启都能正常生效；代价是通知栏常驻一个小图标。</p>
 */
public class KeepAliveService extends Service {

    private static final String CHANNEL = "keepalive";
    private static final int NOTIFICATION_ID = 4203;

    public static void enable(Context ctx) {
        Store.get(ctx).prefs().edit().putBoolean("keepalive", true).apply();
        try {
            androidx.core.content.ContextCompat.startForegroundService(ctx,
                    new Intent(ctx, KeepAliveService.class));
        } catch (Throwable ignored) {
        }
    }

    public static void disable(Context ctx) {
        Store.get(ctx).prefs().edit().putBoolean("keepalive", false).apply();
        try {
            ctx.stopService(new Intent(ctx, KeepAliveService.class));
        } catch (Throwable ignored) {
        }
    }

    public static boolean on(Context ctx) {
        return Store.get(ctx).prefs().getBoolean("keepalive", false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!on(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIFICATION_ID, build());
        try {
            SyncAlarm.schedule(this);
        } catch (Throwable ignored) {
        }
        return START_STICKY;
    }

    private Notification build() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "后台常驻",
                    NotificationManager.IMPORTANCE_MIN);
            ch.setDescription("保持作业自动同步不被系统杀掉");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_clock)
                .setContentTitle("作业自动同步已开启")
                .setContentText("每 6 小时自动查一次，可在「后台保活」里关闭")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}