package com.dsh.toolbox.ui;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 每个版本改了什么。写死在代码里，离线也能看。
 * 新版本发布时，在最前面加一行。
 */
public final class Changelog {

    /** {版本号, 日期, 改动1, 改动2, ...} */
    private static final String[][] LOG = {
            {"3.1", "2026-11-24",
                    "新增网页版：开着「共享给电脑」时，手机或电脑浏览器打开 http://手机IP:8765 就能看作业和课表，可以「添加到主屏幕」当 App 用",
                    "网页版支持按平台/课程/时间筛选，课表按周看，还能点「同步」让手机立刻刷新一次"},
            {"3.0", "2026-11-24",
                    "「共享给电脑」改成自动发现：电脑版打开就会自己找到开着共享的手机，不用再手抄 IP",
                    "手机开着共享时，重开 App 会自动恢复共享和发现"},
            {"2.9", "2026-11-24",
                    "新增「共享给电脑」：手机在设置里打开它，电脑版点「从手机拉取」填上地址，作业和课表就都过去了",
                    "电脑版不用再登录四个平台，手机上抓一次、电脑直接用",
                    "手机上开着「共享」时，重开 App 会自动恢复，不用每次手动点"},
            {"2.8", "2026-11-24",
                    "智慧树课程列表：改成按页拉全（最多 5 页、按课程 ID 去重），之前可能只显示第一页的课",
                    "学习通任务：除了汇总列表，还把每门课的临期任务合并进来，减少漏抓",
                    "学习通遇到没有截止时间的任务会在日志里单独计数，方便判断是漏抓还是本来就没有",
                    "设置 → 同步诊断里能看到这次抓了多少门课、按哪个来源抓的"},
            {"2.7", "2026-11-24",
                    "修智慧树「有作业却抓不到」：改成按页抓（每页 20 条，最多 200 条），不再依赖一次返回全部",
                    "智慧树课程名容错：全半角括号、多余空格、多一个后缀都能对上",
                    "同步记录会写明原因，比如「智慧树：有 3 门课的任务不在监控范围」",
                    "设置里新增「同步诊断」，抓不到时直接截图就能定位"},
            {"2.6", "2026-11-24",
                    "同步时显示进度条：现在抓哪门课、抓到第几门、已经等了多久",
                    "不想等可以「放到后台」，抓完再告诉你结果",
                    "「检查更新」失败时能分清是「网络不通」还是「已经是最新的」",
                    "新增「课堂派」平台入口（登录后即可抓数据，接口正在接入）"},
            {"2.5", "2026-11-24",
                    "打开 App 时自动查一次有没有新版本，有就直接提醒",
                    "更新源改成两条通道：GitHub 接口取清单，CDN 只当兜底",
                    "点「以后再说」后 24 小时内不再打扰"},
            {"2.4", "2026-11-24",
                    "启动时检查更新（最多 6 小时一次）",
                    "发现新版本先问一句，不再静默"},
            {"2.3", "2026-11-24",
                    "应用内更新正式启用：设置里「检查更新」可下载并安装",
                    "源码、安装包、更新清单都放到 GitHub 仓库"},
            {"2.2", "2026-11-24",
                    "新增「设置」页，原来的菜单项都搬了进去",
                    "自动同步间隔可选：不自动 / 1 小时 ~ 每周一次",
                    "新增「同步记录」，能看到每次同步抓到几条"},
            {"2.1", "2026-11-24",
                    "应用内检查更新、下载并安装",
                    "校验文件大小、SHA-256 和签名，对不上就拒绝安装"},
            {"2.0", "2026-11-24",
                    "多品牌后台保活：自启动、电池优化、精确闹钟、通知逐项引导",
                    "可选常驻通知，降低被系统清掉的概率"},
            {"1.9", "2026-11-24",
                    "定时同步改用系统闹钟 + 前台服务，比原来准时",
                    "主页显示「上次同步 / 下次自动」时间"},
            {"1.8", "2026-11-23",
                    "修复换新手机后智慧树、学习通一直「等待同步」的问题",
                    "检查「选取课程」时会先把平台业务页打开一次，拿到登录凭证"},
            {"1.7", "2026-11-23",
                    "应用改名为「油专人拯救计划」"},
            {"1.6", "2026-11-23",
                    "新增「课表」：从学校教务系统导入，按周查看，周日算一周第一天",
                    "支持点周次标题手动校正第 1 周的周日"},
            {"1.5", "2026-11-23",
                    "新手引导：带着登录、选课、同步真的走一遍",
                    "底部导航分成「作业 / 课表」",
                    "课程筛选支持全选、全不选；雨课堂扫码登录有小贴士"},
            {"1.4", "2026-11-23",
                    "整体界面重做：渐变汇总卡、圆角卡片、彩色来源标签"},
            {"1.3", "2026-11-23",
                    "登录状态持久化，冷启动后也不丢",
                    "每 6 小时自动同步；某个平台失败时不清空已有数据"},
            {"1.2", "2026-11-23",
                    "接入超星学习通（来自「学习规划助手」的临期任务）"},
            {"1.1", "2026-11-23",
                    "已逾期的作业统一收进最后一组，不再区分能否补交"},
            {"1.0", "2026-11-23",
                    "第一个版本：雨课堂、智慧树未完成作业聚合",
                    "按今天 / 七天内 / 已逾期分组"},
    };

