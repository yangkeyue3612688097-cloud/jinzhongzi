// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

/**
 * @title  Security
 * @notice 零依赖安全组件集合（不引入 OpenZeppelin，方便 Remix / Hardhat / FISCO 直接编译）
 * @dev    包含：重入锁、角色权限（平台管理员 + 仲裁员）、紧急暂停
 */

/* -------------------------------------------------------------------------- */
/*                                 重入锁                                      */
/* -------------------------------------------------------------------------- */
abstract contract ReentrancyGuard {
    uint256 private constant _NOT_ENTERED = 1;
    uint256 private constant _ENTERED = 2;
    uint256 private _status = _NOT_ENTERED;

    error ReentrantCall();

    modifier nonReentrant() {
        if (_status == _ENTERED) revert ReentrantCall();
        _status = _ENTERED;
        _;
        _status = _NOT_ENTERED;
    }
}

/* -------------------------------------------------------------------------- */
/*                              角色与权限                                     */
/* -------------------------------------------------------------------------- */
abstract contract AdminControl {
    /// @notice 平台运营管理员：配置参数、上链评分、维护结算方
    address public admin;
    /// @notice 仲裁员：处理纠纷，与管理员职责分离（避免"既当运动员又当裁判"）
    address public arbiter;
    /// @notice 两步转移目标
    address public pendingAdmin;

    error NotAdmin();
    error NotArbiter();
    error ZeroAddress();

    event AdminTransferStarted(address indexed from, address indexed to);
    event AdminChanged(address indexed oldAdmin, address indexed newAdmin);
    event ArbiterChanged(address indexed oldArbiter, address indexed newArbiter);

    constructor(address _admin, address _arbiter) {
        if (_admin == address(0)) revert ZeroAddress();
        admin = _admin;
        arbiter = _arbiter == address(0) ? _admin : _arbiter;
    }

    modifier onlyAdmin() {
        if (msg.sender != admin) revert NotAdmin();
        _;
    }

    modifier onlyArbiter() {
        if (msg.sender != arbiter) revert NotArbiter();
        _;
    }

    /// @notice 发起管理员转移（两步确认，防止误操作把合约锁死）
    function transferAdmin(address newAdmin) external onlyAdmin {
        if (newAdmin == address(0)) revert ZeroAddress();
        pendingAdmin = newAdmin;
        emit AdminTransferStarted(msg.sender, newAdmin);
    }

    /// @notice 新管理员接受职位
    function acceptAdmin() external {
        if (msg.sender != pendingAdmin) revert NotAdmin();
        address old = admin;
        admin = pendingAdmin;
        pendingAdmin = address(0);
        emit AdminChanged(old, admin);
    }

    /// @notice 指定仲裁员
    function setArbiter(address newArbiter) external onlyAdmin {
        if (newArbiter == address(0)) revert ZeroAddress();
        address old = arbiter;
        arbiter = newArbiter;
        emit ArbiterChanged(old, newArbiter);
    }
}

/* -------------------------------------------------------------------------- */
/*                                紧急暂停                                     */
/* -------------------------------------------------------------------------- */
abstract contract PauseGuard is AdminControl {
    bool public paused;

    error ContractPaused();

    event Paused(address indexed by);
    event Unpaused(address indexed by);

    modifier whenNotPaused() {
        if (paused) revert ContractPaused();
        _;
    }

    function pause() external onlyAdmin {
        paused = true;
        emit Paused(msg.sender);
    }

    function unpause() external onlyAdmin {
        paused = false;
        emit Unpaused(msg.sender);
    }
}
