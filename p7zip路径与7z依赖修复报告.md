# p7zip 路径与 7Z 依赖修复报告

## 问题

用户测试 7Z 时失败：

```text
p7zip错误：IOException p7zip exit code=7
Java 7Z错误：NoClassDefFoundError Failed resolution of: Lorg/tukaani/xz/LZMA2Options;
```

同时 RAR5 也失败。

## 原因分析

### Java 7Z 失败

Apache Commons Compress 的 7Z 实现需要 `org.tukaani.xz` 支持 LZMA/LZMA2，但项目没有显式引入该依赖，导致运行时：

```text
NoClassDefFoundError: org/tukaani/xz/LZMA2Options
```

### p7zip exit code=7

p7zip 的 exit code 7 通常代表命令行错误。当前文件路径包含中文、空格、特殊字符：

```text
/storage/emulated/0/Download/subtitle/[zmk.pw]大话西游之大圣娶亲[1995].7Z
```

AndroidP7zip 的命令字符串解析对复杂路径可能不稳定，导致命令行错误。

## 修复

修改文件：

- `app/build.gradle`
- `app/src/main/java/com/example/subtitledownloader/MainActivity.java`

### 1. 补 xz 依赖

新增：

```gradle
implementation 'org.tukaani:xz:1.9'
```

用于补齐 Commons Compress 7Z 解压所需的 LZMA/LZMA2 类。

### 2. p7zip 使用 ASCII 临时路径

不再直接让 p7zip 处理中文/特殊字符路径。

新流程：

1. 在 App cache 下创建临时目录；
2. 将原压缩包复制为简单英文文件名：

```text
cache/p7zip_work_x/archive.7z
cache/p7zip_work_x/archive.rar
```

3. 解压到：

```text
cache/p7zip_work_x/out/
```

4. 解压成功后，将输出文件复制回真实目标目录；
5. 删除临时目录。

## 预期效果

- Java 7Z fallback 不再因缺 `org.tukaani.xz` 直接失败；
- p7zip 不再因为中文/特殊字符压缩包路径直接 exit code 7；
- RAR5/7Z 成功率提升。

## 后续验证

需要在 GitHub Actions 或本地 Android SDK/NDK 环境中重新编译安装测试。
