package com.dsh.toolbox;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.dsh.toolbox.data.AuthStore;
import com.dsh.toolbox.data.CookieStore;
import com.dsh.toolbox.data.Extractor;
import com.dsh.toolbox.data.Homework;
import com.dsh.toolbox.data.JwClient;
import com.dsh.toolbox.data.JwParser;
import com.dsh.toolbox.data.Sources;
import com.dsh.toolbox.data.Timetable;
import com.dsh.toolbox.data.TimetableStore;
import com.dsh.toolbox.data.Store;
import com.dsh.toolbox.ui.HomeworkAdapter;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Shows the official platform website and extracts homework candidates from
 * the page's network traffic plus its DOM.
 */
public class WebActivity extends AppCompatActivity {

    public static final String EXTRA_SOURCE = "source";
    public static final String EXTRA_URL = "url";

    /** 教务系统「学生个人课表」入口（正方 jwxt）。 */
    public static final String JW_TABLE_URL =
            "https://jwxt.upc.edu.cn/jsxsd/xskb/xskb_list.do?Ves632DSdyV=NEW_XSD_PYGL";

    /** 智慧树「待完成」业务页：登录凭证 Authorization 只在这类页面的请求里出现。 */
    private static final String ZHS_APP =
            "https://hike-teaching-center.polymas.com/custom-stu-hike/studyTask";

    /** 学习通「学习规划」业务页：stat2-ans 的会话 cookie 要打开它才会落到 cookie jar。 */
    private static final String CX_APP =
            "https://stat2-ans.chaoxing.com/stat2-vue/studyPlanAssistant";

    private boolean pendingCourses;

    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private static final String EXTRACT_JS =
            "(function(){try{"
                    + "var cap=(window.__hwCapture||[]).slice(-40).map(function(x){"
                    + "return {u:String(x.u||''),t:String(x.t||'').slice(0,60000),"
                    + "h:(x.h&&typeof x.h==='object'?x.h:null)};});"
                    + "var dom=[];try{dom=(window.__hwHarvest?window.__hwHarvest():[]);}catch(e){}"
                    + "return JSON.stringify({cap:cap,dom:dom});"
                    + "}catch(e){return JSON.stringify({cap:[],dom:[],err:String(e)});}})()";

    private static final String LOG_JS =
            "(function(){try{var a=window.__hwCapture||[];var u=a.map(function(x){return String(x.u||'').slice(0,90);});"
                    + "var m={iw:window.innerWidth,sw:document.documentElement?document.documentElement.scrollWidth:0,"
                    + "dpr:window.devicePixelRatio||1,vv:(window.visualViewport?window.visualViewport.scale:0),"
                    + "title:String(document.title||'')};"
                    + "return JSON.stringify({n:a.length,u:u.slice(-12),m:m});}catch(e){return JSON.stringify({n:0,u:[],m:{}});}})()";

