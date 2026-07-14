# GitHub Actions Android SDK 修复报告

## 问题

手动构建失败：

```text
sdkmanager: command not found
```

原因是 GitHub runner 当前没有将 Android SDK commandline-tools 的 `sdkmanager` 加入 PATH。

## 修复

修改 `.github/workflows/android.yml`，在安装 NDK/CMake 前增加：

```yaml
- name: Set up Android SDK
  uses: android-actions/setup-android@v3
```

然后再执行：

```bash
yes | sdkmanager --licenses
sdkmanager "ndk;21.4.7075529" "cmake;3.22.1"
```

## 说明

Workflow 仍然只支持 `workflow_dispatch` 手动触发，不会因 push 自动运行。
