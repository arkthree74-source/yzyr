package com.dsh.toolbox.data;

import android.content.Context;
import android.webkit.CookieManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Diagnostic helper: replay chaoxing endpoints with the WebView's cookies and dump the bodies,
 * so the AI/planner panel endpoints can be modelled into a sync client.
 *
 * <p>A candidate may carry a grep pattern after {@code ##}; when present the response is scanned
 * for matches instead of being dumped (handy for minified JS bundles).</p>
 */
public final class CxProbe {

    private CxProbe() {
    }

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/122.0.0.0 Safari/537.36";

    private static final String CLAZZ =
            "158884969_494838755,158195869_494838755,153191459_494838755,147550915_494838755,"
                    + "144209623_494838755,134071328_494838755,134067591_494838755";

    private static final String[] CANDIDATES = {
            "GET https://stat2-ans.chaoxing.com/stat2/learning/plan/recommended-course-list",
            "GET https://stat2-ans.chaoxing.com/stat2-vue/studyPlanAssistant ## /stat2/[a-zA-Z0-9_/\\-]{3,60}",
            "GET https://stat2-ans.chaoxing.com/stat2-vue/js/8864.bd7016b0.js ## /stat2/[a-zA-Z0-9_/\\-]{3,60}",
            "GET https://stat2-ans.chaoxing.com/stat2-vue/js/app.8ac3a5a0.js ## /stat2/[a-zA-Z0-9_/\\-]{3,60}",
            "GET https://mooc2-ans.chaoxing.com/mooc2-ans/mycourse/stu-job-info?clazzPersonStr=" + CLAZZ,
    };

    public static String run(Context ctx) {
        List<String> out = new ArrayList<>();
        for (String line : CANDIDATES) {
            String method = line.startsWith("POST") ? "POST" : "GET";
            String spec = line.substring(method.length() + 1);
            String body = null;
            String grep = null;
            int g = spec.indexOf(" ## ");
            if (g > 0) {
                grep = spec.substring(g + 4).trim();
                spec = spec.substring(0, g).trim();
            }
            int sp = spec.indexOf(' ');
            if (sp > 0) {
                body = spec.substring(sp + 1);
                spec = spec.substring(0, sp);
            }
            String host;
            try {
                host = new URL(spec).getHost();
            } catch (Exception e) {
                out.add(spec + " -> bad url");
                continue;
            }
            String cookie = CookieManager.getInstance().getCookie("https://" + host);
            out.add("### " + method + " " + spec + " cookie=" + (cookie == null ? 0 : cookie.length())
                    + (grep == null ? "" : " grep=" + grep));
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(spec).openConnection();
                c.setRequestMethod(method);
                c.setConnectTimeout(12000);
                c.setReadTimeout(30000);
                c.setInstanceFollowRedirects(false);
                c.setRequestProperty("User-Agent", UA);
                c.setRequestProperty("Accept", "*/*");
                c.setRequestProperty("Referer",
                        "https://stat2-ans.chaoxing.com/stat2-vue/studyPlanAssistant");
                if (cookie != null && !cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
                if (body != null) {
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type",
                            "application/x-www-form-urlencoded; charset=UTF-8");
                    c.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
                }
                int code = c.getResponseCode();
                String location = c.getHeaderField("Location");
                String text = read(c, code >= 400 ? c.getErrorStream() : c.getInputStream());
                out.add("code=" + code + (location == null ? "" : " location=" + location));
                if (grep == null) {
                    out.add("body(" + text.length() + ")=" + text);
                } else {
                    Set<String> hits = new LinkedHashSet<>();
                    Matcher m = Pattern.compile(grep).matcher(text);
                    while (m.find() && hits.size() < 80) hits.add(m.group());
                    out.add("hits(" + hits.size() + ")=" + String.join("\n  ", hits));
                }
            } catch (Exception e) {
                out.add("FAILED " + e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                if (c != null) c.disconnect();
            }
            out.add("");
        }
        return String.join("\n", out);
    }

    private static String read(HttpURLConnection c, InputStream in) throws Exception {
        if (in == null) return "";
        try (InputStream i = in; ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            int total = 0;
            while ((n = i.read(buf)) > 0) {
                bos.write(buf, 0, n);
                total += n;
                if (total > 3_000_000) break;
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}