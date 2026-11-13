const fs = require("fs");
const path = require("path");
const { ethers, network } = require("hardhat");

/**
 * 部署脚本
 * 用法：
 *   npx hardhat run scripts/deploy.js                    # 本地内存链（一次性，跑完即消失）
 *   npx hardhat run scripts/deploy.js --network localhost # 本地持久节点（配合 npx hardhat node）
 *
 * 部署顺序（顺序不能颠倒）：
 *   1. AICreationEvidence(admin, arbiter)
 *   2. AICreationProfitSplit(evidence, admin, arbiter, platformWallet)
 *   3. evidence.setSettler(split, true)      ← 必须做，否则作品状态不会自动流转为 Settled
 */
async function main() {
  const signers = await ethers.getSigners();
  const deployer = signers[0];
  const arbiter = signers[1] || deployer;
  const platformWallet = signers[2] || deployer;

  console.log("网络:", network.name);
  console.log("部署账户:", deployer.address);
  console.log("仲裁员:", arbiter.address);
  console.log("平台收款地址:", platformWallet.address);
  console.log("");

  const Evidence = await ethers.getContractFactory("AICreationEvidence");
  const evidence = await Evidence.deploy(deployer.address, arbiter.address);
  await evidence.waitForDeployment();
  const evidenceAddr = await evidence.getAddress();
  console.log("AICreationEvidence   ->", evidenceAddr);

  const Split = await ethers.getContractFactory("AICreationProfitSplit");
  const split = await Split.deploy(
    evidenceAddr,
    deployer.address,
    arbiter.address,
    platformWallet
  );
  await split.waitForDeployment();
  const splitAddr = await split.getAddress();
  console.log("AICreationProfitSplit ->", splitAddr);

  const tx = await evidence.setSettler(splitAddr, true);
  await tx.wait();
  console.log("已授权分润合约为结算方 (isSettler = true)");
  console.log("");

  const out = {
    network: network.name,
    chainId: (await ethers.provider.getNetwork()).chainId.toString(),
    deployedAt: new Date().toISOString(),
    AICreationEvidence: evidenceAddr,
    AICreationProfitSplit: splitAddr,
    admin: deployer.address,
    arbiter: arbiter.address,
    platformWallet: platformWallet.address
  };

  const dir = path.join(__dirname, "..", "deployments");
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
  const file = path.join(dir, `${network.name}.json`);
  fs.writeFileSync(file, JSON.stringify(out, null, 2));
  console.log("地址已写入:", file);
  console.log(JSON.stringify(out, null, 2));
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
