package com.dsh.toolbox.ui;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.dsh.toolbox.data.Store;
import com.dsh.toolbox.data.Updater;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.Locale;

/**
 * 应用内更新的交互流程：检查 → 提示 → 下载 → 校验 → 拉起系统安装器。
 *
 * <p>主页面与设置页共用。清单地址来自 {@link Updater#manifest(android.content.Context)}，
 * 空表示还没配置（此时只有手动点检查才会提示，静默检查直接跳过）。</p>
 */
public final class UpdateFlow {

    /** 打开 App 时最多每隔这么久联网查一次（省流量、也避免每次开屏都弹）。 */
    private static final long LAUNCH_CHECK_INTERVAL = 6L * 3600L * 1000L;
    /** 用户点了「以后再说」之后，同一个版本这么多小时内不再打扰。 */
    private static final long SKIP_QUIET = 24L * 3600L * 1000L;

    private UpdateFlow() {
    }

    /**
     * 每次打开 App 时调用：隔 6 小时以上才真正联网查，发现更大版本就弹「发现新版本」。
     * 同一个版本被「以后再说」过，24 小时内不再弹。
     */
    public static void checkOnLaunch(final Activity act) {
        if (act.isFinishing() || act.isDestroyed()) return;
        final String url = Updater.manifest(act);
        if (url.isEmpty()) return;
        final SharedPreferences p = Store.get(act).prefs();
        final long now = System.currentTimeMillis();
        if (now - p.getLong("update_check_at", 0) < LAUNCH_CHECK_INTERVAL) return;
        p.edit().putLong("update_check_at", now).apply();
        new Thread(() -> {
            final Updater.Info info = Updater.check(act, url);
            act.runOnUiThread(() -> {
                if (act.isFinishing() || act.isDestroyed() || info == null) return;
                int skipCode = p.getInt("update_skip_code", 0);
                long skipAt = p.getLong("update_skip_at", 0);
                if (info.code <= skipCode && System.currentTimeMillis() - skipAt < SKIP_QUIET) return;
                show(act, info);
            });
        }).start();
    }

    public static void check(final Activity act, final boolean silent) {
        if (act.isFinishing()) return;
        final String url = Updater.manifest(act);
        if (url.isEmpty()) {
            if (!silent) {
                new MaterialAlertDialogBuilder(act)
                        .setTitle("还没配置更新地址")
                        .setMessage("把 update.json 和新版 APK 放到任意能公网访问的地方，"
                                + "再把 Updater.DEFAULT_MANIFEST 改成那个地址——之后老用户点这里就能收到更新。")
                        .setPositiveButton("知道了", null)
                        .show();
            }
            return;
        }
        final AlertDialog[] ph = new AlertDialog[1];
        if (!silent) {
            ph[0] = new MaterialAlertDialogBuilder(act)
                    .setTitle("正在检查更新…")
                    .setCancelable(false)
                    .create();
            ph[0].show();
        }
        new Thread(() -> {
            final Updater.Info info = Updater.check(act, url);
            act.runOnUiThread(() -> {
                if (ph[0] != null && ph[0].isShowing()) ph[0].dismiss();
                if (act.isFinishing()) return;
                if (info == null) {
                    if (!silent) Toast.makeText(act, "已经是最新版本", Toast.LENGTH_SHORT).show();
                } else {
                    show(act, info);
                }
            });
        }).start();
    }

    public static void show(final Activity act, final Updater.Info info) {
        StringBuilder msg = new StringBuilder();
        String notes = info.notes == null || info.notes.isEmpty()
                ? Changelog.latestNotes() : info.notes;
        msg.append(notes == null || notes.isEmpty() ? "有新版本可以更新。" : notes);
        if (info.size > 0) {
            msg.append("\n\n大小约 ")
                    .append(String.format(Locale.getDefault(), "%.1f", info.size / 1048576.0))
                    .append(" MB");
        }
        msg.append("\n\n更新不会动你的作业清单和课表，装完直接接着用。");
        new MaterialAlertDialogBuilder(act)
                .setTitle("发现新版本 " + info.name)
                .setMessage(msg.toString())
                .setPositiveButton("立即更新", (d, w) -> {
                    Store.get(act).prefs().edit()
                            .remove("update_skip_code").remove("update_skip_at").apply();
                    download(act, info);
                })
                .setNegativeButton("以后再说", (d, w) -> Store.get(act).prefs().edit()
                        .putInt("update_skip_code", info.code)
                        .putLong("update_skip_at", System.currentTimeMillis()).apply())
                .show();
    }

    public static void download(final Activity act, final Updater.Info info) {
        if (!Updater.canInstall(act)) {
            new MaterialAlertDialogBuilder(act)
                    .setTitle("需要先允许安装")
                    .setMessage("系统要你允许本应用「安装未知应用」，才能装上更新包。")
                    .setPositiveButton("去允许", (d, w) -> Updater.openInstallPermission(act))
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        final ProgressDialog pd = new ProgressDialog(act);
        pd.setTitle("正在下载 " + info.name);
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setMax(100);
        pd.setCancelable(false);
        pd.show();
        new Thread(() -> {
            try {
                final File apk = Updater.download(act, info, (done, total) -> {
                    if (total > 0) pd.setProgress((int) (done * 100 / total));
                });
                final String err = Updater.verify(act, apk, info);
                act.runOnUiThread(() -> {
                    if (pd.isShowing()) pd.dismiss();
                    if (act.isFinishing()) return;
                    if (err != null) {
                        Toast.makeText(act, err, Toast.LENGTH_LONG).show();
                    } else {
                        Updater.install(act, apk);
                    }
                });
            } catch (Throwable t) {
                act.runOnUiThread(() -> {
                    if (pd.isShowing()) pd.dismiss();
                    Toast.makeText(act, "下载失败：" + t, Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
}