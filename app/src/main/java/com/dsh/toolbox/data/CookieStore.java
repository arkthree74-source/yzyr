package com.dsh.toolbox.data;

import android.content.Context;
import android.webkit.CookieManager;

/**
 * Cookie 存档。
 *
 * <p>后台进程里 WebView 还没初始化时，直接问 {@link CookieManager} 可能一直不返回（实测：闹钟
 * 拉起的前台服务卡死在第一次 getCookie 上，两分钟没有任何进展）。所以改成：<b>只有应用在前台、
 * WebView 已经热起来的时候才去读 CookieManager</b>，读到的值顺手存进 prefs；后台同步一律用存档。</p>
 */
public final class CookieStore {

    /** 需要长期保留登录态的几个站点。 */
    private static final String[] HOSTS = {
            "https://www.yuketang.cn",
            "https://cloudapi.polymas.com",
            "https://stat2-ans.chaoxing.com",
            "https://i.mooc.chaoxing.com",
            "https://jwxt.upc.edu.cn",
    };

    private static Context app;
    /** 应用是否在「前台且 WebView 已初始化」的状态。 */
    private static volatile boolean warm;

    private CookieStore() {
    }

    public static void init(Context ctx) {
        if (app == null) app = ctx.getApplicationContext();
    }

    /**
     * 由 Activity 在本进程 WebView 可用后调用：把 CookieManager 里的值刷进存档。
     * 只有这里会碰 CookieManager，后台线程永远不碰。
     */
    public static void capture(Context ctx) {
        init(ctx);
        warm = true;
        for (String host : HOSTS) {
            String c = read(host);
            if (c != null && !c.isEmpty()) save(host, c);
        }
    }

    /** 后台安全的读取：只读存档，不碰 WebView。 */
    public static String cookie(String origin) {
        if (app == null) return null;
        return Store.get(app).prefs().getString(key(origin), null);
    }

    /**
     * 后台进程开始时试一下能不能读到实时 cookie：读得到就刷新存档，读不到（卡住）就超时放弃，
     * 后面全程用存档。这样既不会卡死，也不至于永远用旧 cookie。
     */
    public static void captureWithTimeout(Context ctx, long timeoutMs) {
        init(ctx);
        Thread t = new Thread(() -> {
            for (String host : HOSTS) {
                String c = read(host);
                if (c != null && !c.isEmpty()) save(host, c);
            }
        }, "cookie-capture");
        t.setDaemon(true);
        t.start();
        try {
            t.join(timeoutMs);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    public static boolean isWarm() {
        return warm;
    }

    /** 前台进程里把内存里的 cookie 落盘；后台进程直接跳过。 */
    public static void flushIfWarm() {
        if (!warm) return;
        try {
            CookieManager.getInstance().flush();
        } catch (Throwable ignored) {
        }
    }

    private static String read(String origin) {
        try {
            return CookieManager.getInstance().getCookie(origin);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void save(String origin, String cookie) {
        if (app == null) return;
        Store.get(app).prefs().edit().putString(key(origin), cookie).apply();
    }

    private static String key(String origin) {
        return "ck_" + origin.replaceAll("[^A-Za-z0-9]", "_");
    }
}