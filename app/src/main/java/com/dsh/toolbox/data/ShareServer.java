package com.dsh.toolbox.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 「共享给电脑」：在局域网开一个小 HTTP 服务，电脑版拉 /data 就能拿到同一份作业和课表，
 * 不用在电脑上再登录一次。
 */
public final class ShareServer {

    public static final int PORT = 8765;
    private static final String TAG = "ShareServer";
    private static ServerSocket server;
    private static Thread thread;
    private static volatile boolean running;

    private ShareServer() {
    }

    public static boolean isRunning() {
        return running;
    }

    public static synchronized String start(Context ctx) {
        if (running) return address(ctx);
        try {
            server = new ServerSocket(PORT);
            running = true;
            final Context app = ctx.getApplicationContext();
            thread = new Thread(() -> loop(app), "share-server");
            thread.setDaemon(true);
            thread.start();
            setEnabled(ctx, true);
            ShareBeacon.start(ctx);
            return address(ctx);
        } catch (Exception e) {
            running = false;
            return "";
        }
    }

    public static synchronized void stop(Context ctx) {
        running = false;
        try {
            if (server != null) server.close();
        } catch (Exception ignored) {
        }
        server = null;
        thread = null;
        ShareBeacon.stop();
        setEnabled(ctx, false);
    }

    public static void setEnabled(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean("share_on", on).apply();
    }

    public static boolean enabled(Context ctx) {
        return prefs(ctx).getBoolean("share_on", false);
    }

    /** 手机在局域网里的 IPv4 地址，优先 wlan。 */
    public static String ip() {
        try {
            List<NetworkInterface> list = Collections.list(NetworkInterface.getNetworkInterfaces());
            List<String> wlan = new ArrayList<>();
            List<String> other = new ArrayList<>();
            for (NetworkInterface ni : list) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                String name = ni.getName() == null ? "" : ni.getName();
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!(a instanceof Inet4Address)) continue;
                    if (a.isLoopbackAddress()) continue;
                    String ip = a.getHostAddress();
                    if (name.startsWith("wlan") || name.startsWith("ap")) wlan.add(ip);
                    else if (name.startsWith("rmnet")) continue;
                    else other.add(ip);
                }
            }
            if (!wlan.isEmpty()) return wlan.get(0);
            if (!other.isEmpty()) return other.get(0);
        } catch (Exception e) {
            Log.w(TAG, "ip fail", e);
        }
        return "";
    }

    public static String address(Context ctx) {
        String ip = ip();
        return ip.length() == 0 ? "" : "http://" + ip + ":" + PORT;
    }

    private static void loop(Context ctx) {
        while (running) {
            try {
                Socket s = server.accept();
                handle(ctx, s);
            } catch (Exception e) {
                if (!running) return;
                Log.w(TAG, "accept", e);
            }
        }
    }

    private static void handle(Context ctx, Socket s) {
        try {
            s.setSoTimeout(5000);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
            String line = in.readLine();
            String path = "";
            if (line != null) {
                String[] parts = line.split(" ");
                if (parts.length > 1) path = parts[1];
            }
            // 读掉请求头
            String h;
            while ((h = in.readLine()) != null && h.length() > 0) {
                // ignore
            }
            String body;
            String type = "application/json; charset=utf-8";
            if (path.startsWith("/ping")) {
                body = "{\"app\":\"yzyr\",\"ok\":true}";
            } else if (path.startsWith("/data")) {
                body = payload(ctx).toString();
            } else if (path.startsWith("/sync")) {
                final Context app = ctx;
                new Thread(() -> {
                    try {
                        SyncAll.run(app);
                    } catch (Throwable ignored) {
                    }
                }, "web-sync").start();
                body = "{\"ok\":true,\"msg\":\"sync started\"}";
            } else if (path.equals("/") || path.startsWith("/?") || path.startsWith("/index")) {
                type = "text/html; charset=utf-8";
                body = page(ctx);
            } else {
                body = "{\"ok\":false,\"msg\":\"unknown\"}";
            }
            byte[] bytes = body.getBytes("UTF-8");
            OutputStream out = s.getOutputStream();
            String head = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: " + type + "\r\n"
                    + "Content-Length: " + bytes.length + "\r\n"
                    + "Access-Control-Allow-Origin: *\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(head.getBytes("UTF-8"));
            out.write(bytes);
            out.flush();
        } catch (Exception e) {
            Log.w(TAG, "handle", e);
        } finally {
            try {
                s.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static JSONObject payload(Context ctx) {
        JSONObject root = new JSONObject();
        try {
            Store store = Store.get(ctx);
            JSONArray hw = new JSONArray();
            List<Homework> all = store.all();
            for (Homework h : all) hw.put(h.toJson());
            root.put("homework", hw);
            root.put("lastYkt", store.lastSync(Sources.YKT));
            root.put("lastZhs", store.lastSync(Sources.ZHS));
            root.put("lastCx", store.lastSync(Sources.CX));

            JSONArray tt = new JSONArray();
            List<Timetable> list = TimetableStore.load(ctx);
            for (Timetable t : list) tt.put(t.toJson());
            root.put("timetable", tt);
            root.put("term", TimetableStore.term(ctx));
            root.put("timetableAt", TimetableStore.updatedAt(ctx));
            JSONObject times = new JSONObject();
            for (Map.Entry<Integer, String> e : TimetableStore.sectionTimes(ctx).entrySet()) {
                times.put(String.valueOf(e.getKey()), e.getValue());
            }
            root.put("sectionTimes", times);
            long week1 = TimetableStore.start(ctx);
            root.put("week1", week1);
            root.put("nowWeek", TimetableStore.weekOf(System.currentTimeMillis(), week1));
            root.put("app", "yzyr");
            root.put("version", 1);
        } catch (Exception e) {
            Log.w(TAG, "payload", e);
        }
        return root;
    }

    /** 读 assets/web.html，就是浏览器打开的那个页面。 */
    private static String page(Context ctx) {
        try {
            java.io.InputStream in = ctx.getAssets().open("web.html");
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            return new String(out.toByteArray(), "UTF-8");
        } catch (Exception e) {
            return "<h1>页面没找到</h1>";
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("hw", Context.MODE_PRIVATE);
    }
}