# 7Z 纯 Java 解压接入分析报告

## 背景

用户希望处理 `.7z` 压缩包。此前尝试 `sevenzipjbinding` 失败，原因是该组件没有可用 Android ABI native 库。

## 新方案

对 `.7z` 先采用纯 Java 的 Apache Commons Compress，不依赖 native ABI：

```gradle
implementation 'org.apache.commons:commons-compress:1.26.2'
```

## 已实现

修改文件：

- `app/build.gradle`
- `app/src/main/java/com/example/subtitledownloader/MainActivity.java`

新增逻辑：

- 下载到 `.7z` 后自动调用 `extract7zArchive()`；
- 使用 `SevenZFile` 逐项读取；
- 解压到压缩包对应目录；
- 保留目录结构；
- 使用已有路径安全检查，避免路径穿越；
- 解压失败时保留原 `.7z` 文件并提示错误。

## 当前解压策略

```text
ZIP  -> Zip4j
7Z   -> Apache Commons Compress SevenZFile
RAR4 -> junrar
RAR5 -> 当前仍不支持，保留文件并提示
```

## 限制

Commons Compress 可处理常见 7z，但如果遇到：

- 加密 7z；
- 特殊压缩算法；
- 损坏文件；

仍可能失败。失败时 App 会保留原压缩包并显示错误。

## 验证

已执行依赖解析：

```bash
./gradlew :app:dependencies --configuration debugRuntimeClasspath
```

依赖解析成功。
