package com.example.subtitledownloader;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Html;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {
    private static final int REQ_STORAGE = 100;
    private static final String BASE = "https://assrt.net";
    private EditText searchInput;
    private Button searchButton;
    private TextView statusText;
    private ListView resultList;
    private final ArrayList<SubtitleItem> items = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        searchInput = findViewById(R.id.searchInput);
        searchButton = findViewById(R.id.searchButton);
        statusText = findViewById(R.id.statusText);
        resultList = findViewById(R.id.resultList);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        resultList.setAdapter(adapter);

        searchButton.setOnClickListener(v -> doSearch());
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP)) {
                doSearch();
                return true;
            }
            return false;
        });
        resultList.setOnItemClickListener((parent, view, position, id) -> new DownloadTask().execute(items.get(position)));

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
        new SearchTask().execute(q);
    }

    private class SearchTask extends AsyncTask<String, String, List<SubtitleItem>> {
        private String error;

        @Override protected void onPreExecute() {
            items.clear();
            adapter.clear();
            statusText.setText("正在搜索 assrt.net ...");
        }

        @Override protected List<SubtitleItem> doInBackground(String... params) {
            try {
                String q = URLEncoder.encode(params[0], "UTF-8");
                String html = httpGet(BASE + "/sub/?searchword=" + q);
                return parseSearch(html);
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
            if (error != null) statusText.setText("搜索失败：" + error);
            else if (items.isEmpty()) statusText.setText("没有找到结果。assrt.net 页面变化时可能需要调整解析规则。");
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
                publishProgress("下载字幕文件...");
                DownloadedFile file = downloadToSubtitleFolder(downloadUrl, safeName(item.title));
                if (file.file != null && file.file.getName().toLowerCase(Locale.US).endsWith(".zip")) {
                    int count = unzipSubtitles(file.file);
                    if (count > 0) return "已下载并解压 " + count + " 个字幕：" + subtitleDir().getAbsolutePath();
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
            boolean detail = lower.contains("/sub/") && (lower.contains("id=") || lower.matches(".*/sub/\\d+.*"));
            boolean direct = looksLikeDownload(lower);
            if (!detail && !direct) continue;
            if (containsUrl(out, abs)) continue;
            out.add(new SubtitleItem(text, abs));
        }
        return out;
    }

    private static String findDownloadUrl(String html) {
        Pattern a = Pattern.compile("<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = a.matcher(html);
        String firstDownload = null;
        while (m.find()) {
            String href = htmlDecode(m.group(1)).trim();
            String text = cleanText(m.group(2)).toLowerCase(Locale.US);
            String abs = absoluteUrl(href);
            String lower = abs.toLowerCase(Locale.US);
            if (looksLikeDownload(lower)) return abs;
            if (firstDownload == null && (lower.contains("download") || text.contains("下载"))) firstDownload = abs;
        }
        return firstDownload;
    }

    private static boolean looksLikeDownload(String url) {
        String lower = url.toLowerCase(Locale.US);
        return lower.contains("/download") || lower.matches(".*\\.(zip|rar|7z|ass|ssa|srt)(\\?.*)?$");
    }

    private static String httpGet(String url) throws IOException { return new String(httpBytes(url).data, "UTF-8"); }

    private static HttpData httpBytes(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV) SubtitleDownloader/1.0");
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml,application/zip,*/*");
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code + "：" + url);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InputStream in = new BufferedInputStream(conn.getInputStream())) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) bos.write(buf, 0, n);
        }
        String name = fileNameFromConnection(conn, url);
        return new HttpData(bos.toByteArray(), name);
    }

    private DownloadedFile downloadToSubtitleFolder(String url, String fallbackName) throws IOException {
        HttpData data = httpBytes(url);
        String name = data.fileName != null ? data.fileName : fallbackName + ".zip";
        name = safeName(name);
        if (!name.matches("(?i).*\\.(zip|rar|7z|ass|ssa|srt)$")) name += ".zip";

        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, name);
            values.put(MediaStore.Downloads.MIME_TYPE, mimeFor(name));
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/subtitle");
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("无法创建下载文件");
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IOException("无法写入下载文件");
                out.write(data.data);
            }
            return new DownloadedFile(null, "Download/subtitle/" + name);
        } else {
            File dir = subtitleDir();
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("无法创建目录：" + dir);
            File file = uniqueFile(dir, name);
            try (FileOutputStream out = new FileOutputStream(file)) { out.write(data.data); }
            return new DownloadedFile(file, file.getAbsolutePath());
        }
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
                String lower = name.toLowerCase(Locale.US);
                if (!(lower.endsWith(".ass") || lower.endsWith(".ssa") || lower.endsWith(".srt"))) continue;
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
        HttpData(byte[] data, String fileName) { this.data = data; this.fileName = fileName; }
    }

    private static class DownloadedFile {
        final File file;
        final String displayPath;
        DownloadedFile(File file, String displayPath) { this.file = file; this.displayPath = displayPath; }
    }
}
