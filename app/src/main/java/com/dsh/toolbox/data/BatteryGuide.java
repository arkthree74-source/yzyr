package com.dsh.toolbox.data;

import android.app.AlarmManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.core.app.NotificationManagerCompat;

/**
 * 各家国产 ROM 的后台权限设置引导。
 *
 * <p>没有一家厂商提供标准接口读「自启动是否已允许」，所以这里只能做两件事：</p>
 * <ol>
 *   <li>把能查的状态查出来（电池优化、精确闹钟、通知）；</li>
 *   <li>按机型提供「去设置」的直跳入口，跳不过去就落到应用详情页。</li>
 * </ol>
 */
public final class BatteryGuide {

    public enum Kind {AUTO_START, BATTERY, EXACT_ALARM, NOTIFICATION, APP_DETAIL}

    /** 各厂商自启动管理页的候选组件（按命中率排序，跳不过就换下一个）。 */
    private static final String[][] AUTO_START = {
            // vivo / iQOO
            {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
            {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"},
            // 华为
            {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
            {"com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"},
            // 荣耀（与华为分家后是独立包名）
            {"com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
            {"com.hihonor.systemmanager", "com.hihonor.systemmanager.optimize.process.ProtectActivity"},
            // 小米 / 红米
            {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
            // OPPO / 一加 / realme
            {"com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"},
            {"com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"},
            {"com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"},
            {"com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"},
            // 三星
            {"com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"},
            // 魅族
            {"com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"},
            // 通用兜底：部分 ROM 有「应用启动管理」
            {"com.android.settings", "com.android.settings.Settings$HighPowerApplicationsActivity"},
    };

    private BatteryGuide() {
    }

    /** 机型名，用来在界面上说人话。 */
    public static String brand() {
        String m = String.valueOf(Build.MANUFACTURER).toLowerCase();
        if (m.contains("vivo") || m.contains("iqoo")) return "vivo / iQOO";
        if (m.contains("honor")) return "荣耀";
        if (m.contains("huawei")) return "华为";
        if (m.contains("xiaomi") || m.contains("redmi") || m.contains("poco")) return "小米 / 红米";
        if (m.contains("oppo") || m.contains("realme") || m.contains("oneplus")) return "OPPO / 一加 / realme";
        if (m.contains("samsung")) return "三星";
        if (m.contains("meizu")) return "魅族";
        if (m.contains("motorola")) return "摩托罗拉";
        if (m.contains("sony")) return "索尼";
        return Build.MANUFACTURER;
    }

    /** 自启动权限的入口文案（各家叫法不一样）。 */
    public static String autoStartName() {
        String b = brand();
        if (b.startsWith("vivo")) return "自启动";
        if (b.equals("华为")) return "应用启动管理";
        if (b.equals("荣耀")) return "应用启动管理";
        if (b.startsWith("小米")) return "自启动";
        if (b.startsWith("OPPO")) return "自启动";
        if (b.equals("三星")) return "自动运行应用程序";
        if (b.equals("魅族")) return "后台应用管理";
        return "自启动 / 后台运行";
    }

    /** 电池优化是否已经对本 App 放行。 */
    public static boolean ignoringBattery(Context ctx) {
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean exactAlarmAllowed(Context ctx) {
        try {
            if (Build.VERSION.SDK_INT < 31) return true;
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean notificationsEnabled(Context ctx) {
        try {
            return NotificationManagerCompat.from(ctx).areNotificationsEnabled();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 打开对应设置页。返回 true 表示成功跳转；跳不过去时退到本应用详情页。
     */
    public static boolean open(Context ctx, Kind kind) {
        Intent i = intent(ctx, kind);
        if (i != null) {
            try {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
                return true;
            } catch (Throwable ignored) {
            }
        }
        return openDetails(ctx);
    }

    private static Intent intent(Context ctx, Kind kind) {
        switch (kind) {
            case AUTO_START:
                for (String[] c : AUTO_START) {
                    try {
                        Intent i = new Intent();
                        i.setComponent(new ComponentName(c[0], c[1]));
                        if (ctx.getPackageManager().resolveActivity(i, 0) != null) return i;
                    } catch (Throwable ignored) {
                    }
                }
                return null;
            case BATTERY:
                try {
                    Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    i.setData(Uri.parse("package:" + ctx.getPackageName()));
                    return i;
                } catch (Throwable t) {
                    return new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                }
            case EXACT_ALARM:
                if (Build.VERSION.SDK_INT >= 31) {
                    try {
                        Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                        i.setData(Uri.parse("package:" + ctx.getPackageName()));
                        return i;
                    } catch (Throwable ignored) {
                    }
                }
                return null;
            case NOTIFICATION:
                try {
                    Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                    i.putExtra(Settings.EXTRA_APP_PACKAGE, ctx.getPackageName());
                    return i;
                } catch (Throwable ignored) {
                    return null;
                }
            case APP_DETAIL:
            default:
                return null;
        }
    }

    private static boolean openDetails(Context ctx) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + ctx.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}