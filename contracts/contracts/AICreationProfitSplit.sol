// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import "./base/Security.sol";
import "./ContributionMath.sol";
import "./AICreationEvidence.sol";

/**
 * @title  AICreationProfitSplit
 * @notice 协作贡献量化 + 收益托管 + 自动分润合约
 *
 * @dev    与旧版（v0.1）的核心差异，也是本项目"逻辑修正"的关键：
 *
 *         ① 旧版把"贡献权重"当参数由调用方随便传，链上根本没有公式 → 已经失去"可复核"的意义。
 *            本版把申请书里的 CI = W1·S1 + W2·S2 + W3·S3 真正落到链上计算（ContributionMath），
 *            权重、α、λ、各项得分全部留痕，任何人可复算。
 *
 *         ② 旧版分润是一次性结清（workProfitSettled），而 AIGC 作品是"多次授权 / 多次变现"的，
 *            一次结清会导致第二次收入无处可去。本版改为「收益托管 → 按份额表多次结算」，
 *            同一份额表可服务无限轮收益（版税模型），符合真实商业场景。
 *
 *         ③ 旧版用 push 直接转账，一个地址失败（合约钱包 / 黑名单 / gas 不足）整批回滚。
 *            本版用 pull 记账 + 主动提取，付款方永远能成功，收款方各自领取。
 *
 *         ④ 旧版没有任何"分配方案确认"环节，平台可以随便定比例。本版加了
 *            份额锁定 → 公示异议期 → 协作者确认 / 仲裁，对应"异常协作与纠纷处理机制"。
 *
 *         ⑤ 旧版 distributeProfit 里调用 evidence.setWorkStatus 会必然失败（分润合约不是
 *            evidence 的 admin），整笔分润直接 revert。本版通过 isSettler 白名单解决。
 */
