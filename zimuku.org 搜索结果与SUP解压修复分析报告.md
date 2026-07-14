# zimuku.org 搜索结果与 SUP 解压修复分析报告

## 问题

用户反馈：

1. 列表中第一个结果反复弹出验证码；
2. 部分结果可以下载，但下载到的 RAR 没有解压出文件。

## 原因判断

### 第一个结果反复验证码

zimuku 搜索页中可能包含影视聚合页、页内锚点、页脚外链等非字幕详情链接。旧逻辑把多种数字页都当作详情页：

```text
/subs/数字.html
/数字.html
/数字
```

这些页面不一定有具体字幕下载入口。解析失败后，可能误取到：

```text
#subinfo
http://srtku.com
```

从而进入无关页面并触发验证码循环。

### RAR 未解压

当前 `isSubtitleFile()` 只提取：

```text
.ass / .ssa / .srt
```

但用户示例中存在 `SUP` 字幕。如果 RAR 内是 `.sup` 文件，旧逻辑会认为压缩包里没有字幕，因此不解压。

## 修复

修改 `app/src/main/java/com/example/subtitledownloader/MainActivity.java`：

1. 收紧搜索结果详情页识别，只把以下链接当作 zimuku 字幕详情页：

```text
/detail/数字.html
```

2. 新增 `shouldIgnoreLink()`，过滤：

```text
#锚点
javascript:
/jp.php
非下载用途的 srtku.com 外链
```

3. 直接文件类型增加 `.sup`：

```text
zip / rar / 7z / ass / ssa / srt / sup
```

4. 解压 ZIP/RAR 时支持提取 `.sup`。

5. 本地文件管理和 MIME 判断也支持 `.sup`。

## 预期效果

- 搜索列表不再混入影视聚合页或锚点链接；
- 点击第一个结果时不应再误跳到 `#subinfo` 或 `srtku.com`；
- RAR 中包含 `.sup` 时会被提取到 `Download/subtitle/`。
