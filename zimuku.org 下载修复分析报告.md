# zimuku.org 下载修复分析报告

## 背景

用户要求先回退到 Git 提交：

```text
3123875 Preserve cookies across download redirects
```

该版本已实现弹窗验证码图片和手动输入验证码流程，但用户反馈下载仍有问题。

## 已执行回退

```bash
git reset --hard 3123875b1efcbcd23822e76abf08ab382e1ccb0c
git clean -fd
```

当前修复基于 `3123875`。

## 下载链路实测

用示例详情页验证：

```text
https://zimuku.org/detail/233027.html
```

实际链路：

```text
/detail/233027.html
-> /dld/233027.html
-> /download/.../svr/d0
-> 301 Location: //s.zimuku.org/download/...
-> HTTP 200 application/octet-stream
-> Content-Disposition: attachment; filename="[zmk.pw]%E7%B9%81%E8%8B%B1%E7%89%B9%E6%95%88V4.ass"
-> 文件内容为 ASS 字幕，[Script Info] 开头
```

## 问题判断

真实下载会跳转到 `s.zimuku.org` 子域，且文件名在 `Content-Disposition` 中是 URL 编码。Android 环境中还可能受到明文 HTTP 限制影响。

## 已修复内容

修改文件：

- `app/src/main/java/com/example/subtitledownloader/MainActivity.java`
- `app/src/main/res/xml/network_security_config.xml`

修复点：

1. `normalizeDownloadHost()` 增加 zimuku 下载域名规范化：
   - `http://zimuku.org/...` -> `https://zimuku.org/...`
   - `http://s.zimuku.org/...` -> `https://s.zimuku.org/...`
2. `network_security_config.xml` 增加 `zimuku.org` 子域配置，兼容真实下载链路。
3. `fileNameFromConnection()` 增加 UTF-8 URL 解码，正确保存中文文件名，例如：
   - `[zmk.pw]繁英特效V4.ass`

## 验证限制

当前容器仍没有 Android SDK，因此无法执行完整 Android 编译：

```bash
./gradlew assembleDebug
```

需要在配置 Android SDK 的环境中编译安装测试。
