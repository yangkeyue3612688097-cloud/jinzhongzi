// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import "./base/Security.sol";

/**
 * @title  AICreationEvidence
 * @notice AI 创作全过程可信存证合约（三层时序哈希链 + 协作角色 + 生命周期状态机 + 纠纷冻结）
 *
 * @dev    设计要点（对应大创申请书 2. 面向 AI 创作过程的区块链可信存证机制研究）：
 *
 *         ① 输入层：createWork 时把「初始提示词 + 创作意图」的哈希作为 inputRoot 上链，
 *            并立即生成第 1 条 Prompt 证据，保证"作品一诞生就有输入层锚点"。
 *         ② 过程层：每条证据的 chainHash = H(workId, evidenceId, type, dataHash,
 *            operator, timestamp, prevChainHash)，形成不可插入、不可删除、不可改序的
 *            时序哈希链；任何一条被改动，其后所有 chainHash 全部失效，可被 verifyChain 定位。
 *         ③ 结果层：finalizeWork 把最终作品哈希 resultHash 与链头 processRoot 绑定，
 *            形成"输入 → 过程 → 结果"的闭环证据链。
 *
 *         链上只存哈希与索引，原文（提示词、图片、版本 JSON）放链下（IPFS / 对象存储），
 *         由 uri 字段定位，满足"低 Gas、可举证、可复核"。
 */
