package com.dsh.toolbox;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.dsh.toolbox.data.DeadlineParser;
import com.dsh.toolbox.data.Homework;
import com.dsh.toolbox.data.Sources;
import com.dsh.toolbox.data.Store;
import com.dsh.toolbox.data.SyncAlarm;
import com.dsh.toolbox.ui.HomeworkAdapter;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements HomeworkAdapter.Listener {

    private Store store;
    private HomeworkAdapter adapter;
    private SwipeRefreshLayout refreshLayout;
    private View emptyState;
    private boolean onTimetable;
    private com.dsh.toolbox.ui.TimetableGrid timetableGrid;
    private View timetableEmpty;
    private View timetableScroll;
    private TextView timetableSub;
    private View weekBar;
    private TextView timetableWeek;
    private View btnThisWeek;
    /** 0 = follow the current calendar week; otherwise the week the user navigated to. */
    private int ttWeek;
    private TextView statTotal;
    private TextView statToday;
    private TextView statWeek;
    private TextView statOverdue;
    private TextView syncInfo;
    private String filter = "";
    /** 0 = any deadline, 1 = today, 2 = within 7 days, 3 = overdue. */
    private int dueFilter = 0;
    /** Empty = every course. */
    private final java.util.LinkedHashSet<String> courseFilter = new java.util.LinkedHashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        store = Store.get(this);
        com.dsh.toolbox.data.SyncScheduler.ensureScheduled(this);
        com.dsh.toolbox.data.SyncScheduler.catchUp(this);
        requestNotificationPermission();
        // 打开 App 时顺手查一次更新（6 小时内不重复查）；有新版本弹「发现新版本」
        if (!com.dsh.toolbox.ui.GuideFlow.active(this)) {
            getWindow().getDecorView().postDelayed(
                    () -> com.dsh.toolbox.ui.UpdateFlow.checkOnLaunch(this), 2500);
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        statTotal = findViewById(R.id.statTotal);
        statToday = findViewById(R.id.statToday);
        statWeek = findViewById(R.id.statWeek);
        statOverdue = findViewById(R.id.statOverdue);
        syncInfo = findViewById(R.id.syncInfo);
        emptyState = findViewById(R.id.emptyState);
        refreshLayout = findViewById(R.id.refresh);

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HomeworkAdapter(this);
        list.setAdapter(adapter);

        ChipGroup chips = findViewById(R.id.chipGroup);
        chips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                filter = "";
            } else {
                int id = checkedIds.get(0);
                if (id == R.id.chipYkt) filter = Sources.YKT;
                else if (id == R.id.chipZhs) filter = Sources.ZHS;
                else if (id == R.id.chipCx) filter = Sources.CX;
                else filter = "";
            }
            reload();
        });

        findViewById(R.id.fab).setOnClickListener(v -> showAddDialog());
        findViewById(R.id.emptyAction).setOnClickListener(v ->
                startActivity(new Intent(this, PlatformsActivity.class)));

        ChipGroup dueChips = findViewById(R.id.chipGroupDue);
        dueChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            dueFilter = 0;
            if (!checkedIds.isEmpty()) {
                int id = checkedIds.get(0);
                if (id == R.id.chipDueToday) dueFilter = 1;
                else if (id == R.id.chipDueWeek) dueFilter = 2;
                else if (id == R.id.chipDueOverdue) dueFilter = 3;
            }
            reload();
        });
        findViewById(R.id.chipCourse).setOnClickListener(v -> showCourseFilter());

