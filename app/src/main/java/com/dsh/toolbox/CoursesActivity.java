package com.dsh.toolbox;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dsh.toolbox.data.Course;
import com.dsh.toolbox.data.CourseStore;
import com.dsh.toolbox.data.Sources;
import com.dsh.toolbox.data.YktClient;
import com.dsh.toolbox.data.YktSync;
import com.dsh.toolbox.data.ZhsSync;
import com.dsh.toolbox.ui.CourseAdapter;
import com.dsh.toolbox.ui.GuideFlow;
import com.dsh.toolbox.ui.GuideOverlay;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Pick which courses to monitor for one platform. */
public class CoursesActivity extends AppCompatActivity implements CourseAdapter.Listener {

    public static final String EXTRA_SOURCE = "source";

    private String source;
    private Sources.Info info;
    private CourseAdapter adapter;
    private RecyclerView list;
    private View progress;
    private TextView hint;
    private List<Course> courses = new ArrayList<>();
    private boolean guideShown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_courses);
        source = getIntent().getStringExtra(EXTRA_SOURCE);
        if (source == null || source.isEmpty()) source = Sources.YKT;
        info = Sources.byKey(source);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(info.name + " · 选择课程");
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_courses);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_open_web) {
                Intent i = new Intent(this, WebActivity.class);
                i.putExtra(WebActivity.EXTRA_SOURCE, source);
                i.putExtra(WebActivity.EXTRA_URL, info.startUrl);
                startActivity(i);
                return true;
            }
            return false;
        });

        progress = findViewById(R.id.progress);
        hint = findViewById(R.id.hint);
        list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CourseAdapter(this);
        list.setAdapter(adapter);

        findViewById(R.id.btnAll).setOnClickListener(v -> setAll(true));
        findViewById(R.id.btnNone).setOnClickListener(v -> setAll(false));
        MaterialButton save = findViewById(R.id.btnSave);
        save.setOnClickListener(v -> {
            CourseStore.save(this, source, courses);
            Toast.makeText(this, "已保存 " + CourseStore.selectedCount(this, source) + " 门课程",
                    Toast.LENGTH_SHORT).show();
            finish();
        });

        courses = CourseStore.load(this, source);
        render();
        refresh();
    }

    private void setAll(boolean on) {
        for (Course c : courses) c.selected = on;
        adapter.notifyDataSetChanged();
    }

    private void render() {
        List<Course> sorted = new ArrayList<>(courses);
        Collections.sort(sorted, new Comparator<Course>() {
            @Override
            public int compare(Course a, Course b) {
                int ta = a.term.startsWith("2026秋") ? 0 : 1;
                int tb = b.term.startsWith("2026秋") ? 0 : 1;
                if (ta != tb) return ta - tb;
                return b.term.compareTo(a.term);
            }
        });
        courses = sorted;
        adapter.submit(sorted);
        if (sorted.isEmpty()) {
            hint.setText("还没有课程列表，点右上角「网页」登录后回来刷新");
        } else {
            hint.setText("勾选要监控的课程（共 " + sorted.size() + " 门，已选 "
                    + CourseStore.selectedCount(this, source) + " 门）");
        }
        maybeGuide();
    }

    /** First-run walkthrough: teach ticking a row, then saving. */
    private void maybeGuide() {
        if (guideShown || !Sources.YKT.equals(source)) return;
        if (GuideFlow.step(this) != GuideFlow.PLATFORMS) return;
        list.post(() -> {
            if (list.getChildCount() == 0) return;
            guideShown = true;
            List<GuideOverlay.Item> items = new ArrayList<>();
            items.add(new GuideOverlay.Item(list.getChildAt(0),
                    "先勾上要盯的课",
                    "点这一行（或左边的方框），打勾＝以后只盯这门课的作业。", true));
            GuideOverlay.show(this, items, null,
                    () -> {
                        GuideFlow.set(this, GuideFlow.COURSES);
                        list.postDelayed(this::guideSave, 500);
                    },
                    () -> GuideFlow.set(this, GuideFlow.DONE));
        });
    }

    private void guideSave() {
        List<GuideOverlay.Item> items = new ArrayList<>();
        items.add(new GuideOverlay.Item(findViewById(R.id.btnSave),
                "点「保存」记住选择",
                "保存后每 6 小时自动查这些课，你什么都不用做。", true));
        GuideOverlay.show(this, items, null,
                () -> GuideFlow.set(this, GuideFlow.BACK),
                () -> GuideFlow.set(this, GuideFlow.DONE));
    }

    private void refresh() {
        progress.setVisibility(View.VISIBLE);
        new Thread(() -> {
            String msg = null;
            boolean needLogin = false;
            try {
                List<Course> fresh;
                if (Sources.KTP.equals(source)) {
                    // 课堂派的课程/作业接口还在摸底，先不动它的课程列表
                    fresh = new java.util.ArrayList<>();
                    runOnUiThread(() -> hint.setText(
                            "课堂派：先在网页里登录，然后回上一页点这张卡片的「登录」旁边的提示，"
                                    + "让 App 把接口数据抓下来（正在接入中）。"));
                } else if (Sources.ZHS.equals(source)) {
                    fresh = com.dsh.toolbox.data.ZhsSync.courses(this);
                } else if (Sources.CX.equals(source)) {
                    fresh = com.dsh.toolbox.data.CxSync.courses(this);
                } else {
                    fresh = YktSync.courses();
                }
                fresh = CourseStore.merge(this, source, fresh);
                List<Course> finalFresh = fresh;
                runOnUiThread(() -> {
                    courses = finalFresh;
                    render();
                });
            } catch (Throwable t) {
                boolean in;
                if (Sources.ZHS.equals(source)) in = com.dsh.toolbox.data.ZhsClient.loggedIn();
                else if (Sources.CX.equals(source)) in = com.dsh.toolbox.data.CxClient.loggedIn();
                else in = YktClient.loggedIn();
                needLogin = !in;
                msg = "刷新课程失败：" + (in ? t.toString() : "还没拿到这个平台的登录信息");
            }
            String finalMsg = msg;
            boolean finalNeedLogin = needLogin;
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                if (finalMsg == null) return;
                if (finalNeedLogin) {
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("先打开一次平台网页")
                            .setMessage("这个平台要把页面打开一次，登录信息才会落在手机上。\n"
                                    + "点「去打开」→ 登录 → 点右下角「选取课程」。")
                            .setPositiveButton("去打开", (d, w) -> {
                                Intent i = new Intent(this, WebActivity.class);
                                i.putExtra(WebActivity.EXTRA_SOURCE, source);
                                i.putExtra(WebActivity.EXTRA_URL, Sources.byKey(source).startUrl);
                                startActivity(i);
                            })
                            .setNegativeButton("知道了", null)
                            .show();
                } else {
                    Toast.makeText(this, finalMsg, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    @Override
    public void onToggle(Course course, boolean checked) {
        CourseStore.save(this, source, courses);
        hint.setText("已选 " + CourseStore.selectedCount(this, source) + " 门课程");
    }

    @Override
    public void onOpen(Course course) {
        Intent i = new Intent(this, WebActivity.class);
        i.putExtra(WebActivity.EXTRA_SOURCE, source);
        String url = Sources.ZHS.equals(source)
                ? ZhsSync.courseUrl(course)
                : Sources.CX.equals(source)
                ? com.dsh.toolbox.data.CxSync.courseUrl(course)
                : YktSync.courseUrl(course);
        i.putExtra(WebActivity.EXTRA_URL, url);
        startActivity(i);
    }
}