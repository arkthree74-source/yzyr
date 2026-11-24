package com.dsh.toolbox;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dsh.toolbox.data.CourseStore;
import com.dsh.toolbox.data.KeepAliveService;
import com.dsh.toolbox.data.Sources;
import com.dsh.toolbox.data.Store;
import com.dsh.toolbox.data.SyncHistory;
import com.dsh.toolbox.data.ShareServer;
import com.dsh.toolbox.data.SyncSettings;
import com.dsh.toolbox.ui.GuideFlow;
import com.dsh.toolbox.ui.GuideOverlay;
import com.dsh.toolbox.ui.KeepAliveDialog;
import com.dsh.toolbox.ui.UpdateFlow;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/**
 * 设置页：自动同步频率、后台保活、同步记录、选课、新手引导、清空、检查更新。
 *
 * <p>以前这些散在右上角三个点里，现在集中一页，底部导航第三栏进来。</p>
 */
public class SettingsActivity extends AppCompatActivity {

    private LinearLayout body;
    private TextView freqValue;
    private TextView keepValue;
    private TextView historyValue;
    private TextView coursesValue;
    private TextView shareValue;
    private View keepAliveRow;
    private View lastRowView;
    private boolean guideShown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        body = findViewById(R.id.settingsBody);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
        maybeGuide();
    }

    private void render() {
        if (body == null) return;
        body.removeAllViews();

        section("自动同步");
        freqValue = row("同步频率", SyncSettings.label(this), v -> pickFrequency());
        keepValue = row("后台保活",
                (KeepAliveService.on(this) ? "常驻通知已开 · " : "") + "让系统别把它清掉",
                v -> KeepAliveDialog.show(this));
        keepAliveRow = lastRowView;
        historyValue = row("同步记录", historySummary(), v -> showHistory());

        section("我的账号");
        coursesValue = row("选取课程", coursesSummary(), v ->
                startActivity(new Intent(this, PlatformsActivity.class)));
        shareValue = row("共享给电脑", shareSummary(), v -> showShare());
        row("新手引导", "带你把登录、选课、同步走一遍", v -> {
            GuideFlow.set(this, GuideFlow.MAIN);
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
        });
        row("清空清单", "只清本地记录，不动平台上的作业", v -> confirmClear());

        section("关于");
        row("检查更新", "当前 " + versionName(), v -> UpdateFlow.check(this, false));
        row("更新记录", "每个版本改了什么", v -> com.dsh.toolbox.ui.Changelog.show(this));
        row("这个 App 干了什么", "不保存账号密码，只在本机存登录状态", v -> about());
        row("同步诊断", "某个平台抓不到时，把这页发给我", v -> showDiag());

        TextView foot = new TextView(this);
        foot.setText("本应用不是学校官方客户端，数据来自你自己登录后的页面。"
                + "遇到作业抓不到，多半是学校改了页面，等更新就好。");
        foot.setTextSize(11.5f);
        foot.setTextColor(getColor(R.color.text3));
        foot.setLineSpacing(dp(3), 1f);
        foot.setPadding(dp(4), dp(18), dp(4), 0);
        body.addView(foot, new LinearLayout.LayoutParams(-1, -2));

        updateValues();
    }

    private String shareSummary() {
        if (!ShareServer.isRunning()) return "手机和电脑同一 Wi-Fi 时才用，点这里开";
        String a = ShareServer.address(this);
        return a.length() == 0 ? "已开启（没拿到 IP，先连上 Wi-Fi）" : "已开启 " + a;
    }

    private void showShare() {
        if (ShareServer.isRunning()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("共享给电脑")
                    .setMessage("地址：" + ShareServer.address(this)
                            + "\n\n在电脑版里点「从手机拉取」，填这个地址就行。"
                            + "\n关掉之后电脑就连不上了。")
                    .setPositiveButton("关掉共享", (d, w) -> {
                        ShareServer.stop(this);
                        render();
                    })
                    .setNegativeButton("继续开着", null)
                    .show();
            return;
        }
        String addr = ShareServer.start(this);
        if (addr.length() == 0) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("没能开启")
                    .setMessage("先连上 Wi-Fi（或者开热点给电脑连），再试一次。")
                    .setPositiveButton("知道了", null)
                    .show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("共享已开启")
                .setMessage("地址：" + addr
                        + "\n\n电脑版：设置 → 从手机拉取 → 填这个地址。"
                        + "\n\n注意：手机和电脑要在同一个 Wi-Fi 下；宿舍路由器如果开了「设备隔离」会连不上，可以把手机热点打开、让电脑连手机。")
                .setPositiveButton("知道了", null)
                .show();
        render();
    }

    private void updateValues() {
        if (shareValue != null) shareValue.setText(shareSummary());
        if (keepValue != null) {
            keepValue.setText((KeepAliveService.on(this) ? "常驻通知已开 · " : "") + "让系统别把它清掉");
        }
        if (historyValue != null) historyValue.setText(historySummary());
        if (coursesValue != null) coursesValue.setText(coursesSummary());
    }

    private String historySummary() {
        List<SyncHistory.Entry> list = SyncHistory.list(this);
        if (list.isEmpty()) return "还没有记录";
        return "最近 " + list.size() + " 次 · 上次 " + list.get(0).time();
    }

    private String coursesSummary() {
        int n = 0;
        for (String s : Sources.ALL_KEYS) n += CourseStore.selected(this, s).size();
        return n == 0 ? "还没选课" : "已选 " + n + " 门";
    }

    // ---------------------------------------------------------------- 各项操作

    private void pickFrequency() {
        final int[] options = SyncSettings.OPTIONS;
        String[] labels = new String[options.length];
        for (int i = 0; i < options.length; i++) labels[i] = SyncSettings.label(options[i]);
        int current = 0;
        for (int i = 0; i < options.length; i++) if (options[i] == SyncSettings.hours(this)) current = i;
        new MaterialAlertDialogBuilder(this)
                .setTitle("多久自动查一次")
                .setSingleChoiceItems(labels, current, (d, which) -> {
                    d.dismiss();
                    applyFrequency(options[which]);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void applyFrequency(int hours) {
        if (hours == 0) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("不自动同步？")
                    .setMessage("关掉之后只有你手动点「同步」才会更新。后台保活的通知也会一起关掉。")
                    .setPositiveButton("就这样", (d, w) -> {
                        SyncSettings.setHours(this, 0);
                        KeepAliveService.disable(this);
                        toast("已关闭自动同步");
                        render();
                    })
                    .setNegativeButton("算了", null)
                    .show();
            return;
        }
        SyncSettings.setHours(this, hours);
        render();
        if (!KeepAliveService.on(this)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("顺手把后台保活打开？")
                    .setMessage(SyncSettings.label(hours) + "自动同步要靠系统按时叫醒它。"
                            + "把电池优化、自启动这些放开，再开个常驻通知，最稳。")
                    .setPositiveButton("去设置", (d, w) -> KeepAliveDialog.show(this))
                    .setNegativeButton("以后再说", null)
                    .show();
        }
    }

    /**
     * 同步诊断：把最近一次同步的日志和智慧树/学习通的 HTTP 摘要显示出来。
     * 用户没法连电脑时，让他截这一页就够了。
     */
    private void showDiag() {
        String[] files = {"sync_diag.log", "zhs_http.log", "cx_http.log", "ykt_http.log"};
        StringBuilder sb = new StringBuilder();
        sb.append("App ").append(versionName()).append(" · ")
          .append(new java.text.SimpleDateFormat("M/d HH:mm", java.util.Locale.CHINA)
                  .format(new java.util.Date())).append("\n\n");
        for (String name : files) {
            String text = readFile(name);
            if (text == null || text.trim().isEmpty()) continue;
            sb.append("──── ").append(name).append(" ────\n");
            sb.append(text.length() > 3000 ? text.substring(0, 3000) : text).append("\n\n");
        }
        if (sb.length() < 40) sb.append("还没有日志。先点一次「同步」。");

        TextView t = new TextView(this);
        t.setText(sb.toString());
        t.setTextSize(10);
        t.setTypeface(android.graphics.Typeface.MONOSPACE);
        t.setTextColor(getColor(R.color.text));
        t.setTextIsSelectable(true);
        t.setPadding(dp(16), dp(10), dp(16), dp(10));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(t);

        new MaterialAlertDialogBuilder(this)
                .setTitle("同步诊断")
                .setView(scroll)
                .setPositiveButton("知道了", null)
                .show();
    }

    private String readFile(String name) {
        try {
            java.io.File f = new java.io.File(getFilesDir(), name);
            if (!f.exists()) return null;
            byte[] buf = new byte[(int) f.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                int off = 0;
                while (off < buf.length) {
                    int n = in.read(buf, off, buf.length - off);
                    if (n <= 0) break;
                    off += n;
                }
            }
            return new String(buf, "UTF-8");
        } catch (Throwable t) {
            return null;
        }
    }

    private void showHistory() {
        List<SyncHistory.Entry> list = SyncHistory.list(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(6), dp(20), dp(4));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);

        if (list.isEmpty()) {
            TextView t = new TextView(this);
            t.setText("还没有同步过。回到「作业」页点一下右上角「同步」就有了。");
            t.setTextSize(13);
            t.setTextColor(getColor(R.color.text2));
            root.addView(t, new LinearLayout.LayoutParams(-1, -2));
        } else {
            for (SyncHistory.Entry e : list) {
                TextView t = new TextView(this);
                t.setText(e.line());
                t.setTextSize(13);
                t.setTextColor(e.sessionLost ? getColor(R.color.danger) : getColor(R.color.text));
                t.setPadding(0, dp(7), 0, dp(7));
                root.addView(t, new LinearLayout.LayoutParams(-1, -2));
                View line = new View(this);
                line.setBackgroundColor(getColor(R.color.divider));
                root.addView(line, new LinearLayout.LayoutParams(-1, Math.max(1, dp(0.6f))));
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("同步记录")
                .setView(scroll)
                .setPositiveButton("知道了", null)
                .setNeutralButton("清空记录", (d, w) -> {
                    SyncHistory.clear(this);
                    render();
                })
                .show();
    }

    private void confirmClear() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("清空清单？")
                .setMessage("会删掉本机记录的所有作业（平台上的作业不受影响）。下次同步会重新抓回来。")
                .setPositiveButton("清空", (d, w) -> {
                    Store.get(this).clearAll();
                    toast("清单已清空");
                    render();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void about() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("关于")
                .setMessage("一个帮你看「哪些作业还没交」的小工具。\n\n"
                        + "· 雨课堂、智慧树、超星学习通：你在 App 里登录一次，之后它按你选的频率自动去查未完成的作业。\n"
                        + "· 课表：从学校教务系统导入学期理论课表，按周查看。\n"
                        + "· 账号密码不经过本应用，登录状态只存在这台手机上。\n\n"
                        + "版本 " + versionName())
                .setPositiveButton("知道了", null)
                .show();
    }

    // ---------------------------------------------------------------- 视图小工具

    private void section(String title) {
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(12);
        t.setTextColor(getColor(R.color.text3));
        t.setPadding(dp(6), dp(body.getChildCount() == 0 ? 4 : 18), dp(6), dp(8));
        body.addView(t, new LinearLayout.LayoutParams(-1, -2));
    }

    private TextView row(String title, String value, View.OnClickListener onClick) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setClickable(true);
        card.setFocusable(true);
        card.setForeground(getDrawable(R.drawable.ripple_row));
        card.setOnClickListener(onClick);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(14.5f);
        t.setTextColor(getColor(R.color.text));
        texts.addView(t);
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(11.5f);
        v.setTextColor(getColor(R.color.text3));
        v.setPadding(0, dp(3), dp(8), 0);
        texts.addView(v);
        card.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(18);
        arrow.setTextColor(getColor(R.color.text3));
        card.addView(arrow);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        body.addView(card, lp);
        lastRowView = card;
        return v;
    }

    /** 新手引导最后一步：把「后台保活」这一行高亮出来，用户点进去就把设置打开了。 */
    private void maybeGuide() {
        if (guideShown) return;
        if (GuideFlow.step(this) != GuideFlow.SETTINGS) return;
        if (keepAliveRow == null) return;
        guideShown = true;
        java.util.List<GuideOverlay.Item> steps = new java.util.ArrayList<>();
        steps.add(new GuideOverlay.Item(keepAliveRow,
                "还有一步：把后台保活打开",
                "点这一行，把电池优化、自启动、常驻通知按提示放开。不打开的话，系统会把 App 清掉，"
                        + "自动同步就断了。", true));
        GuideOverlay.show(this, steps, "打开后台保活", () -> {
            guideShown = false;
            GuideFlow.set(this, GuideFlow.DONE);
        }, () -> {
            guideShown = false;
            GuideFlow.set(this, GuideFlow.DONE);
        });
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "?";
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}