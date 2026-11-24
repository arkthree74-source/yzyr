package com.dsh.toolbox.data;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Minimal reader for the 智慧树 (zhihuishu) student API used by the mobile site.
 *
 * <p>The site keeps the session in its cookies, which the WebView already owns after the
 * student signs in, so requests here simply reuse them.
 */
public final class ZhsClient {

    public static final String HOST = "https://cloudapi.polymas.com";
    private static final String REFERER = "https://hike-teaching-center.polymas.com/custom-stu-hike/studyTask";

    private ZhsClient() {
    }

    private static Context app;

    public static void init(Context c) {
        app = c.getApplicationContext();
    }

    public static boolean loggedIn() {
        String cookie = CookieStore.cookie(HOST);
        if (cookie != null && !cookie.isEmpty()) return true;
        return app != null && !AuthStore.token(app, Sources.ZHS).isEmpty();
    }

    public static String get(String path) {
        return request("GET", path, null);
    }

    public static String post(String path, String body) {
        return request("POST", path, body);
    }

    private static String request(String method, String path, String payload) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(HOST + path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestMethod(method);
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            conn.setRequestProperty("Referer", REFERER);
            conn.setRequestProperty("Origin", "https://hike-teaching-center.polymas.com");
            conn.setRequestProperty("Accept-Language", "zh-CN");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) "
                            + "Chrome/120.0.0.0 Mobile Safari/537.36");
            String cookie = CookieStore.cookie(HOST);
            if (cookie != null && !cookie.isEmpty()) conn.setRequestProperty("Cookie", cookie);
            String auth = app == null ? "" : AuthStore.token(app, Sources.ZHS);
            if (!auth.isEmpty()) conn.setRequestProperty("Authorization", auth);
            if (payload != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Length", String.valueOf(payload.getBytes("UTF-8").length));
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(payload.getBytes("UTF-8"));
                }
            }
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in == null) return null;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            }
            String body = sb.toString();
            if (app != null) {
                String preview = body.length() > 400 ? body.substring(0, 400) : body;
                YktSync.write(app, "zhs_http.log", method + " " + path + "\ncode=" + code
                        + " cookie=" + (cookie == null ? 0 : cookie.length())
                        + " auth=" + (auth.isEmpty() ? "no" : "yes")
                        + "\n" + preview + "\n\n");
            }
            return body;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static String courseList() {
        // 这个接口只认 POST，GET 会返回 {"code":500,"msg":"操作失败，请稍后重试"}
        return post("/student-course/student/index/queryStudentCourse", "{}");
    }

    public static String todoList(int page, int size) {
        return get("/student-course/stu/todo/findNoFinishTaskTotal?keyword=&type=&pageNum="
                + page + "&pageSize=" + size);
    }
}