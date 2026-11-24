package com.dsh.toolbox.data;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 课堂派（ketangpai.com）客户端。
 *
 * <p>它家的网页是 Vue 单页应用，真正的接口在 {@code https://openapiv5.ketangpai.com/}，
 * 一律 POST + JSON，登录态靠 Cookie（{@code docs.ketangpai.com} / {@code openapiv5} 域名下）。
 * 目前只做了「用现有登录态试几个候选接口并把响应存下来」的探测，等确认了作业列表的返回结构
 * 再写正式的 {@link KtpSync}。</p>
 */
public final class KtpClient {

    public static final String HOST = "https://openapiv5.ketangpai.com";
    public static final String WEB = "https://www.ketangpai.com";
    public static final String REFERER = WEB + "/";
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/122.0.0.0 Safari/537.36";

    private static Context app;

    private KtpClient() {
    }

    public static void init(Context ctx) {
        if (ctx != null) app = ctx.getApplicationContext();
    }

    public static boolean loggedIn() {
        if (app == null) return false;
        String ck = CookieStore.cookie(HOST);
        if (ck == null || ck.isEmpty()) ck = CookieStore.cookie(WEB);
        return ck != null && !ck.isEmpty();
    }

    /** POST 一个接口，返回响应体（失败返回 null）。 */
    public static String post(String path, String jsonBody) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(HOST + path).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(15000);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            c.setRequestProperty("Accept", "application/json, text/plain, */*");
            c.setRequestProperty("Origin", WEB);
            c.setRequestProperty("Referer", REFERER);
            String ck = CookieStore.cookie(HOST);
            if (ck == null || ck.isEmpty()) ck = CookieStore.cookie(WEB);
            if (ck != null && !ck.isEmpty()) c.setRequestProperty("Cookie", ck);
            String token = AuthStore.token(app, Sources.KTP);
            if (token != null && !token.isEmpty()) {
                // 还没摸清它到底用哪个头，先两个常见的都带上
                c.setRequestProperty("Authorization", token);
                c.setRequestProperty("token", token);
            }
            byte[] body = (jsonBody == null ? "{}" : jsonBody).getBytes("UTF-8");
            c.setFixedLengthStreamingMode(body.length);
            try (OutputStream os = c.getOutputStream()) {
                os.write(body);
            }
            int code = c.getResponseCode();
            InputStream in = code / 100 == 2 ? c.getInputStream() : c.getErrorStream();
            String text = in == null ? "" : read(in);
            if (app != null) {
                YktSync.write(app, "ktp_http.log", "POST " + path + " -> " + code + "\n"
                        + (text.length() > 4000 ? text.substring(0, 4000) : text) + "\n");
            }
            return text;
        } catch (Throwable t) {
            if (app != null) YktSync.write(app, "ktp_http.log", "POST " + path + " FAIL " + t + "\n");
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /**
     * 探测：挨个试候选接口，把响应按名字存到 files/ 下，方便离线看结构。
     */
    public static String probe(Context ctx) {
        init(ctx);
        String[][] candidates = {
                {"/CourseApi/semesterCourseList", "{}"},
                {"/UserApi/getUserInfo", "{}"},
                {"/HomeworkApi/getHandupHomework", "{}"},
                {"/CourseApi/getCourseStateAll", "{}"},
        };
        StringBuilder log = new StringBuilder("ktp probe, loggedIn=" + loggedIn() + "\n");
        for (String[] cand : candidates) {
            String body = post(cand[0], cand[1]);
            String name = "ktp_" + cand[0].replace('/', '_').replace(' ', '_') + ".json";
            if (body != null) YktSync.write(ctx, name, body);
            log.append(cand[0]).append(" -> ").append(body == null ? "null" : body.length() + " chars")
                    .append('\n');
        }
        YktSync.write(ctx, "ktp_probe.txt", log.toString());
        return log.toString();
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return new String(bos.toByteArray(), "UTF-8");
    }
}