    private Changelog() {
    }

    /** 最近一个版本的改动（给更新弹窗当默认说明）。 */
    public static String latestNotes() {
        return notes(0);
    }

    /** 指定版本的改动文本。 */
    public static String notes(int index) {
        if (index < 0 || index >= LOG.length) return "";
        String[] row = LOG[index];
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < row.length; i++) {
            if (sb.length() > 0) sb.append('\n');
            sb.append("· ").append(row[i]);
        }
        return sb.toString();
    }

    public static void show(Activity act) {
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(act.getResources().getDisplayMetrics().density * 18);
        int padH = Math.round(act.getResources().getDisplayMetrics().density * 20);
        root.setPadding(padH, pad / 3, padH, 0);

        for (String[] row : LOG) {
            TextView head = new TextView(act);
            head.setText("v" + row[0] + "   " + row[1]);
            head.setTextSize(13.5f);
            head.setTypeface(Typeface.DEFAULT_BOLD);
            head.setTextColor(0xFF2F6FED);
            head.setPadding(0, pad / 2, 0, pad / 6);
            root.addView(head, new LinearLayout.LayoutParams(-1, -2));

            TextView bodyView = new TextView(act);
            bodyView.setText(notesOf(row));
            bodyView.setTextSize(13f);
            bodyView.setTextColor(0xFF374151);
            bodyView.setLineSpacing(0, 1.25f);
            bodyView.setPadding(0, 0, 0, pad / 3);
            root.addView(bodyView, new LinearLayout.LayoutParams(-1, -2));
        }

        ScrollView sv = new ScrollView(act);
        sv.addView(root, new ViewGroup.LayoutParams(-1, -2));
        sv.setVerticalScrollBarEnabled(true);
        sv.setPadding(0, 0, 0, pad / 2);
        sv.setClipToPadding(false);
        sv.setLayoutParams(new ViewGroup.LayoutParams(-1, -2));

        LinearLayout wrap = new LinearLayout(act);
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        wrap.addView(sv);

        new MaterialAlertDialogBuilder(act)
                .setTitle("更新记录")
                .setView(wrap)
                .setPositiveButton("知道了", null)
                .show();
    }

    private static String notesOf(String[] row) {
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < row.length; i++) {
            if (sb.length() > 0) sb.append('\n');
            sb.append("· ").append(row[i]);
        }
        return sb.toString();
    }
}