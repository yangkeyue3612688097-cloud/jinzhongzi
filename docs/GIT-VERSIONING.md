# Git 仓库与持续版本更新操作手册

本手册用于把项目上传到 Gitee 或 GitHub，并在比赛期间持续保存和发布新版本。

## 1. 三种工具分别负责什么

| 工具 | 作用 | 推荐用途 |
| --- | --- | --- |
| 本地 Git 仓库 | 保存每次修改历史 | 日常开发、回退、比较改动 |
| Gitee 或 GitHub | 远程备份和团队共享 | 给队友、老师或评委查看 |
| Work Budding | 比赛项目协作和材料归档 | 提交阶段成果、评审材料 |

不要把 Work Budding 当代码仓库主库。代码与版本历史应以 Git 远程仓库为准，再定期把源码包和评审材料上传到 Work Budding。

推荐选择：

- 国内网络访问不稳定时优先使用 Gitee。
- 需要展示国际化协作或后续开源时使用 GitHub。
- 比赛期间仓库先设为“私有”，提交截止后再按比赛要求决定是否公开。

## 2. 当前本地仓库位置

本机已经准备好：

```text
D:\ai_block_project\git-repo
```

打开 PowerShell 后进入仓库：

```powershell
cd D:\ai_block_project\git-repo
```

检查当前状态：

```powershell
git status
git log --oneline --decorate -5
git tag
```

## 3. 在 Gitee 创建远程仓库

1. 登录 Gitee，点击右上角 `+`，选择“新建仓库”。
2. 仓库名称填写 `ai-block-project`。
3. 路径填写 `ai-block-project`。
4. 仓库介绍填写“AI 创作来源存证、协作确权与收益分账平台”。
5. 可见性先选“私有”。
6. 不要勾选“使用 Readme 文件初始化仓库”。
7. 不要添加 `.gitignore` 或许可证，本地仓库已经准备好。
8. 创建后复制 HTTPS 仓库地址，例如：

```text
https://gitee.com/你的用户名/ai-block-project.git
```

## 4. 第一次推送到 Gitee

在 `D:\ai_block_project\git-repo` 执行：

```powershell
git remote add origin https://gitee.com/你的用户名/ai-block-project.git
git push -u origin main
git push origin --tags
```

第一次推送会弹出登录窗口。推荐使用 Gitee 私人令牌，不要直接把账号密码写进远程地址或项目文件。

验证远程地址：

```powershell
git remote -v
```

以后如果仓库地址写错，可修改：

```powershell
git remote set-url origin 新的仓库地址
```

## 5. 使用 GitHub 的替代步骤

1. 登录 GitHub，打开 `New repository`。
2. 仓库名填写 `ai-block-project`。
3. 选择 `Private`。
4. 不要初始化 README、`.gitignore` 或 License。
5. 创建后将 HTTPS 地址添加为 `origin`。

首次认证建议安装 Git Credential Manager 并使用浏览器登录，或者创建 Fine-grained Personal Access Token。不要把令牌粘贴到源码、README 或聊天截图中。

## 6. 每次开发后的标准更新流程

先拉取远程变化：

```powershell
git pull --rebase
```

查看修改：

```powershell
git status
git diff
```

提交并推送：

```powershell
git add -A
git commit -m "feat: 增加某功能"
git push
```

常用提交前缀：

| 前缀 | 含义 | 示例 |
| --- | --- | --- |
| `feat` | 新功能 | `feat: 增加协议反提案状态` |
| `fix` | 修复问题 | `fix: 修复重复分账校验` |
| `docs` | 文档修改 | `docs: 补充演示录屏步骤` |
| `test` | 测试 | `test: 增加版本哈希测试` |
| `refactor` | 不改变行为的重构 | `refactor: 拆分存证构建器` |
| `chore` | 配置和维护 | `chore: 更新依赖版本` |

一次提交只处理一个主题。不要使用“修改一下”“最终版”“真的最终版”这类无法说明内容的提交信息。

## 7. 发布比赛可演示版本

版本号从当前 `1.0.0` 开始。增加可演示功能时用次版本，例如 `1.1.0`；只修复缺陷时用修订版本，例如 `1.1.1`。

发布前按顺序操作：

1. 更新 `CHANGELOG.md`，写清楚本版新增、修复和已知限制。
2. 更新 `VERSION` 文件。
3. 运行与本次改动相关的测试。
4. 提交代码。
5. 运行发布脚本。

示例：发布 `v1.1.0`

```powershell
git add -A
git commit -m "chore: prepare v1.1.0"
.\scripts\release.ps1 -Version 1.1.0
```

脚本会检查工作区是否干净、创建 `v1.1.0` 标签，并推送 `main` 分支和标签。

如果 PowerShell 阻止本地脚本，只在当前窗口临时放开：

```powershell
Set-ExecutionPolicy -Scope Process Bypass
```

## 8. 比赛期间的推荐分支方法

只使用 `main` 也能工作，但为了安全和清楚，推荐：

```powershell
git switch -c feature/name-of-change
```

完成后：

```powershell
git add -A
git commit -m "feat: 完成某功能"
git push -u origin feature/name-of-change
```

然后在 Gitee 或 GitHub 网页发起合并请求。测试通过后再合并回 `main`。

比赛演示前一天不要再把未测试的大功能直接提交到 `main`。应先在分支验证，再合并和打标签。

## 9. 提交前安全检查

以下内容绝不能提交：

- `.env`、数据库密码、JWT 密钥、FISCO 私钥。
- SDK 证书、`keystore`、`.pem`、`.key`、`.p12`。
- `node_modules/`、`target/`、`dist/`、Hardhat `artifacts/` 和 `cache/`。
- 上传图片、运行日志、PID、临时截图和发布压缩包。

提交前执行：

```powershell
git status
git diff --cached
```

如果误提交密钥：

1. 立即在对应平台作废并重新生成密钥。
2. 不要只删除文件后再提交，因为旧提交历史仍保留内容。
3. 重新建立干净历史或使用 Git 历史清理工具处理。

## 10. 录屏演示顺序

录屏时按以下顺序展示：

1. 打开 Gitee/GitHub 仓库首页，说明这是项目的远程版本库。
2. 打开“提交记录”，展示多个有意义的提交。
3. 打开“标签/Releases”，展示 `v1.0.0` 比赛版本。
4. 在本地运行 `git status`，说明工作区是否干净。
5. 修改 `CHANGELOG.md` 中一条演示文字。
6. 运行 `git diff` 展示修改。
7. 执行 `git add`、`git commit`、`git push`。
8. 回到网页刷新，展示新提交已经出现。