contract AICreationProfitSplit is ReentrancyGuard, PauseGuard {
    using ContributionMath for uint256;

    /* ------------------------------------------------------------------ */
    /*                              常量与类型                              */
    /* ------------------------------------------------------------------ */

    uint16 public constant MAX_SHARE_HOLDERS = 20;
    uint256 private constant BPS = 10_000;

    /// @notice 贡献量化全局参数（管理员可调，但受申请书区间约束）
    struct Params {
        uint16 w1;             // 核心权重维度权重（0.5~0.6）
        uint16 w2;             // 过程投入维度权重（0.2~0.3）
        uint16 w3;             // 价值落地维度权重（0.1~0.2）
        uint16 w11;            // 提示词质量权重（W12 > W11）
        uint16 w12;            // 版本采纳权重
        uint16 w21;            // 操作记录完整性权重
        uint16 w22;            // 有效迭代次数权重
        uint16 platformFeeBps; // 平台服务费（≤ 10%）
        uint32 challengePeriod;// 份额公示异议期（秒）
    }

    /// @notice 单个协作者的评分输入（链下评分引擎产出，平台上链）
    struct ScoreInput {
        address account;
        uint16 s11;   // 提示词质量得分 S11
        uint16 s12;   // 版本采纳得分 S12
        uint16 alpha; // 版本采纳因子 α
        uint16 s21;   // 操作记录完整性 S21
        uint16 s22;   // 有效迭代次数 S22
        uint16 lambda;// 时序衰减系数 λ
        uint16 s3;    // 价值落地得分 S3
    }

    /// @notice 份额表快照（锁定后不可改，除非走争议重算）
    struct ShareSheet {
        uint8 version;
        bytes32 sheetHash;        // 链下评分明细 JSON 的哈希（可审计"为什么是这个分"）
        uint64 lockedAt;
        uint64 objectionDeadline; // 异议期截止
        bool objected;            // 是否被提出异议（冻结结算）
        uint16 consents;          // 已确认份额的协作者数量
        uint256 totalCi;          // ΣCI
        address[] accounts;
        uint256[] ci;
    }

    /* ------------------------------------------------------------------ */
    /*                                存储                                  */
    /* ------------------------------------------------------------------ */

    AICreationEvidence public immutable evidence;

    Params public params;

    /// @notice 评分上链方（链下贡献计算服务 / 平台运营），与 admin 分离便于审计
    address public scorer;
    /// @notice 平台服务费收款地址
    address public platformWallet;

    mapping(uint256 => ShareSheet) internal _sheets;
    mapping(uint256 => mapping(address => bool)) public hasConsented;

    mapping(uint256 => uint256) public totalDeposited;
    mapping(uint256 => uint256) public totalSettled;
    mapping(uint256 => uint32) public settleRound;
    /// @notice 全平台累计结算额（数据看板展示用）
    uint256 public totalRevenueAll;

    mapping(address => uint256) public pendingWithdrawals;

    /* ------------------------------------------------------------------ */
    /*                                事件                                  */
    /* ------------------------------------------------------------------ */

    event ParamsUpdated(Params params);
    event ScorerUpdated(address indexed scorer);
    event PlatformWalletUpdated(address indexed wallet);

    event SharesLocked(
        uint256 indexed workId,
        uint8 version,
        bytes32 sheetHash,
        address[] accounts,
        uint256[] ci,
        uint256 totalCi,
        uint64 objectionDeadline
    );

    event SharesConsented(uint256 indexed workId, address indexed account, uint16 consents);
    event SharesObjected(uint256 indexed workId, address indexed account, string reason);
    event ObjectionResolved(uint256 indexed workId, bool dismissed, address indexed by);

    event RevenueDeposited(
        uint256 indexed workId,
        address indexed payer,
        uint256 amount,
        uint256 escrow
    );

    event RevenueSettled(
        uint256 indexed workId,
        uint32 round,
        uint256 gross,
        uint256 platformFee,
        uint256 net,
        uint256 totalCi
    );

    event ShareCredited(uint256 indexed workId, address indexed account, uint256 ci, uint256 amount);
    event Withdrawn(address indexed account, uint256 amount);

    /* ------------------------------------------------------------------ */
    /*                                错误                                  */
    /* ------------------------------------------------------------------ */

    error NotScorer();
    error SheetNotLocked();
    error SheetAlreadyLocked();
    error InvalidWorkStatus();
    error NotListedContributor();
    error DuplicateAccount();
    error TooManyHolders();
    error ZeroTotalContribution();
    error ZeroAmount();
    error NothingToSettle();
    error ObjectionPending();
    error ObjectionWindowOpen();
    error AlreadyConsented();
    error NotInObjectionWindow();
    error NoObjection();
    error NothingToWithdraw();
    error TransferFailed();
    error InvalidParams();

    constructor(
        address evidenceAddr,
        address _admin,
        address _arbiter,
        address _platformWallet
    ) AdminControl(_admin, _arbiter) {
        if (evidenceAddr == address(0)) revert ZeroAddress();
        if (_platformWallet == address(0)) revert ZeroAddress();
        evidence = AICreationEvidence(evidenceAddr);
        platformWallet = _platformWallet;
        scorer = _admin;

        // 默认参数：W1=0.55, W2=0.25, W3=0.20；W11=0.4, W12=0.6；W21=0.5, W22=0.5
        params = Params({
            w1: 5500,
            w2: 2500,
            w3: 2000,
            w11: 4000,
            w12: 6000,
            w21: 5000,
            w22: 5000,
            platformFeeBps: 500,      // 5% 平台服务费
            challengePeriod: 3 days
        });
    }

    modifier onlyScorer() {
        if (msg.sender != scorer && msg.sender != admin) revert NotScorer();
        _;
    }

    /* ------------------------------------------------------------------ */
    /*                              参数管理                                */
    /* ------------------------------------------------------------------ */

    /**
     * @notice 更新贡献量化参数（受申请书约束区间校验）
     */
    function setParams(Params calldata p) external onlyAdmin {
        if (p.platformFeeBps > 1000) revert InvalidParams(); // 服务费上限 10%
        if (p.challengePeriod > 30 days) revert InvalidParams();
        ContributionMath.validateDimensionWeights(p.w1, p.w2, p.w3);
        ContributionMath.validateCoreWeights(p.w11, p.w12);
        ContributionMath.validateProcessWeights(p.w21, p.w22);

        params = p;
        emit ParamsUpdated(p);
    }

    function setScorer(address newScorer) external onlyAdmin {
        if (newScorer == address(0)) revert ZeroAddress();
        scorer = newScorer;
        emit ScorerUpdated(newScorer);
    }

    function setPlatformWallet(address newWallet) external onlyAdmin {
        if (newWallet == address(0)) revert ZeroAddress();
        platformWallet = newWallet;
        emit PlatformWalletUpdated(newWallet);
    }

    /* ------------------------------------------------------------------ */
    /*                          贡献量化与份额锁定                          */
    /* ------------------------------------------------------------------ */

    /**
     * @notice 把链下评分结果按 CI 公式复算后锁上链，形成分润份额表
     * @param workId    作品编号（必须已定稿）
     * @param scores    每个协作者的七项原始指标（S11,S12,α,S21,S22,λ,S3）
     * @param sheetHash 链下评分明细 JSON 的哈希，用于事后复核评分依据
     *
     * 计算过程（所有人可在链上复算，也可在链下用同一公式验证）：
     *   S1 = α·(W11·S11 + W12·S12)
     *   S2 = λ·(W21·S21 + W22·S22)
     *   CI = W1·S1 + W2·S2 + W3·S3
     *   分润比例 = CI_i / ΣCI
     */
    function lockShares(
        uint256 workId,
        ScoreInput[] calldata scores,
        bytes32 sheetHash
    ) external onlyScorer whenNotPaused {
        AICreationEvidence.Work memory w = evidence.getWork(workId);
        if (w.status != AICreationEvidence.WorkStatus.Finalized) revert InvalidWorkStatus();
        if (scores.length == 0) revert ZeroAmount();
        if (scores.length > MAX_SHARE_HOLDERS) revert TooManyHolders();

        ShareSheet storage sheet = _sheets[workId];
        // 首次结算前允许修订（版本+1）；一旦产生过结算，份额表永久冻结
        if (sheet.version > 0 && settleRound[workId] > 0) revert SheetAlreadyLocked();

        Params memory p = params;
        address[] memory accounts = new address[](scores.length);
        uint256[] memory cis = new uint256[](scores.length);
        uint256 totalCi;

        for (uint256 i = 0; i < scores.length; ++i) {
            ScoreInput calldata s = scores[i];
            if (!evidence.isActiveContributor(workId, s.account)) revert NotListedContributor();
            for (uint256 j = 0; j < i; ++j) {
                if (accounts[j] == s.account) revert DuplicateAccount();
            }

            uint256 s1 = ContributionMath.coreScore(s.alpha, p.w11, s.s11, p.w12, s.s12);
            uint256 s2 = ContributionMath.processScore(s.lambda, p.w21, s.s21, p.w22, s.s22);
            uint256 ci = ContributionMath.compositeIndex(p.w1, s1, p.w2, s2, p.w3, s.s3);

            accounts[i] = s.account;
            cis[i] = ci;
            totalCi += ci;
        }
        if (totalCi == 0) revert ZeroTotalContribution();

        // 覆盖旧版本（若存在）
        delete sheet.accounts;
        delete sheet.ci;
        for (uint256 i = 0; i < accounts.length; ++i) {
            sheet.accounts.push(accounts[i]);
            sheet.ci.push(cis[i]);
        }
        sheet.version += 1;
        sheet.sheetHash = sheetHash;
        sheet.lockedAt = uint64(block.timestamp);
        sheet.objectionDeadline = uint64(block.timestamp + p.challengePeriod);
        sheet.objected = false;
        sheet.consents = 0;
        sheet.totalCi = totalCi;

        emit SharesLocked(
            workId,
            sheet.version,
            sheetHash,
            accounts,
            cis,
            totalCi,
            sheet.objectionDeadline
        );
    }

    /// @notice 协作者确认份额表（全部确认可跳过异议期，立即结算）
    function consentShares(uint256 workId) external {
        ShareSheet storage sheet = _sheets[workId];
        if (sheet.version == 0) revert SheetNotLocked();
        if (!_listed(sheet, msg.sender)) revert NotListedContributor();
        if (hasConsented[workId][msg.sender]) revert AlreadyConsented();

        hasConsented[workId][msg.sender] = true;
        sheet.consents += 1;
        emit SharesConsented(workId, msg.sender, sheet.consents);
    }

    /// @notice 协作者对份额表提出异议（异议期内有效），冻结结算直至仲裁
    function objectShares(uint256 workId, string calldata reason) external {
        ShareSheet storage sheet = _sheets[workId];
        if (sheet.version == 0) revert SheetNotLocked();
        if (!_listed(sheet, msg.sender)) revert NotListedContributor();
        if (block.timestamp > sheet.objectionDeadline) revert NotInObjectionWindow();

        sheet.objected = true;
        emit SharesObjected(workId, msg.sender, reason);
    }

    /// @notice 仲裁员处理异议：dismissed=true 驳回异议（解除冻结）；false 维持冻结待重算
    function resolveObjection(uint256 workId, bool dismissed) external onlyArbiter {
        ShareSheet storage sheet = _sheets[workId];
        if (!sheet.objected) revert NoObjection();
        if (dismissed) sheet.objected = false;
        emit ObjectionResolved(workId, dismissed, msg.sender);
    }

    /* ------------------------------------------------------------------ */
    /*                          收益托管与分润                              */
    /* ------------------------------------------------------------------ */

    /**
     * @notice 收益进入托管（买家付款 / 平台代收 / 授权费）
     * @dev    托管模式：即使作品处于纠纷冻结，资金仍可安全入账，不会导致买家付款失败
     */
    function depositRevenue(uint256 workId) external payable whenNotPaused {
        if (msg.value == 0) revert ZeroAmount();
        evidence.getWork(workId); // 校验作品存在
        totalDeposited[workId] += msg.value;

        emit RevenueDeposited(workId, msg.sender, msg.value, escrowOf(workId));
    }

    /**
     * @notice 结算托管收益（任何人可调用，付款方 / 平台 / 协作者都能触发）
     * @dev    结算条件：份额表已锁定 + 无待处理异议 + （全员确认 或 异议期已过）
     */
    function settle(uint256 workId) external nonReentrant whenNotPaused {
        ShareSheet storage sheet = _sheets[workId];
        if (sheet.version == 0) revert SheetNotLocked();

        AICreationEvidence.Work memory w = evidence.getWork(workId);
        if (w.status == AICreationEvidence.WorkStatus.Disputed) revert InvalidWorkStatus();
        if (w.status == AICreationEvidence.WorkStatus.Revoked) revert InvalidWorkStatus();
        if (w.status != AICreationEvidence.WorkStatus.Finalized &&
            w.status != AICreationEvidence.WorkStatus.Settled) revert InvalidWorkStatus();

        if (sheet.objected) revert ObjectionPending();
        bool allConsented = sheet.consents >= w.contributorTotal;
        if (!allConsented && block.timestamp < sheet.objectionDeadline) revert ObjectionWindowOpen();

        uint256 gross = escrowOf(workId);
        if (gross == 0) revert NothingToSettle();

        Params memory p = params;
        uint256 fee = (gross * p.platformFeeBps) / BPS;
        uint256 net = gross - fee;

        uint256[] memory cis = sheet.ci; // storage -> memory 拷贝
        uint256[] memory amounts = ContributionMath.allocate(net, cis, sheet.totalCi);

        totalSettled[workId] += gross;
        totalRevenueAll += gross;
        settleRound[workId] += 1;

        if (fee > 0) {
            pendingWithdrawals[platformWallet] += fee;
        }
        for (uint256 i = 0; i < sheet.accounts.length; ++i) {
            if (amounts[i] == 0) continue;
            pendingWithdrawals[sheet.accounts[i]] += amounts[i];
            emit ShareCredited(workId, sheet.accounts[i], cis[i], amounts[i]);
        }

        emit RevenueSettled(workId, settleRound[workId], gross, fee, net, sheet.totalCi);

        // 状态同步：分润合约必须在 evidence 中登记为 settler。
        // 使用 try/catch，即使运营漏配白名单也不会阻塞收益分配（资金安全优先）。
        try evidence.markSettled(workId) {} catch {}
    }

    /* ------------------------------------------------------------------ */
    /*                              提取收益                                */
    /* ------------------------------------------------------------------ */

    /// @notice 提取累计待领取收益（Pull 模式，避免转账失败导致整批回滚）
    function withdraw() external nonReentrant returns (uint256 amount) {
        amount = pendingWithdrawals[msg.sender];
        if (amount == 0) revert NothingToWithdraw();
        pendingWithdrawals[msg.sender] = 0;

        (bool ok, ) = payable(msg.sender).call{value: amount}("");
        if (!ok) revert TransferFailed();

        emit Withdrawn(msg.sender, amount);
    }

    /// @notice 代提给指定地址（例如把收益转给冷钱包）
    function withdrawTo(address to) external nonReentrant returns (uint256 amount) {
        if (to == address(0)) revert ZeroAddress();
        amount = pendingWithdrawals[msg.sender];
        if (amount == 0) revert NothingToWithdraw();
        pendingWithdrawals[msg.sender] = 0;

        (bool ok, ) = payable(to).call{value: amount}("");
        if (!ok) revert TransferFailed();

        emit Withdrawn(msg.sender, amount);
    }

    /* ------------------------------------------------------------------ */
    /*                              查询接口                                */
    /* ------------------------------------------------------------------ */

    function getShareSheet(uint256 workId) external view returns (ShareSheet memory) {
        return _sheets[workId];
    }

    function escrowOf(uint256 workId) public view returns (uint256) {
        return totalDeposited[workId] - totalSettled[workId];
    }

    function isListed(uint256 workId, address account) external view returns (bool) {
        return _listed(_sheets[workId], account);
    }

    /**
     * @notice 试算：这笔托管收益会怎么分（前端"分润预览"直接用这个）
     * @return gross 待分配总额
     * @return fee   平台服务费
     * @return net   协作者可分配净额
     * @return accounts / amounts 每个协作者分到多少
     */
    function previewSettlement(
        uint256 workId
    )
        external
        view
        returns (
            uint256 gross,
            uint256 fee,
            uint256 net,
            address[] memory accounts,
            uint256[] memory amounts
        )
    {
        ShareSheet storage sheet = _sheets[workId];
        gross = escrowOf(workId);
        if (sheet.version == 0 || gross == 0) {
            return (gross, 0, 0, new address[](0), new uint256[](0));
        }
        uint256 f = (gross * params.platformFeeBps) / BPS;
        uint256 n = gross - f;
        uint256[] memory cis = sheet.ci;
        return (gross, f, n, sheet.accounts, ContributionMath.allocate(n, cis, sheet.totalCi));
    }

    /// @dev 判断地址是否在份额表中
    function _listed(ShareSheet storage sheet, address account) internal view returns (bool) {
        for (uint256 i = 0; i < sheet.accounts.length; ++i) {
            if (sheet.accounts[i] == account) return true;
        }
        return false;
    }

    /// @notice 合约需保留接收 ETH 的能力（托管资金）
    receive() external payable {}
}
