# 7-Zip 组件接入分析报告

## 背景

用户反馈：

- RAR5 解压失败；
- 部分字幕包是 `.7z`；
- 需要接入 7-Zip 组件增强解压能力。

## 已实现

修改文件：

- `app/build.gradle`
- `app/src/main/java/com/example/subtitledownloader/MainActivity.java`

新增依赖：

```gradle
implementation 'net.sf.sevenzipjbinding:sevenzipjbinding:16.02-2.01'
implementation 'net.sf.sevenzipjbinding:sevenzipjbinding-all-platforms:16.02-2.01'
```

新增 7-Zip 解压流程：

- `.rar`：优先用 7-Zip JBinding 解压，失败后回退 junrar；
- `.7z`：使用 7-Zip JBinding 解压；
- `.zip`：继续使用 Zip4j；
- 解压仍然输出到压缩包对应目录；
- 解压保留目录结构，并继续做路径安全检查。

## 风险说明

`sevenzipjbinding-all-platforms` 是 Java 7-Zip binding 的 all-platforms 包，是否包含当前 Android 设备 ABI 需要真机/模拟器验证。如果运行时 native 库加载失败，App 会捕获错误并提示具体错误类型。

如果确认该库在 Android 上不可用，下一步需要改接 Android 专用的 p7zip/libarchive native `.so`，或引入已打包 Android ABI 的 7z 组件。

## 当前降级策略

- RAR：7-Zip 失败后自动尝试 junrar；
- 7Z：7-Zip 失败后保留原文件并提示失败原因；
- ZIP：Zip4j 处理，不走 7-Zip。

## 验证

当前容器没有 Android SDK，未能完成 APK 编译安装验证。

已执行 Gradle 依赖解析，依赖可从 Maven Central 解析：

```text
net.sf.sevenzipjbinding:sevenzipjbinding:16.02-2.01
net.sf.sevenzipjbinding:sevenzipjbinding-all-platforms:16.02-2.01
```
