/**
 * 哈希工具（前后端必须共用同一套口径，否则 verifyEvidence 永远返回 false）
 *
 * 统一规则：
 *   · 文本（提示词、备注）        → keccak256(utf8 字节)
 *   · JSON（元数据、评分明细）    → 先递归排序 key，再 keccak256(utf8)
 *   · 文件（图片、音视频、压缩包）→ keccak256(文件原始二进制)
 *   · 若必须使用 SHA-256（例如对接已有系统）→ 取 sha256 的 32 字节直接当 bytes32，
 *     但全系统必须统一，不能一半 keccak 一半 sha256
 *
 * 用法：
 *   const { hashText, hashJson, hashFile } = require("./hash-utils");
 */
const fs = require("fs");
const crypto = require("crypto");
const { keccak256, toUtf8Bytes } = require("ethers");

/** 文本 → bytes32 */
function hashText(text) {
  return keccak256(toUtf8Bytes(String(text)));
}

/** 递归排序 key 的规范化 JSON 字符串（保证同一对象永远得到同一哈希） */
function canonicalJson(obj) {
  const walk = (v) => {
    if (Array.isArray(v)) return v.map(walk);
    if (v && typeof v === "object") {
      return Object.keys(v)
        .sort()
        .reduce((acc, k) => {
          acc[k] = walk(v[k]);
          return acc;
        }, {});
    }
    return v;
  };
  return JSON.stringify(walk(obj));
}

/** JSON → bytes32 */
function hashJson(obj) {
  return keccak256(toUtf8Bytes(canonicalJson(obj)));
}

/** 文件 → bytes32（同步） */
function hashFile(filePath) {
  return keccak256(fs.readFileSync(filePath));
}

/** SHA-256（用于需要与旧系统对齐的场景） */
function sha256Hex(filePath) {
  return "0x" + crypto.createHash("sha256").update(fs.readFileSync(filePath)).digest("hex");
}

/** 校验：本地文件与链上 dataHash 是否一致 */
function assertMatch(localHash, onChainHash) {
  if (localHash.toLowerCase() !== onChainHash.toLowerCase()) {
    throw new Error(`哈希不一致：本地 ${localHash} ≠ 链上 ${onChainHash}（文件可能被篡改）`);
  }
  return true;
}

module.exports = { hashText, hashJson, hashFile, sha256Hex, canonicalJson, assertMatch };
