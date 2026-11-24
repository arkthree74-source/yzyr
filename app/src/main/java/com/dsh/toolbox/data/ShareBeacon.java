package com.dsh.toolbox.data;

import android.content.Context;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * 电脑版会定期在局域网广播「YZYR-FIND」，手机收到就回一句「我在 ip:8765」，
 * 这样电脑自动知道手机地址，用户不用手抄 IP。
 */
public final class ShareBeacon {

    public static final int PORT = 8766;
    private static final String TAG = "ShareBeacon";
    private static volatile boolean running;
    private static DatagramSocket socket;
    private static Thread thread;

    private ShareBeacon() {
    }

    public static synchronized void start(Context ctx) {
        if (running) return;
        running = true;
        final Context app = ctx.getApplicationContext();
        thread = new Thread(() -> {
            try {
                socket = new DatagramSocket(PORT);
                socket.setBroadcast(true);
                socket.setSoTimeout(2000);
                byte[] buf = new byte[128];
                while (running) {
                    try {
                        DatagramPacket p = new DatagramPacket(buf, buf.length);
                        socket.receive(p);
                        String msg = new String(p.getData(), 0, p.getLength(), "UTF-8");
                        if (!msg.startsWith("YZYR-FIND")) continue;
                        String ip = ShareServer.ip();
                        if (ip.length() == 0) ip = p.getAddress().getHostAddress();
                        byte[] out = ("YZYR|" + ShareServer.PORT + "|" + ip).getBytes("UTF-8");
                        socket.send(new DatagramPacket(out, out.length, p.getAddress(), p.getPort()));
                    } catch (java.net.SocketTimeoutException te) {
                        // 继续等
                    } catch (Exception e) {
                        if (!running) return;
                        Log.w(TAG, "recv", e);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "bind", e);
            } finally {
                stop();
            }
        }, "share-beacon");
        thread.setDaemon(true);
        thread.start();
    }

    public static synchronized void stop() {
        running = false;
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {
        }
        socket = null;
        thread = null;
    }

    public static boolean isRunning() {
        return running;
    }
}