com.google.android.material.bottomnavigation.BottomNavigationView bottomNav =
                findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_settings) {
                // 设置是独立页面：进去回来还停在原来那一栏。
                startActivity(new Intent(this, SettingsActivity.class));
                return false;
            }
            showTab(item.getItemId() == R.id.nav_timetable);
            return true;
        });

        timetableGrid = findViewById(R.id.timetableGrid);
        timetableEmpty = findViewById(R.id.timetableEmpty);
        timetableScroll = findViewById(R.id.timetableScroll);
        timetableSub = findViewById(R.id.timetableSub);
        weekBar = findViewById(R.id.weekBar);
        timetableWeek = findViewById(R.id.timetableWeek);
        btnThisWeek = findViewById(R.id.btnThisWeek);
        findViewById(R.id.btnImportTimetable).setOnClickListener(v -> importTimetable());
        findViewById(R.id.btnPrevWeek).setOnClickListener(v -> {
            ttWeek = Math.max(1, (ttWeek <= 0 ? 1 : ttWeek) - 1);
            renderTimetable();
        });
        findViewById(R.id.btnNextWeek).setOnClickListener(v -> {
            ttWeek = Math.min(30, (ttWeek <= 0 ? 1 : ttWeek) + 1);
            renderTimetable();
        });
        btnThisWeek.setOnClickListener(v -> {
            ttWeek = 0;
            renderTimetable();
        });
        timetableWeek.setOnClickListener(v -> editWeekStart());
        timetableWeek.setOnLongClickListener(v -> {
            editWeekStart();
            return true;
        });

        maybeOfferGuide();

        refreshLayout.setColorSchemeColors(getColor(R.color.brand));
        refreshLayout.setOnRefreshListener(() -> {
            reload();
            refreshLayout.postDelayed(() -> refreshLayout.setRefreshing(false), 350);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
        if (onTimetable) renderTimetable();
        runGuideForCurrentStep();
        com.dsh.toolbox.data.SyncScheduler.catchUp(this);
    }

    /** Bottom bar: 作业 list vs. the (upcoming) timetable pane. */
    private void showTab(boolean timetable) {
        onTimetable = timetable;
        int shown = timetable ? View.GONE : View.VISIBLE;
        findViewById(R.id.paneTimetable).setVisibility(timetable ? View.VISIBLE : View.GONE);
        findViewById(R.id.refresh).setVisibility(shown);
        findViewById(R.id.summary).setVisibility(shown);
        findViewById(R.id.fab).setVisibility(shown);
        View chips = (View) findViewById(R.id.chipGroup).getParent();
        View dueChips = (View) findViewById(R.id.chipGroupDue).getParent();
        chips.setVisibility(shown);
        dueChips.setVisibility(shown);
        if (timetable) {
            emptyState.setVisibility(View.GONE);
            renderTimetable();
        } else {
            reload();
        }
    }

    private void requestNotificationPermission() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_courses) {
            startActivity(new Intent(this, PlatformsActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.action_guide) {
            com.dsh.toolbox.ui.GuideFlow.set(this, com.dsh.toolbox.ui.GuideFlow.MAIN);
            startMainGuide();
            return true;
        }
        if (item.getItemId() == R.id.action_sync) {
            runYktDiag();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void runYktDiag() {
        if (!com.dsh.toolbox.data.CourseStore.anySelected(this)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("还没有选课")
                    .setMessage("同步只会抓取你勾选的课程。先去「选取课程」里选平台、登录、勾课，再回来同步。")
                    .setPositiveButton("去选取课程", (d, w) ->
                            startActivity(new Intent(this, PlatformsActivity.class)))
                    .setNegativeButton("知道了", null)
                    .show();
            return;
        }
        final AlertDialog progress = new MaterialAlertDialogBuilder(this)
                .setTitle("正在同步作业…")
                .setMessage("只抓取你选中的课程，请稍候")
                .setCancelable(false)
                .create();
        progress.show();
        new Thread(() -> {
            com.dsh.toolbox.data.SyncAll.Report report = com.dsh.toolbox.data.SyncAll.run(this);
            StringBuilder sb = new StringBuilder();
            for (java.util.Map.Entry<String, Integer> e : report.perPlatform.entrySet()) {
                String key = e.getKey();
                sb.append(com.dsh.toolbox.data.Sources.nameOf(key)).append("：")
                        .append(e.getValue()).append(" 条");
                if (report.authMissing.contains(key)) {
                    sb.append("（登录信息没拿到，去「选取课程」点「登录」把页面打开一次）");
                }
                sb.append('\n');
            }
            String body = sb.length() == 0
                    ? "还没有选课。先去「选取课程」登录平台、勾选要监控的课。"
                    : sb.toString();
            boolean lost = report.sessionLost;
            String title = lost ? "同步完成 · 有平台需要重新登录" : "同步完成";
            String log = report.log;
            runOnUiThread(() -> {
                if (!isFinishing()) progress.dismiss();
                com.google.android.material.dialog.MaterialAlertDialogBuilder b =
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(title)
                                .setMessage(body)
                                .setPositiveButton("好", null)
                                .setNeutralButton("查看日志", (d, w) -> showSyncLog(log));
                if (com.dsh.toolbox.ui.GuideFlow.step(this)
                        == com.dsh.toolbox.ui.GuideFlow.SETTINGS) {
                    // 新手引导最后一步：领他去设置页打开后台保活。
                    b.setNeutralButton("去开后台保活", (d, w) ->
                            startActivity(new Intent(this, SettingsActivity.class)));
                }
                b.show();
                reload();
            });
        }).start();
    }

    private void showSyncLog(String log) {
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        android.widget.TextView tv = new android.widget.TextView(this);
        tv.setText(log);
        tv.setTextSize(11f);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        int pad = dp(14);
        tv.setPadding(pad, pad, pad, pad);
        sv.addView(tv);
        new MaterialAlertDialogBuilder(this)
                .setTitle("同步日志")
                .setView(sv)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void confirmClearAll() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("清空整个清单？")
                .setMessage("会删除所有平台已抓取的作业记录（登录状态保留，可重新提取）。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (d, w) -> {
                    store.clearAll();
                    reload();
                    android.widget.Toast.makeText(this, "已清空", android.widget.Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void reload() {
        List<Homework> all = store.all();
        long now = System.currentTimeMillis();

        List<Homework> visible = new ArrayList<>();
        for (Homework h : all) {
            if (!courseFilter.isEmpty() && !courseFilter.contains(h.course)) continue;
            visible.add(h);
        }

        int total = 0;
        int today = 0;
        int week = 0;
        int overdue = 0;
        for (Homework h : visible) {
            if (h.done) continue;
            total++;
            if (h.dueAt <= 0) continue;
            if (h.dueAt < now) {
                overdue++;
            } else {
                long delta = h.dueAt - now;
                if (delta <= 7L * 24 * 3600 * 1000) week++;
                if (sameDay(h.dueAt, now)) today++;
            }
        }
        statTotal.setText(String.valueOf(total));
        statToday.setText(String.valueOf(today));
        statWeek.setText(String.valueOf(week));
        statOverdue.setText(String.valueOf(overdue));
        updateSyncInfo();

        List<Homework> shown = new ArrayList<>();
        for (Homework h : visible) {
            if (!filter.isEmpty() && !filter.equals(h.source)) continue;
            if (!matchDue(h, now)) continue;
            shown.add(h);
        }

        List<Homework> undone = new ArrayList<>();
        List<Homework> done = new ArrayList<>();
        for (Homework h : shown) (h.done ? done : undone).add(h);

        Collections.sort(undone, new Comparator<Homework>() {
            @Override
            public int compare(Homework a, Homework b) {
                if (a.dueAt == 0 && b.dueAt == 0) return a.title.compareTo(b.title);
                if (a.dueAt == 0) return 1;
                if (b.dueAt == 0) return -1;
                return Long.compare(a.dueAt, b.dueAt);
            }
        });
        Collections.sort(done, new Comparator<Homework>() {
            @Override
            public int compare(Homework a, Homework b) {
                return Long.compare(b.fetchedAt, a.fetchedAt);
            }
        });

        List<Homework> overdueItems = new ArrayList<>();
        List<Homework> todayItems = new ArrayList<>();
        List<Homework> tomorrowItems = new ArrayList<>();
        List<Homework> weekItems = new ArrayList<>();
        List<Homework> laterItems = new ArrayList<>();
        List<Homework> noDateItems = new ArrayList<>();

        for (Homework h : undone) {
            if (h.dueAt <= 0) {
                noDateItems.add(h);
            } else if (h.dueAt < now) {
                overdueItems.add(h);
            } else if (sameDay(h.dueAt, now)) {
                todayItems.add(h);
            } else if (sameDay(h.dueAt, now + 24L * 3600 * 1000)) {
                tomorrowItems.add(h);
            } else if (h.dueAt - now <= 7L * 24 * 3600 * 1000) {
                weekItems.add(h);
            } else {
                laterItems.add(h);
            }
        }

        List<Object> rows = new ArrayList<>();
        addSection(rows, "今天截止", todayItems);
        addSection(rows, "明天截止", tomorrowItems);
        addSection(rows, "七天内", weekItems);
        addSection(rows, "以后", laterItems);
        addSection(rows, "没有截止时间", noDateItems);
        addSection(rows, "已逾期", overdueItems);
        addSection(rows, "已完成", done);

        adapter.submit(rows);
        emptyState.setVisibility(!onTimetable && shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** 「上次同步 12:30 · 下次自动 18:30」——让自动同步这件事看得见。 */
    private void updateSyncInfo() {
        if (syncInfo == null) return;
        long last = 0;
        for (String s : Sources.ALL_KEYS) last = Math.max(last, store.lastSync(s));
        SimpleDateFormat hm = new SimpleDateFormat("M/d HH:mm", Locale.CHINA);
        StringBuilder sb = new StringBuilder();
        sb.append(last > 0 ? "上次同步 " + hm.format(new Date(last)) : "还没同步过");
        long next = System.currentTimeMillis() + SyncAlarm.nextIn(this);
        if (SyncAlarm.nextIn(this) > 0) {
            sb.append(" · 下次自动 ").append(hm.format(new Date(next)));
        } else {
            sb.append(" · 每 6 小时自动同步");
        }
        syncInfo.setText(sb.toString());
    }

    private boolean matchDue(Homework h, long now) {
        switch (dueFilter) {
            case 1:
                return h.dueAt > 0 && sameDay(h.dueAt, now);
            case 2:
                return h.dueAt > now && h.dueAt - now <= 7L * 24 * 3600 * 1000;
            case 3:
                return h.dueAt > 0 && h.dueAt < now;
            default:
                return true;
        }
    }

    /** Multi-select dialog with every course currently in the list. */
    private void showCourseFilter() {
        final List<String> courses = new ArrayList<>();
        for (Homework h : store.all()) {
            if (h.course == null || h.course.isEmpty()) continue;
            if (!courses.contains(h.course)) courses.add(h.course);
        }
        Collections.sort(courses);
        if (courses.isEmpty()) {
            Toast.makeText(this, "还没有课程数据，先同步一次", Toast.LENGTH_SHORT).show();
            return;
        }

        final List<com.google.android.material.checkbox.MaterialCheckBox> boxes = new ArrayList<>();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(8), 0, dp(8), 0);
        com.google.android.material.button.MaterialButton allBtn = textButton("全选");
        com.google.android.material.button.MaterialButton noneBtn = textButton("全不选");
        bar.addView(allBtn);
        bar.addView(noneBtn);
        root.addView(bar);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(12), 0, dp(12), 0);
        for (String course : courses) {
            com.google.android.material.checkbox.MaterialCheckBox cb =
                    new com.google.android.material.checkbox.MaterialCheckBox(this);
            cb.setText(course);
            cb.setTextSize(14);
            cb.setChecked(courseFilter.isEmpty() || courseFilter.contains(course));
            column.addView(cb);
            boxes.add(cb);
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(column);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(340));
        root.addView(scroll, sp);

        allBtn.setOnClickListener(v -> {
            for (com.google.android.material.checkbox.MaterialCheckBox cb : boxes) cb.setChecked(true);
        });
        noneBtn.setOnClickListener(v -> {
            for (com.google.android.material.checkbox.MaterialCheckBox cb : boxes) cb.setChecked(false);
        });

        new MaterialAlertDialogBuilder(this)
                .setTitle("只看这些课")
                .setView(root)
                .setNegativeButton("取消", null)
                .setNeutralButton("全部课程", (d, w) -> {
                    courseFilter.clear();
                    updateCourseChip();
                    reload();
                })
                .setPositiveButton("确定", (d, w) -> {
                    List<String> picked = new ArrayList<>();
                    for (int i = 0; i < boxes.size(); i++) {
                        if (boxes.get(i).isChecked()) picked.add(courses.get(i));
                    }
                    courseFilter.clear();
                    if (!picked.isEmpty() && picked.size() < courses.size()) courseFilter.addAll(picked);
                    updateCourseChip();
                    reload();
                })
                .show();
    }

    private com.google.android.material.button.MaterialButton textButton(String label) {
        com.google.android.material.button.MaterialButton b = new com.google.android.material.button.MaterialButton(
                this, null, com.google.android.material.R.attr.materialButtonStyle);
        b.setText(label);
        b.setTextSize(13);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0));
        b.setTextColor(getColor(R.color.brand));
        b.setInsetTop(0);
        b.setInsetBottom(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(6), dp(6), dp(6), dp(6));
        return b;
    }


    private static boolean checkedAll(boolean[] a) {
        for (boolean b : a) if (!b) return false;
        return true;
    }

    private void updateCourseChip() {
        com.google.android.material.chip.Chip chip = findViewById(R.id.chipCourse);
        if (courseFilter.isEmpty()) {
            chip.setText("按课程筛选");
        } else {
            chip.setText("课程 · " + courseFilter.size());
        }
    }

    private void checkUpdate(boolean silent) {
        final String url = com.dsh.toolbox.data.Updater.manifest(this);
        if (url.isEmpty()) {
            if (!silent) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("还没配置更新地址")
                        .setMessage("把 update.json 和新版 APK 放到任意能公网访问的地方，"
                                + "再把 Updater.DEFAULT_MANIFEST 改成那个地址——之后老用户点这里就能收到更新，"
                                + "不用再手动传安装包。")
                        .setPositiveButton("知道了", null)
                        .show();
            }
            return;
        }
        final AlertDialog[] ph = new AlertDialog[1];
        if (!silent) {
            ph[0] = new MaterialAlertDialogBuilder(this)
                    .setTitle("正在检查更新…")
                    .setCancelable(false)
                    .create();
            ph[0].show();
        }
        new Thread(() -> {
            final com.dsh.toolbox.data.Updater.Info info = com.dsh.toolbox.data.Updater.check(this, url);
            runOnUiThread(() -> {
                if (ph[0] != null && ph[0].isShowing()) ph[0].dismiss();
                if (isFinishing()) return;
                if (info == null) {
                    if (!silent) Toast.makeText(this, "已经是最新版本", Toast.LENGTH_SHORT).show();
                } else {
                    showUpdate(info);
                }
            });
        }).start();
    }

    private void showUpdate(final com.dsh.toolbox.data.Updater.Info info) {
        StringBuilder msg = new StringBuilder();
        msg.append(info.notes == null || info.notes.isEmpty() ? "有新版本可以更新。" : info.notes);
        if (info.size > 0) {
            msg.append("\n\n大小约 ")
                    .append(String.format(java.util.Locale.getDefault(), "%.1f", info.size / 1048576.0))
                    .append(" MB");
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("发现新版本 " + info.name)
                .setMessage(msg.toString())
                .setPositiveButton("立即更新", (d, w) -> downloadUpdate(info))
                .setNegativeButton("以后再说", null)
                .show();
    }

    private void downloadUpdate(final com.dsh.toolbox.data.Updater.Info info) {
        if (!com.dsh.toolbox.data.Updater.canInstall(this)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("需要先允许安装")
                    .setMessage("系统要你允许本应用「安装未知应用」，才能装上更新包。")
                    .setPositiveButton("去允许", (d, w) ->
                            com.dsh.toolbox.data.Updater.openInstallPermission(this))
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        final android.app.ProgressDialog pd = new android.app.ProgressDialog(this);
        pd.setTitle("正在下载 " + info.name);
        pd.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
        pd.setMax(100);
        pd.setCancelable(false);
        pd.show();
        new Thread(() -> {
            try {
                final java.io.File apk = com.dsh.toolbox.data.Updater.download(this, info, (done, total) -> {
                    if (total > 0) pd.setProgress((int) (done * 100 / total));
                });
                final String err = com.dsh.toolbox.data.Updater.verify(this, apk, info);
                runOnUiThread(() -> {
                    if (pd.isShowing()) pd.dismiss();
                    if (isFinishing()) return;
                    if (err != null) {
                        Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                    } else {
                        com.dsh.toolbox.data.Updater.install(this, apk);
                    }
                });
            } catch (Throwable t) {
                runOnUiThread(() -> {
                    if (pd.isShowing()) pd.dismiss();
                    Toast.makeText(this, "下载失败：" + t, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void showKeepAlive() {
        final int pad = dp(18);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(pad, dp(6), pad, dp(4));
        scroll.addView(root);

        android.widget.TextView intro = new android.widget.TextView(this);
        intro.setText("各家手机都会管后台。想让它每天自己查作业，下面几项逐个打开就行。");
        intro.setTextSize(13);
        intro.setTextColor(getColor(R.color.text2));
        intro.setLineSpacing(dp(2), 1f);
        root.addView(intro, new android.widget.LinearLayout.LayoutParams(-1, -2));

        final boolean battery = com.dsh.toolbox.data.BatteryGuide.ignoringBattery(this);
        final boolean exact = com.dsh.toolbox.data.BatteryGuide.exactAlarmAllowed(this);
        final boolean notif = com.dsh.toolbox.data.BatteryGuide.notificationsEnabled(this);

        keepRow(root, "电池优化", battery ? "已放行，不会被冻结" : "还限制着，后台容易被冻结",
                battery, "去关闭", () -> com.dsh.toolbox.data.BatteryGuide.open(this,
                        com.dsh.toolbox.data.BatteryGuide.Kind.BATTERY));
        keepRow(root, "精确闹钟", exact ? "已允许，到点更准" : "没允许，提醒可能被推迟",
                exact, "去允许", () -> com.dsh.toolbox.data.BatteryGuide.open(this,
                        com.dsh.toolbox.data.BatteryGuide.Kind.EXACT_ALARM));
        keepRow(root, "通知", notif ? "已开启" : "关着的话「该重新登录了」的提醒发不出来",
                notif, "去开启", () -> com.dsh.toolbox.data.BatteryGuide.open(this,
                        com.dsh.toolbox.data.BatteryGuide.Kind.NOTIFICATION));
        keepRow(root, com.dsh.toolbox.data.BatteryGuide.brand() + " · "
                        + com.dsh.toolbox.data.BatteryGuide.autoStartName(),
                "允许后，被系统清理掉也能按时叫醒",
                true, "去设置", () -> com.dsh.toolbox.data.BatteryGuide.open(this,
                        com.dsh.toolbox.data.BatteryGuide.Kind.AUTO_START));

        android.widget.CheckBox cb = new android.widget.CheckBox(this);
        cb.setText("常驻通知（最稳，代价是一直显示一条通知）");
        cb.setTextSize(13);
        cb.setTextColor(getColor(R.color.text));
        cb.setPadding(0, dp(10), 0, 0);
        cb.setChecked(com.dsh.toolbox.data.KeepAliveService.on(this));
        cb.setOnCheckedChangeListener((v, on) -> {
            if (on) com.dsh.toolbox.data.KeepAliveService.enable(this);
            else com.dsh.toolbox.data.KeepAliveService.disable(this);
        });
        root.addView(cb, new android.widget.LinearLayout.LayoutParams(-1, -2));

        android.widget.TextView foot = new android.widget.TextView(this);
        foot.setText("没开常驻通知时：系统把你清掉之后，下一次同步可能要等下次打开 App 才补跑。"
                + "开了之后闹钟到点就能自己跑。");
        foot.setTextSize(12);
        foot.setTextColor(getColor(R.color.text3));
        foot.setPadding(0, dp(8), 0, 0);
        foot.setLineSpacing(dp(2), 1f);
        root.addView(foot, new android.widget.LinearLayout.LayoutParams(-1, -2));

        new MaterialAlertDialogBuilder(this)
                .setTitle("后台保活")
                .setView(scroll)
                .setPositiveButton("刷新状态", (d, w) -> showKeepAlive())
                .setNegativeButton("关闭", null)
                .show();
    }

    /** 一行「标题 + 状态 + 去设置」。 */
    private void keepRow(android.widget.LinearLayout parent, String title, String status,
                         boolean ok, String action, Runnable onClick) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        android.widget.LinearLayout texts = new android.widget.LinearLayout(this);
        texts.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.TextView t = new android.widget.TextView(this);
        t.setText(title);
        t.setTextSize(14);
        t.setTextColor(getColor(R.color.text));
        android.widget.TextView s = new android.widget.TextView(this);
        s.setText(status);
        s.setTextSize(11.5f);
        s.setTextColor(getColor(ok ? R.color.brand : R.color.danger));
        s.setPadding(0, dp(2), 0, 0);
        texts.addView(t);
        texts.addView(s);
        row.addView(texts, new android.widget.LinearLayout.LayoutParams(0, -2, 1f));

        com.google.android.material.button.MaterialButton b = textButton(action);
        b.setOnClickListener(v -> onClick.run());
        row.addView(b);
        parent.addView(row, new android.widget.LinearLayout.LayoutParams(-1, -2));
    }

    private void addSection(List<Object> rows, String title, List<Homework> items) {
        if (items.isEmpty()) return;
        rows.add(title + "  (" + items.size() + ")");
        rows.addAll(items);
    }

    private static boolean sameDay(long a, long b) {
        Calendar ca = Calendar.getInstance();
        ca.setTimeInMillis(a);
        Calendar cb = Calendar.getInstance();
        cb.setTimeInMillis(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
                && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR);
    }

    // ---------------- item interactions ----------------

// ---------------- 课表 ----------------

    private void importTimetable() {
        Intent i = new Intent(this, WebActivity.class);
        i.putExtra(WebActivity.EXTRA_SOURCE, com.dsh.toolbox.data.Sources.JW);
        i.putExtra(WebActivity.EXTRA_URL, com.dsh.toolbox.data.JwClient.ENTRY);
        startActivity(i);
    }

    private void renderTimetable() {
        List<com.dsh.toolbox.data.Timetable> list = com.dsh.toolbox.data.TimetableStore.load(this);
        boolean has = !list.isEmpty();
        timetableScroll.setVisibility(has ? View.VISIBLE : View.GONE);
        timetableEmpty.setVisibility(has ? View.GONE : View.VISIBLE);
        weekBar.setVisibility(has ? View.VISIBLE : View.GONE);
        if (!has) {
            timetableSub.setText("还没导入课表");
            return;
        }
        long week1 = com.dsh.toolbox.data.TimetableStore.start(this);
        int nowWeek = com.dsh.toolbox.data.TimetableStore.weekOf(System.currentTimeMillis(), week1);
        if (ttWeek <= 0) ttWeek = Math.max(1, nowWeek);
        ttWeek = Math.max(1, Math.min(30, ttWeek));

        int count = 0;
        for (com.dsh.toolbox.data.Timetable t : list) if (t.inWeek(ttWeek)) count++;
        timetableGrid.setData(list, ttWeek);
        timetableGrid.setTimes(com.dsh.toolbox.data.TimetableStore.sectionTimes(this));

        long ws = com.dsh.toolbox.data.TimetableStore.weekStart(week1, ttWeek);
        long we = ws + 6 * com.dsh.toolbox.data.TimetableStore.DAY_MS;
        String range = android.text.format.DateFormat.format("M/d", ws)
                + " - " + android.text.format.DateFormat.format("M/d", we);
        timetableWeek.setText("第 " + ttWeek + " 周 · " + range + (ttWeek == nowWeek ? " · 本周" : ""));
        btnThisWeek.setVisibility(ttWeek == nowWeek ? View.INVISIBLE : View.VISIBLE);

        String term = com.dsh.toolbox.data.TimetableStore.term(this);
        long at = com.dsh.toolbox.data.TimetableStore.updatedAt(this);
        StringBuilder sb = new StringBuilder();
        if (!term.isEmpty()) sb.append(term).append(" · ");
        sb.append(count).append(" 节课");
        if (at > 0) sb.append(" · 更新于 ").append(android.text.format.DateFormat
                .format("M/d HH:mm", at).toString());
        timetableSub.setText(sb.toString());
    }

    /** Lets the student correct the 开学日期 so the week numbers line up with the 校历. */
    private void editWeekStart() {
        long week1 = com.dsh.toolbox.data.TimetableStore.start(this);
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_DATETIME);
        input.setText(android.text.format.DateFormat.format("yyyy-MM-dd", week1).toString());
        input.setSelection(input.getText().length());
        new MaterialAlertDialogBuilder(this)
                .setTitle("第 1 周的周日")
                .setMessage("课表按「周日 = 一周第一天」计算。填第 1 周的周日日期（例如 2026-09-06）。")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    try {
                        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd",
                                java.util.Locale.getDefault());
                        java.util.Date date = f.parse(input.getText().toString().trim());
                        if (date == null) return;
                        com.dsh.toolbox.data.TimetableStore.setStart(this, date.getTime());
                        ttWeek = 0;
                        renderTimetable();
                    } catch (Exception e) {
                        Toast.makeText(this, "日期格式是 2026-09-06 这样", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    @Override
    public void onToggle(Homework h) {
        store.setDone(h.id, !h.done);
        reload();
    }

    @Override
    public void onOpen(Homework h) {
        String url = TextUtils.isEmpty(h.url) ? store.syncUrl(h.source) : h.url;
        if (TextUtils.isEmpty(url)) url = Sources.byKey(h.source).startUrl;
        Intent i = new Intent(this, WebActivity.class);
        i.putExtra(WebActivity.EXTRA_SOURCE, h.source);
        i.putExtra(WebActivity.EXTRA_URL, url);
        startActivity(i);
    }

    @Override
    public void onDelete(final Homework h) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("删除这条作业？")
                .setMessage(h.title)
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> {
                    store.remove(h.id);
                    reload();
                })
                .show();
    }

    // ---------------- manual add ----------------

    private void showAddDialog() {
        int pad = dp(20);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, 0);

        final TextInputLayout tilTitle = new TextInputLayout(this);
        tilTitle.setHint("作业标题（必填）");
        final TextInputEditText etTitle = new TextInputEditText(this);
        etTitle.setSingleLine(true);
        tilTitle.addView(etTitle);
        box.addView(tilTitle);

        final TextInputLayout tilCourse = new TextInputLayout(this);
        tilCourse.setHint("课程（可选）");
        final TextInputEditText etCourse = new TextInputEditText(this);
        etCourse.setSingleLine(true);
        tilCourse.addView(etCourse);
        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        box.addView(tilCourse, lp);

        final TextInputLayout tilDue = new TextInputLayout(this);
        tilDue.setHint("截止时间（如 2026-11-25 23:59，可留空）");
        final TextInputEditText etDue = new TextInputEditText(this);
        etDue.setSingleLine(true);
        tilDue.addView(etDue);
        ViewGroup.MarginLayoutParams lp2 = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp2.topMargin = dp(12);
        box.addView(tilDue, lp2);

        TextView label = new TextView(this);
        label.setText("平台");
        label.setTextSize(12);
        label.setPadding(0, dp(14), 0, dp(4));
        box.addView(label);

        final Spinner spinner = new Spinner(this);
        List<String> names = new ArrayList<>();
        for (Sources.Info info : Sources.ALL) names.add(info.name);
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, names);
        spinner.setAdapter(spinnerAdapter);
        box.addView(spinner);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);

        final AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("添加作业")
                .setView(scroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("添加", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String title = text(etTitle);
                    if (title.isEmpty()) {
                        tilTitle.setError("请输入标题");
                        return;
                    }
                    tilTitle.setError(null);
                    String dueRaw = text(etDue);
                    long due = 0;
                    if (!dueRaw.isEmpty()) {
                        due = DeadlineParser.parse(dueRaw);
                        if (due <= 0) {
                            tilDue.setError("时间格式没看懂，试试 2026-11-25 23:59");
                            return;
                        }
                    }
                    Homework h = new Homework();
                    h.source = Sources.ALL[spinner.getSelectedItemPosition()].key;
                    h.title = title;
                    h.course = text(etCourse);
                    h.dueAt = due;
                    store.add(h);
                    reload();
                    Toast.makeText(this, "已添加", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private String text(EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
    // ------------------------------------------------------------------ guide

    private boolean guideShowing;

    private void maybeOfferGuide() {
        if (store.prefs().getBoolean("guide_v1", false)) {
            runGuideForCurrentStep();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("第一次用？")
                .setMessage("用 30 秒带你走一遍：认界面、登录雨课堂、勾课、同步一次。")
                .setPositiveButton("跟着做一遍", (d, w) -> {
                    com.dsh.toolbox.ui.GuideFlow.set(this, com.dsh.toolbox.ui.GuideFlow.MAIN);
                    findViewById(android.R.id.content).postDelayed(this::startMainGuide, 300);
                })
                .setNegativeButton("我自己看看", (d, w) ->
                        com.dsh.toolbox.ui.GuideFlow.set(this, com.dsh.toolbox.ui.GuideFlow.DONE))
                .show();
    }

    private void runGuideForCurrentStep() {
        if (guideShowing) return;
        int step = com.dsh.toolbox.ui.GuideFlow.step(this);
        if (step == com.dsh.toolbox.ui.GuideFlow.BACK) {
            com.dsh.toolbox.ui.GuideFlow.set(this, com.dsh.toolbox.ui.GuideFlow.SYNC);
            step = com.dsh.toolbox.ui.GuideFlow.SYNC;
        }
        if (step == com.dsh.toolbox.ui.GuideFlow.MAIN) {
            findViewById(android.R.id.content).postDelayed(this::startMainGuide, 300);
        } else if (step == com.dsh.toolbox.ui.GuideFlow.SYNC) {
            findViewById(android.R.id.content).postDelayed(this::startSyncGuide, 400);
        }
    }

    /** Screen tour: what everything on the home page does. */
    private void startMainGuide() {
        List<com.dsh.toolbox.ui.GuideOverlay.Item> steps = new ArrayList<>();

        View summary = findViewById(R.id.summary);
        if (summary != null) {
            steps.add(new com.dsh.toolbox.ui.GuideOverlay.Item(summary,
                    "这里是今天的总览",
                    "大数字是还没交的作业；下面三个是今天截止、七天内截止、已经逾期的条数。"));
        }
        View platformRow = findViewById(R.id.chipGroup);
        if (platformRow != null) {
            steps.add(new com.dsh.toolbox.ui.GuideOverlay.Item(platformRow,
                    "先按平台看",
                    "只看雨课堂 / 智慧树 / 学习通，点一下就切；「全部」是三个平台一起看。"));
        }
        View dueRow = findViewById(R.id.chipGroupDue);
        if (dueRow != null) {
            steps.add(new com.dsh.toolbox.ui.GuideOverlay.Item(dueRow,
                    "再按时间筛",
                    "想先交今天的点「今天」，看这周的点「7天」，补交的点「逾期」；「课程」还能只盯某几门课。"));
        }
        RecyclerView list = findViewById(R.id.list);
        if (list != null && list.getChildCount() > 0) {
            steps.add(new com.dsh.toolbox.ui.GuideOverlay.Item(list.getChildAt(0),
                    "一条作业 = 一张卡片",
                    "点右边的小圆圈＝标记完成；长按可以删除；点卡片本身会直接打开作业网页。"));
        }
        View fab = findViewById(R.id.fab);
        if (fab != null) {
            steps.add(new com.dsh.toolbox.ui.GuideOverlay.Item(fab,
                    "老师口头布置的也能记",
                    "点这个 ＋ 手动加一条，自己填标题和截止时间。"));
        }
        View toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            steps.add(new com.dsh.toolbox.ui.GuideOverlay.Item(toolbar,
                    "最后一站：右上角",
                    "列表图标＝「选取课程」：登录平台、勾上要盯的课。点下面的按钮，我们用雨课堂带你走一遍完整流程。"));
        }

        guideShowing = true;
        com.dsh.toolbox.ui.GuideOverlay.show(this, steps, "带我去选雨课堂",
                () -> {
                    guideShowing = false;
                    com.dsh.toolbox.ui.GuideFlow.set(this, com.dsh.toolbox.ui.GuideFlow.PLATFORMS);
                    startActivity(new Intent(this, PlatformsActivity.class));
                },
                () -> {
                    guideShowing = false;
                    com.dsh.toolbox.ui.GuideFlow.set(this, com.dsh.toolbox.ui.GuideFlow.DONE);
                });
    }

    /** Last step of the walkthrough: run a sync with the newbie watching. */
    private void startSyncGuide() {
        View toolbar = findViewById(R.id.toolbar);
        if (toolbar == null) return;
        guideShowing = true;
        List<com.dsh.toolbox.ui.GuideOverlay.Item> steps = new ArrayList<>();
        steps.add(new com.dsh.toolbox.ui.GuideOverlay.Item(toolbar,
                "最后一步：同步一次",
                "平时每 6 小时会自动更新，想马上看到就点右上角的「同步」。点下面的按钮立刻同步一次。"));
        com.dsh.toolbox.ui.GuideOverlay.show(this, steps, "立即同步",
                () -> {
                    guideShowing = false;
                    // 同步完顺手把「后台保活」带用户开掉，否则 6 小时同步在国产 ROM 上跑不起来。
                    com.dsh.toolbox.ui.GuideFlow.set(this, com.dsh.toolbox.ui.GuideFlow.SETTINGS);
                    runYktDiag();
                },
                () -> {
                    guideShowing = false;
                    com.dsh.toolbox.ui.GuideFlow.set(this, com.dsh.toolbox.ui.GuideFlow.DONE);
                });
    }
}