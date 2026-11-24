# 油专人拯救计划

把 **雨课堂 / 智慧树（知到）/ 超星学习通 / 学校教务系统** 的作业和课表，收进一个安卓 App。

打开就是一句话：**还有哪些作业没做、哪几份快到期、哪些已经逾期**；底部第二栏是**按周显示的课表**，从教务系统一键导入。

> 个人自用的小工具，不是任何平台的官方客户端。只在本机保存登录状态，不收集、不上传任何作业或账号数据。

---

## 功能

| 模块 | 说明 |
| --- | --- |
| 作业汇总 | 三个平台的「未完成」合并成一张清单，按 今天截止 / 明天截止 / 七天内 / 以后 / 没有截止时间 / 已逾期 / 已完成 分组 |
| 筛选 | 第一行按平台过滤（雨课堂 / 智慧树 / 学习通），第二行按课程与截止时间过滤（全部 / 今天 / 7 天 / 逾期） |
| 自动同步 | 后台按设定频率（默认 6 小时）自动抓一次，可选 1 小时 ~ 每周，也可关掉 |
| 同步记录 | 每次同步的时间、总条数、各平台条数都留档，能直接看出后台到底有没有跑 |
| 后台保活 | 电池优化 / 精确闹钟 / 通知 / 自启动逐项开关，按机型（vivo、iQOO、华为、荣耀、小米、OPPO、一加、realme、三星、魅族）跳对应系统页面；可选常驻通知 |
| 课表 | 从教务系统导入学期理论课表，按周查看（周日为一周第一天），`‹ ›` 切周次，点格子看教室教师 |
| 应用内更新 | 联网检查新版本，在 App 内下载 → 校验 sha256 与签名 → 拉起系统安装器 |
| 手动补充 | 老师口头布置的作业可以手动加一条；勾选完成只存在本机 |

### 三个平台各自怎么拿数据

| 平台 | 凭证 | 数据来源 |
| --- | --- | --- |
| 雨课堂 | `www.yuketang.cn` 的 Cookie | 课程页「未完成」背后的 `todo_list` 接口（请求头 `xtbz: ykt`） |
| 智慧树 | 业务页请求头里的 `Authorization` JWT + Cookie | `cloudapi.polymas.com` 的「待完成」接口 |
| 学习通 | `stat2-ans.chaoxing.com` 的 Cookie | 「学习规划助手」的临近截止任务接口 |
| 教务系统 | CAS 登录后的 `JSESSIONID` | 强智科技教务系统 `xskb_list.do` 的课表 HTML |

三个平台的作业都只收**有截止时间**的条目；已逾期的会保留并归到「已逾期」分组。雨课堂的课堂弹题 / 随堂活动会被过滤掉。

> 课表解析目前只适配**中国石油大学（华东）**的强智教务系统，换学校需要改 `data/JwParser.java`。

---

## 编译

环境：JDK 17、Android SDK（platform 34 + build-tools 34.0.0）、Gradle 8.7（或直接用 Android Studio 打开，让它生成 wrapper）。

```bash
# 1. 指向本机 SDK
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# 2. 编译
gradle assembleRelease        # 产物：app/build/outputs/apk/release/app-release.apk
```

签名说明：仓库里**不含 keystore**。`app/build.gradle.kts` 会读取 `keystore/signing.properties`（未提交），文件长这样：

```properties
storeFile=../keystore/homework.jks
storePassword=******
keyAlias=homework
keyPassword=******
```

没有这个文件时，`release` 会退回 debug 签名，方便别人 clone 下来直接跑通，但**不能**覆盖安装正式包。

编译期还有两个可选变体：

- `diag`：与 release 同签名、但 `debuggable=true`，方便 `adb shell run-as` 读私有文件调试；
- 它的 `AndroidManifest.xml`（`app/src/diag/`）额外把 `WebActivity` 设成 `exported`，可用 `dshopen://` deep link 直接跳页面。正式包不含这些。

---

## 目录结构

```
app/src/main/java/com/dsh/toolbox/
├── MainActivity.java          作业清单、筛选、同步入口
├── PlatformsActivity.java     平台列表（登录 / 选课入口）
├── CoursesActivity.java       课程多选
├── SettingsActivity.java      设置页（频率 / 保活 / 同步记录 / 更新）
├── WebActivity.java           内嵌官方网页，登录与抓取
├── data/
│   ├── YktClient.java  YktSync.java     雨课堂
│   ├── ZhsClient.java  ZhsSync.java     智慧树
│   ├── CxClient.java   CxSync.java      学习通
│   ├── JwClient.java   JwParser.java    教务系统课表
│   ├── CookieStore.java                 登录态存档（后台只读存档，不碰 WebView）
│   ├── SyncAll.java  SyncWorker.java  SyncAlarm.java  SyncService.java
│   ├── SyncSettings.java  SyncHistory.java  SyncScheduler.java
│   ├── Updater.java                     应用内更新
│   └── Store.java  Homework.java  CourseStore.java  Timetable*.java
└── ui/                        列表适配器、课表格子、引导蒙层、对话框
```

---

## 应用内更新是怎么工作的

仓库根目录的 `update.json` 就是更新清单：

```json
{
  "versionCode": 15,
  "versionName": "2.3",
  "url": "https://cdn.jsdelivr.net/gh/arkthree74-source/yzyr@main/releases/yzyr-v2.3.apk",
  "size": 5201266,
  "sha256": "……",
  "notes": "这次改了什么"
}
```

App 里的 `Updater.DEFAULT_MANIFEST` 指向这个清单（走 jsDelivr CDN，国内可直连）：`https://cdn.jsdelivr.net/gh/arkthree74-source/yzyr@main/update.json`。检查到 `versionCode` 比本机大就提示下载；下载后校验 **sha256** 与**签名证书**，两者都对才拉起系统安装器，所以别人即使改了 APK 也装不上。

发新版只要三步：改 `versionCode`/`versionName` → 编译 → 把新 APK 放进 `releases/`、更新 `update.json`。

---

## 声明

- 仅用于个人查看自己的作业与课表，抓的都是本人账号本来就能看到的数据；
- 不保存账号密码，登录态来自系统 WebView 的 Cookie（本机）；
- 平台接口随时可能变，接口一变这里就得跟着改；
- 与雨课堂、智慧树、超星、学校教务系统均无关联。