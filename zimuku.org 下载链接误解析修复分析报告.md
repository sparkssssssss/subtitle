# zimuku.org 下载链接误解析修复分析报告

## 问题

用户提供的 ADB 日志显示，下载时反复进入验证码循环。

关键日志：

```text
download detail=https://zimuku.org/detail/232914.html firstUrl=https://zimuku.org/#subinfo
HTTP request url=https://zimuku.org/#subinfo
HTTP request url=http://srtku.com referer=https://zimuku.org/#subinfo
resolveDownloadData captcha at depth=1 url=http://srtku.com
```

## 根因

详情页的真实下载链接是：

```text
/dld/232914.html
```

但旧的 `findHrefByAnchorRegex()` 正则会从 `<a id="down1" ...>` 开始，跨越多个标签继续匹配后续的 `href`，最终误取到了页面内锚点：

```text
#subinfo
```

之后下载解析从首页/锚点页面继续搜索链接，误进入页脚的 `http://srtku.com`，所以触发了无关的验证码循环。

## 修复

修改 `app/src/main/java/com/example/subtitledownloader/MainActivity.java`：

- `findHrefByAnchorRegex()` 先只匹配完整的 `<a ...>` 起始标签；
- 再在这个单独标签内部提取 `href`；
- 防止跨标签误匹配到后续锚点或页脚链接。

修复后应正确解析：

```text
<a id="down1" href="/dld/232914.html" ...>
```

得到：

```text
https://zimuku.org/dld/232914.html
```

## 预期效果

下载流程应变为：

```text
/detail/232914.html
-> /dld/232914.html
-> /download/.../svr/...
-> 真实字幕文件
```

不应再误跳到：

```text
https://zimuku.org/#subinfo
http://srtku.com
```
