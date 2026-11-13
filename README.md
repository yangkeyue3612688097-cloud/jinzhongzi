# AI 创作存证与协作分润平台

面向 AI 创作者及 2-10 人创作团队，提供作品来源存证、版本校验、协作确权、协商分润、收入结算和公开验证。

项目不只记录最终文件，还把本地生成参数、工作流哈希、文件指纹、创作者签名、作品版本、协作协议、收入事件和结算凭证组成一条可复核的责任链。

## 当前状态

- 本地业务闭环已经实现，前端 + 后端 + 本地链 + 数据库已打通成一条可运行的产品链。
- 后端支持 `app.chain.mode: evm`，通过 web3j 直连本地 Hardhat 链，真发交易、真等回执、真出块。
- 作品存证锚定、协作协议、收入事件、分账结算凭证均已实测通过。
- 已提供 Windows 一键启动脚本 `scripts/start-local.bat`（MySQL + Hardhat + 后端 + 前端 + ComfyUI 五件套）。
- 当前签名由平台托管 RSA 密钥完成，不是用户独立钱包签名。
- 分润比例由协作者协商并逐人确认，系统只校验合计为 100%，不会由算法强制决定。
- 本地链为开发链（Hardhat 内存链，重启后清空），生产需换持久化节点。

比赛口径以 [docs/COMPETITION-READINESS.md](docs/COMPETITION-READINESS.md) 为准。

## 仓库结构

```text
.
├── backend/       Spring Boot 业务后端、SQL 和链适配器
├── contracts/     Solidity 合约、Hardhat 测试和部署脚本
├── demo-assets/   证书与验证页截图
├── docs/          产品、比赛、演示和 Git 协作文档
├── frontend/      Vue 3 + Vite 前端
└── scripts/       ComfyUI 采集、联调和本地运行脚本
```

## 本地运行

### 一键启动（Windows）

双击 `scripts/start-local.bat`，自动依次拉起 MySQL(3307) → Hardhat 链(8545) → 后端(8080) → 前端(5173) → ComfyUI(8188)，并做就绪等待与健康检查。

- 首次使用请先 `copy friend-project/.env.example friend-project/.env` 并填入本机数据库密码等值。
- 演示账号：`demo / 123456`（主）、`partner / 123456`（协作者）。
- ComfyUI 为 CPU 模式，首张图约 15~30 秒。

### 环境要求

- JDK 17
- Maven 3.9+
- Node.js 20+
- MySQL 8
- Python 3.10+（仅在运行 ComfyUI 采集器时需要）

### 环境变量

不要把真实密码或私钥写入源码。启动后端前在 PowerShell 中设置：

```powershell
$env:DB_URL="jdbc:mysql://localhost:3307/aigc_evidence?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="你的数据库密码"
$env:JWT_SECRET="至少32位随机字符串"
$env:FISCO_PRIVATE_KEY="合作方提供的测试私钥"
$env:FISCO_BUSINESS_ANCHOR_ADDRESS="已部署的合约地址"
```

当前未接入真实链时，不需要设置 `FISCO_PRIVATE_KEY` 和 `FISCO_BUSINESS_ANCHOR_ADDRESS`。

### 启动前端

```powershell
cd frontend
npm install
npm run dev
```

前端默认访问 `http://127.0.0.1:5173`。

### 启动后端

```powershell
cd backend
mvn spring-boot:run
```

后端默认访问 `http://127.0.0.1:8080/api`。

### 运行合约测试

```powershell
cd contracts
npm install
npx hardhat test
```

## Git 版本管理

第一次推送、日常提交和创建版本标签的完整步骤见：

- [docs/GIT-VERSIONING.md](docs/GIT-VERSIONING.md)
- [CHANGELOG.md](CHANGELOG.md)

已配置 `.gitignore`，不会提交 `node_modules`、`target`、`dist`、Hardhat `artifacts/cache`、日志、PID、上传文件、压缩包和本地密钥。

## 重要边界

- 区块链只锚定摘要和时间，不保存原图、完整 prompt 或个人信息。
- 区块链不自动转账，也不判定版权归属。
- 当前分账是业务记账和结算凭证，不宣称已完成真实银行打款。
- ComfyUI 是来源参数采集端，不是本项目的绘图平台竞争点。

