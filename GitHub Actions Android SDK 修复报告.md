# GitHub Actions Android SDK 修复报告

## 问题

手动构建曾失败：

```text
sdkmanager: command not found
```

后续又在 `android-actions/setup-android@v3` 阶段失败：

```text
Warning: An error occurred while preparing SDK package Android SDK Tools: Error on ZipFile unknown archive.
Error: The process '/usr/local/lib/android/sdk/cmdline-tools/16.0/bin/sdkmanager' failed with exit code 1
```

失败发生在 Action 自动准备 `Android SDK Tools` 时，属于 SDK 包下载/解压或 runner 环境中旧 SDK Tools 包不稳定导致的 CI 环境问题。

## 修复

修改 `.github/workflows/android.yml`，不再使用 `android-actions/setup-android@v3` 自动安装 SDK Tools，改为直接使用 GitHub Ubuntu runner 预置的 Android SDK 路径：

```yaml
- name: Configure Android SDK paths
  run: |
    SDK_ROOT="${ANDROID_HOME:-/usr/local/lib/android/sdk}"
    echo "ANDROID_HOME=$SDK_ROOT" >> "$GITHUB_ENV"
    echo "ANDROID_SDK_ROOT=$SDK_ROOT" >> "$GITHUB_ENV"
    echo "$SDK_ROOT/cmdline-tools/latest/bin" >> "$GITHUB_PATH"
    echo "$SDK_ROOT/platform-tools" >> "$GITHUB_PATH"

- name: Install Android NDK and CMake
  run: |
    SDK_ROOT="${ANDROID_HOME:-/usr/local/lib/android/sdk}"
    SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
    if [ ! -x "$SDKMANAGER" ]; then
      SDKMANAGER="$SDK_ROOT/cmdline-tools/16.0/bin/sdkmanager"
    fi
    yes | "$SDKMANAGER" --licenses
    "$SDKMANAGER" "platforms;android-32" "build-tools;32.0.0" "ndk;21.4.7075529" "cmake;3.22.1"
```

同时显式安装项目需要的：

- `platforms;android-32`
- `build-tools;32.0.0`
- `ndk;21.4.7075529`
- `cmake;3.22.1`

## 说明

Workflow 仍然只支持 `workflow_dispatch` 手动触发，不会因 push 自动运行。

## Changed Files

- `.github/workflows/android.yml`
- `GitHub Actions Android SDK 修复报告.md`
