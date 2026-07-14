# zimuku.org 验证码循环修复分析报告

## 问题

用户反馈：在“解析下载地址”阶段反复弹出验证码，输入后仍继续弹，形成循环。

后续排查需要借助 ADB logcat，因为这类问题常见于：

- 验证码提交实际上没有成功；
- 验证码通过了，但后续下载请求丢失了关键 Cookie / Referer / 跳转链；
- 下载子域或中转页返回的是新的验证页，而不是实际文件。

## 原因分析

当前下载流程为：

```text
详情页 -> /dld/... -> /download/... -> 真实文件
```

验证码可能出现在 `/download/...` 这类带时效签名的下载 URL 上。旧逻辑在用户提交验证码成功后，会重新执行完整 `DownloadTask`：

```text
重新打开详情页 -> 重新解析 /dld/... -> 重新生成新的 /download/... -> 再次触发验证码
```

因此用户虽然提交了验证码，但后续请求没有继续使用刚才触发验证码的下载 URL，而是从详情页重新开始，导致反复遇到新的验证页。

## 修复方案

在 `app/src/main/java/com/example/subtitledownloader/MainActivity.java` 中：

1. 新增 `pendingDownloadUrl`，记录触发验证码的具体 URL；
2. 下载遇到验证码时，保存：
   - `pendingDownloadItem`
   - `pendingDownloadUrl = captcha.sourceUrl`
3. 验证码提交成功后，不再直接重新跑完整 `DownloadTask`；
4. 新增 `ResumeDownloadTask`，从 `pendingDownloadUrl` 继续解析/下载；
5. 将保存文件、解压 ZIP/RAR 的公共逻辑抽成 `downloadFromUrl()`，供初次下载和继续下载复用。

## 预期效果

验证码提交成功后，App 会从刚才被验证码拦截的下载 URL 继续执行，而不是重新从详情页生成新下载链接，从而避免“解析下载地址 -> 弹验证码 -> 提交 -> 又解析 -> 又弹验证码”的循环。

## 变更文件

- `app/src/main/java/com/example/subtitledownloader/MainActivity.java`
- `zimuku.org 验证码循环修复分析报告.md`

## 下一步排查建议

请抓取设备的 logcat，重点关注 `SubtitleDownloader` 标签。建议命令：

```bash
adb logcat -v time | grep SubtitleDownloader
```

或者：

```bash
adb logcat -v time | grep -E "SubtitleDownloader|chromium|WebView|Cookie"
```

重点看这些信息：

- 请求 URL 和 Referer
- HTTP 状态码
- `Content-Type`
- `Location`
- 当前 Cookie 的键名集合
- 是否在 `resolveDownloadData()` 的某一层再次命中验证码

## 验证限制

当前容器没有 Android SDK，无法执行完整 APK 编译。需要在本地 Android SDK 环境中运行：

```bash
./gradlew assembleDebug
```
