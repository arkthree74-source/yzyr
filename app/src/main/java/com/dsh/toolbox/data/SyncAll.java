package com.dsh.toolbox.data;

import android.content.Context;
import android.webkit.CookieManager;

/**
 * One entry point that refreshes every selected platform.
 *
 * <p>Both the manual 「同步」 button and the background job go through here so the two paths can
 * never drift apart. The important rule: a platform whose session has expired never touches the
 * cached rows — it only reports {@link Report#sessionLost}, so the list keeps whatever it had
 * instead of silently emptying itself.</p>
 */
public final class SyncAll {

    public static final class Report {
        public int kept;
        public boolean anyRan;
        public boolean sessionLost;
        public String log = "";
        /** 每个平台分别抓到了多少条，用来给用户一个能看懂的结果。 */
        public final java.util.Map<String, Integer> perPlatform = new java.util.LinkedHashMap<>();
        /** 平台有选课但登录信息缺失。 */
        public final java.util.Set<String> authMissing = new java.util.LinkedHashSet<>();
    }

    private SyncAll() {
    }

    public static Report run(Context ctx) {
        return run(ctx, null);
    }

    /** 带进度的同步：界面传一个监听进来就能画进度条，后台同步传 null。 */
    public static Report run(Context ctx, SyncProgress.Listener listener) {
        Report r = new Report();
        SyncProgress prog = new SyncProgress(listener);
        StringBuilder log = new StringBuilder();
        try {
            r.anyRan |= syncYkt(ctx, log, r, prog);
            r.anyRan |= syncZhs(ctx, log, r, prog);
            r.anyRan |= syncCx(ctx, log, r, prog);
        } catch (Throwable t) {
            log.append("ERROR ").append(t).append('\n');
        } finally {
            flushCookies();
        }
        log.append("kept=").append(r.kept).append('\n');
        r.log = log.toString();
        YktSync.write(ctx, "sync_diag.log", r.log);
        SyncHistory.add(ctx, r);
        if (r.sessionLost) Notifier.sessionLost(ctx);
        return r;
    }

    private static boolean syncYkt(Context ctx, StringBuilder log, Report r, SyncProgress prog) {
        if (!CourseStore.anySelected(ctx, Sources.YKT)) return false;
        boolean auth = YktClient.loggedIn();
        log.append("== yuketang ==").append(auth ? "" : " (cookie missing, trying anyway)").append('\n');
        int n = YktSync.syncSelected(ctx, log, prog);
        r.kept += n;
        r.perPlatform.put(Sources.YKT, n);
        if (!auth) {
            r.authMissing.add(Sources.YKT);
            if (n == 0) r.sessionLost = true;
        }
        return true;
    }

    private static boolean syncZhs(Context ctx, StringBuilder log, Report r, SyncProgress prog) {
        if (!CourseStore.anySelected(ctx, Sources.ZHS)) return false;
        boolean auth = ZhsClient.loggedIn();
        log.append("== zhihuishu ==").append(auth ? "" : " (no token/cookie, trying anyway)").append('\n');
        int n = ZhsSync.syncSelected(ctx, log, prog);
        r.kept += n;
        r.perPlatform.put(Sources.ZHS, n);
        if (!auth) {
            r.authMissing.add(Sources.ZHS);
            if (n == 0) r.sessionLost = true;
        }
        return true;
    }

    private static boolean syncCx(Context ctx, StringBuilder log, Report r, SyncProgress prog) {
        if (!CourseStore.anySelected(ctx, Sources.CX)) return false;
        boolean auth = CxClient.loggedIn();
        log.append("== chaoxing ==").append(auth ? "" : " (cookie missing, trying anyway)").append('\n');
        int n = CxSync.syncSelected(ctx, log, prog);
        r.kept += n;
        r.perPlatform.put(Sources.CX, n);
        if (!auth) {
            r.authMissing.add(Sources.CX);
            if (n == 0) r.sessionLost = true;
        }
        return true;
    }

    /** Persist the WebView cookie jar so a cold start still has the sessions. */
    public static void flushCookies() {
        // 只有前台进程（WebView 已初始化）才碰 CookieManager，后台碰它会卡死。
        CookieStore.flushIfWarm();
    }
}