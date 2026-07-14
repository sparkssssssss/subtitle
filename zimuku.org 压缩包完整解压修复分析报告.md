# zimuku.org 压缩包完整解压修复分析报告

## 问题

用户反馈下载到 ZIP/RAR 后没有解压，例如：

```text
[zmk.pw]A.Chinese.0dyssey.Part.lll.2016.1080p.BluRay.x264-WiKi.zip
[zmk.pw]大话西游之大圣娶亲A.Chinese.Odyssey.Part.Two.Cinderella.1995.1080p.BluRay.REMUX..rar
```

旧逻辑只提取压缩包中的字幕白名单扩展名：

```text
.ass / .ssa / .srt / .sup
```

如果压缩包内包含目录、说明文件、其它字幕格式、或文件名识别异常，就会出现“压缩包已下载但没有解压文件”的体验。

## 修复

按用户要求改为：**ZIP/RAR 直接完整解压到对应目录**。

修改文件：

- `app/src/main/java/com/example/subtitledownloader/MainActivity.java`

主要改动：

1. `unzipSubtitles()` 改为 `unzipArchive()`；
2. `unrarSubtitles()` 改为 `unrarArchive()`；
3. 不再按字幕扩展名过滤，压缩包内所有普通文件都会解压；
4. 每个压缩包解压到独立目录，目录名基于压缩包文件名，例如：

```text
Download/subtitle/[zmk.pw]A.Chinese.0dyssey.Part.lll.2016.1080p.BluRay.x264-WiKi/
```

5. 保留压缩包内部目录结构；
6. 增加路径安全处理，过滤 `..`、空路径等，避免 Zip Slip 类路径穿越问题；
7. 如果解压目标重名，自动追加 `_1`、`_2`。

## 预期效果

下载 ZIP/RAR 后，会在 `Download/subtitle/` 下创建对应目录，并把压缩包里的文件完整释放进去，而不是只提取白名单字幕扩展名。
