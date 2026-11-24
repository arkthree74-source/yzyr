package com.dsh.toolbox.data;

import android.webkit.CookieManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Minimal authenticated HTTP client for yuketang.cn. Reuses the cookies that the
 * WebView obtained during login, so no credentials ever leave the device.
 */
public final class YktClient {

    public static final String ORIGIN = "https://www.yuketang.cn";

    private static final String UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";

    /** Signed-in student's university id; the todo_list endpoint wants it as a header. */
    static volatile String universityId = "2901";

    private YktClient() {
    }

    public static String cookie() {
        try {
            // 后台进程不碰 CookieManager（会卡死），统一走存档；存档由 WebActivity 刷新。
            return CookieStore.cookie(ORIGIN);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean loggedIn() {
        String c = cookie();
        return c != null && c.toLowerCase().contains("sessionid");
    }

    /** GET a path such as "/v/course_meta/learning_list/?front_time=1". */
    public static String get(String path) throws IOException {
        return get(path, null, null);
    }

    /**
     * @param classroomId when set, the classroom-scoped headers the web app sends are added
     */
    public static String get(String path, Long classroomId, Long skuId) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(ORIGIN + path).openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(25000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            conn.setRequestProperty("Referer", "https://www.yuketang.cn/m/v2");
            // The platform only checks that this marker is present and equals "ykt".
            conn.setRequestProperty("xtbz", "ykt");
            conn.setRequestProperty("X-Client", "web");
            conn.setRequestProperty("Xt-Agent", "web");
            conn.setRequestProperty("uv-id", universityId);
            conn.setRequestProperty("university-id", universityId);
            if (classroomId != null && classroomId > 0) {
                conn.setRequestProperty("classroom-id", String.valueOf(classroomId));
            }
            String c = cookie();
            if (c != null && !c.isEmpty()) conn.setRequestProperty("Cookie", c);
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in == null) return null;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                char[] buf = new char[8192];
                int n;
                while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    public static String learningList() throws IOException {
        return get("/v/course_meta/learning_list/?front_time=" + System.currentTimeMillis());
    }

    /** Course detail meta; the response carries the classroom sku id we need. */
    public static String classroomLogs(long courseId, long classroomId) throws IOException {
        return get("/v/course_meta/classroom_logs?course_id=" + courseId
                + "&classroom_id=" + classroomId
                + "&activity_type=-1&front_time=" + System.currentTimeMillis());
    }

    /** Everything still open in one classroom, with deadlines and type ids. */
    public static String todoList(long classroomId, long skuId) throws IOException {
        return get("/c27/online_courseware/course/classroom/" + classroomId + "/" + skuId
                        + "/todo_list/?client_type=h5&front_time=" + System.currentTimeMillis(),
                classroomId, skuId);
    }
}