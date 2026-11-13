// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

/**
 * @title BusinessAnchor
 * @notice Anchors business facts without putting private business data on chain.
 * @dev The backend hashes canonical business payloads and sends only the hash,
 *      business id, type and an optional metadata URI.
 */
contract BusinessAnchor {
    uint8 public constant TYPE_AGREEMENT = 1;
    uint8 public constant TYPE_REVENUE_EVENT = 2;
    uint8 public constant TYPE_SETTLEMENT_RECEIPT = 3;
    uint8 public constant TYPE_AUDIT_CHECKPOINT = 4;
    uint8 public constant TYPE_AUTHORSHIP_EVIDENCE = 5;

    struct Anchor {
        uint256 anchorId;
        bytes32 businessId;
        bytes32 dataHash;
        uint8 businessType;
        address operator;
        uint64 timestamp;
        string metadataURI;
    }

    address public admin;
    uint256 public totalAnchors;
    mapping(address => bool) public operators;
    mapping(bytes32 => mapping(uint8 => mapping(bytes32 => Anchor))) private _anchors;
    mapping(bytes32 => mapping(uint8 => uint256)) public latestAnchorId;

    event OperatorUpdated(address indexed operator, bool allowed);
    event BusinessAnchored(
        uint256 indexed anchorId,
        bytes32 indexed businessId,
        uint8 indexed businessType,
        bytes32 dataHash,
        address operator,
        uint64 timestamp,
        string metadataURI
    );

    error NotAdmin();
    error NotOperator();
    error ZeroAddress();
    error EmptyBusinessId();
    error EmptyDataHash();
    error InvalidBusinessType();
    error AlreadyAnchored();
    error AnchorNotFound();

    constructor(address initialAdmin) {
        if (initialAdmin == address(0)) revert ZeroAddress();
        admin = initialAdmin;
        operators[initialAdmin] = true;
        emit OperatorUpdated(initialAdmin, true);
    }

    modifier onlyAdmin() {
        if (msg.sender != admin) revert NotAdmin();
        _;
    }

    modifier onlyOperator() {
        if (!operators[msg.sender]) revert NotOperator();
        _;
    }

    function setOperator(address operator, bool allowed) external onlyAdmin {
        if (operator == address(0)) revert ZeroAddress();
        operators[operator] = allowed;
        emit OperatorUpdated(operator, allowed);
    }

    function anchor(
        bytes32 businessId,
        bytes32 dataHash,
        uint8 businessType,
        string calldata metadataURI
    ) external onlyOperator returns (uint256 anchorId) {
        if (businessId == bytes32(0)) revert EmptyBusinessId();
        if (dataHash == bytes32(0)) revert EmptyDataHash();
        if (businessType == 0 || businessType > TYPE_AUTHORSHIP_EVIDENCE) {
            revert InvalidBusinessType();
        }
        if (_anchors[businessId][businessType][dataHash].operator != address(0)) {
            revert AlreadyAnchored();
        }

        anchorId = ++totalAnchors;
        _anchors[businessId][businessType][dataHash] = Anchor({
            anchorId: anchorId,
            businessId: businessId,
            dataHash: dataHash,
            businessType: businessType,
            operator: msg.sender,
            timestamp: uint64(block.timestamp),
            metadataURI: metadataURI
        });
        latestAnchorId[businessId][businessType] = anchorId;

        emit BusinessAnchored(
            anchorId,
            businessId,
            businessType,
            dataHash,
            msg.sender,
            uint64(block.timestamp),
            metadataURI
        );
    }

    function verify(
        bytes32 businessId,
        bytes32 dataHash,
        uint8 businessType
    ) external view returns (bool) {
        Anchor storage item = _anchors[businessId][businessType][dataHash];
        return item.operator != address(0) && item.dataHash == dataHash;
    }

    function getAnchor(
        bytes32 businessId,
        bytes32 dataHash,
        uint8 businessType
    ) external view returns (Anchor memory) {
        Anchor storage item = _anchors[businessId][businessType][dataHash];
        if (item.operator == address(0)) revert AnchorNotFound();
        return item;
    }
}
