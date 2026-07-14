# GitHub Actions 手动触发修改报告

## 背景

用户担心每次 push 自动触发 GitHub Actions 构建，消耗账户 Actions 分钟数。

## 修改

修改文件：

- `.github/workflows/android.yml`

旧配置：

```yaml
on:
  push:
    branches: [ main, master ]
  pull_request:
    branches: [ main, master ]
  workflow_dispatch:
```

新配置：

```yaml
on:
  workflow_dispatch:
```

## 效果

后续 Android CI 不会再因 push 或 pull_request 自动运行，只能在 GitHub Actions 页面手动点击运行。

注意：提交该修改本身的这一次 push，GitHub 是否触发取决于 GitHub 对工作流变更的处理时机；之后应只支持手动触发。
