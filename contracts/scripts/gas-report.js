const { ethers } = require("hardhat");

/**
 * Gas 消耗报告（写报告 / 答辩用：证明"轻量化"不是口号）
 *   npx hardhat run scripts/gas-report.js
 */
async function main() {
  const [admin, arbiter, platform, creator, prompter, editor] = await ethers.getSigners();

  const Evidence = await ethers.getContractFactory("AICreationEvidence");
  const evidence = await Evidence.deploy(admin.address, arbiter.address);
  await evidence.waitForDeployment();

  const Split = await ethers.getContractFactory("AICreationProfitSplit");
  const split = await Split.deploy(
    await evidence.getAddress(),
    admin.address,
    arbiter.address,
    platform.address
  );
  await split.waitForDeployment();
  await (await evidence.setSettler(await split.getAddress(), true)).wait();

  const rows = [];
  const rec = async (label, txPromise) => {
    const tx = await txPromise;
    const rc = await tx.wait();
    rows.push({ 操作: label, "Gas 消耗": rc.gasUsed.toString() });
    return rc;
  };

  await rec("createWork（建作品+输入层存证）", evidence.connect(creator).createWork(ethers.id("p"), "ipfs://m"));
  const workId = 1n;
  await rec("addContributor（登记协作者）", evidence.connect(creator).addContributor(workId, prompter.address, 2));
  await rec("acceptRole（确认角色）", evidence.connect(prompter).acceptRole(workId));
  await rec("addEvidence（追加一条过程证据）", evidence.connect(creator).addEvidence(workId, 1, ethers.id("params"), "ipfs://p"));
  await rec("addEvidence（追加一条过程证据）", evidence.connect(creator).addEvidence(workId, 2, ethers.id("gen"), "ipfs://g"));
  await rec("finalizeWork（定稿+结果层存证）", evidence.connect(creator).finalizeWork(workId, ethers.id("r"), "ipfs://f"));

  const scores = [
    { account: creator.address, s11: 8000, s12: 8000, alpha: 10000, s21: 8000, s22: 8000, lambda: 9000, s3: 8000 },
    { account: prompter.address, s11: 9000, s12: 9000, alpha: 10000, s21: 9000, s22: 8500, lambda: 9500, s3: 8000 }
  ];
  await rec("lockShares（2 人份额上链）", split.connect(admin).lockShares(workId, scores, ethers.id("sheet")));
  await rec("consentShares（确认份额）", split.connect(creator).consentShares(workId));
  await rec("consentShares（确认份额）", split.connect(prompter).consentShares(workId));
  await rec("depositRevenue（收益入托管）", split.connect(creator).depositRevenue(workId, { value: ethers.parseEther("1") }));
  await rec("settle（2 人分润结算）", split.settle(workId));
  await rec("withdraw（提现）", split.connect(prompter).withdraw());

  const fee = await ethers.provider.getFeeData();
  const gasPriceGwei = Number(fee.gasPrice || 0n) / 1e9;

  console.log("\n=== Gas 消耗报告 ===");
  console.table(rows);
  console.log(`当前 gasPrice ≈ ${gasPriceGwei} gwei`);
  console.log("说明：链上只存 bytes32 哈希与索引，原文（提示词全文、图片、版本 JSON）全部在链下，");
  console.log("     单条过程证据约 24 万 gas，1 gwei 下约 0.00024 ETH；若把原文直接上链，");
  console.log("     单条成本会高出 2~3 个数量级，这也是本项目采用『链下存储 + 链上锚定』的原因。");
  console.log("     后续可用默克尔树批量聚合（申请书已规划）把多条证据合并为一次锚定，进一步降本。\n");
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
