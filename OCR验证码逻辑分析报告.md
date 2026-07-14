# OCR 验证码逻辑分析报告

## Overview

分析文件：

- `app/src/main/java/com/example/subtitledownloader/MainActivity.java`

当前 OCR 流程：

1. `showCaptchaDialog()` 显示 zimuku 验证码弹窗和手动输入框；
2. 弹窗显示后调用 `requestOcr(challenge.imageBase64, callback)`；
3. `requestOcr()` 将 base64 文本 POST 到 `https://ddd.112114.xyz/ocr/b64/json`；
4. 响应 JSON 中 `status == 200` 时取 `result.trim()`；
5. OCR 有结果后先做格式归一化和可信度校验；
6. 只有验证码格式可信时才填入输入框并自动提交；
7. OCR 失败、为空或格式不可信时保留原手动输入逻辑。

## 主要结论

原逻辑的问题是“只要 OCR 返回非空结果就自动提交”，误识别时会导致弹窗马上关闭并提交错误验证码，用户体验类似验证码循环。

现已改为“格式可信才自动提交”。

## 已实施修复

### 1. OCR 结果格式过滤

新增：

```java
private static String normalizeCaptchaCode(String raw) {
    if (raw == null) return "";
    return raw.trim().replaceAll("[^A-Za-z0-9]", "");
}

private static boolean isTrustedCaptchaCode(String code) {
    return code != null && code.matches("[A-Za-z0-9]{4,6}");
}
```

现在 OCR 返回会先过滤非字母数字字符，并且只有 4 到 6 位字母数字才认为可信。

### 2. 自动提交增加可信度门槛

原逻辑：OCR 非空就自动提交。

新逻辑：

```java
String code = normalizeCaptchaCode(result);
if (isTrustedCaptchaCode(code)) {
    input.setText(code);
    input.setSelection(input.getText().length());

    if (dialog.isShowing()) {
        dialog.dismiss();
        new CaptchaTask().execute(code, challenge.sourceUrl);
    }
} else {
    Log.d("OCR", "OCR结果格式不可信，等待用户手动输入: " + result);
}
```

效果：OCR 不可信时不会关闭弹窗，也不会自动提交，用户仍可手动输入。

### 3. OCR HTTP 错误流空指针防护

对 `connection.getErrorStream()` 返回 null 的情况增加处理：

```java
if (inputStream == null) {
    errorMsg = "OCR HTTP " + responseCode;
    return "";
}
```

避免非 2xx 且无错误流时进入空指针异常。

## 剩余风险

- 如果 zimuku 验证码实际长度不是 4 到 6 位，需要调整 `isTrustedCaptchaCode()` 的长度范围。
- OCR 结果虽然格式可信，但仍可能识别错误；这种情况下仍会自动提交并可能再次弹验证码。
- 第三方 OCR 服务不可用时会回退手动输入，不影响基础流程。

## Changed Files

- `app/src/main/java/com/example/subtitledownloader/MainActivity.java`
- `OCR验证码逻辑分析报告.md`
