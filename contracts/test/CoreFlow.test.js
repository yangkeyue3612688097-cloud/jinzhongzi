const { expect } = require("chai");
const { ethers, network } = require("hardhat");

/* -------------------------------------------------------------------------- */
/*        与合约 ContributionMath 完全一致的 BigInt 镜像实现（用于交叉验证）        */
/* -------------------------------------------------------------------------- */
const BPS = 10_000n;

function coreScore(alpha, w11, s11, w12, s12) {
  const inner = (w11 * s11 + w12 * s12) / BPS;
  return (alpha * inner) / BPS;
}
function processScore(lambda, w21, s21, w22, s22) {
  const inner = (w21 * s21 + w22 * s22) / BPS;
  return (lambda * inner) / BPS;
}
function compositeIndex(w1, s1, w2, s2, w3, s3) {
  return (w1 * s1 + w2 * s2 + w3 * s3) / BPS;
}
function ciOf(s, p) {
  const s1 = coreScore(s.alpha, p.w11, s.s11, p.w12, s.s12);
  const s2 = processScore(s.lambda, p.w21, s.s21, p.w22, s.s22);
  return compositeIndex(p.w1, s1, p.w2, s2, p.w3, s.s3);
}
function allocate(amount, cis, totalCi) {
  const out = cis.map((c) => (amount * c) / totalCi);
  let sum = out.reduce((a, b) => a + b, 0n);
  if (sum < amount) {
    let maxIdx = 0;
    for (let i = 1; i < cis.length; i++) if (cis[i] > cis[maxIdx]) maxIdx = i;
    out[maxIdx] += amount - sum;
  }
  return out;
}

const P = {
  w1: 5500n, w2: 2500n, w3: 2000n,
  w11: 4000n, w12: 6000n, w21: 5000n, w22: 5000n
};

async function expectRevert(promise, errName) {
  try {
    await promise;
  } catch (e) {
    const msg = (e.shortMessage || "") + " " + (e.message || "");
    if (errName) expect(msg).to.include(errName);
    return;
  }
  expect.fail(`期望 revert ${errName || ""}，但调用成功`);
}

