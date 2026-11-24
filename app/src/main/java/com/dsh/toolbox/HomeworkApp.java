package com.dsh.toolbox;

import android.app.Application;
import android.webkit.CookieManager;

import com.dsh.toolbox.data.CookieStore;
import com.dsh.toolbox.data.ShareServer;

/**
 * 进程启动时初始化 cookie 存档。
 *
 * <p>后台（闹钟/定时任务）拉起的进程不会创建 WebView：实测在这种进程里第一次调用
 * {@link CookieManager#getCookie} 会一直不返回，同步就卡死了。所以后台只用 {@link CookieStore}
 * 里的存档，存档由前台页面（WebActivity）在离开时刷新。</p>
 */
public class HomeworkApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        CookieStore.init(this);
        try {
            if (ShareServer.enabled(this)) ShareServer.start(this);
        } catch (Throwable ignored) {
        }
        try {
            CookieManager.getInstance().setAcceptCookie(true);
        } catch (Throwable ignored) {
        }
    }
}