const { expect } = require("chai");
const { ethers } = require("hardhat");
const { scoreWork } = require("../scripts/scoring-engine");

/**
 * 联调测试：链下评分引擎 → 链上合约
 * 验证"评分引擎输出的七项指标"能被合约直接接受，且 CI / 分润比例符合预期
 */
describe("链下评分引擎 × 链上合约 联调", function () {
  let admin, arbiter, platform, creator, prompter, editor, operator;
  let evidence, split;

  beforeEach(async () => {
    [admin, arbiter, platform, creator, prompter, editor, operator] = await ethers.getSigners();

    const Evidence = await ethers.getContractFactory("AICreationEvidence");
    evidence = await Evidence.deploy(admin.address, arbiter.address);
    await evidence.waitForDeployment();

    const Split = await ethers.getContractFactory("AICreationProfitSplit");
    split = await Split.deploy(
      await evidence.getAddress(),
      admin.address,
      arbiter.address,
      platform.address
    );
    await split.waitForDeployment();
    await (await evidence.setSettler(await split.getAddress(), true)).wait();
  });

  it("评分引擎产出的 scores 可直接上链，且份额比例合理", async () => {
    const workId = 1n;
    const now = Math.floor(Date.now() / 1000);

    await evidence.connect(creator).createWork(ethers.id("prompt"), "ipfs://m");
    await evidence.connect(creator).addContributor(workId, prompter.address, 2);
    await evidence.connect(creator).addContributor(workId, editor.address, 5);
    await evidence.connect(creator).addContributor(workId, operator.address, 6);
    for (const s of [prompter, editor, operator]) await evidence.connect(s).acceptRole(workId);
    await evidence.connect(creator).finalizeWork(workId, ethers.id("result"), "ipfs://f");

    // 模拟后端从数据库读出的过程记录（带链下原文 payload）
    const records = [
      { evidenceId: 1, etype: 0, operator: creator.address, uri: "ipfs://p1", timestamp: now - 9000, dataHash: "0x01", payload: { prompt: "一只黑猫" } },
      { evidenceId: 2, etype: 0, operator: prompter.address, uri: "ipfs://p2", timestamp: now - 8600, dataHash: "0x02", payload: { prompt: "赛博朋克风格的黑猫，霓虹灯雨夜街道，电影感布光，85mm 镜头，超精细毛发，8k" } },
      { evidenceId: 3, etype: 1, operator: prompter.address, uri: "ipfs://p3", timestamp: now - 8500, dataHash: "0x03", payload: { cfg: 8.5, steps: 38 } },
      { evidenceId: 4, etype: 2, operator: creator.address, uri: "ipfs://p4", timestamp: now - 8200, dataHash: "0x04" },
      { evidenceId: 5, etype: 3, operator: editor.address, uri: "ipfs://p5", timestamp: now - 5000, dataHash: "0x05" },
      { evidenceId: 6, etype: 3, operator: editor.address, uri: "ipfs://p6", timestamp: now - 4800, dataHash: "0x06" }
    ];
    const contributors = [
      { account: creator.address, role: 1 },
      { account: prompter.address, role: 2 },
      { account: editor.address, role: 5 },
      { account: operator.address, role: 6 }
    ];
    const ctx = {
      adoption: {
        [creator.address]: { versions: 1, adopted: 1, isFinal: false },
        [prompter.address]: { versions: 2, adopted: 2, isFinal: true },
        [editor.address]: { versions: 0, adopted: 2, isFinal: false }
      },
      salesCount: { [operator.address]: 12, [editor.address]: 3 }
    };

    const { scores, sheetHash } = scoreWork(records, contributors, ctx);

    // 所有指标必须落在合约允许区间
    for (const s of scores) {
      for (const k of ["s11", "s12", "s21", "s22", "s3"]) {
        expect(s[k] >= 0 && s[k] <= 10000, `${k} 越界: ${s[k]}`).to.equal(true);
      }
      expect(s.alpha <= 10000, `alpha 越界: ${s.alpha}`).to.equal(true);
      expect(s.lambda <= 10000, `lambda 越界: ${s.lambda}`).to.equal(true);
    }

    await (await split.connect(admin).lockShares(workId, scores, sheetHash)).wait();
    const sheet = await split.getShareSheet(workId);
    expect(sheet.version).to.equal(1n);
    expect(sheet.totalCi > 0n).to.equal(true);

    // 提示词设计者应拿到最高份额（它贡献了被最终采纳的提示词）
    let maxIdx = 0;
    for (let i = 1; i < sheet.ci.length; i++) if (sheet.ci[i] > sheet.ci[maxIdx]) maxIdx = i;
    expect(sheet.accounts[maxIdx]).to.equal(prompter.address);

    // 任意角色都不应因为"没有提示词"而被判 0（否则 55% 权重凭空消失）
    for (const ci of sheet.ci) expect(ci > 0n).to.equal(true);

    // 分润预览：总额守恒
    await split.depositRevenue(workId, { value: ethers.parseEther("2") });
    const [, fee, net, , amounts] = await split.previewSettlement(workId);
    expect(amounts.reduce((a, b) => a + b, 0n)).to.equal(net);
    expect(fee + net).to.equal(ethers.parseEther("2"));
  });
});
