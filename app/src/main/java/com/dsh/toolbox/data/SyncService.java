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
 * 真正干活的同步服务：以「前台服务」身份运行，国产 ROM 才不会在几秒内把进程掐掉。
 */
public class SyncService extends Service {

    private static final String CHANNEL = "sync_service";
    private static final int NOTIFICATION_ID = 4202;

    private volatile boolean running;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        note(this, "service onStart");
        startForeground(NOTIFICATION_ID, build());
        if (running) return START_NOT_STICKY;
        running = true;
        new Thread(() -> {
            try {
                // 先试着拿一次实时 cookie（最多等 4s，卡住就放弃，用存档继续）。
                CookieStore.captureWithTimeout(this, 4000);
                note(this, "cookie capture done");
                SyncAll.Report r = SyncAll.run(this);
                note(this, "service sync done kept=" + r.kept);
            } catch (Throwable t) {
                note(this, "service sync failed " + t);
            } finally {
                SyncAlarm.schedule(this);
                running = false;
                try {
                    if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
                    else stopForeground(true);
                } catch (Throwable ignored) {
                }
                stopSelf();
            }
        }, "sync-service").start();
        return START_NOT_STICKY;
    }

    /** 追加一行到 files/alarm.log，方便出问题时排查闹钟到底有没有醒。 */
    static void note(Context ctx, String line) {
        try {
            String stamp = new java.text.SimpleDateFormat("M/d HH:mm:ss",
                    java.util.Locale.CHINA).format(new java.util.Date());
            java.io.File f = new java.io.File(ctx.getFilesDir(), "alarm.log");
            if (f.length() > 64 * 1024) f.delete();
            java.io.FileOutputStream out = new java.io.FileOutputStream(f, true);
            out.write((stamp + "  " + line + "\n").getBytes("UTF-8"));
            out.close();
        } catch (Throwable ignored) {
        }
    }

    private Notification build() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "后台同步",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("定时刷新作业时短暂显示");
            nm.createNotificationChannel(ch);
        }
        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_clock)
                .setContentTitle("正在同步作业")
                .setContentText("查一下三个平台有没有新作业…")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}