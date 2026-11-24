package com.dsh.toolbox.data;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.dsh.toolbox.MainActivity;
import com.dsh.toolbox.R;

/** Local notifications: only used to tell the user that a login has to be refreshed. */
public final class Notifier {

    private static final String CHANNEL = "sync";
    private static final int ID_SESSION = 1001;

    private Notifier() {
    }

    public static void sessionLost(Context ctx) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "同步提醒",
                        NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("登录过期、同步失败时提醒");
                nm.createNotificationChannel(ch);
            }
            Intent open = new Intent(ctx, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(ctx, 0, open,
                    Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
            NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL)
                    .setSmallIcon(R.drawable.ic_clock)
                    .setContentTitle("有个平台需要重新登录")
                    .setContentText("登录状态过期了，点这里重新登录一次，作业清单不会丢")
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);
            NotificationManagerCompat.from(ctx).notify(ID_SESSION, b.build());
        } catch (Throwable ignored) {
        }
    }
}