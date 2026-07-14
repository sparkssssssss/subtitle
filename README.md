# 字幕下载（Android TV）

一个尽量简单的 Android 字幕下载工具，面向电视/遥控器使用。

## 功能

- 字幕源：`https://assrt.net/`
- 输入关键词搜索字幕
- 用遥控器方向键选择结果，确定键下载
- 保存目录：公共下载目录下的 `Download/subtitle/`
- 下载到 `.zip` / `.rar` 时会尝试解压其中的 `.ass`、`.ssa`、`.srt` 文件

## 项目结构

- `app/src/main/java/com/example/subtitledownloader/MainActivity.java`：主逻辑，包含搜索、解析、下载、保存/解压
- `app/src/main/res/layout/activity_main.xml`：简单电视界面
- `app/src/main/AndroidManifest.xml`：网络、存储、电视启动入口配置

## 构建

本项目使用 Android Gradle Plugin 7.2.2 / Gradle 7.5。

```bash
./gradlew assembleDebug
```

生成 APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

> 当前容器缺少 Android SDK，所以这里无法完成本地编译。安装 Android SDK 后，设置 `ANDROID_HOME` 或在 `local.properties` 写入：
>
> ```properties
> sdk.dir=/path/to/android-sdk
> ```

## 注意

assrt.net 没有使用官方公开 API，本应用通过网页 HTML 做轻量解析。如果网站页面结构或访问策略变化，可能需要调整 `parseSearch()` / `findDownloadUrl()` 的规则。

RAR 解压通过 `junrar` 实现。部分 RAR5、加密压缩包或特殊压缩包可能无法解压；这种情况下应用会保留原 `.rar` 文件在 `Download/subtitle/`。
