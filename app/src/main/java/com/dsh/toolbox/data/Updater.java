package com.dsh.toolbox.data;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * 应用内更新：读一个 JSON 清单，比对版本号，下载新 APK 并交给系统安装器。
 *
 * <p>清单格式（放在任何能公网访问的地方即可）：</p>
 * <pre>
 * {
 *   "versionCode": 13,
 *   "versionName": "2.1",
 *   "url": "https://你的域名/油专人拯救计划-v2.1.apk",
 *   "size": 5181710,
 *   "sha256": "……",
 *   "notes": "这一版改了什么"
 * }
 * </pre>
 *
 * <p>下载完会做两道校验：sha256 对得上、APK 的签名证书和当前安装的一致。
 * 任何一条不满足都不让装——防止有人把下载地址换成自己的包。</p>
 */
public final class Updater {

    /** 更新清单地址。换成你自己的之后，老用户就能自动发现新版本。 */
    public static final String DEFAULT_MANIFEST = "https://cdn.jsdelivr.net/gh/arkthree74-source/yzyr@main/update.json";

    public interface Progress {
        void onProgress(long done, long total);
    }

    public static class Info {
        public int code;
        public String name = "";
        public String url = "";
        public String notes = "";
        public String sha256 = "";
        public long size;
    }

    private Updater() {
    }

    public static String manifest(Context ctx) {
        String custom = Store.get(ctx).prefs().getString("update_url", "");
        return custom == null || custom.isEmpty() ? DEFAULT_MANIFEST : custom;
    }

    public static void setManifest(Context ctx, String url) {
        Store.get(ctx).prefs().edit().putString("update_url", url == null ? "" : url).apply();
    }

    /** 拉清单；没有新版本或拉不到时返回 null。 */
    public static Info check(Context ctx, String manifestUrl) {
        if (manifestUrl == null || manifestUrl.isEmpty()) return null;
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(manifestUrl).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "Android");
            c.setRequestProperty("Cache-Control", "no-cache");
            if (c.getResponseCode() / 100 != 2) return null;
            String body = read(c.getInputStream());
            JSONObject o = new JSONObject(body);
            Info i = new Info();
            i.code = o.optInt("versionCode", 0);
            i.name = o.optString("versionName", "");
            i.url = o.optString("url", "");
            i.notes = o.optString("notes", "");
            i.sha256 = o.optString("sha256", "");
            i.size = o.optLong("size", 0);
            if (i.code <= 0 || i.url.isEmpty()) return null;
            int mine = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionCode;
            return i.code > mine ? i : null;
        } catch (Throwable t) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    public static File target(Context ctx) {
        File dir = new File(ctx.getCacheDir(), "update");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "new.apk");
    }

    /** 下载到 target()；失败抛异常。 */
    public static File download(Context ctx, Info info, Progress p) throws Exception {
        File out = target(ctx);
        if (out.exists() && !out.delete()) throw new Exception("旧文件删不掉");
        HttpURLConnection c = (HttpURLConnection) new URL(info.url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "Android");
        c.connect();
        if (c.getResponseCode() / 100 != 2) throw new Exception("下载失败 HTTP " + c.getResponseCode());
        long total = info.size > 0 ? info.size : c.getContentLengthLong();
        try (InputStream in = c.getInputStream(); FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[64 * 1024];
            long done = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                fos.write(buf, 0, n);
                done += n;
                if (p != null) p.onProgress(done, total);
            }
            fos.flush();
        } finally {
            c.disconnect();
        }
        return out;
    }

    /** 校验：包名相同 + 签名证书相同 + （给了 sha256 时）哈希一致。 */
    public static String verify(Context ctx, File apk, Info info) {
        try {
            if (!apk.exists() || apk.length() == 0) return "下载的文件是空的";
            if (!info.sha256.isEmpty()) {
                String got = sha256(apk);
                if (!got.equalsIgnoreCase(info.sha256.trim())) return "文件校验不通过（可能没下完整）";
            }
            PackageManager pm = ctx.getPackageManager();
            PackageInfo np = pm.getPackageArchiveInfo(apk.getAbsolutePath(),
                    PackageManager.GET_SIGNING_CERTIFICATES);
            if (np == null) return "这个文件不是安装包";
            if (!ctx.getPackageName().equals(np.packageName)) return "安装包不是本应用";
            String mine = ownCert(ctx);
            String theirs = certOf(np);
            if (mine == null || theirs == null || !mine.equals(theirs)) return "签名对不上，已拒绝安装";
            return null;
        } catch (Throwable t) {
            return "校验出错：" + t;
        }
    }

    public static boolean canInstall(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                return ctx.getPackageManager().canRequestPackageInstalls();
            } catch (Throwable ignored) {
            }
        }
        return true;
    }

    public static void openInstallPermission(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                i.setData(Uri.parse("package:" + ctx.getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
                return;
            } catch (Throwable ignored) {
            }
        }
        try {
            Intent i = new Intent(Settings.ACTION_SECURITY_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Throwable ignored) {
        }
    }

    /** 把 APK 交给系统安装器。 */
    public static void install(Activity act, File apk) {
        Uri uri = FileProvider.getUriForFile(act, act.getPackageName() + ".fileprovider", apk);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        act.startActivity(i);
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new java.io.FileInputStream(f)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        return hex(md.digest());
    }

    private static String ownCert(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(),
                    PackageManager.GET_SIGNING_CERTIFICATES);
            return certOf(pi);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String certOf(PackageInfo pi) {
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                if (pi.signingInfo == null) return null;
                Signature[] arr = pi.signingInfo.getApkContentsSigners();
                if (arr == null || arr.length == 0) return null;
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                return hex(md.digest(arr[0].toByteArray()));
            }
            Signature[] arr = pi.signatures;
            if (arr == null || arr.length == 0) return null;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return hex(md.digest(arr[0].toByteArray()));
        } catch (Throwable t) {
            return null;
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static String read(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return new String(bos.toByteArray(), "UTF-8");
    }
}