package com.dsh.toolbox.data;

import android.content.Context;
import android.webkit.CookieManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Fetches the 正方(jwxt) 学生个人课表 page with the cookies WebView already holds
 * (the school uses CAS SSO, so the login itself happens in the WebView).
 */
public final class JwClient {

    public static final String HOST = "https://jwxt.upc.edu.cn";
    public static final String ENTRY = HOST + "/jsxsd/framework/xsMainV.htmlx";
    public static final String TABLE =
            HOST + "/jsxsd/xskb/xskb_list.do?Ves632DSdyV=NEW_XSD_PYGL";

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private JwClient() {
    }

    public static boolean loggedIn() {
        String c = CookieManager.getInstance().getCookie(HOST);
        return c != null && c.contains("JSESSIONID");
    }

    /** @return the timetable HTML, or null when the session is gone / the request failed. */
    public static String table(Context ctx) {
        String html = get(TABLE);
        if (html == null || html.length() < 400 || html.contains("cas/login")) {
            // fall back to the entry page once, in case the CAS ticket round-trip needs it
            get(ENTRY);
            html = get(TABLE);
        }
        if (html == null || html.contains("cas/login")) return null;
        return html;
    }

    /** @return the 教务系统 home page HTML (used to read 当前周 / 学期开始日期). */
    public static String entry(Context ctx) {
        return get(ENTRY);
    }

    private static String get(String spec) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(spec);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(25000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            conn.setRequestProperty("Referer", HOST + "/jsxsd/framework/xsMainV.htmlx");
            String cookie = CookieManager.getInstance().getCookie(spec.startsWith("https") ? HOST : spec);
            if (cookie != null && !cookie.isEmpty()) conn.setRequestProperty("Cookie", cookie);
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}