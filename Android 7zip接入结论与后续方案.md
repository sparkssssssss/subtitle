# Android 7-Zip 接入结论与后续方案

## 现象

用户测试 RAR5 文件时出现错误：

```text
SevenZipJBinding couldn't be initialized automaticly using initialization from platform depended JAR...
文件类型：RAR5
junrar错误：null
```

## 结论

这说明 `sevenzipjbinding-all-platforms` 依赖虽然能在 Gradle 中解析，但不适合当前 Android 运行时：它没有可用的 Android ABI native 库，导致 7-Zip 初始化失败。

因此问题不是 RAR 文件本身，而是所选 7-Zip Java Binding 组件不适用于 Android。

## 已修正

已移除不可用依赖：

```gradle
net.sf.sevenzipjbinding:sevenzipjbinding
net.sf.sevenzipjbinding:sevenzipjbinding-all-platforms
```

代码改为明确提示：

- RAR5：当前内置组件暂不支持自动解压，保留原文件；
- 7Z：当前内置组件暂不支持自动解压，保留原文件；
- RAR4：继续尝试 junrar；
- ZIP：继续使用 Zip4j。

## 后续真正可行方案

要在 Android 上稳定支持 RAR5 和 7Z，需要接入 Android 原生解压组件：

### 方案 1：打包 p7zip/7zz Android 可执行文件

- 在 `app/src/main/assets/` 或 `jniLibs/` 中放入不同 ABI 的 7z/7zz 可执行文件；
- 首次运行时复制到 app 私有目录并赋予执行权限；
- 解压时用 `ProcessBuilder` 调用：

```text
7zz x archive.rar -o/output/dir -y
```

优点：接近桌面 7-Zip 能力，RAR5/7Z 支持最好。  
缺点：需要准备 Android ABI 二进制，APK 增大。

### 方案 2：集成 libarchive/p7zip `.so` + JNI

- 用 NDK 编译 libarchive 或 p7zip；
- 写 JNI 包装解压接口；
- Java/Kotlin 调用 JNI 解压。

优点：集成更正规。  
缺点：开发复杂度更高。

## 推荐

对本项目而言，推荐优先采用“打包 Android 版 7zz 可执行文件”的方式，开发成本低于 JNI，兼容 RAR5/7Z 的概率更高。

需要先获取或构建以下 ABI 的 Android 7zz/p7zip 二进制：

- arm64-v8a
- armeabi-v7a
- x86_64（模拟器）

拿到二进制后，再接入 App 调用流程。
