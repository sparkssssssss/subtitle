# GitHub Actions JDK17 修复报告

## 问题

`android-actions/setup-android@v3` 下载了新版 Android commandline-tools：

```text
cmdline-tools/16.0
```

该版本要求：

```text
JDK 17 or later
```

但 workflow 原来使用 JDK 11，导致失败：

```text
This tool requires JDK 17 or later. Your version was detected as 11.0.31.
```

## 修复

将 `.github/workflows/android.yml` 中的 JDK 从 11 改为 17：

```yaml
- name: Set up JDK 17
  uses: actions/setup-java@v4
  with:
    distribution: temurin
    java-version: '17'
    cache: gradle
```

## 说明

Gradle 7.5 / Android Gradle Plugin 7.2.2 可在 JDK 17 下运行。Workflow 仍然只支持手动触发。
