package com.dsh.toolbox.ui;

import android.app.Activity;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dsh.toolbox.R;
import com.dsh.toolbox.data.BatteryGuide;
import com.dsh.toolbox.data.KeepAliveService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

/**
 * 「后台保活」弹窗：电池优化 / 精确闹钟 / 通知 / 自启动 + 常驻通知开关。
 * 主页面与设置页共用。
 */
public final class KeepAliveDialog {

    private KeepAliveDialog() {
    }

    public static void show(final Activity act) {
        final int pad = dp(act, 18);
        ScrollView scroll = new ScrollView(act);
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(act, 6), pad, dp(act, 4));
        scroll.addView(root);

        TextView intro = new TextView(act);
        intro.setText("各家手机都会管后台。想让它每天自己查作业，下面几项逐个打开就行。");
        intro.setTextSize(13);
        intro.setTextColor(act.getColor(R.color.text2));
        intro.setLineSpacing(dp(act, 2), 1f);
        root.addView(intro, new LinearLayout.LayoutParams(-1, -2));

        final boolean battery = BatteryGuide.ignoringBattery(act);
        final boolean exact = BatteryGuide.exactAlarmAllowed(act);
        final boolean notif = BatteryGuide.notificationsEnabled(act);

        row(act, root, "电池优化", battery ? "已放行，不会被冻结" : "还限制着，后台容易被冻结",
                battery, "去关闭", () -> BatteryGuide.open(act, BatteryGuide.Kind.BATTERY));
        row(act, root, "精确闹钟", exact ? "已允许，到点更准" : "没允许，提醒可能被推迟",
                exact, "去允许", () -> BatteryGuide.open(act, BatteryGuide.Kind.EXACT_ALARM));
        row(act, root, "通知", notif ? "已开启" : "关着的话「该重新登录了」的提醒发不出来",
                notif, "去开启", () -> BatteryGuide.open(act, BatteryGuide.Kind.NOTIFICATION));
        row(act, root, BatteryGuide.brand() + " · " + BatteryGuide.autoStartName(),
                "允许后，被系统清理掉也能按时叫醒",
                true, "去设置", () -> BatteryGuide.open(act, BatteryGuide.Kind.AUTO_START));

        CheckBox cb = new CheckBox(act);
        cb.setText("常驻通知（最稳，代价是一直显示一条通知）");
        cb.setTextSize(13);
        cb.setTextColor(act.getColor(R.color.text));
        cb.setPadding(0, dp(act, 10), 0, 0);
        cb.setChecked(KeepAliveService.on(act));
        cb.setOnCheckedChangeListener((v, on) -> {
            if (on) KeepAliveService.enable(act);
            else KeepAliveService.disable(act);
        });
        root.addView(cb, new LinearLayout.LayoutParams(-1, -2));

        TextView foot = new TextView(act);
        foot.setText("没开常驻通知时：系统把你清掉之后，下一次同步可能要等下次打开 App 才补跑。"
                + "开了之后闹钟到点就能自己跑。");
        foot.setTextSize(12);
        foot.setTextColor(act.getColor(R.color.text3));
        foot.setPadding(0, dp(act, 8), 0, 0);
        foot.setLineSpacing(dp(act, 2), 1f);
        root.addView(foot, new LinearLayout.LayoutParams(-1, -2));

        new MaterialAlertDialogBuilder(act)
                .setTitle("后台保活")
                .setView(scroll)
                .setPositiveButton("刷新状态", (d, w) -> show(act))
                .setNegativeButton("关闭", null)
                .show();
    }

    private static void row(Activity act, LinearLayout parent, String title, String status,
                            boolean ok, String action, Runnable onClick) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(act, 10), 0, dp(act, 10));

        LinearLayout texts = new LinearLayout(act);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(act);
        t.setText(title);
        t.setTextSize(14);
        t.setTextColor(act.getColor(R.color.text));
        TextView s = new TextView(act);
        s.setText(status);
        s.setTextSize(11.5f);
        s.setTextColor(act.getColor(ok ? R.color.brand : R.color.danger));
        s.setPadding(0, dp(act, 2), 0, 0);
        texts.addView(t);
        texts.addView(s);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        MaterialButton b = new MaterialButton(act, null,
                com.google.android.material.R.attr.materialButtonStyle);
        b.setText(action);
        b.setTextSize(13);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0));
        b.setTextColor(act.getColor(R.color.brand));
        b.setInsetTop(0);
        b.setInsetBottom(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(act, 6), dp(act, 6), dp(act, 6), dp(act, 6));
        b.setOnClickListener(v -> onClick.run());
        row.addView(b);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private static int dp(Activity act, int v) {
        return Math.round(v * act.getResources().getDisplayMetrics().density);
    }

    /** 频率文案，设置页与引导共用。 */
    public static String hoursLabel(int hours) {
        if (hours <= 0) return "不自动同步";
        if (hours == 24) return "每天一次";
        if (hours == 48) return "每两天一次";
        if (hours == 168) return "每周一次";
        return String.format(Locale.CHINA, "每 %d 小时一次", hours);
    }
}