package com.example.subtitledownloader;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Html;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;

public class MainActivity extends Activity {
    private static final int REQ_STORAGE = 100;
    private static final String BASE = "https://zimuku.org";
    private static String cookieHeader = "";
    private EditText searchInput;
    private Button searchButton;
    private Button filesButton;
    private Button clearButton;
    private TextView statusText;
    private ListView resultList;
    private final ArrayList<SubtitleItem> items = new ArrayList<>();
    private final ArrayList<File> localFiles = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private boolean fileMode = false;
    private String pendingSearchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        searchInput = findViewById(R.id.searchInput);
        searchButton = findViewById(R.id.searchButton);
        filesButton = findViewById(R.id.filesButton);
        clearButton = findViewById(R.id.clearButton);
        statusText = findViewById(R.id.statusText);
        resultList = findViewById(R.id.resultList);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        resultList.setAdapter(adapter);

        searchButton.setOnClickListener(v -> doSearch());
        filesButton.setOnClickListener(v -> toggleFileMode());
        clearButton.setOnClickListener(v -> confirmClearSubtitleDir());
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP)) {
                doSearch();
                return true;
            }
            return false;
        });
        resultList.setOnItemClickListener((parent, view, position, id) -> {
            if (fileMode) confirmDeleteFile(localFiles.get(position));
            else new DownloadTask().execute(items.get(position));
        });

        ensureStoragePermission();
    }

    private void ensureStoragePermission() {
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT < 29 &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
        }
    }

    private void doSearch() {
        String q = searchInput.getText().toString().trim();
        if (q.length() == 0) {
            Toast.makeText(this, "请输入关键词", Toast.LENGTH_SHORT).show();
            return;
        }
        fileMode = false;
        filesButton.setText("本地文件");
        pendingSearchQuery = q;
        new SearchTask().execute(q);
    }

    private void toggleFileMode() {
        fileMode = !fileMode;
        filesButton.setText(fileMode ? "返回搜索" : "本地文件");
        if (fileMode) loadLocalFiles();
        else {
            adapter.clear();
            for (SubtitleItem item : items) adapter.add(item.title + "\n" + item.url);
            adapter.notifyDataSetChanged();
            statusText.setText(items.isEmpty() ? "输入关键词后按确定键搜索。" : "已返回搜索结果。选择一项按确定键下载。");
        }
    }

    private void loadLocalFiles() {
        localFiles.clear();
        adapter.clear();
        File dir = subtitleDir();
        File[] files = dir.listFiles(file -> file.isFile() && isManagedFile(file.getName()));
        if (files != null) {
            Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
            localFiles.addAll(Arrays.asList(files));
        }
        for (File file : localFiles) adapter.add(formatFileRow(file));
        adapter.notifyDataSetChanged();
        statusText.setText(localFiles.isEmpty()
                ? "本地目录为空：" + dir.getAbsolutePath()
                : "本地文件 " + localFiles.size() + " 个。选择文件按确定键删除；也可点清空目录。");
    }

    private void confirmDeleteFile(File file) {
        new AlertDialog.Builder(this)
                .setTitle("删除文件？")
                .setMessage(file.getName())
                .setPositiveButton("删除", (dialog, which) -> {
                    boolean ok = file.delete();
                    Toast.makeText(this, ok ? "已删除" : "删除失败", Toast.LENGTH_SHORT).show();
                    loadLocalFiles();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmClearSubtitleDir() {
        File dir = subtitleDir();
        File[] files = dir.listFiles(file -> file.isFile() && isManagedFile(file.getName()));
        int count = files == null ? 0 : files.length;
        if (count == 0) {
            Toast.makeText(this, "没有可清理的字幕文件", Toast.LENGTH_SHORT).show();
            if (fileMode) loadLocalFiles();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("清空字幕目录？")
                .setMessage("将删除 Download/subtitle/ 下的 " + count + " 个字幕/压缩包文件。")
                .setPositiveButton("清空", (dialog, which) -> {
                    int deleted = clearSubtitleFiles();
                    Toast.makeText(this, "已删除 " + deleted + " 个文件", Toast.LENGTH_SHORT).show();
                    if (fileMode) loadLocalFiles();
                    else statusText.setText("已清理 " + deleted + " 个本地字幕文件。");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private int clearSubtitleFiles() {
        File dir = subtitleDir();
        File[] files = dir.listFiles(file -> file.isFile() && isManagedFile(file.getName()));
        int deleted = 0;
        if (files != null) {
            for (File file : files) if (file.delete()) deleted++;
        }
        return deleted;
    }

    private static String formatFileRow(File file) {
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(file.lastModified()));
        return file.getName() + "\n" + humanSize(file.length()) + "　" + time;
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        return String.format(Locale.US, "%.1f MB", kb / 1024.0);
    }

    private class SearchTask extends AsyncTask<String, String, List<SubtitleItem>> {
        private String error;
        private CaptchaChallenge captcha;

        @Override protected void onPreExecute() {
            items.clear();
            adapter.clear();
            statusText.setText("正在搜索 zimuku.org ...");
        }

        @Override protected List<SubtitleItem> doInBackground(String... params) {
            try {
                String q = URLEncoder.encode(params[0], "UTF-8");
                IOException lastError = null;
                for (String url : zimukuSearchUrls(q)) {
                    try {
                        HttpData page = httpData(url);
                        String html = new String(page.data, "UTF-8");
                        captcha = parseCaptcha(html, url);
                        if (captcha != null) return new ArrayList<>();
                        List<SubtitleItem> parsed = parseSearch(html);
                        if (!parsed.isEmpty()) return parsed;
                    } catch (IOException e) {
                        lastError = e;
                    }
                }
                if (lastError != null) throw lastError;
                return new ArrayList<>();
            } catch (Exception e) {
                error = e.getMessage();
                return new ArrayList<>();
            }
        }

        @Override protected void onPostExecute(List<SubtitleItem> result) {
            items.addAll(result);
            adapter.clear();
            for (SubtitleItem item : items) adapter.add(item.title + "\n" + item.url);
            adapter.notifyDataSetChanged();
            if (captcha != null) {
                statusText.setText("zimuku.org 需要验证码，请手动输入。 ");
                showCaptchaDialog(captcha);
            } else if (error != null) statusText.setText("搜索失败：" + error);
            else if (items.isEmpty()) statusText.setText("没有找到结果。zimuku.org 页面变化时可能需要调整解析规则。");
            else statusText.setText("找到 " + items.size() + " 个结果，选择一项按确定键下载。仅显示前 30 项。");
        }
    }

    private class DownloadTask extends AsyncTask<SubtitleItem, String, String> {
        private String error;

        @Override protected void onPreExecute() { statusText.setText("准备下载..."); }
        @Override protected void onProgressUpdate(String... values) { statusText.setText(values[0]); }

        @Override protected String doInBackground(SubtitleItem... params) {
            try {
                SubtitleItem item = params[0];
                publishProgress("打开详情页...");
                String detailHtml = httpGet(item.url);
                String downloadUrl = findDownloadUrl(detailHtml);
                if (downloadUrl == null) {
                    if (looksLikeDownload(item.url)) downloadUrl = item.url;
                    else throw new IOException("未在详情页找到下载链接");
                }
                publishProgress("解析下载链接...");
                HttpData downloadData = resolveDownloadData(downloadUrl);
                publishProgress("保存字幕文件...");
                DownloadedFile file = downloadToSubtitleFolder(downloadData, safeName(item.title));
                String lowerName = file.file.getName().toLowerCase(Locale.US);
                if (lowerName.endsWith(".zip")) {
                    int count = unzipSubtitles(file.file);
                    if (count > 0) return "已下载并解压 ZIP 中的 " + count + " 个字幕：" + subtitleDir().getAbsolutePath();
                } else if (lowerName.endsWith(".rar")) {
                    try {
                        int count = unrarSubtitles(file.file);
                        if (count > 0) return "已下载并解压 RAR 中的 " + count + " 个字幕：" + subtitleDir().getAbsolutePath();
                        return "已下载 RAR，但里面没有找到 ass/ssa/srt 字幕：" + file.displayPath;
                    } catch (Exception rarError) {
                        return "已下载 RAR，但自动解压失败：" + rarError.getMessage() + "\n文件：" + file.displayPath;
                    }
                }
                return "已下载到：" + file.displayPath;
            } catch (Exception e) {
                error = e.getMessage();
                return null;
            }
        }

        @Override protected void onPostExecute(String msg) {
            if (error != null) statusText.setText("下载失败：" + error);
            else statusText.setText(msg);
            if (fileMode) loadLocalFiles();
        }
    }

    private static List<SubtitleItem> parseSearch(String html) {
        ArrayList<SubtitleItem> out = new ArrayList<>();
        Pattern a = Pattern.compile("<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = a.matcher(html);
        while (m.find() && out.size() < 30) {
            String href = htmlDecode(m.group(1)).trim();
            String text = cleanText(m.group(2));
            if (text.length() < 2) continue;
            String abs = absoluteUrl(href);
            String lower = abs.toLowerCase(Locale.US);
            boolean detail = looksLikeZimukuDetail(lower);
            boolean direct = looksLikeDownload(lower);
            if (!detail && !direct) continue;
            if (containsUrl(out, abs)) continue;
            out.add(new SubtitleItem(text, abs));
        }
        return out;
    }

    private void showCaptchaDialog(CaptchaChallenge challenge) {
        byte[] imageBytes = Base64.decode(challenge.imageBase64, Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        box.setPadding(pad, pad / 2, pad, 0);

        ImageView image = new ImageView(this);
        image.setImageBitmap(bitmap);
        image.setAdjustViewBounds(true);
        box.addView(image);

        EditText input = new EditText(this);
        input.setHint("输入上方验证码");
        input.setSingleLine(true);
        input.setTextColor(0xFF000000);
        box.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("zimuku.org 验证码")
                .setView(box)
                .setPositiveButton("提交", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String code = input.getText().toString().trim();
                if (code.length() == 0) {
                    Toast.makeText(this, "请输入验证码", Toast.LENGTH_SHORT).show();
                    return;
                }
                dialog.dismiss();
                new CaptchaTask().execute(code, challenge.sourceUrl);
            });
        });
        dialog.show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private class CaptchaTask extends AsyncTask<String, Void, Boolean> {
        private String error;
        private CaptchaChallenge nextCaptcha;

        @Override protected void onPreExecute() { statusText.setText("正在提交验证码..."); }

        @Override protected Boolean doInBackground(String... codes) {
            try {
                String sourceUrl = codes.length > 1 ? codes[1] : "";
                if (sourceUrl != null && sourceUrl.length() > 0) {
                    putCookie("srcurl", stringToHex(sourceUrl));
                }
                String verifyUrl = BASE + "/?security_verify_img=" + stringToHex(codes[0]);
                HttpData response = httpDataAllowHttpError(verifyUrl);
                String html = new String(response.data, "UTF-8");
                nextCaptcha = parseCaptcha(html, sourceUrl != null && sourceUrl.length() > 0 ? sourceUrl : verifyUrl);
                if (nextCaptcha != null) throw new IOException("验证码可能不正确或已过期");
                return true;
            } catch (Exception e) {
                error = e.getMessage();
                return false;
            }
        }

        @Override protected void onPostExecute(Boolean ok) {
            if (ok) {
                statusText.setText("验证码已提交，正在重试搜索...");
                if (pendingSearchQuery.length() > 0) new SearchTask().execute(pendingSearchQuery);
            } else if (nextCaptcha != null) {
                statusText.setText(error + "，请重新输入。 ");
                showCaptchaDialog(nextCaptcha);
            } else {
                statusText.setText("验证码提交失败：" + error);
            }
        }
    }

    private static String[] zimukuSearchUrls(String encodedQuery) {
        return new String[]{
                BASE + "/search?q=" + encodedQuery,
                BASE + "/search?keyword=" + encodedQuery,
                BASE + "/search/" + encodedQuery
        };
    }

    private static CaptchaChallenge parseCaptcha(String html, String sourceUrl) {
        if (!html.contains("security_verify_img") && !html.contains("网站防火墙")) return null;
        Matcher m = Pattern.compile("src=[\"']data:image/[^;]+;base64,([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html);
        if (!m.find()) return null;
        return new CaptchaChallenge(m.group(1), sourceUrl);
    }

    private static String stringToHex(String str) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) sb.append(Integer.toHexString(str.charAt(i)));
        return sb.toString();
    }

    private static boolean looksLikeZimukuDetail(String lowerUrl) {
        if (!lowerUrl.startsWith(BASE)) return false;
        if (lowerUrl.contains("/search")) return false;
        return lowerUrl.contains("/detail/") || lowerUrl.contains("/subtitle/") ||
                lowerUrl.contains("/sub/") || lowerUrl.matches(".*/[0-9]+\\.html(\\?.*)?$") ||
                lowerUrl.matches(".*/[0-9]+(\\?.*)?$");
    }

    private static String findDownloadUrl(String html) {
        String down1 = findHrefByAnchorRegex(html, "(?is)<a[^>]+id=[\"']down1[\"'][^>]*>");
        if (down1 != null) return down1;

        String zimukuDownload = findHrefByUrlPart(html, "/download/");
        if (zimukuDownload != null) return zimukuDownload;

        String dld = findHrefByUrlPart(html, "/dld/");
        if (dld != null) return dld;

        String direct = findDirectFileUrl(html);
        if (direct != null) return direct;

        Pattern a = Pattern.compile("<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = a.matcher(html);
        String firstDownload = null;
        while (m.find()) {
            String href = htmlDecode(m.group(1)).trim();
            String text = cleanText(m.group(2)).toLowerCase(Locale.US);
            String abs = absoluteUrl(href);
            String lower = abs.toLowerCase(Locale.US);
            if (lower.contains("/jp.php")) continue;
            if (firstDownload == null && (lower.contains("download") || text.contains("下载") || text.contains("立即"))) firstDownload = abs;
        }
        return firstDownload;
    }

    private static String findHrefByAnchorRegex(String html, String anchorStartRegex) {
        Pattern p = Pattern.compile(anchorStartRegex + ".*?href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(html);
        return m.find() ? absoluteUrl(htmlDecode(m.group(1)).trim()) : null;
    }

    private static String findHrefByUrlPart(String html, String urlPart) {
        Pattern a = Pattern.compile("<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = a.matcher(html);
        while (m.find()) {
            String href = htmlDecode(m.group(1)).trim();
            String abs = absoluteUrl(href);
            String lower = abs.toLowerCase(Locale.US);
            if (lower.contains("/jp.php")) continue;
            if (lower.contains(urlPart)) return abs;
        }
        return null;
    }

    private static String findDirectFileUrl(String html) {
        Pattern a = Pattern.compile("<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = a.matcher(html);
        while (m.find()) {
            String href = htmlDecode(m.group(1)).trim();
            String abs = absoluteUrl(href);
            if (looksLikeDirectFile(abs)) return abs;
        }
        return null;
    }

    private static boolean looksLikeDownload(String url) {
        String lower = url.toLowerCase(Locale.US);
        return lower.contains("/download") || looksLikeDirectFile(lower);
    }

    private static boolean looksLikeDirectFile(String url) {
        String lower = url.toLowerCase(Locale.US);
        return lower.matches(".*\\.(zip|rar|7z|ass|ssa|srt)(\\?.*)?$");
    }

    private static String httpGet(String url) throws IOException { return new String(httpData(url).data, "UTF-8"); }

    private static HttpData httpBytes(String url) throws IOException { return httpData(url); }

    private static HttpData httpBytes(String url, String referer) throws IOException { return httpData(url, false, referer); }

    private static HttpData httpData(String url) throws IOException { return httpData(url, false); }

    private static HttpData httpDataAllowHttpError(String url) throws IOException { return httpData(url, true); }

    private static HttpData httpData(String url, boolean allowHttpError) throws IOException { return httpData(url, allowHttpError, null); }

    private static HttpData httpData(String url, boolean allowHttpError, String referer) throws IOException {
        String current = normalizeDownloadHost(url);
        for (int redirect = 0; redirect < 6; redirect++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(current).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV) SubtitleDownloader/1.0");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml,application/zip,*/*");
            if (referer != null && referer.length() > 0) conn.setRequestProperty("Referer", referer);
            if (cookieHeader.length() > 0) conn.setRequestProperty("Cookie", cookieHeader);
            int code = conn.getResponseCode();
            rememberCookies(conn);
            if (isRedirect(code)) {
                String location = conn.getHeaderField("Location");
                if (location == null || location.length() == 0) throw new IOException("HTTP " + code + " 缺少 Location：" + current);
                current = normalizeDownloadHost(resolveUrl(location, current));
                continue;
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            InputStream raw = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (raw != null) {
                try (InputStream in = new BufferedInputStream(raw)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) >= 0) bos.write(buf, 0, n);
                }
            }
            byte[] body = bos.toByteArray();
            if (!allowHttpError && (code < 200 || code >= 300)) {
                String text = new String(body, "UTF-8");
                if (parseCaptcha(text, current) == null) throw new IOException("HTTP " + code + "：" + current);
            }
            String name = fileNameFromConnection(conn, current);
            return new HttpData(body, name, conn.getContentType());
        }
        throw new IOException("HTTP 重定向次数过多：" + url);
    }

    private static HttpData resolveDownloadData(String url) throws IOException {
        String current = url;
        String referer = null;
        for (int depth = 0; depth < 5; depth++) {
            HttpData data = httpBytes(current, referer);
            if (!looksLikeHtml(data)) return data;
            String html = new String(data.data, "UTF-8");
            CaptchaChallenge captcha = parseCaptcha(html, current);
            if (captcha != null) throw new IOException("下载时遇到验证码，请先重新搜索并完成验证码");
            String next = findDownloadUrl(html);
            if (next == null || next.equals(current)) throw new IOException("下载页里没有找到真实字幕文件链接");
            referer = current;
            current = next;
        }
        throw new IOException("下载跳转层级过多");
    }

    private static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    private static String resolveUrl(String location, String baseUrl) throws IOException {
        return new URL(new URL(baseUrl), location).toString();
    }

    private static String normalizeDownloadHost(String url) {
        if (url.equals("http://srtku.com")) return "https://srtku.com";
        if (url.startsWith("http://srtku.com/")) return "https://srtku.com/" + url.substring("http://srtku.com/".length());
        if (url.equals("http://www.srtku.com")) return "https://www.srtku.com";
        if (url.startsWith("http://www.srtku.com/")) return "https://www.srtku.com/" + url.substring("http://www.srtku.com/".length());
        return url;
    }

    private static boolean looksLikeHtml(HttpData data) {
        String lower = data.contentType == null ? "" : data.contentType.toLowerCase(Locale.US);
        if (lower.contains("text/html")) return true;
        int n = Math.min(data.data.length, 200);
        String head = new String(data.data, 0, n).trim().toLowerCase(Locale.US);
        return head.startsWith("<!doctype html") || head.startsWith("<html") || head.contains("<html");
    }

    private DownloadedFile downloadToSubtitleFolder(HttpData data, String fallbackName) throws IOException {
        String name = data.fileName != null ? data.fileName : fallbackName;
        name = safeName(name);
        String detectedExt = detectExtension(data.data);
        if (!name.matches("(?i).*\\.(zip|rar|7z|ass|ssa|srt)$")) name += detectedExt != null ? detectedExt : ".zip";

        File dir = subtitleDir();
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("无法创建目录：" + dir);
        File file = uniqueFile(dir, name);
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(data.data); }
        return new DownloadedFile(file, file.getAbsolutePath());
    }

    private static String detectExtension(byte[] data) {
        if (data.length >= 4 && data[0] == 'P' && data[1] == 'K') return ".zip";
        if (data.length >= 7 && data[0] == 'R' && data[1] == 'a' && data[2] == 'r' && data[3] == '!') return ".rar";
        if (data.length >= 6 && (data[0] & 0xff) == 0x37 && (data[1] & 0xff) == 0x7a) return ".7z";
        String head = new String(data, 0, Math.min(data.length, 200)).toLowerCase(Locale.US);
        if (head.contains("[script info]") || head.contains("dialogue:")) return ".ass";
        if (head.matches("(?s)\\s*\\d+\\s*\\r?\\n\\d{2}:\\d{2}:\\d{2}.*")) return ".srt";
        return null;
    }

    private int unzipSubtitles(File zip) throws IOException {
        int count = 0;
        File dir = subtitleDir();
        byte[] buf = new byte[8192];
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new java.io.FileInputStream(zip)))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String name = new File(e.getName()).getName();
                if (!isSubtitleFile(name)) continue;
                File outFile = uniqueFile(dir, safeName(name));
                try (FileOutputStream out = new FileOutputStream(outFile)) {
                    int n;
                    while ((n = zis.read(buf)) > 0) out.write(buf, 0, n);
                }
                count++;
            }
        }
        return count;
    }

    private int unrarSubtitles(File rar) throws IOException, RarException {
        int count = 0;
        File dir = subtitleDir();
        try (Archive archive = new Archive(rar)) {
            FileHeader header;
            while ((header = archive.nextFileHeader()) != null) {
                if (header.isDirectory()) continue;
                String name = header.getFileNameString();
                if (name == null || name.length() == 0) name = header.getFileNameW();
                name = new File(name).getName();
                if (!isSubtitleFile(name)) continue;
                File outFile = uniqueFile(dir, safeName(name));
                try (FileOutputStream out = new FileOutputStream(outFile)) {
                    archive.extractFile(header, out);
                }
                count++;
            }
        }
        return count;
    }

    private static boolean isSubtitleFile(String name) {
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".ass") || lower.endsWith(".ssa") || lower.endsWith(".srt");
    }

    private static boolean isManagedFile(String name) {
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".ass") || lower.endsWith(".ssa") || lower.endsWith(".srt") ||
                lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z");
    }

    private static void rememberCookies(HttpURLConnection conn) {
        List<String> cookies = conn.getHeaderFields().get("Set-Cookie");
        if (cookies == null || cookies.isEmpty()) return;
        for (String cookie : cookies) putCookiePair(cookie.split(";", 2)[0]);
    }

    private static void putCookie(String key, String value) {
        putCookiePair(key + "=" + value);
    }

    private static void putCookiePair(String pair) {
        String key = pair.split("=", 2)[0];
        ArrayList<String> merged = new ArrayList<>();
        if (cookieHeader.length() > 0) merged.addAll(Arrays.asList(cookieHeader.split("; ")));
        for (int i = merged.size() - 1; i >= 0; i--) {
            if (merged.get(i).startsWith(key + "=")) merged.remove(i);
        }
        merged.add(pair);
        cookieHeader = joinCookies(merged);
    }

    private static String joinCookies(List<String> cookies) {
        StringBuilder sb = new StringBuilder();
        for (String cookie : cookies) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(cookie);
        }
        return sb.toString();
    }

    private static String fileNameFromConnection(HttpURLConnection conn, String url) {
        String cd = conn.getHeaderField("Content-Disposition");
        if (cd != null) {
            Matcher m = Pattern.compile("filename\\*?=([^;]+)", Pattern.CASE_INSENSITIVE).matcher(cd);
            if (m.find()) return m.group(1).replace("UTF-8''", "").replace("\"", "").trim();
        }
        String path = Uri.parse(url).getLastPathSegment();
        return path == null || path.length() == 0 ? null : path;
    }

    private static String absoluteUrl(String href) {
        if (href.startsWith("http://") || href.startsWith("https://")) return href;
        if (href.startsWith("//")) return "https:" + href;
        if (href.startsWith("/")) return BASE + href;
        return BASE + "/" + href;
    }

    private File subtitleDir() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "subtitle");
    }

    private static File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; ; i++) {
            f = new File(dir, base + "_" + i + ext);
            if (!f.exists()) return f;
        }
    }

    private static String cleanText(String s) {
        s = s.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return htmlDecode(s);
    }

    private static String htmlDecode(String s) {
        if (Build.VERSION.SDK_INT >= 24) return Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString();
        return Html.fromHtml(s).toString();
    }

    private static String safeName(String s) {
        s = htmlDecode(s).replaceAll("[\\\\/:*?\"<>|\r\n]+", "_").trim();
        return s.length() == 0 ? "subtitle" : (s.length() > 80 ? s.substring(0, 80) : s);
    }

    private static String mimeFor(String name) {
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".zip")) return "application/zip";
        if (lower.endsWith(".srt") || lower.endsWith(".ass") || lower.endsWith(".ssa")) return "text/plain";
        return "application/octet-stream";
    }

    private static boolean containsUrl(List<SubtitleItem> list, String url) {
        for (SubtitleItem item : list) if (item.url.equals(url)) return true;
        return false;
    }

    private static class SubtitleItem {
        final String title;
        final String url;
        SubtitleItem(String title, String url) { this.title = title; this.url = url; }
    }

    private static class HttpData {
        final byte[] data;
        final String fileName;
        final String contentType;
        HttpData(byte[] data, String fileName, String contentType) {
            this.data = data;
            this.fileName = fileName;
            this.contentType = contentType;
        }
    }

    private static class CaptchaChallenge {
        final String imageBase64;
        final String sourceUrl;
        CaptchaChallenge(String imageBase64, String sourceUrl) {
            this.imageBase64 = imageBase64;
            this.sourceUrl = sourceUrl;
        }
    }

    private static class DownloadedFile {
        final File file;
        final String displayPath;
        DownloadedFile(File file, String displayPath) { this.file = file; this.displayPath = displayPath; }
    }
}