    private String source;
    private Sources.Info info;
    private Store store;
    private WebView web;
    private ProgressBar progress;
    private TextView hint;
    private MaterialButton btnExtract;
    private String defaultUa;
    private boolean desktop;
    private String lastLog = "";
    private MaterialToolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web);

        store = Store.get(this);
        source = getIntent().getStringExtra(EXTRA_SOURCE);
        if (source == null) source = Sources.YKT;
        info = Sources.byKey(source);
        desktop = store.prefs().getBoolean("desktop_" + source,
                Sources.CX.equals(source) || Sources.JW.equals(source)
                || Sources.KTP.equals(source));

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        this.toolbar = toolbar;
        toolbar.setTitle(info.name + " · 登录与抓取");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        progress = findViewById(R.id.progress);
        hint = findViewById(R.id.hint);
        web = findViewById(R.id.web);
        btnExtract = findViewById(R.id.btnExtract);
        findViewById(R.id.btnBack).setOnClickListener(v -> {
            if (web.canGoBack()) web.goBack();
            else onBackPressed();
        });
        btnExtract.setOnClickListener(v -> onPrimaryAction());
        findViewById(R.id.btnMore).setOnClickListener(v -> showMore());

        setupWeb();
        applyUa();

        String url = getIntent().getStringExtra(EXTRA_URL);
        // diag-only deep link: dshopen://open?source=ykt&url=<encoded>
        Uri data = getIntent().getData();
        if (data != null && "dshopen".equals(data.getScheme())) {
            String s = data.getQueryParameter("source");
            if (s != null && !s.isEmpty()) {
                source = s;
                info = Sources.byKey(source);
                toolbar.setTitle(info.name + " · 登录与抓取");
            }
            String u = data.getQueryParameter("url");
            if (u != null && !u.isEmpty()) url = u;
        }
        if (url == null || url.isEmpty()) url = info.startUrl;
        applyPrimaryAction();
        web.loadUrl(url);
    }

    /** After login every platform leads to course picking; the 教务系统 leads to its 课表 page. */
    private void applyPrimaryAction() {
        if (Sources.JW.equals(source)) {
            btnExtract.setText("打开课表查询");
        } else if (Sources.KTP.equals(source)) {
            btnExtract.setText("测一下课堂派接口");
        } else {
            btnExtract.setText("选取课程");
        }
    }

    private void onPrimaryAction() {
        if (Sources.JW.equals(source)) {
            hint.setText("正在打开「学生个人课表」…");
            web.loadUrl(JW_TABLE_URL);
            return;
        }
        if (Sources.KTP.equals(source)) {
            // 课堂派的接口还在摸底：先把它几个候选接口的原始响应存下来，确认结构再接自动同步
            hint.setText("正在试课堂派接口，几秒就好…");
            new Thread(() -> {
                String log = com.dsh.toolbox.data.KtpClient.probe(this);
                runOnUiThread(() -> {
                    hint.setText("课堂派接口探测完成，结果已存到 ktp_probe.txt");
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("课堂派接口探测")
                            .setMessage(log)
                            .setPositiveButton("好", null)
                            .show();
                });
            }).start();
            return;
        }
        // 智慧树的登录态在 polymas 业务页的请求头里，学习通的会话 cookie 在 stat2-ans 上，
        // 只登录主站是拿不到的：先替用户把业务页打开一次，落好凭证再进选课页。
        if (Sources.ZHS.equals(source) && AuthStore.token(this, source).isEmpty()) {
            pendingCourses = true;
            hint.setText("正在打开待完成页面，取一次登录凭证…");
            web.loadUrl(ZHS_APP);
            return;
        }
        if (Sources.CX.equals(source) && cxCookieMissing()) {
            pendingCourses = true;
            hint.setText("正在打开学习规划页面，取一次登录凭证…");
            web.loadUrl(CX_APP);
            return;
        }
        openCourses();
    }

    private void openCourses() {
        Intent i = new Intent(this, CoursesActivity.class);
        i.putExtra(CoursesActivity.EXTRA_SOURCE, source);
        startActivity(i);
    }

    private boolean cxCookieMissing() {
        String c = CookieManager.getInstance().getCookie("https://stat2-ans.chaoxing.com");
        return c == null || c.isEmpty();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Uri data = intent.getData();
        if (data == null || !"dshopen".equals(data.getScheme())) return;
        String s = data.getQueryParameter("source");
        if (s != null && !s.isEmpty()) {
            source = s;
            info = Sources.byKey(source);
            toolbar.setTitle(info.name + " · 登录与抓取");
        }
        String u = data.getQueryParameter("url");
        lastLog = "";
        applyPrimaryAction();
        if (u != null && !u.isEmpty()) web.loadUrl(u);
    }

    private void showMore() {
        final String[] items = {
                desktop ? "切换为手机版网页" : "切换为桌面版网页",
                "回到平台首页",
                "重新加载",
                "查看抓取日志",
                "抓取接口样本（不入库）",
                "保存页面存储",
                "清除本平台数据",
                "调试命令",
                "手动提取本页作业（并入清单）",
                "导出页面 HTML",
                "运行探测脚本",
                "探测超星接口（Java）"
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle("更多")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            toggleDesktop();
                            break;
                        case 1:
                            web.loadUrl(info.startUrl);
                            break;
                        case 2:
                            web.reload();
                            break;
                        case 3:
                            showLog();
                            break;
                        case 4:
                            captureSample();
                            break;
                        case 5:
                            dumpStorage();
                            break;
                        case 6:
                            confirmClear();
                            break;
                        case 7:
                            showDebug();
                            break;
                        case 8:
                            extract();
                            break;
                        case 9:
                            exportHtml();
                            break;
                        case 10:
                            runProbe();
                            break;
                        case 11:
                            runCxProbe();
                            break;
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private final java.util.Set<String> seenUrls = new java.util.HashSet<>();

    /** Append every interesting request URL so we can find the platform's real APIs. */
    private synchronized void netLog(String url) {
        if (!seenUrls.add(url)) return;
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(
                new java.io.File(getFilesDir(), "net.log"), true)) {
            out.write((url + "\n").getBytes("UTF-8"));
        } catch (Exception ignored) {
        }
    }

    /** Replay the chaoxing endpoints in Java so we see the raw JSON with the session cookies. */
    private void runCxProbe() {
        Toast.makeText(this, "正在探测超星接口…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String text;
            try {
                text = com.dsh.toolbox.data.CxProbe.run(this);
            } catch (Throwable t) {
                text = "FAILED " + t;
            }
            final String result = text;
            dump("cx_probe.txt", result);
            runOnUiThread(() -> Toast.makeText(this,
                    "探测完成（" + result.length() + " 字符）", Toast.LENGTH_SHORT).show());
        }).start();
    }

    /** Run assets/probe.js inside the page and save whatever it reports to files/probe.json. */
    private void runProbe() {
        String js = readAsset("probe.js");
        if (js == null) {
            Toast.makeText(this, "缺少 probe.js", Toast.LENGTH_SHORT).show();
            return;
        }
        web.evaluateJavascript("(function(){window.__probe='';\n" + js + "\n})()", null);
        final android.os.Handler handler = new android.os.Handler(getMainLooper());
        final int[] tries = {0};
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                web.evaluateJavascript("window.__probe||''", value -> {
                    String s = unwrap(value);
                    if (s != null && !s.trim().isEmpty() && !"''".equals(s)) {
                        dump("probe.json", s);
                        Toast.makeText(WebActivity.this,
                                "探测结果已保存（" + s.length() + " 字符）", Toast.LENGTH_SHORT).show();
                    } else if (tries[0]++ < 20) {
                        handler.postDelayed(this, 800);
                    } else {
                        Toast.makeText(WebActivity.this, "探测超时", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }, 800);
    }

    /** Save the rendered HTML (plus same-origin iframes) so we can inspect the real markup offline. */
    private void exportHtml() {
        String js = "(function(){try{var out=[document.documentElement.outerHTML];"
                + "var fs=document.querySelectorAll('iframe');"
                + "for(var i=0;i<fs.length;i++){try{var d=fs[i].contentDocument;"
                + "if(d&&d.documentElement){out.push('\\n<!--IFRAME '+fs[i].src+'-->\\n'+d.documentElement.outerHTML);}}catch(e){}}"
                + "return out.join('');}catch(e){return 'ERR '+e;}})()";
        web.evaluateJavascript(js, value -> {
            String html = unwrap(value);
            if (html == null || html.length() < 20) {
                Toast.makeText(this, "页面还没准备好", Toast.LENGTH_SHORT).show();
                return;
            }
            dump("page.html", html);
            Toast.makeText(this, "页面 HTML 已保存（" + html.length() + " 字节）",
                    Toast.LENGTH_SHORT).show();
        });
    }

    /** Dump the page storage so we can see how the site keeps its login token. */
    private void dumpStorage() {
        String js = "(function(){try{var o={local:{},session:{},cookie:document.cookie};"
                + "for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);"
                + "o.local[k]=String(localStorage.getItem(k)).slice(0,4000);}"
                + "for(var j=0;j<sessionStorage.length;j++){var s=sessionStorage.key(j);"
                + "o.session[s]=String(sessionStorage.getItem(s)).slice(0,4000);}"
                + "return JSON.stringify(o);}catch(e){return 'ERR '+e;}})()";
        web.evaluateJavascript(js, value -> {
            String r = unwrap(value);
            dump("storage.txt", r == null ? "" : r);
            Toast.makeText(this, "页面存储已保存", Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Save the raw network payloads of the current page to the app folder and leave the
     * list untouched. Used while wiring a new platform up, never in normal use.
     */
    private void captureSample() {
        hint.setText("正在抓取接口样本…");
        web.evaluateJavascript(EXTRACT_JS, value -> {
            String raw = unwrap(value);
            if (raw == null || raw.isEmpty()) {
                hint.setText("页面还没准备好");
                return;
            }
            String cap = "[]";
            String dom = "[]";
            try {
                JSONObject o = new JSONObject(raw);
                JSONArray c = o.optJSONArray("cap");
                JSONArray dm = o.optJSONArray("dom");
                if (c != null) cap = c.toString();
                if (dm != null) dom = dm.toString();
            } catch (Exception ignored) {
            }
            dump("sample_capture.json", cap);
            dump("sample_dom.json", dom);
            dump("sample_page.txt", web.getUrl());
            hint.setText("样本已保存：" + shortUrl(web.getUrl()));
            Toast.makeText(this, "接口样本已保存", Toast.LENGTH_SHORT).show();
        });
    }

    private void showDebug() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("url:... 或 js:...");
        input.setTextSize(13);
        new MaterialAlertDialogBuilder(this)
                .setTitle("调试命令")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("执行", (d, w) -> {
                    String cmd = input.getText().toString().trim();
                    if (cmd.startsWith("url:")) {
                        web.loadUrl(cmd.substring(4).trim());
                    } else if (cmd.startsWith("js:")) {
                        web.evaluateJavascript(cmd.substring(3), v -> {
                            String r = unwrap(v);
                            dump("last_eval.txt", r == null ? "" : r);
                            Toast.makeText(this, r == null ? "" : r, Toast.LENGTH_LONG).show();
                        });
                    }
                })
                .show();
    }

    private void toggleDesktop() {
        desktop = !desktop;
        store.prefs().edit().putBoolean("desktop_" + source, desktop).apply();
        applyUa();
        Toast.makeText(this, desktop ? "已切换桌面版网页，重新加载" : "已切换手机版网页，重新加载",
                Toast.LENGTH_SHORT).show();
        web.reload();
    }

    private void setupWeb() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        defaultUa = s.getUserAgentString();

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setVisibility(View.VISIBLE);
                progress.setProgress(newProgress);
            }
        });

        web.setWebViewClient(new WebViewClient() {
            @Override
            public android.webkit.WebResourceResponse shouldInterceptRequest(
                    WebView view, android.webkit.WebResourceRequest req) {
                try {
                    String u = req.getUrl().toString();
                    if (u.contains("chaoxing") || u.contains("yuketang") || u.contains("polymas")) {
                        netLog(u);
                    }
                } catch (Throwable ignored) {
                }
                return null;
            }
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progress.setVisibility(View.VISIBLE);
                progress.setProgress(0);
                hint.setText("正在加载…");
                injectScripts();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                toolbar.setSubtitle(url);
                progress.setVisibility(View.GONE);
                if (Sources.JW.equals(source)) {
                    if (url != null && url.contains("xskb")) {
                        hint.setText("正在读取课表…");
                        captureTimetable();
                    } else if (url != null && url.contains("/cas/")) {
                        hint.setText("先在这里登录学校账号，登录完点右下角「打开课表查询」");
                    } else {
                        hint.setText("登录后点菜单「课程」→「各类课表查询」，或直接点右下角「打开课表查询」");
                    }
                } else {
                    hint.setText("登录完成后点右下角「选取课程」");
                }
                injectScripts();
                captureToken();
                if (pendingCourses) {
                    boolean ready = Sources.ZHS.equals(source)
                            ? (!AuthStore.token(WebActivity.this, source).isEmpty()
                               || (url != null && url.contains("polymas.com")))
                            : (url != null && url.contains("stat2-ans.chaoxing.com"));
                    if (ready) {
                        pendingCourses = false;
                        hint.setText("已拿到登录凭证，正在打开课程列表…");
                        openCourses();
                    } else {
                        hint.setText("请在这个页面登录一次，登录完成后会自动打开课程列表");
                    }
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String u = uri.toString();
                if (u.startsWith("http://") || u.startsWith("https://")) return false;
                try {
                    Intent i = Intent.parseUri(u, Intent.URI_INTENT_SCHEME);
                    i.addCategory(Intent.CATEGORY_BROWSABLE);
                    i.setComponent(null);
                    startActivity(i);
                } catch (Exception ignored) {
                }
                return true;
            }
        });
    }

    private void applyUa() {
        String ua = desktop ? DESKTOP_UA : defaultUa;
        if (ua != null && !ua.isEmpty()) web.getSettings().setUserAgentString(ua);
    }

    private void injectScripts() {
        String hook = readAsset("hook.js");
        if (hook != null) web.evaluateJavascript(hook, null);
        String harvest = readAsset("harvest.js");
        if (harvest != null) web.evaluateJavascript(harvest, null);
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name);
             BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

// ---------------- 教务系统课表 ----------------

    private void captureTimetable() {
        hint.setText("正在读取课表…");
        new Thread(() -> {
            String html = JwClient.table(getApplicationContext());
            String main = JwClient.entry(getApplicationContext());
            if (main != null) dump("jw_main.html", main);
            List<Timetable> list = html == null ? new ArrayList<>() : JwParser.parse(html);
            if (list.isEmpty()) {
                // fallback: read the rendered DOM (some 正方 deployments render the grid with JS)
                runOnUiThread(() -> web.evaluateJavascript(
                        "(function(){try{return document.documentElement.outerHTML;}catch(e){return '';}})()",
                        value -> {
                            String dom = unwrap(value);
                            List<Timetable> fromDom = dom == null ? new ArrayList<>() : JwParser.parse(dom);
                            if (dom != null) dump("jw_kb.html", dom);
                            if (fromDom.isEmpty()) {
                                hint.setText("没识别到课表。确认页面是「学生个人课表」，再点一次「打开课表查询」");
                                Toast.makeText(this, "没识别到课表", Toast.LENGTH_LONG).show();
                            } else {
                                saveTimetable(fromDom, dom);
                            }
                        }));
                return;
            }
            dump("jw_kb.html", html);
            final List<Timetable> finalList = list;
            runOnUiThread(() -> saveTimetable(finalList, html));
        }).start();
    }

    private void saveTimetable(List<Timetable> list, String html) {
        String term = "";
        if (html != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(20\\d{2})\\s*[-—]\\s*(20\\d{2})\\s*学年")
                    .matcher(html);
            if (m.find()) term = m.group(1) + "-" + m.group(2) + " 学年";
        }
        TimetableStore.save(this, list, term);
        TimetableStore.saveSectionTimes(this, JwParser.sectionTimes(html));
        Toast.makeText(this, "已导入 " + list.size() + " 节课", Toast.LENGTH_LONG).show();
        hint.setText("课表已导入，回主页看「课表」");
        finish();
    }

    // ---------------- extraction ----------------

    private void extract() {
        btnExtract.setEnabled(false);
        hint.setText("正在读取页面数据…");
        web.evaluateJavascript(EXTRACT_JS, value -> {
            btnExtract.setEnabled(true);
            handleExtraction(value);
        });
    }

    private void handleExtraction(String value) {
        String raw = unwrap(value);
        if (raw == null || raw.isEmpty()) {
            hint.setText("页面还没准备好，请稍等再试");
            return;
        }
        String cap = "[]";
        String dom = "[]";
        try {
            JSONObject o = new JSONObject(raw);
            JSONArray capArr = o.optJSONArray("cap");
            JSONArray domArr = o.optJSONArray("dom");
            if (capArr != null) cap = capArr.toString();
            if (domArr != null) dom = domArr.toString();
        } catch (Exception e) {
            hint.setText("页面数据解析失败，重试或换桌面版试试");
            return;
        }

        Extractor.Stats stats = new Extractor.Stats();
        List<Homework> found = Extractor.parse(source, web.getUrl(), cap, dom, stats);
        lastLog = buildLog(stats, cap, dom);
        dump("last_capture.json", cap);
        dump("last_dom.json", dom);
        dump("last_page.txt", String.valueOf(stats.page));

        if (found.isEmpty()) {
            hint.setText("没识别到作业");
            new MaterialAlertDialogBuilder(this)
                    .setTitle("没识别到作业")
                    .setMessage("当前页面：" + shortUrl(stats.page)
                            + "\n接口响应：" + stats.jsonBodies + " 条"
                            + "\n页面DOM：" + stats.domNodes + " 条"
                            + "\n\n建议：\n1. 先确认已登录成功\n2. 进入「作业 / 学习任务」列表页\n"
                            + "3. 等页面完全加载后再点「提取」\n4. 仍不行可切到「桌面版网页」重试")
                    .setNegativeButton("关闭", null)
                    .setNeutralButton("看日志", (d, w) -> showLog())
                    .setPositiveButton("重试", (d, w) -> extract())
                    .show();
            return;
        }

        showConfirmDialog(found);
    }

    private void showConfirmDialog(final List<Homework> found) {
        final boolean[] checked = new boolean[found.size()];
        Arrays.fill(checked, true);
        final CharSequence[] labels = new CharSequence[found.size()];
        long now = System.currentTimeMillis();
        for (int i = 0; i < found.size(); i++) {
            Homework h = found.get(i);
            String due = HomeworkAdapter.dueLabel(h.dueAt, now);
            labels[i] = h.title + "\n" + (h.course.isEmpty() ? "" : h.course + " · ") + due;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("发现 " + found.size() + " 条，确认加入")
                .setMultiChoiceItems(labels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        checked[which] = isChecked;
                    }
                })
                .setNegativeButton("取消", null)
                .setPositiveButton("加入清单", (d, w) -> {
                    List<Homework> picked = new ArrayList<>();
                    for (int i = 0; i < found.size(); i++) {
                        if (checked[i]) picked.add(found.get(i));
                    }
                    if (picked.isEmpty()) {
                        Toast.makeText(this, "没有选择任何条目", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int added = store.merge(picked);
                    store.setSyncUrl(source, web.getUrl());
                    store.setLastSync(source, System.currentTimeMillis());
                    Toast.makeText(this, "新增 " + added + " 条，已更新 " + picked.size() + " 条",
                            Toast.LENGTH_SHORT).show();
                    finish();
                })
                .show();
    }

    private String buildLog(Extractor.Stats stats, String cap, String dom) {
        StringBuilder sb = new StringBuilder();
        sb.append("页面：").append(shortUrl(stats.page)).append('\n');
        sb.append("接口响应：").append(stats.jsonBodies).append(" 条\n");
        sb.append("DOM节点：").append(stats.domNodes).append(" 条\n");
        sb.append("捕获原始：").append(cap.length()).append(" 字符（dom ").append(dom.length()).append("）\n");
        sb.append("\n最近请求：\n");
        try {
            JSONArray arr = new JSONArray(cap);
            int from = Math.max(0, arr.length() - 8);
            for (int i = from; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                sb.append("· ").append(shortUrl(o.optString("u", ""))).append('\n');
            }
        } catch (Exception ignored) {
        }
        return sb.toString();
    }

    private void showLog() {
        String text = lastLog.isEmpty() ? "还没有提取记录，先点一次「提取」。" : lastLog;
        web.evaluateJavascript(LOG_JS, value -> {
            String extra = "";
            try {
                JSONObject o = new JSONObject(unwrap(value));
                JSONObject m = o.optJSONObject("m");
                extra = "\n当前页面已捕获接口：" + o.optInt("n") + " 条";
                if (m != null) {
                    extra += "\n视口宽：" + m.optInt("iw") + "px  页面宽：" + m.optInt("sw") + "px"
                            + "  DPR：" + m.optDouble("dpr", 1) + "  缩放：" + m.optDouble("vv", 0);
                }
            } catch (Exception ignored) {
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle("抓取日志")
                    .setMessage(text + extra)
                    .setPositiveButton("知道了", null)
                    .show();
        });
    }

    private void confirmClear() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("清除本平台数据？")
                .setMessage("将退出登录状态并删除已抓取的作业记录。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清除", (d, w) -> {
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();
                    store.clearSource(source);
                    store.setSyncUrl(source, "");
                    store.setLastSync(source, 0);
                    web.clearHistory();
                    web.clearCache(true);
                    web.loadUrl(info.startUrl);
                    Toast.makeText(this, "已清除", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void dump(String name, String content) {
        if (content == null) return;
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(
                new java.io.File(getFilesDir(), name))) {
            out.write(content.getBytes("UTF-8"));
        } catch (Exception ignored) {
        }
    }

    private String unwrap(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            try {
                Object o = new JSONTokener(v).nextValue();
                return String.valueOf(o);
            } catch (Exception ignored) {
                return v.substring(1, v.length() - 1);
            }
        }
        return v;
    }

    /**
     * 智慧树把登录态放在请求头 Authorization 里（localStorage 不存），
     * 这里从 hook 抓到的请求头里捞出来存下来，后台同步就能直接用。
     */
    private void captureToken() {
        if (!Sources.ZHS.equals(source)) return;
        web.evaluateJavascript(TOKEN_JS, value -> {
            String t = unwrap(value);
            if (t != null && t.length() > 40) {
                AuthStore.setToken(this, source, t);
                hint.setText("已获取登录凭证，回到主页点「同步」即可");
            }
        });
    }

    private static String TOKEN_JS =
            "(function(){try{var c=window.__hwCapture||[];"
                    + "for(var i=c.length-1;i>=0;i--){var h=c[i]&&c[i].h;if(!h)continue;"
                    + "for(var k in h){if(String(k).toLowerCase()==='authorization'){return String(h[k]);}}"
                    + "}return '';}catch(e){return '';}})()";

    private static String shortUrl(String url) {
        if (url == null) return "";
        String u = url;
        int q = u.indexOf('?');
        if (q > 0) u = u.substring(0, q);
        if (u.length() > 80) u = u.substring(0, 77) + "…";
        return u;
    }

    // ---------------- menu / back ----------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_web, menu);
        MenuItem item = menu.findItem(R.id.action_desktop);
        if (item != null) item.setChecked(desktop);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }
        if (id == R.id.action_desktop) {
            item.setChecked(desktop);
            toggleDesktop();
            return true;
        }
        if (id == R.id.action_log) {
            showLog();
            return true;
        }
        if (id == R.id.action_clear) {
            confirmClear();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        // Keep the login cookies on disk: the daily sync reads them without opening a page.
        try {
            CookieManager.getInstance().flush();
            // 存档给后台同步用（后台进程不能碰 CookieManager）。
            CookieStore.capture(this);
        } catch (Exception ignored) {
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try {
            CookieManager.getInstance().flush();
        } catch (Exception ignored) {
        }
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
        }
        super.onDestroy();
    }
}