const { ethers } = require("hardhat");

const BPS = 10000n;
const fmt = (v) => ethers.formatEther(v);
const pct = (v, total) => ((Number(v) * 100) / Number(total)).toFixed(2) + "%";

const ROLE = {
  0: "无",
  1: "创意发起者",
  2: "提示词设计者",
  3: "模型/技术提供方",
  4: "生成执行者",
  5: "后期编辑者",
  6: "运营推广者"
};
const ETYPE = { 0: "提示词", 1: "参数", 2: "生成结果", 3: "人工编辑", 4: "最终成品" };
const STATUS = { 0: "创作中", 1: "已定稿", 2: "已结算", 3: "纠纷冻结", 4: "已作废" };

/**
 * 全链路演示脚本（可直接用于答辩录屏 / 现场演示）
 *   npx hardhat run scripts/demo-e2e.js
 *
 * 演示内容：一个四人协作的 AIGC 插画从存证 → 定稿 → 贡献量化 → 收益托管 → 自动分润 →
 *          协作者提现 → 二次变现再分润的完整闭环
 */
async function main() {
  const [admin, arbiter, platform, creator, prompter, editor, operator, buyer] =
    await ethers.getSigners();

  console.log("═".repeat(78));
  console.log("  AIGC 可信存证 + 协作分润  全链路演示");
  console.log("═".repeat(78));

  /* ------------------------------ 1. 部署 ------------------------------ */
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

  console.log("\n【0】合约部署完成");
  console.log("   存证合约:", await evidence.getAddress());
  console.log("   分润合约:", await split.getAddress());

  /* ---------------------------- 2. 创建作品 ---------------------------- */
  const promptText =
    "赛博朋克风格的黑猫，霓虹灯雨夜街道，电影感布光，85mm 镜头，超精细毛发，8k";
  const promptHash = ethers.keccak256(ethers.toUtf8Bytes(promptText));

  await (await evidence.connect(creator).createWork(promptHash, "ipfs://meta/cyber-cat")).wait();
  const workId = 1n;

  console.log("\n【1】作品创建 · 输入层存证");
  console.log("   创作者:", creator.address);
  console.log("   提示词:", promptText);
  console.log("   提示词哈希(inputRoot):", promptHash);

  /* ---------------------------- 3. 协作登记 ---------------------------- */
  await (await evidence.connect(creator).addContributor(workId, prompter.address, 2)).wait();
  await (await evidence.connect(creator).addContributor(workId, editor.address, 5)).wait();
  await (await evidence.connect(creator).addContributor(workId, operator.address, 6)).wait();
  await (await evidence.connect(prompter).acceptRole(workId)).wait();
  await (await evidence.connect(editor).acceptRole(workId)).wait();
  await (await evidence.connect(operator).acceptRole(workId)).wait();

  console.log("\n【2】链上协作协议（角色登记 + 本人确认）");
  for (const [addr, name] of [
    [creator, "创意发起者"],
    [prompter, "提示词设计者"],
    [editor, "后期编辑者"],
    [operator, "运营推广者"]
  ]) {
    const c = await evidence.getContributor(workId, addr);
    console.log(`   ${name.padEnd(6)} ${addr.address}  角色=${ROLE[c.role]}  已确认=${c.accepted}`);
  }

  /* ---------------------------- 4. 过程存证 ---------------------------- */
  const steps = [
    [prompter, 2, "v2 提示词：加入'霓虹雨夜'与'85mm'", "ipfs://ev/prompt-v2"],
    [prompter, 1, "cfg=8.5, steps=38, seed=20261030, sampler=DPM++2M", "ipfs://ev/params"],
    [creator, 2, "首轮生成：四宫格 1024x1024", "ipfs://ev/gen-01"],
    [editor, 3, "局部重绘：修复爪子结构 + 调色", "ipfs://ev/edit-01"],
    [creator, 2, "终版生成：hair detail 加强", "ipfs://ev/gen-02"],
    [editor, 3, "二次修图：锐化 + 输出 4K", "ipfs://ev/edit-02"]
  ];
  for (const [who, type, note, uri] of steps) {
    await (
      await evidence
        .connect(who)
        .addEvidence(workId, type, ethers.keccak256(ethers.toUtf8Bytes(note)), uri)
    ).wait();
  }

  console.log("\n【3】过程层存证 · 时序哈希链");
  const ids = await evidence.getWorkEvidenceIds(workId, 0, 100);
  for (const id of ids) {
    const e = await evidence.getEvidence(id);
    console.log(
      `   #${String(e.evidenceId).padEnd(3)} ${ETYPE[e.etype].padEnd(5)} ${String(
        e.operator
      ).slice(0, 10)}… chain=${String(e.chainHash).slice(0, 18)}…`
    );
  }
  const [chainOk] = await evidence.verifyChain(workId);
  console.log("   链完整性校验:", chainOk ? "✔ 通过（无断链、无篡改）" : "✘ 异常");

  /* ---------------------------- 5. 定稿 ---------------------------- */
  const resultHash = ethers.keccak256(ethers.toUtf8Bytes("final/cyber-cat-4k.png"));
  await (await evidence.connect(creator).finalizeWork(workId, resultHash, "ipfs://ev/final")).wait();
  const w = await evidence.getWork(workId);
  console.log("\n【4】作品定稿 · 结果层存证");
  console.log("   成品哈希(resultHash):", resultHash);
  console.log("   链头(processRoot)  :", w.processRoot);
  console.log("   状态:", STATUS[w.status], "｜证据数:", w.evidenceTotal.toString());

  /* ---------------------------- 6. 贡献量化 ---------------------------- */
  // 说明：七项指标由链下评分引擎产出（可直接改成调用 AI 评测接口），链上按公式复算
  const scores = [
    { account: creator.address, s11: 8200, s12: 7800, alpha: 10000, s21: 7600, s22: 7000, lambda: 9500, s3: 7500 },
    { account: prompter.address, s11: 9400, s12: 9200, alpha: 10000, s21: 9000, s22: 8800, lambda: 9800, s3: 8200 },
    { account: editor.address, s11: 6200, s12: 7200, alpha: 9000, s21: 8800, s22: 7600, lambda: 9000, s3: 7200 },
    { account: operator.address, s11: 5000, s12: 6200, alpha: 8000, s21: 7400, s22: 6200, lambda: 8500, s3: 9600 }
  ];
  await (await split.connect(admin).lockShares(workId, scores, ethers.id("score-sheet:cyber-cat:v1"))).wait();

  const sheet = await split.getShareSheet(workId);
  console.log("\n【5】贡献量化上链（CI = W1·S1 + W2·S2 + W3·S3）");
  console.log("   参数: W1=0.55 W2=0.25 W3=0.20 ｜ W11=0.4 W12=0.6 ｜ W21=0.5 W22=0.5");
  console.log("   " + "协作者".padEnd(12) + "S1".padEnd(9) + "S2".padEnd(9) + "S3".padEnd(8) + "CI".padEnd(9) + "分润占比");
  const p = await split.params();
  for (let i = 0; i < sheet.accounts.length; i++) {
    const s = scores[i];
    const s1 = (BigInt(s.alpha) * ((BigInt(p.w11) * BigInt(s.s11) + BigInt(p.w12) * BigInt(s.s12)) / BPS)) / BPS;
    const s2 = (BigInt(s.lambda) * ((BigInt(p.w21) * BigInt(s.s21) + BigInt(p.w22) * BigInt(s.s22)) / BPS)) / BPS;
    const ci = sheet.ci[i];
    const name = ["创意发起", "提示词设计", "后期编辑", "运营推广"][i];
    console.log(
      "   " +
        name.padEnd(12) +
        (Number(s1) / 100).toFixed(2).padEnd(9) +
        (Number(s2) / 100).toFixed(2).padEnd(9) +
        (Number(s.s3) / 100).toFixed(2).padEnd(8) +
        (Number(ci) / 100).toFixed(2).padEnd(9) +
        pct(ci, sheet.totalCi)
    );
  }

  /* ---------------------------- 7. 收益托管 ---------------------------- */
  await (await split.connect(buyer).depositRevenue(workId, { value: ethers.parseEther("1") })).wait();
  console.log("\n【6】买家支付 1.0 ETH → 进入合约托管（escrow）");
  console.log("   托管余额:", fmt(await split.escrowOf(workId)), "ETH");

  /* ---------------------------- 8. 份额确认 ---------------------------- */
  for (const s of scores) {
    await (await split.connect(await ethers.getSigner(s.account)).consentShares(workId)).wait();
  }
  console.log("\n【7】4 位协作者全部确认份额表 → 跳过 3 天异议期，可立即结算");

  /* ---------------------------- 9. 自动分润 ---------------------------- */
  const [gross, fee, net, accounts, amounts] = await split.previewSettlement(workId);
  console.log("\n【8】分润预览");
  console.log("   收益总额:", fmt(gross), "ETH");
  console.log("   平台服务费(5%):", fmt(fee), "ETH");
  console.log("   协作者净额:", fmt(net), "ETH");
  for (let i = 0; i < accounts.length; i++) {
    console.log(
      `   ${["创意发起", "提示词设计", "后期编辑", "运营推广"][i].padEnd(10)} ${fmt(amounts[i])} ETH (${pct(
        amounts[i],
        net
      )})`
    );
  }

  await (await split.settle(workId)).wait();
  console.log("\n【9】任何人可触发结算 → 收益已记入各自可提取余额");
  console.log("   作品状态:", STATUS[(await evidence.getWork(workId)).status]);
  console.log("   本轮结算轮次:", (await split.settleRound(workId)).toString());

  /* --------------------------- 10. 提现 --------------------------- */
  const before = await ethers.provider.getBalance(editor.address);
  await (await split.connect(editor).withdraw()).wait();
  const after = await ethers.provider.getBalance(editor.address);
  console.log("\n【10】后期编辑者提现");
  console.log("   余额变化:", fmt(after - before), "ETH（已扣除 gas）");

  /* --------------------------- 11. 二次变现 --------------------------- */
  await (await split.connect(buyer).depositRevenue(workId, { value: ethers.parseEther("0.5") })).wait();
  const second = await split.previewSettlement(workId);
  await (await split.settle(workId)).wait();
  console.log("\n【11】作品二次授权变现 0.5 ETH → 同一份额表自动再分润");
  console.log("   第二轮净额:", fmt(second[2]), "ETH｜累计结算额:", fmt(await split.totalSettled(workId)), "ETH");
  console.log("   累计待提取（创意发起者）:", fmt(await split.pendingWithdrawals(creator.address)), "ETH");

  /* --------------------------- 12. 纠纷演示 --------------------------- */
  await (await evidence.connect(editor).raiseDispute(workId, "第二轮分成比例与约定不符")).wait();
  await (await split.connect(buyer).depositRevenue(workId, { value: ethers.parseEther("0.1") })).wait();
  console.log("\n【12】纠纷冻结演示");
  console.log("   编辑者发起纠纷 → 状态:", STATUS[(await evidence.getWork(workId)).status]);
  try {
    await split.settle(workId);
    console.log("   ✘ 不应结算成功");
  } catch (e) {
    console.log("   结算被拒绝 ✔（资金 0.1 ETH 安全滞留托管，等待仲裁）");
  }
  await (await evidence.connect(arbiter).resolveDispute(workId, 1)).wait();
  await (await split.settle(workId)).wait();
  console.log("   仲裁解除冻结 → 托管资金正常结算完成 ✔");

  console.log("\n" + "═".repeat(78));
  console.log("  演示结束：存证可验证、分润可复算、资金不滞留、纠纷可冻结可仲裁");
  console.log("═".repeat(78));
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
