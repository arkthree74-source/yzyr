package com.dsh.toolbox.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

/**
 * Small store for the bearer tokens some platforms put in their request headers.
 *
 * <p>Captured from the WebView (which the student already logged into) and reused by the
 * background sync, so passwords never touch this app.
 */
public final class AuthStore {

    private AuthStore() {
    }

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("auth", Context.MODE_PRIVATE);
    }

    public static String token(Context c, String source) {
        return p(c).getString("token_" + source, "");
    }

    public static void setToken(Context c, String source, String token) {
        if (token == null || token.isEmpty()) return;
        if (token.equals(token(c, source))) return;
        p(c).edit().putString("token_" + source, token).apply();
    }

    /** Pull an Authorization value out of a captured request-header map. */
    public static String pickAuthorization(Object headers) {
        if (!(headers instanceof Map)) return "";
        for (Object e : ((Map<?, ?>) headers).entrySet()) {
            Map.Entry<?, ?> kv = (Map.Entry<?, ?>) e;
            if (kv.getKey() == null || kv.getValue() == null) continue;
            String k = String.valueOf(kv.getKey());
            if (k.equalsIgnoreCase("authorization")) return String.valueOf(kv.getValue());
        }
        return "";
    }
}