describe("AI 创作可信存证 + 协作分润（核心链路）", function () {
  let admin, arbiter, platform, creator, prompter, editor, operator, buyer, stranger;
  let evidence, split;

  beforeEach(async () => {
    [admin, arbiter, platform, creator, prompter, editor, operator, buyer, stranger] =
      await ethers.getSigners();

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

    // 关键：授权分润合约回写作品状态（旧版合约缺这一步，导致 distribute 必 revert）
    await evidence.connect(admin).setSettler(await split.getAddress(), true);
  });

  it("部署参数默认值符合申请书区间", async () => {
    const p = await split.params();
    expect(p.w1).to.equal(5500n);
    expect(p.w2).to.equal(2500n);
    expect(p.w3).to.equal(2000n);
    expect(p.w11 + p.w12).to.equal(BPS);
    expect(p.w12 > p.w11).to.equal(true);
    expect(p.platformFeeBps).to.equal(500n);
  });

  it("权重参数非法时应拒绝", async () => {
    await expectRevert(
      split.connect(admin).setParams({ w1: 7000, w2: 2000, w3: 1000, w11: 4000, w12: 6000, w21: 5000, w22: 5000, platformFeeBps: 500, challengePeriod: 259200 }),
      "FactorOutOfRange"
    );
    await expectRevert(
      split.connect(admin).setParams({ w1: 5500, w2: 2500, w3: 2000, w11: 6000, w12: 4000, w21: 5000, w22: 5000, platformFeeBps: 500, challengePeriod: 259200 }),
      "FactorOutOfRange"
    );
    await expectRevert(
      split.connect(admin).setParams({ w1: 5500, w2: 2500, w3: 2000, w11: 4000, w12: 6000, w21: 5000, w22: 5000, platformFeeBps: 2000, challengePeriod: 259200 }),
      "InvalidParams"
    );
  });

  it("完整链路：建作品 → 协作存证 → 定稿 → 量化份额 → 收益托管 → 自动分润 → 提取", async () => {
    const promptHash = ethers.keccak256(ethers.toUtf8Bytes("一只赛博朋克风格的猫，霓虹夜景，8k"));
    const tx = await evidence.connect(creator).createWork(promptHash, "ipfs://meta/work-1");
    await tx.wait();

    const workId = 1n;
    let w = await evidence.getWork(workId);
    expect(w.creator).to.equal(creator.address);
    expect(w.inputRoot).to.equal(promptHash);
    expect(w.status).to.equal(0n); // Draft
    expect(w.evidenceTotal).to.equal(1n); // 输入层 Prompt 自动上链

    // 登记三个协作者（链上协作协议）
    await evidence.connect(creator).addContributor(workId, prompter.address, 2); // PromptDesigner
    await evidence.connect(creator).addContributor(workId, editor.address, 5);   // Editor
    await evidence.connect(creator).addContributor(workId, operator.address, 6); // Operator
    await evidence.connect(prompter).acceptRole(workId);
    await evidence.connect(editor).acceptRole(workId);
    await evidence.connect(operator).acceptRole(workId);
    expect(await evidence.isActiveContributor(workId, prompter.address)).to.equal(true);
    expect(await evidence.isActiveContributor(workId, stranger.address)).to.equal(false);

    // 追加过程证据（参数 / 生成 / 编辑）
    await evidence.connect(prompter).addEvidence(workId, 1, ethers.id("params:cfg7:seed42"), "ipfs://e/params");
    await evidence.connect(creator).addEvidence(workId, 2, ethers.id("gen:v1"), "ipfs://e/gen1");
    await evidence.connect(editor).addEvidence(workId, 3, ethers.id("edit:inpaint-v2"), "ipfs://e/edit1");

    // 时序链完整性
    const [ok, badIdx] = await evidence.verifyChain(workId);
    expect(ok).to.equal(true);
    expect(badIdx).to.equal(0n);

    // 定稿
    const resultHash = ethers.id("final:cyberpunk-cat-v3");
    await evidence.connect(creator).finalizeWork(workId, resultHash, "ipfs://e/final");
    w = await evidence.getWork(workId);
    expect(w.status).to.equal(1n); // Finalized
    expect(w.resultHash).to.equal(resultHash);
    expect(w.processRoot).to.not.equal(ethers.ZeroHash);

    // 定稿后不可再追加证据
    await expectRevert(
      evidence.connect(editor).addEvidence(workId, 3, ethers.id("late"), "ipfs://e/late"),
      "InvalidStatus"
    );

    // 贡献量化上链
    const scores = [
      { account: creator.address, s11: 9000, s12: 8500, alpha: 10000, s21: 8000, s22: 7500, lambda: 9000, s3: 8000 },
      { account: prompter.address, s11: 9500, s12: 9000, alpha: 10000, s21: 8500, s22: 8000, lambda: 9500, s3: 7000 },
      { account: editor.address, s11: 6000, s12: 7000, alpha: 8500, s21: 9000, s22: 8500, lambda: 9500, s3: 7000 },
      { account: operator.address, s11: 5000, s12: 6000, alpha: 7000, s21: 7000, s22: 6000, lambda: 8000, s3: 9500 }
    ];
    const sheetHash = ethers.id("score-sheet:work-1:v1:json");
    await split.connect(admin).lockShares(workId, scores, sheetHash);

    const sheet = await split.getShareSheet(workId);
    expect(sheet.version).to.equal(1n);
    expect(sheet.sheetHash).to.equal(sheetHash);

    const expectedCi = scores.map((s) =>
      ciOf(
        {
          s11: BigInt(s.s11), s12: BigInt(s.s12), alpha: BigInt(s.alpha),
          s21: BigInt(s.s21), s22: BigInt(s.s22), lambda: BigInt(s.lambda), s3: BigInt(s.s3)
        },
        P
      )
    );
    const totalCi = expectedCi.reduce((a, b) => a + b, 0n);
    expect(sheet.totalCi).to.equal(totalCi);
    for (let i = 0; i < scores.length; i++) {
      expect(sheet.ci[i]).to.equal(expectedCi[i], `第 ${i} 个协作者 CI 不一致`);
    }

    // 收益托管（买家付款 1 ETH）
    await split.connect(buyer).depositRevenue(workId, { value: ethers.parseEther("1") });
    expect(await split.escrowOf(workId)).to.equal(ethers.parseEther("1"));

    // 全员确认份额 → 可跳过异议期立即结算
    for (const s of scores) {
      await split.connect(await ethers.getSigner(s.account)).consentShares(workId);
    }

    const [gross, fee, net, accounts, amounts] = await split.previewSettlement(workId);
    expect(gross).to.equal(ethers.parseEther("1"));
    expect(fee).to.equal(ethers.parseEther("0.05")); // 5% 平台费
    expect(net).to.equal(ethers.parseEther("0.95"));
    const expectAmounts = allocate(net, expectedCi, totalCi);
    for (let i = 0; i < expectAmounts.length; i++) {
      expect(amounts[i]).to.equal(expectAmounts[i], `第 ${i} 个协作者分润金额不一致`);
    }
    const sum = amounts.reduce((a, b) => a + b, 0n);
    expect(sum).to.equal(net); // 无资金滞留

    // 任意第三方可触发结算（去信任结算）
    await split.connect(stranger).settle(workId);

    // 平台费与协作者收益分别记账，可各自提取
    expect(await split.pendingWithdrawals(platform.address)).to.equal(fee);
    for (let i = 0; i < accounts.length; i++) {
      expect(await split.pendingWithdrawals(accounts[i])).to.equal(amounts[i]);
    }
    expect(await split.escrowOf(workId)).to.equal(0n);
    expect(await split.settleRound(workId)).to.equal(1n);

    // 作品状态自动流转为 Settled
    expect((await evidence.getWork(workId)).status).to.equal(2n);

    // 提取
    const before = await ethers.provider.getBalance(editor.address);
    const txw = await split.connect(editor).withdraw();
    const rc = await txw.wait();
    const gas = rc.gasUsed * rc.gasPrice;
    const after = await ethers.provider.getBalance(editor.address);
    expect(after - before + gas).to.equal(await split.pendingWithdrawals(editor.address).then(() => amounts[2]));
    expect(await split.pendingWithdrawals(editor.address)).to.equal(0n);

    // 二次提取应失败
    await expectRevert(split.connect(editor).withdraw(), "NothingToWithdraw");
  });

  it("同一份额表可服务多轮收益（版税模型，旧版一次性结清会卡死）", async () => {
    const workId = 1n;
    await evidence.connect(creator).createWork(ethers.id("prompt"), "ipfs://m");
    await evidence.connect(creator).addContributor(workId, prompter.address, 2);
    await evidence.connect(prompter).acceptRole(workId);
    await evidence.connect(creator).finalizeWork(workId, ethers.id("result"), "ipfs://f");

    const scores = [
      { account: creator.address, s11: 8000, s12: 8000, alpha: 10000, s21: 8000, s22: 8000, lambda: 9000, s3: 8000 },
      { account: prompter.address, s11: 9000, s12: 9000, alpha: 10000, s21: 9000, s22: 8000, lambda: 9000, s3: 8000 }
    ];
    await split.connect(admin).lockShares(workId, scores, ethers.id("sheet"));
    await split.connect(creator).consentShares(workId);
    await split.connect(prompter).consentShares(workId);

    // 第一轮
    await split.connect(buyer).depositRevenue(workId, { value: ethers.parseEther("0.4") });
    await split.settle(workId);
    const first = await split.pendingWithdrawals(creator.address);
    expect(first > 0n).to.equal(true);

    // 第二轮（同一作品再次授权变现）
    await split.connect(buyer).depositRevenue(workId, { value: ethers.parseEther("0.6") });
    await split.settle(workId);
    expect(await split.settleRound(workId)).to.equal(2n);
    expect((await split.pendingWithdrawals(creator.address)) > first).to.equal(true);
    expect(await split.totalSettled(workId)).to.equal(ethers.parseEther("1"));
    expect(await split.escrowOf(workId)).to.equal(0n);
  });

  it("异议期内不能结算；异议被驳回后恢复", async () => {
    const workId = 1n;
    await evidence.connect(creator).createWork(ethers.id("p"), "ipfs://m");
    await evidence.connect(creator).addContributor(workId, editor.address, 5);
    await evidence.connect(editor).acceptRole(workId);
    await evidence.connect(creator).finalizeWork(workId, ethers.id("r"), "ipfs://f");

    const scores = [
      { account: creator.address, s11: 8000, s12: 8000, alpha: 10000, s21: 8000, s22: 8000, lambda: 9000, s3: 8000 },
      { account: editor.address, s11: 7000, s12: 7000, alpha: 9000, s21: 9000, s22: 8000, lambda: 9000, s3: 8000 }
    ];
    await split.connect(admin).lockShares(workId, scores, ethers.id("sheet"));
    await split.connect(buyer).depositRevenue(workId, { value: ethers.parseEther("1") });

    // 异议期未过、未全员确认 → 拒绝结算
    await expectRevert(split.settle(workId), "ObjectionWindowOpen");

    // 提出异议 → 明确冻结
    await split.connect(editor).objectShares(workId, "我的迭代次数被低估");
    await network.provider.send("evm_increaseTime", [4 * 24 * 3600]);
    await network.provider.send("evm_mine");
    await expectRevert(split.settle(workId), "ObjectionPending");

    // 仲裁驳回异议 → 可结算
    await split.connect(arbiter).resolveObjection(workId, true);
    await split.settle(workId);
    expect(await split.settleRound(workId)).to.equal(1n);
  });

  it("作品被冻结（Disputed）时结算必须失败，资金安全留在托管", async () => {
    const workId = 1n;
    await evidence.connect(creator).createWork(ethers.id("p"), "ipfs://m");
    await evidence.connect(creator).addContributor(workId, editor.address, 5);
    await evidence.connect(editor).acceptRole(workId);
    await evidence.connect(creator).finalizeWork(workId, ethers.id("r"), "ipfs://f");

    await split.connect(admin).lockShares(
      workId,
      [
        { account: creator.address, s11: 8000, s12: 8000, alpha: 10000, s21: 8000, s22: 8000, lambda: 9000, s3: 8000 },
        { account: editor.address, s11: 8000, s12: 8000, alpha: 10000, s21: 8000, s22: 8000, lambda: 9000, s3: 8000 }
      ],
      ethers.id("sheet")
    );
    await split.connect(creator).consentShares(workId);
    await split.connect(editor).consentShares(workId);
    await split.connect(buyer).depositRevenue(workId, { value: ethers.parseEther("1") });

    // 协作者发起纠纷
    await evidence.connect(editor).raiseDispute(workId, "分成比例与约定不符");
    expect((await evidence.getWork(workId)).status).to.equal(3n); // Disputed
    await expectRevert(split.settle(workId), "InvalidWorkStatus");
    expect(await split.escrowOf(workId)).to.equal(ethers.parseEther("1"));

    // 仲裁解除冻结后正常结算
    await evidence.connect(arbiter).resolveDispute(workId, 1); // Finalized
    await split.settle(workId);
    expect(await split.settleRound(workId)).to.equal(1n);
  });

  it("权限与状态校验：未定稿不能分润、非协作者不能写证据、未接受角色不能写证据", async () => {
    const workId = 1n;
    await evidence.connect(creator).createWork(ethers.id("p"), "ipfs://m");

    // 未定稿直接锁份额 → 拒绝
    await expectRevert(
      split.connect(admin).lockShares(
        workId,
        [{ account: creator.address, s11: 8000, s12: 8000, alpha: 10000, s21: 8000, s22: 8000, lambda: 9000, s3: 8000 }],
        ethers.id("s")
      ),
      "InvalidWorkStatus"
    );

    // 非协作者写证据 → 拒绝
    await expectRevert(
      evidence.connect(stranger).addEvidence(workId, 3, ethers.id("x"), "ipfs://x"),
      "NotContributor"
    );

    // 被登记但未接受角色 → 拒绝
    await evidence.connect(creator).addContributor(workId, editor.address, 5);
    await expectRevert(
      evidence.connect(editor).addEvidence(workId, 3, ethers.id("x"), "ipfs://x"),
      "RoleNotAccepted"
    );

    // 非管理员改参数 → 拒绝
    await expectRevert(
      split.connect(stranger).setParams({ w1: 5500, w2: 2500, w3: 2000, w11: 4000, w12: 6000, w21: 5000, w22: 5000, platformFeeBps: 500, challengePeriod: 259200 }),
      "NotAdmin"
    );

    // 份额表里塞非协作者 → 拒绝
    await evidence.connect(creator).finalizeWork(workId, ethers.id("r"), "ipfs://f");
    await expectRevert(
      split.connect(admin).lockShares(
        workId,
        [
          { account: creator.address, s11: 8000, s12: 8000, alpha: 10000, s21: 8000, s22: 8000, lambda: 9000, s3: 8000 },
          { account: stranger.address, s11: 8000, s12: 8000, alpha: 10000, s21: 8000, s22: 8000, lambda: 9000, s3: 8000 }
        ],
        ethers.id("s")
      ),
      "NotListedContributor"
    );

    // 重复账号 → 拒绝
    await expectRevert(
      split.connect(admin).lockShares(
        workId,
        [
          { account: creator.address, s11: 8000, s12: 8000, alpha: 10000, s21: 8000, s22: 8000, lambda: 9000, s3: 8000 },
          { account: creator.address, s11: 8000, s12: 8000, alpha: 10000, s21: 8000, s22: 8000, lambda: 9000, s3: 8000 }
        ],
        ethers.id("s")
      ),
      "DuplicateAccount"
    );
  });

  it("精度与资金守恒：任意金额、任意人数都不留尘埃", async () => {
    const workId = 1n;
    await evidence.connect(creator).createWork(ethers.id("p"), "ipfs://m");
    const trio = [prompter, editor, operator];
    for (const t of trio) {
      await evidence.connect(creator).addContributor(workId, t.address, 5);
      await evidence.connect(t).acceptRole(workId);
    }
    await evidence.connect(creator).finalizeWork(workId, ethers.id("r"), "ipfs://f");

    const scores = [creator, ...trio].map((s, i) => ({
      account: s.address,
      s11: 7000 + i * 500,
      s12: 7000 + i * 400,
      alpha: 9000,
      s21: 8000,
      s22: 7500,
      lambda: 9000,
      s3: 7500
    }));
    await split.connect(admin).lockShares(workId, scores, ethers.id("sheet"));
    for (const s of scores) await split.connect(await ethers.getSigner(s.account)).consentShares(workId);

    // 极端小额：1000 wei / 4 人
    await split.depositRevenue(workId, { value: 1000n });
    const [, , net, , amounts] = await split.previewSettlement(workId);
    expect(amounts.reduce((a, b) => a + b, 0n)).to.equal(net);
    await split.settle(workId);
    expect(await split.pendingWithdrawals(platform.address) + amounts.reduce((a, b) => a + b, 0n))
      .to.equal(1000n);
  });

  it("存证校验：哈希比对与篡改识别", async () => {
    const workId = 1n;
    const dataHash = ethers.id("原始提示词内容");
    await evidence.connect(creator).createWork(dataHash, "ipfs://m");

    const ids = await evidence.getWorkEvidenceIds(workId, 0, 10);
    expect(ids.length).to.equal(1);
    expect(await evidence.verifyEvidence(ids[0], dataHash)).to.equal(true);
    expect(await evidence.verifyEvidence(ids[0], ethers.id("被篡改的内容"))).to.equal(false);

    // 链式指针：第二条证据的 prevChainHash 必须等于第一条 chainHash
    await evidence.connect(creator).addEvidence(workId, 1, ethers.id("params"), "ipfs://p");
    const ids2 = await evidence.getWorkEvidenceIds(workId, 0, 10);
    const e1 = await evidence.getEvidence(ids2[0]);
    const e2 = await evidence.getEvidence(ids2[1]);
    expect(e2.prevChainHash).to.equal(e1.chainHash);
    expect((await evidence.getChainHead(workId))).to.equal(e2.chainHash);

    // 契约外复算 chainHash，验证前端/第三方可独立校验
    const recomputed = ethers.keccak256(
      ethers.AbiCoder.defaultAbiCoder().encode(
        ["uint256", "uint256", "uint8", "bytes32", "address", "uint64", "bytes32"],
        [e2.workId, e2.evidenceId, e2.etype, e2.dataHash, e2.operator, e2.timestamp, e2.prevChainHash]
      )
    );
    expect(recomputed).to.equal(e2.chainHash);
  });
});
