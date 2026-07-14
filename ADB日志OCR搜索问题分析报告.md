# ADB 日志 OCR/搜索问题分析报告

## Overview

分析文件：

- 原始日志：`adb.txt`
- 脱敏视图：`/tmp/redacted-adb/adb.txt`

使用 `redact-before-read` 技能先生成脱敏副本，再基于脱敏日志分析，避免暴露可能的 URL 参数、Cookie 或 Token。

## 关键日志

日志中与 App 相关的关键行只有两次搜索请求：

```text
07-14 22:42:07.512 D SubtitleDownloader: HTTP request url=https://zimuku.org/search?q=... referer= cookieKeys=<REDACTED>
07-14 22:42:22.626 D SubtitleDownloader: HTTP request url=https://zimuku.org/search?keyword=... referer= cookieKeys=<REDACTED>
```

未看到以下日志：

- `HTTP response code=...`
- `OCR responseCode...`
- `OCR responseBody...`
- `submit captcha...`
- `resolveDownloadData captcha...`
- Java 崩溃堆栈 / FATAL EXCEPTION

## 结论

这份日志没有进入 OCR 流程。

从时间线看，App 发起第一个 zimuku 搜索请求后约 15 秒没有响应日志，然后开始第二个搜索 URL；第二个搜索请求出现在日志末尾附近，也没有看到响应日志。

这更像是搜索请求卡在网络请求阶段，随后触发 `SocketTimeoutException` 或用户截日志太早，而不是验证码/OCR 本身的问题。

## 根因推断

### 1. 网络请求超时或被阻断

代码中 `httpData()` 设置：

```java
conn.setConnectTimeout(15000);
conn.setReadTimeout(30000);
```

第一条请求从 `22:42:07.512` 发出，第二条请求在 `22:42:22.626` 发出，间隔约 15 秒，符合连接超时后尝试下一个搜索 URL 的行为。

### 2. 当前日志不足以证明 OCR 有问题

因为日志中没有 `OCR` tag，也没有验证码弹窗/提交相关日志，说明当前这次复现还没走到：

- `parseCaptcha()` 命中验证码；
- `showCaptchaDialog()`；
- `requestOcr()`；
- `CaptchaTask` 提交验证码。

### 3. App 的 HTTP 响应日志可能缺失，因为异常发生在 `getResponseCode()` 前

`httpData()` 只有在 `getResponseCode()` 成功返回后才打印：

```java
Log.d(TAG, "HTTP response code=" + code + ...)
```

如果 DNS、TLS、连接或读响应阶段异常，就只会看到 request，不会看到 response。当前日志正符合这个模式。

## 建议

### 1. 增加 HTTP 异常日志

在 `SearchTask` 捕获每个 URL 的 `IOException` 时打印：

```java
Log.d(TAG, "search url failed=" + redactUrl(url) + " error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
```

这样下次日志能直接看出是 `SocketTimeoutException`、`UnknownHostException`、`SSLHandshakeException` 还是 HTTP 错误。

### 2. 给 `httpData()` 的 `getResponseCode()` 外层增加异常日志

当前网络异常不容易定位。建议在打开连接和 `getResponseCode()` 附近捕获并记录当前 URL。

### 3. 抓更完整日志

如果要分析 OCR，需要从出现验证码弹窗开始继续抓到：

- OCR 请求发出；
- OCR response；
- 是否自动提交；
- `submit captcha`；
- 提交后是否继续下载/搜索。

## Changed Files

本次仅分析，未修改代码。
