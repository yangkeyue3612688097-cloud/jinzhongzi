# AIGC 可信存证与协作分润系统 · 智能合约层

大创项目《基于区块链的 AI 可信执行存证与协作分润系统设计与实现》的合约实现（v0.3）。
替换原 v0.1 合约，修正了「公式没上链 / 分润跑不通 / 一次性结清 / push 转账 / 无异议机制」五处硬伤。

**要当场演示，直接用 `remix/AICreationSystem_Remix.sol`**：1363 行单文件，整份粘进 Remix 即可编译部署，
不依赖多文件 import。部署步骤与参数见 `docs/部署与交付清单.md`。

## 快速开始

```bash
npm install
npx hardhat compile
npx hardhat test                      # 10 项测试
npx hardhat run scripts/demo-e2e.js   # 全链路演示（可录屏）
npx hardhat run scripts/gas-report.js # Gas 报告（写报告用）
npx hardhat run scripts/deploy.js     # 部署，地址写入 deployments/
```

## 目录

```
contracts/
  base/Security.sol              重入锁 / 权限（admin+arbiter）/ 暂停，零外部依赖
  ContributionMath.sol           CI 公式的链上定点实现（S1、S2、CI、按 CI 切分）
  AICreationEvidence.sol         存证：三层锚定 + 时序哈希链 + 角色 + 状态机 + 纠纷
  AICreationProfitSplit.sol      分润：量化上链 + 份额快照 + 收益托管 + 多轮结算
test/
  CoreFlow.test.js               核心链路 9 项（含公式交叉验证、精度、权限、纠纷）
  ScoringIntegration.test.js     评分引擎 → 合约 联调
scripts/
  deploy.js                      部署（含 setSettler 授权）
  demo-e2e.js                    12 步全链路演示
  scoring-engine.js              贡献评分引擎（七项指标的规则实现 + 可解释依据）
  hash-utils.js                  哈希口径工具（前后端共用）
  gas-report.js                  Gas 消耗报告
docs/
  设计说明与逻辑修正.md          v0.1 问题清单、新模型、公式落链细节、答辩对应表
  前后端对接指南.md              给前端/后端的调用顺序、事件、错误码
  部署与交付清单.md              部署步骤、参数填法、revert 对照、Gas、录屏脚本
remix/
  AICreationSystem_Remix.sol     单文件版，粘进 Remix 直接部署
```

## 两个合约的分工

- **AICreationEvidence**：谁、在什么时候、做了什么、内容和链接对不对。只认哈希，不管钱。
- **AICreationProfitSplit**：多少钱、按什么比例、分给谁、什么时候能提。只认份额表，不管证据细节。

作品状态只存在存证合约，分润合约读它，避免两套状态不一致。

## 核心接口一览

| 阶段 | 调用 | 谁可以调 |
|---|---|---|
| 建作品 | `createWork(inputRoot, metadataURI)` | 任何人 |
| 登记协作 | `addContributor(workId, account, role)` | 创作者 / 平台 |
| 确认角色 | `acceptRole(workId)` | 被登记人 |
| 存证 | `addEvidence(workId, etype, dataHash, uri)` | 已确认的协作者 |
| 定稿 | `finalizeWork(workId, resultHash, uri)` | 创作者 / 平台 |
| 量化上链 | `lockShares(workId, scores, sheetHash)` | 评分方 / 平台 |
| 收益入账 | `depositRevenue(workId)` payable | 任何人 |
| 结算 | `settle(workId)` | 任何人 |
| 提现 | `withdraw()` | 收益人 |
| 纠纷 | `raiseDispute` / `resolveDispute` | 协作者 / 仲裁员 |

## 版本

- v0.1 旧合约（`智能合约.zip` 里的两个文件）：仅存证骨架 + 一次性转账，公式缺失、分润必 revert。
- v0.2 本仓库：公式上链、托管多轮分润、Pull 支付、异议仲裁、时序链真链式、评分引擎闭环。

## 注意

- Solidity `0.8.24`，EVM `paris`，**零外部依赖**（没引 OpenZeppelin），
  Remix 直接粘贴即可编译，迁移到 FISCO BCOS 只需替换 payable/转账部分。
- 部署后务必执行 `evidence.setSettler(splitAddress, true)`。
- 本机 Node 22 + Hardhat 2.22 的 EDR 原生模块在此环境加载失败，已固定到 Hardhat `2.19.5`。
