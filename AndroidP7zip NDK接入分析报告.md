# AndroidP7zip NDK 接入分析报告

## 背景

用户确认直接上 NDK，以解决 RAR5 和复杂 7Z 解压问题。

## 接入来源

使用仓库：

```text
https://github.com/hzy3774/AndroidP7zip
```

已将其 `libp7zip` 模块 vendor 到当前项目：

```text
libp7zip/
```

## 已修改文件

- `settings.gradle`
- `app/build.gradle`
- `libp7zip/build.gradle`
- `app/src/main/java/com/example/subtitledownloader/MainActivity.java`
- `.github/workflows/android.yml`

## Gradle 配置

新增模块：

```gradle
include ':app', ':libp7zip'
```

App 依赖：

```gradle
implementation project(':libp7zip')
```

`libp7zip` 使用 CMake/NDK 编译 native `p7zip`：

```gradle
ndkVersion '21.4.7075529'
externalNativeBuild {
    cmake {
        path 'src/main/cpp/CMakeLists.txt'
    }
}
```

GitHub Actions 仍然只支持手动触发，但手动构建时会安装：

```text
ndk;21.4.7075529
cmake;3.22.1
```

## App 解压策略

当前策略：

```text
ZIP       -> Zip4j
RAR/RAR5  -> 优先 p7zip，失败时 RAR4 再回退 junrar
7Z        -> 优先 p7zip，失败时回退 Java SevenZFile
```

p7zip 调用方式：

```java
P7ZipApi.executeCommand("7z x \"archive\" -o\"outputDir\" -y")
```

## 风险与验证点

该方案需要 NDK/CMake，构建时间和 APK 体积会增加。

需要在 GitHub Actions 或本地 Android SDK/NDK 环境中验证：

```bash
./gradlew assembleDebug
```

重点验证：

1. `libp7zip` 是否能在 AGP 7.2.2 下编译；
2. native `libp7zip.so` 是否能正常打包进 APK；
3. `P7ZipApi.executeCommand()` 在设备上是否能正常运行；
4. RAR5 是否能成功解压。
