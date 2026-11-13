# Work Budding 项目上传说明

> 适用对象：将本项目放入 Work Budding 的协作项目空间，供团队成员、指导老师或评委查看。

## 1. 推荐结构

在项目的“资产”中新建一个文件夹：

```text
ai_block_project-v1.0/
├── 00-WORK-BUDDING-README.md
├── docs/
├── frontend/
├── backend/
├── contracts/
├── scripts/
└── demo-assets/
```

对应关系：

| Work Budding 目录 | 本项目来源 |
| --- | --- |
| `docs/` | `docs/` 和后端 `docs/` |
| `frontend/` | 前端源码，不包含 `node_modules/` 和 `dist/` |
| `backend/` | `.staging/backend/` 源码、配置模板和 SQL |
| `contracts/` | 合约源码、ABI、Hardhat 配置和测试 |
| `scripts/` | ComfyUI 采集、演示和验证脚本 |
| `demo-assets/` | 证书、验证页截图和演示资料 |

## 2. 上传步骤

1. 进入“项目 / 金种子大赛 / 资产”。
2. 点击“新建文件夹”，建立 `ai_block_project-v1.0`。
3. 进入该文件夹，上传 `ai_block_project-work-budding-source-20261031.zip`。
4. 新建 `demo-assets` 文件夹，上传 PDF 证书和验证页截图。
5. 单独上传 `docs/COMPETITION-READINESS.md` 和 `docs/PROJECT-FILE-INDEX.md`，方便成员不用解压就能阅读。
6. 在项目“项目配置 / 指令”中粘贴
   `docs/WORK-BUDDING-PROJECT-INSTRUCTIONS.md` 的内容。
7. 上传完成后点击压缩包，确认可以下载并解压。

## 3. 不要上传

- `node_modules/`
- `target/`
- `dist/`
- `.git/`
- Hardhat `artifacts/` 和 `cache/`
- 数据库密码、私钥、SDK 证书和 `.env`
- 运行时日志、PID 文件和临时截图

## 4. 比赛口径

项目当前状态必须以 `docs/COMPETITION-READINESS.md` 为准：

- 本地业务闭环已经实现。
- `BusinessAnchor` 合约和 FISCO 适配器已经准备并测试。
- 后端当前为 `mode: pending`。
- 尚没有真实 FISCO 联盟链交易回执。
- 不得表述为“已经完成上链”。