contract AICreationEvidence is PauseGuard {
    /* ------------------------------------------------------------------ */
    /*                              常量与类型                              */
    /* ------------------------------------------------------------------ */

    /// @notice 单个作品最大协作者数量（限制循环上限，防 Gas 攻击）
    uint16 public constant MAX_CONTRIBUTORS = 20;
    /// @notice 单个作品最大证据条数
    uint16 public constant MAX_EVIDENCE_PER_WORK = 300;

    /// @notice 作品生命周期状态机
    enum WorkStatus {
        Draft,      // 创作中：可持续追加证据
        Finalized,  // 已定稿：证据链封闭，可进入贡献量化与分润
        Settled,    // 已发生过收益结算
        Disputed,   // 纠纷冻结：禁止结算，等待仲裁
        Revoked     // 已撤销/作废（仲裁结果之一）
    }

    /// @notice 证据类型（与申请书"创作事件单元"字段一一对应）
    enum EvidenceType {
        Prompt,      // 0 提示词输入（输入层）
        Parameter,   // 1 模型与采样参数
        Generation,  // 2 AI 生成中间结果
        Edit,        // 3 人工迭代编辑
        Final        // 4 最终成品（结果层）
    }

    /// @notice 协作角色模型（对应申请书"多主体协作角色模型设计"）
    enum Role {
        None,           // 0 未登记
        Ideator,        // 1 创意发起者
        PromptDesigner, // 2 提示词设计者
        ModelProvider,  // 3 模型 / 技术提供方
        Generator,      // 4 生成执行者
        Editor,         // 5 后期编辑者
        Operator        // 6 运营推广者
    }

    /// @notice 作品主结构
    struct Work {
        uint256 workId;
        address creator;
        bytes32 inputRoot;    // 输入层根哈希
        bytes32 processRoot;  // 过程层链头（最新 chainHash）
        bytes32 resultHash;   // 结果层最终作品哈希
        uint64 createdAt;
        uint64 finalizedAt;
        uint32 evidenceTotal;
        uint16 contributorTotal; // 已接受角色的协作者数量
        WorkStatus status;
        string metadataURI;   // 链下元数据（标题、模型、场景、封面等 JSON）
    }

    /// @notice 单条证据
    struct Evidence {
        uint256 evidenceId;
        uint256 workId;
        bytes32 dataHash;      // 本条证据原文哈希
        bytes32 prevChainHash; // 前一条证据的 chainHash（链式指针）
        bytes32 chainHash;     // 本条时序链哈希
        address operator;
        uint64 timestamp;
        EvidenceType etype;
        Role role;             // 操作者在该作品中的角色
        string uri;            // 链下原文定位（ipfs:// / https:// / oss key）
    }

    /// @notice 协作者登记信息
    struct Contributor {
        address account;
        Role role;
        bool accepted;   // 是否已确认接受该角色（防止被强行挂名分钱）
        bool active;     // 是否仍在协作（定稿前可被移除）
        uint64 joinedAt;
        uint32 evidenceCount;
    }

    /* ------------------------------------------------------------------ */
    /*                                存储                                  */
    /* ------------------------------------------------------------------ */

    uint256 public totalWorks;
    uint256 public totalEvidence;

    /// @notice 允许把作品状态更新为 Settled 的合约（分润合约），由管理员授权
    mapping(address => bool) public isSettler;

    mapping(uint256 => Work) internal _works;
    mapping(uint256 => Evidence) internal _evidences;
    mapping(uint256 => uint256[]) internal _workEvidenceIds;
    mapping(uint256 => mapping(address => Contributor)) internal _contributors;
    mapping(uint256 => address[]) internal _contributorList;

    /* ------------------------------------------------------------------ */
    /*                                事件                                  */
    /* ------------------------------------------------------------------ */

    event WorkCreated(
        uint256 indexed workId,
        address indexed creator,
        bytes32 inputRoot,
        string metadataURI,
        uint64 timestamp
    );

    event ContributorAdded(
        uint256 indexed workId,
        address indexed account,
        Role role
    );

    event ContributorAccepted(
        uint256 indexed workId,
        address indexed account,
        Role role,
        uint64 timestamp
    );

    event ContributorRemoved(uint256 indexed workId, address indexed account);

    event EvidenceAppended(
        uint256 indexed workId,
        uint256 indexed evidenceId,
        EvidenceType etype,
        Role role,
        address indexed operator,
        bytes32 dataHash,
        bytes32 prevChainHash,
        bytes32 chainHash,
        string uri,
        uint64 timestamp
    );

    event WorkFinalized(
        uint256 indexed workId,
        bytes32 resultHash,
        bytes32 processRoot,
        uint64 timestamp
    );

    event WorkStatusChanged(
        uint256 indexed workId,
        WorkStatus oldStatus,
        WorkStatus newStatus
    );

    event DisputeRaised(uint256 indexed workId, address indexed by, string reason);
    event DisputeResolved(uint256 indexed workId, WorkStatus outcome, address indexed by);

    event SettlerUpdated(address indexed settler, bool allowed);

    /* ------------------------------------------------------------------ */
    /*                              修饰器                                  */
    /* ------------------------------------------------------------------ */

    error WorkNotExist();
    error NotWorkCreator();
    error NotContributor();
    error RoleNotAccepted();
    error InvalidStatus();
    error DuplicateContributor();
    error TooManyContributors();
    error TooManyEvidences();
    error EmptyHash();
    error NotSettler();

    modifier workExists(uint256 workId) {
        if (_works[workId].creator == address(0)) revert WorkNotExist();
        _;
    }

    constructor(address _admin, address _arbiter) AdminControl(_admin, _arbiter) {}

    /* ------------------------------------------------------------------ */
    /*                            作品与协作者                              */
    /* ------------------------------------------------------------------ */

    /**
     * @notice 创建作品并写入输入层锚点
     * @param inputRoot     初始提示词 + 创作意图的哈希（keccak256 或 SHA-256 转 bytes32）
     * @param metadataURI   链下元数据定位
     * @return workId       作品编号（从 1 开始）
     */
    function createWork(
        bytes32 inputRoot,
        string calldata metadataURI
    ) external whenNotPaused returns (uint256 workId) {
        if (inputRoot == bytes32(0)) revert EmptyHash();

        workId = ++totalWorks;

        Work storage w = _works[workId];
        w.workId = workId;
        w.creator = msg.sender;
        w.inputRoot = inputRoot;
        w.createdAt = uint64(block.timestamp);
        w.status = WorkStatus.Draft;
        w.metadataURI = metadataURI;

        // 创作者自动登记为「创意发起者」并视为已接受
        _contributors[workId][msg.sender] = Contributor({
            account: msg.sender,
            role: Role.Ideator,
            accepted: true,
            active: true,
            joinedAt: uint64(block.timestamp),
            evidenceCount: 0
        });
        _contributorList[workId].push(msg.sender);
        w.contributorTotal = 1;

        emit WorkCreated(workId, msg.sender, inputRoot, metadataURI, uint64(block.timestamp));
        emit ContributorAdded(workId, msg.sender, Role.Ideator);
        emit ContributorAccepted(workId, msg.sender, Role.Ideator, uint64(block.timestamp));

        // 输入层证据：提示词哈希立即上链
        _appendEvidence(workId, EvidenceType.Prompt, inputRoot, metadataURI, msg.sender);
    }

    /**
     * @notice 创作者登记协作者及其角色（这就是"链上协作协议"的雏形）
     * @dev    被登记人必须调用 acceptRole 确认后才可追加证据，避免"被挂名分钱"
     */
    function addContributor(
        uint256 workId,
        address account,
        Role role
    ) external workExists(workId) whenNotPaused {
        Work storage w = _works[workId];
        if (msg.sender != w.creator && msg.sender != admin) revert NotWorkCreator();
        if (w.status != WorkStatus.Draft) revert InvalidStatus();
        if (account == address(0) || role == Role.None) revert ZeroAddress();

        Contributor storage c = _contributors[workId][account];
        if (c.account != address(0)) revert DuplicateContributor();
        if (_contributorList[workId].length >= MAX_CONTRIBUTORS) revert TooManyContributors();

        _contributors[workId][account] = Contributor({
            account: account,
            role: role,
            accepted: false,
            active: true,
            joinedAt: uint64(block.timestamp),
            evidenceCount: 0
        });
        _contributorList[workId].push(account);

        emit ContributorAdded(workId, account, role);
    }

    /// @notice 协作者确认接受角色（确认后才计入有效贡献者、才能写证据）
    function acceptRole(uint256 workId) external workExists(workId) whenNotPaused {
        Contributor storage c = _contributors[workId][msg.sender];
        if (c.account == address(0)) revert NotContributor();
        if (c.accepted) revert DuplicateContributor();

        c.accepted = true;
        _works[workId].contributorTotal += 1;

        emit ContributorAccepted(workId, msg.sender, c.role, uint64(block.timestamp));
    }

    /// @notice 创作者移除尚未确认的协作者（定稿前）
    function removeContributor(uint256 workId, address account) external workExists(workId) {
        Work storage w = _works[workId];
        if (msg.sender != w.creator && msg.sender != admin) revert NotWorkCreator();
        if (w.status != WorkStatus.Draft) revert InvalidStatus();

        Contributor storage c = _contributors[workId][account];
        if (c.account == address(0)) revert NotContributor();
        if (c.accepted) revert InvalidStatus(); // 已确认的协作者不能单方面踢出，需走纠纷流程

        c.active = false;
        emit ContributorRemoved(workId, account);
    }

    /* ------------------------------------------------------------------ */
    /*                              证据追加                                */
    /* ------------------------------------------------------------------ */

    /**
     * @notice 追加一条创作过程证据，并推进时序哈希链
     * @param workId 作品编号
     * @param etype  证据类型（Prompt/Parameter/Generation/Edit）
     * @param dataHash 该条证据原文的哈希
     * @param uri    链下原文定位
     */
    function addEvidence(
        uint256 workId,
        EvidenceType etype,
        bytes32 dataHash,
        string calldata uri
    ) external workExists(workId) whenNotPaused returns (uint256 evidenceId) {
        if (etype == EvidenceType.Final) revert InvalidStatus(); // 定稿请用 finalizeWork
        if (dataHash == bytes32(0)) revert EmptyHash();

        Work storage w = _works[workId];
        if (w.status != WorkStatus.Draft) revert InvalidStatus();

        Contributor storage c = _contributors[workId][msg.sender];
        if (c.account == address(0) || !c.active) revert NotContributor();
        if (!c.accepted) revert RoleNotAccepted();
        if (w.evidenceTotal >= MAX_EVIDENCE_PER_WORK) revert TooManyEvidences();

        evidenceId = _appendEvidence(workId, etype, dataHash, uri, msg.sender);
        c.evidenceCount += 1;
    }

    /**
     * @notice 作品定稿：写入结果层哈希，绑定链头，状态切换为 Finalized
     * @dev    定稿后证据链封闭，不可再追加，进入贡献量化与分润阶段
     */
    function finalizeWork(
        uint256 workId,
        bytes32 resultHash,
        string calldata uri
    ) external workExists(workId) whenNotPaused {
        Work storage w = _works[workId];
        if (msg.sender != w.creator && msg.sender != admin) revert NotWorkCreator();
        if (w.status != WorkStatus.Draft) revert InvalidStatus();
        if (resultHash == bytes32(0)) revert EmptyHash();
        if (w.contributorTotal == 0) revert NotContributor();

        w.resultHash = resultHash;
        w.finalizedAt = uint64(block.timestamp);

        _appendEvidence(workId, EvidenceType.Final, resultHash, uri, msg.sender);
        w.status = WorkStatus.Finalized;

        emit WorkFinalized(workId, resultHash, w.processRoot, uint64(block.timestamp));
        emit WorkStatusChanged(workId, WorkStatus.Draft, WorkStatus.Finalized);
    }

    /// @dev 内部：写入证据 + 推进链头
    function _appendEvidence(
        uint256 workId,
        EvidenceType etype,
        bytes32 dataHash,
        string calldata uri,
        address operator
    ) internal returns (uint256 evidenceId) {
        Work storage w = _works[workId];
        evidenceId = ++totalEvidence;

        bytes32 prev = w.processRoot;
        bytes32 chain = keccak256(
            abi.encode(
                workId,
                evidenceId,
                etype,
                dataHash,
                operator,
                uint64(block.timestamp),
                prev
            )
        );

        _evidences[evidenceId] = Evidence({
            evidenceId: evidenceId,
            workId: workId,
            dataHash: dataHash,
            prevChainHash: prev,
            chainHash: chain,
            operator: operator,
            timestamp: uint64(block.timestamp),
            etype: etype,
            role: _contributors[workId][operator].role,
            uri: uri
        });

        _workEvidenceIds[workId].push(evidenceId);
        w.processRoot = chain;
        w.evidenceTotal += 1;

        emit EvidenceAppended(
            workId,
            evidenceId,
            etype,
            _evidences[evidenceId].role,
            operator,
            dataHash,
            prev,
            chain,
            uri,
            uint64(block.timestamp)
        );
    }

    /* ------------------------------------------------------------------ */
    /*                           纠纷与状态流转                             */
    /* ------------------------------------------------------------------ */

    /// @notice 任一方（协作者 / 平台）发起纠纷，冻结作品结算
    function raiseDispute(
        uint256 workId,
        string calldata reason
    ) external workExists(workId) whenNotPaused {
        Work storage w = _works[workId];
        if (msg.sender != w.creator && msg.sender != admin) {
            Contributor storage c = _contributors[workId][msg.sender];
            if (c.account == address(0) || !c.accepted) revert NotContributor();
        }
        if (w.status != WorkStatus.Finalized && w.status != WorkStatus.Settled) {
            revert InvalidStatus();
        }
        WorkStatus old = w.status;
        w.status = WorkStatus.Disputed;
        emit DisputeRaised(workId, msg.sender, reason);
        emit WorkStatusChanged(workId, old, WorkStatus.Disputed);
    }

    /**
     * @notice 仲裁员裁决纠纷
     * @param outcome Finalized（解除冻结，继续结算）/ Settled（维持已结算）/
     *                Revoked（作品作废，永久停止分润）
     */
    function resolveDispute(
        uint256 workId,
        WorkStatus outcome
    ) external onlyArbiter workExists(workId) {
        Work storage w = _works[workId];
        if (w.status != WorkStatus.Disputed) revert InvalidStatus();
        if (
            outcome != WorkStatus.Finalized &&
            outcome != WorkStatus.Settled &&
            outcome != WorkStatus.Revoked
        ) revert InvalidStatus();

        w.status = outcome;
        emit DisputeResolved(workId, outcome, msg.sender);
        emit WorkStatusChanged(workId, WorkStatus.Disputed, outcome);
    }

    /// @notice 分润合约在首次结算后把作品标记为 Settled
    function markSettled(uint256 workId) external workExists(workId) {
        if (!isSettler[msg.sender]) revert NotSettler();
        Work storage w = _works[workId];
        if (w.status != WorkStatus.Finalized) return; // 幂等：已是 Settled 则忽略
        w.status = WorkStatus.Settled;
        emit WorkStatusChanged(workId, WorkStatus.Finalized, WorkStatus.Settled);
    }

    /// @notice 管理员授权 / 撤销结算方（部署分润合约后调用一次）
    function setSettler(address settler, bool allowed) external onlyAdmin {
        if (settler == address(0)) revert ZeroAddress();
        isSettler[settler] = allowed;
        emit SettlerUpdated(settler, allowed);
    }

    /* ------------------------------------------------------------------ */
    /*                              查询接口                                */
    /* ------------------------------------------------------------------ */

    function getWork(uint256 workId) external view workExists(workId) returns (Work memory) {
        return _works[workId];
    }

    function getEvidence(uint256 evidenceId) external view returns (Evidence memory) {
        if (evidenceId == 0 || evidenceId > totalEvidence) revert WorkNotExist();
        return _evidences[evidenceId];
    }

    /// @notice 分页读取某作品的全部证据 ID（前端按序回放创作过程）
    function getWorkEvidenceIds(
        uint256 workId,
        uint256 offset,
        uint256 size
    ) external view workExists(workId) returns (uint256[] memory ids) {
        uint256[] storage all = _workEvidenceIds[workId];
        if (offset >= all.length) return new uint256[](0);
        uint256 end = offset + size;
        if (end > all.length) end = all.length;
        ids = new uint256[](end - offset);
        for (uint256 i = offset; i < end; ++i) {
            ids[i - offset] = all[i];
        }
    }

    /// @notice 分页读取协作者
    function getContributors(
        uint256 workId,
        uint256 offset,
        uint256 size
    ) external view workExists(workId) returns (Contributor[] memory list) {
        address[] storage all = _contributorList[workId];
        if (offset >= all.length) return new Contributor[](0);
        uint256 end = offset + size;
        if (end > all.length) end = all.length;
        list = new Contributor[](end - offset);
        for (uint256 i = offset; i < end; ++i) {
            list[i - offset] = _contributors[workId][all[i]];
        }
    }

    function getContributor(
        uint256 workId,
        address account
    ) external view returns (Contributor memory) {
        return _contributors[workId][account];
    }

    function isActiveContributor(uint256 workId, address account) public view returns (bool) {
        Contributor storage c = _contributors[workId][account];
        return c.account != address(0) && c.active && c.accepted;
    }

    function getChainHead(uint256 workId) external view workExists(workId) returns (bytes32) {
        return _works[workId].processRoot;
    }

    /* ------------------------------------------------------------------ */
    /*                              完整性校验                              */
    /* ------------------------------------------------------------------ */

    /**
     * @notice 校验单条证据是否未被篡改（第三方举证用）
     * @param evidenceId 链上证据 ID
     * @param rawDataHash 本地原始文件 / JSON 重新计算的哈希
     */
    function verifyEvidence(
        uint256 evidenceId,
        bytes32 rawDataHash
    ) external view returns (bool) {
        if (evidenceId == 0 || evidenceId > totalEvidence) return false;
        return _evidences[evidenceId].dataHash == rawDataHash;
    }

    /**
     * @notice 重放整条时序哈希链，验证链内一致性与链接关系
     * @return ok  true = 整条链自洽；false = 存在断链或被改动
     * @return badIndex 第一条不一致的证据下标（0 表示无异常）
     */
    function verifyChain(uint256 workId) external view workExists(workId) returns (bool ok, uint256 badIndex) {
        uint256[] storage ids = _workEvidenceIds[workId];
        bytes32 prev = bytes32(0);
        for (uint256 i = 0; i < ids.length; ++i) {
            Evidence storage e = _evidences[ids[i]];
            if (e.prevChainHash != prev) return (false, i);
            bytes32 recomputed = keccak256(
                abi.encode(
                    e.workId,
                    e.evidenceId,
                    e.etype,
                    e.dataHash,
                    e.operator,
                    e.timestamp,
                    e.prevChainHash
                )
            );
            if (recomputed != e.chainHash) return (false, i);
            prev = e.chainHash;
        }
        if (prev != _works[workId].processRoot) return (false, ids.length);
        return (true, 0);
    }
}
