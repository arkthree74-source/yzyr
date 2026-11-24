package com.dsh.toolbox;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.dsh.toolbox.data.CourseStore;
import com.dsh.toolbox.data.Homework;
import com.dsh.toolbox.data.Sources;
import com.dsh.toolbox.data.Store;
import com.dsh.toolbox.data.YktClient;
import com.dsh.toolbox.ui.GuideFlow;
import com.dsh.toolbox.ui.GuideOverlay;
import com.google.android.material.appbar.MaterialToolbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PlatformsActivity extends AppCompatActivity {

    private Store store;
    private int shownStep = -1;
    private boolean showedLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_platforms);
        store = Store.get(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        bind(R.id.cardYkt, Sources.ALL[0]);
        bind(R.id.cardZhs, Sources.ALL[1]);
        bind(R.id.cardCx, Sources.ALL[2]);
        bind(R.id.cardKtp, Sources.ALL[3]);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus(Sources.ALL[0], R.id.cardYkt);
        refreshStatus(Sources.ALL[1], R.id.cardZhs);
        refreshStatus(Sources.ALL[2], R.id.cardCx);
        refreshStatus(Sources.ALL[3], R.id.cardKtp);
        maybeGuide();
    }

    private void bind(final int cardId, final Sources.Info info) {
        View card = findViewById(cardId);
        int color = ContextCompat.getColor(this, info.colorRes);

        TextView badge = card.findViewById(R.id.badge);
        badge.setText(info.letter);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        badge.setBackground(g);

        TextView name = card.findViewById(R.id.name);
        name.setText(info.name);

        View open = card.findViewById(R.id.open);
        open.setOnClickListener(v -> openWeb(info));
        card.setOnClickListener(v -> openCourses(info));

        refreshStatus(info, cardId);
    }

    private void refreshStatus(Sources.Info info, int cardId) {
        View card = findViewById(cardId);
        TextView status = card.findViewById(R.id.status);
        List<Homework> all = store.all();
        int count = 0;
        int undone = 0;
        for (Homework h : all) {
            if (!info.key.equals(h.source)) continue;
            count++;
            if (!h.done) undone++;
        }
        int picked = CourseStore.selectedCount(this, info.key);
        long last = store.lastSync(info.key);
        if (count == 0) {
            status.setText(picked > 0
                    ? "已选 " + picked + " 门 · 点「登录」把页面打开一次就能同步"
                    : "未选课 · 点这里选择要监控的课程");
        } else {
            status.setText(picked + " 门 · 待完成 " + undone + " 条"
                    + (last > 0 ? " · " + timeLabel(last) : ""));
        }
    }

    /** First-run walkthrough: log in, tap the platform card, then head back home. */
    private void maybeGuide() {
        int step = GuideFlow.step(this);
        if (step == GuideFlow.PLATFORMS) {
            if (!YktClient.loggedIn()) {
                if (showedLogin) return;
                showedLogin = true;
                View card = findViewById(R.id.cardYkt);
                List<GuideOverlay.Item> items = new ArrayList<>();
                items.add(new GuideOverlay.Item(card.findViewById(R.id.open),
                        "第一步：登录雨课堂",
                        "点这个「登录」，在弹出的网页里用微信或学号登录一次，之后就不用再登了。", true));
                GuideOverlay.show(this, items, null, null,
                        () -> GuideFlow.set(this, GuideFlow.DONE));
                return;
            }
            if (shownStep == step) return;
            shownStep = step;
            List<GuideOverlay.Item> items = new ArrayList<>();
            items.add(new GuideOverlay.Item(findViewById(R.id.cardYkt),
                    "接下来：点「雨课堂」卡片",
                    "进去勾选你要盯的课，App 之后每 6 小时自动帮你查。", true));
            GuideOverlay.show(this, items, null, null,
                    () -> GuideFlow.set(this, GuideFlow.DONE));
        } else if (step == GuideFlow.BACK) {
            if (shownStep == step) return;
            shownStep = step;
            List<GuideOverlay.Item> items = new ArrayList<>();
            items.add(new GuideOverlay.Item(findViewById(R.id.toolbar),
                    "课程保存好了",
                    "回到主页看一眼：点右上角「同步」，三平台的作业就会抓回来。"));
            GuideOverlay.show(this, items, "回主页",
                    this::finish,
                    () -> GuideFlow.set(this, GuideFlow.DONE));
        }
    }

    /** Card tap: choose courses to monitor. */
    private void openCourses(Sources.Info info) {
        Intent i = new Intent(this, CoursesActivity.class);
        i.putExtra(CoursesActivity.EXTRA_SOURCE, info.key);
        startActivity(i);
    }

    /** "打开" button: log in / browse the official site. */
    private void openWeb(Sources.Info info) {
        if (Sources.YKT.equals(info.key)) {
            showYktLoginTips(info);
            return;
        }
        startWeb(info);
    }

    private void startWeb(Sources.Info info) {
        Intent i = new Intent(this, WebActivity.class);
        i.putExtra(WebActivity.EXTRA_SOURCE, info.key);
        i.putExtra(WebActivity.EXTRA_URL, info.startUrl);
        startActivity(i);
    }

    /** 雨课堂's QR login is fragile on a single phone, so spell out the two workarounds. */
    private void showYktLoginTips(Sources.Info info) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("雨课堂扫码登录小贴士")
                .setMessage("雨课堂的二维码经常扫不上，任选一种办法：\n\n"
                        + "① 用另一台设备扫：让另一台手机／平板打开微信，扫本机屏幕上的二维码。\n\n"
                        + "② 小窗扫码：把本应用切成小窗或分屏，先给二维码截图；再打开微信「扫一扫 → 相册」，选中那张截图。\n\n"
                        + "登录过程中别把本应用切到后台，否则二维码会刷新失效。")
                .setPositiveButton("我知道了，去登录", (d, w) -> startWeb(info))
                .setNegativeButton("取消", null)
                .show();
    }

    private String timeLabel(long t) {
        Calendar now = Calendar.getInstance();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        boolean today = now.get(Calendar.YEAR) == c.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR);
        if (today) return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(t));
        return new SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(new Date(t));
    }
}