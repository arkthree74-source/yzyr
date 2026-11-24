package com.dsh.toolbox.data;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Thin HTTP client for the chaoxing (学习通) statistics host.
 *
 * <p>Everything we need lives on {@code stat2-ans.chaoxing.com} and authenticates purely with the
 * cookies the in-app WebView collected when the user signed in — no extra signing headers.</p>
 */
public final class CxClient {

    public static final String HOST = "https://stat2-ans.chaoxing.com";

    /** The AI 学习规划助手 page that the API belongs to; used as Referer. */
    static final String REFERER = HOST + "/stat2-vue/studyPlanAssistant";

    /** One call returns the course list plus every task that is due soon. */
    static final String PLAN_PATH = "/stat2/learning/plan/recommended-course-list";

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/122.0.0.0 Safari/537.36";

    private static Context app;

    private CxClient() {
    }

    public static void init(Context ctx) {
        app = ctx.getApplicationContext();
    }

    public static boolean loggedIn() {
        String cookie = CookieStore.cookie(HOST);
        if (cookie != null && !cookie.isEmpty()) return true;
        String any = CookieStore.cookie("https://i.mooc.chaoxing.com");
        return any != null && !any.isEmpty();
    }

    public static String plan() throws Exception {
        return get(PLAN_PATH);
    }

    static String get(String path) throws Exception {
        String cookie = CookieStore.cookie(HOST);
        HttpURLConnection c = (HttpURLConnection) new URL(HOST + path).openConnection();
        try {
            c.setRequestMethod("GET");
            c.setConnectTimeout(12000);
            c.setReadTimeout(25000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "application/json, text/plain, */*");
            c.setRequestProperty("Referer", REFERER);
            if (cookie != null && !cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String body = read(in);
            if (app != null) {
                YktSync.write(app, "cx_http.log",
                        "path=" + path + "\ncode=" + code + "\ncookie=" + (cookie == null ? 0 : cookie.length())
                                + "\nbody=" + (body.length() > 4000 ? body.substring(0, 4000) : body) + "\n");
            }
            if (code >= 400) throw new IllegalStateException("HTTP " + code);
            return body;
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        try (InputStream i = in; ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = i.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}