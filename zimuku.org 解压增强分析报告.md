# zimuku.org 解压增强分析报告

## 背景

用户确认采用渐进方案：先加文件头诊断，并将 ZIP 解压从 Java `ZipInputStream` 替换为 Zip4j；RAR 暂时继续使用 junrar，后续再考虑 7-Zip/libarchive。

## 已实现

修改文件：

- `app/build.gradle`
- `app/src/main/java/com/example/subtitledownloader/MainActivity.java`

## ZIP 解压增强

新增依赖：

```gradle
implementation 'net.lingala.zip4j:zip4j:2.11.5'
```

ZIP 解压改为 Zip4j：

- 优先使用 GBK 字符集，改善中文文件名兼容；
- 失败后回退默认字符集；
- 完整解压到对应目录；
- 解压后递归统计实际释放文件数。

## 文件头诊断

新增压缩包类型识别：

- `ZIP`
- `RAR4`
- `RAR5`
- `7Z`
- `HTML`
- `未知`

用于判断：

1. 文件是否真的是压缩包；
2. 是否下载到了验证码/网页 HTML；
3. RAR 是否是 RAR5。

## RAR 当前状态

RAR 仍使用：

```gradle
implementation 'com.github.junrar:junrar:7.5.5'
```

如果解压失败且文件头是 `RAR5`，App 会提示：

```text
这是 RAR5，当前 junrar 组件可能不支持，需要后续接入 7-Zip/libarchive。
```

## 后续建议

如果用户确认 RAR5 是主要失败来源，下一步应接入 7-Zip 或 libarchive 的 Android 原生解压方案。Zip4j 只能改善 ZIP，不能解决 RAR5。
