# subtitle 目录清空修复分析报告

## 问题

用户确认“清空目录”应删除 `Download/subtitle/` 下的所有内容。

旧实现只删除：

```java
file.isFile() && isManagedFile(file.getName())
```

也就是只删除白名单扩展名的普通文件，不会删除解压出来的目录，也不会删除其它辅助文件。

## 修复

修改文件：

- `app/src/main/java/com/example/subtitledownloader/MainActivity.java`

主要改动：

1. 本地文件列表改为显示 `Download/subtitle/` 下全部一级项目，包括文件和目录；
2. 单项删除支持递归删除目录；
3. “清空目录”改为删除 `Download/subtitle/` 下全部内容，包括：
   - 字幕文件；
   - ZIP/RAR/7Z 压缩包；
   - 解压目录；
   - 其它下载产生的文件；
4. `Download/subtitle/` 目录本身保留，只清空其内部内容；
5. 清空确认文案改为明确提示“全部内容，包括文件和解压目录”。

## 预期效果

点击“清空目录”后，`Download/subtitle/` 下应变为空目录。
