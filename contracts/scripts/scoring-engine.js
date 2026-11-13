/**
 * 贡献评分引擎（参考实现，Node.js）
 * ---------------------------------------------------------------------------
 * 作用：把"创作过程存证数据"折算成合约所需的七项指标（S11, S12, α, S21, S22, λ, S3）。
 *
 * 为什么必须要有它：
 *   合约里的 CI 公式是"计算器"，但它不会自己知道某人的提示词写得好不好。
 *   如果这七项指标是拍脑袋填的，分润再"链上可复核"也没有意义。
 *   所以本引擎用**可解释、可复现、可审计**的规则把过程数据折算成分数，
 *   并把每项分数的来源（breakdown）一起返回，前端可以显示"我为什么得 89 分"。
 *
 * 部署关系：
 *   后端调用 scoreWork() → 得到 scores + sheetHash → 调 lockShares(workId, scores, sheetHash)
 *   链下评分明细 JSON 存数据库/IPFS，其 keccak256 必须等于 sheetHash，事后可逐项复核。
 */

const { keccak256, toUtf8Bytes } = require("ethers");

/* -------------------------------------------------------------------------- */
/*                                 可调参数                                    */
/* -------------------------------------------------------------------------- */
const CONFIG = {
  // S11 三项子指标的权重（和为 1）
  s11: { structure: 0.45, length: 0.25, iteration: 0.30 },
  // S12：最终采纳占 70%，历史采纳率占 30%
  s12: { finalAdopted: 0.7, adoptionRate: 0.3 },
  // S21：类型覆盖 / 可验证性 / 时间连续性
  s21: { coverage: 40, verifiable: 30, continuity: 30 },
  // S22 迭代得分：30 分起步，每次有效迭代 +12 分，上限 100
  s22: { base: 30, perIteration: 12, cap: 100 },
  // λ 时序衰减：0.6 + 0.4 × 有效迭代占比
  lambda: { base: 0.6, span: 0.4 },
  // S3 价值落地：60% 角色价值基准 + 40% 平台运营数据
  s3: { roleWeight: 0.6, dataWeight: 0.4, dataBase: 50, dataPerSale: 2 },
  // 角色价值基准（0~100），可按业务调整
  roleBaseline: {
    1: 78, // 创意发起者
    2: 80, // 提示词设计者
    3: 62, // 模型/技术提供方
    4: 70, // 生成执行者
    5: 74, // 后期编辑者
    6: 90  // 运营推广者
  },
  // 提示词结构要素（命中几类算几分）
  promptFacets: [
    ["主体", /猫|狗|人|女|男|城|山|海|花|树|建筑|角色|portrait|cat|dog|city|landscape|character/i],
    ["风格", /赛博|朋克|国风|水彩|油画|写实|动漫|像素|极简|cyberpunk|anime|realistic|watercolor|oil/i],
    ["光影", /光|影|霓虹|逆光|柔光|照明|light|neon|shadow|glow/i],
    ["构图", /构图|特写|全景|俯视|仰视|三分|对称|close-up|wide|composition|symmetry/i],
    ["镜头", /mm|镜头|焦段|广角|长焦|lens|focal|bokeh|景深/i],
    ["画质", /8k|4k|高清|超精细|细节|渲染|hdr|detailed|high quality|octane/i]
  ]
};

/* -------------------------------------------------------------------------- */
/*                                  工具函数                                   */
/* -------------------------------------------------------------------------- */
const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
/** 得分（0~100）转合约使用的 1e4 定点整数：100 分 -> 10000 */
const toBps = (v) => Math.round(clamp(v, 0, 100) * 100);
/** 系数（0~1）转合约使用的 1e4 定点整数：1.0 -> 10000 */
const toFactorBps = (v) => Math.round(clamp(v, 0, 1) * 10000);

/** 递归排序键的规范化 JSON —— 保证链下 sheetHash 可被任何人复现 */
function canonicalize(obj) {
  if (Array.isArray(obj)) return obj.map(canonicalize);
  if (obj && typeof obj === "object") {
    return Object.keys(obj)
      .sort()
      .reduce((acc, k) => {
        acc[k] = canonicalize(obj[k]);
        return acc;
      }, {});
  }
  return obj;
}


const ROLE_NAME = {
  0: "未登记",
  1: "创意发起者",
  2: "提示词设计者",
  3: "模型/技术提供方",
  4: "生成执行者",
  5: "后期编辑者",
  6: "运营推广者"
};

/* -------------------------------------------------------------------------- */
/*                               各维度打分                                    */
/* -------------------------------------------------------------------------- */

/** 提示词结构完整度：命中 6 类要素各得 1/6 */
function structureScore(promptText = "") {
  const hits = CONFIG.promptFacets.filter(([, re]) => re.test(promptText)).map(([k]) => k);
  return { score: (hits.length / CONFIG.promptFacets.length) * 100, hits };
}

/** 篇幅适配度：中文 30~120 字 / 英文 20~80 词为满分区间，偏离线性扣分 */
function lengthScore(promptText = "") {
  const isCJK = /[\u4e00-\u9fa5]/.test(promptText);
  const n = isCJK
    ? (promptText.match(/[\u4e00-\u9fa5]/g) || []).length
    : promptText.trim().split(/\s+/).filter(Boolean).length;
  const [lo, hi] = isCJK ? [30, 120] : [20, 80];
  if (n === 0) return 0;
  if (n >= lo && n <= hi) return 100;
  if (n < lo) return (n / lo) * 100;
  return clamp(100 - ((n - hi) / hi) * 40, 40, 100); // 超长轻微扣分，冗余提示词反而降低可控性
}

/** 迭代深度：该成员的提示词版本数 */
function iterationScore(versions) {
  return clamp(40 + 15 * versions, 0, 100);
}

/** 操作记录完整性 S21 */
function completenessScore(records, allRecords) {
  // ① 类型覆盖：该成员是否留下 Prompt / Parameter / Generation / Edit 中的多种证据
  const types = new Set(records.map((r) => r.etype));
  const coverage = (types.size / 4) * CONFIG.s21.coverage;

  // ② 可验证性：每条证据都有链下定位（uri）且哈希非空
  const valid = records.filter(
    (r) => r.uri && r.dataHash && r.dataHash !== "0x" + "0".repeat(64)
  ).length;
  const verifiable = records.length ? (valid / records.length) * CONFIG.s21.verifiable : 0;

  // ③ 时间连续性：相邻两条证据间隔 < 30 分钟记满分
  const ts = records.map((r) => r.timestamp).sort((a, b) => a - b);
  let gaps = 0;
  let gapsOk = 0;
  for (let i = 1; i < ts.length; i++) {
    gaps++;
    if (ts[i] - ts[i - 1] <= 1800) gapsOk++;
  }
  const continuity = gaps ? (gapsOk / gaps) * CONFIG.s21.continuity : CONFIG.s21.continuity * 0.6;

  return {
    score: coverage + verifiable + continuity,
    breakdown: {
      类型覆盖: +coverage.toFixed(2),
      可验证性: +verifiable.toFixed(2),
      时间连续性: +continuity.toFixed(2)
    }
  };
}

/** 价值落地 S3：角色基准 + 平台运营数据（销量 / 授权次数） */
function valueScore(role, salesCount = 0) {
  const baseline = CONFIG.roleBaseline[role] ?? 70;
  const dataScore = salesCount > 0 ? clamp(CONFIG.s3.dataBase + salesCount * CONFIG.s3.dataPerSale, 0, 100) : baseline;
  const score = CONFIG.s3.roleWeight * baseline + CONFIG.s3.dataWeight * dataScore;
  return {
    score,
    breakdown: { 角色价值基准: baseline, 平台运营数据: +dataScore.toFixed(2) }
  };
}

/* -------------------------------------------------------------------------- */
/*                                主流程                                       */
/* -------------------------------------------------------------------------- */

/**
 * @param {Object[]} records 该作品的全部证据（按 evidenceId 升序）
 *        每条：{ evidenceId, etype, operator, dataHash, uri, timestamp, payload }
 *        payload 为链下原文解析后的对象（提示词文本、参数等），可缺省
 * @param {Object[]} contributors [{ account, role }]
 * @param {Object} ctx { finalEvidenceId, salesCount: { [account]: number },
 *                       adoption: { [account]: { versions, adopted, isFinal } } }
 * @returns {{ scores: Object[], sheet: Object, sheetHash: string }}
 */
function scoreWork(records, contributors, ctx = {}) {
  const scores = [];
  const detail = [];

  for (const c of contributors) {
    const key = c.account.toLowerCase();
    const mine = records.filter((r) => r.operator.toLowerCase() === key);

    /* --------------------------- 核心产出质量 S11 -------------------------- */
    // 语义：S11 = 该角色"核心产出物"的质量。
    //   · 提示词类角色（创意发起/提示词设计）→ 直接评提示词本身
    //   · 其他角色（模型/生成/编辑/运营）→ 用其产出物的可量化代理指标评定，
    //     否则编辑者和运营者的核心维度会被直接判 0，导致 55% 的权重凭空消失（这是原公式落地的最大坑）
    const prompts = mine
      .filter((r) => r.etype === 0)
      .map((r) => (r.payload && r.payload.prompt) || "");
    const bestPrompt = prompts.slice().sort((a, b) => b.length - a.length)[0] || "";
    const st = structureScore(bestPrompt);
    const ln = lengthScore(bestPrompt);
    const it = iterationScore(prompts.length);
    const promptQuality = prompts.length
      ? CONFIG.s11.structure * st.score + CONFIG.s11.length * ln + CONFIG.s11.iteration * it
      : 0;

    const paramCount = mine.filter((r) => r.etype === 1).length;
    const genCount = mine.filter((r) => r.etype === 2).length;
    const editCount = mine.filter((r) => r.etype === 3).length;
    const preCtx = (ctx.adoption && ctx.adoption[c.account]) || {};
    const preAdoptRate = preCtx.versions
      ? clamp(((preCtx.adopted || 0) / preCtx.versions) * 100, 0, 100)
      : 0;

    let s11 = 0;
    if (promptQuality > 0) {
      s11 = promptQuality;
    } else if (c.role === 3) {
      // 模型/技术提供：参数完备性 + 是否配套生成
      s11 = clamp(50 + (paramCount > 0 ? 30 : 0) + (genCount > 0 ? 15 : 0), 0, 100);
    } else if (c.role === 4) {
      // 生成执行：有效生成产出量
      s11 = clamp(55 + Math.min(genCount, 4) * 10, 0, 100);
    } else if (c.role === 5) {
      // 后期编辑：编辑工作量 + 采纳率
      s11 = clamp(45 + Math.min(editCount, 4) * 8 + preAdoptRate * 0.2, 0, 100);
    } else if (c.role === 6) {
      // 运营推广：素材与发布产出
      s11 = clamp(55 + (mine.length > 0 ? 15 : 0), 0, 100);
    }

    /* --------------------------- 版本采纳 S12 ----------------------------- */
    const adoption = (ctx.adoption && ctx.adoption[c.account]) || {};
    const finalAdopted = adoption.isFinal ? 100 : 0;
    const adoptionRate = adoption.versions
      ? clamp(((adoption.adopted || 0) / adoption.versions) * 100, 0, 100)
      : 0;
    const s12 = CONFIG.s12.finalAdopted * finalAdopted + CONFIG.s12.adoptionRate * adoptionRate;

    /* ---------------------------- 采纳因子 α ------------------------------ */
    // 最终版本被采纳 = 1.0；有版本被采纳 = 0.85；仅试验性 = 0.6；无任何产出 = 0.4
    let alpha = 0.4;
    if (adoption.isFinal) alpha = 1.0;
    else if ((adoption.adopted || 0) > 0) alpha = 0.85;
    else if (s11 > 0) alpha = 0.6;

    /* ---------------------------- 完整性 S21 ------------------------------ */
    const comp = completenessScore(mine, records);

    /* ---------------------------- 迭代 S22 -------------------------------- */
    const validIterations = adoption.adopted || Math.max(0, mine.filter((r) => r.etype === 3).length);
    const s22 = clamp(CONFIG.s22.base + CONFIG.s22.perIteration * validIterations, 0, CONFIG.s22.cap);

    /* ------------------------------ 衰减 λ -------------------------------- */
    const totalIter = Math.max(mine.length, 1);
    const effectiveRatio = clamp(validIterations / totalIter, 0, 1);
    const lambda = CONFIG.lambda.base + CONFIG.lambda.span * effectiveRatio;

    /* ------------------------------ 价值 S3 ------------------------------- */
    const sales = (ctx.salesCount && ctx.salesCount[c.account]) || 0;
    const val = valueScore(c.role, sales);

    const row = {
      account: c.account,
      s11: toBps(s11),
      s12: toBps(s12),
      alpha: toFactorBps(alpha),
      s21: toBps(comp.score),
      s22: toBps(s22),
      lambda: toFactorBps(lambda),
      s3: toBps(val.score)
    };
    scores.push(row);

    detail.push({
      account: c.account,
      role: ROLE_NAME[c.role] || c.role,
      evidenceCount: mine.length,
      raw: {
        S11: +s11.toFixed(2),
        S12: +s12.toFixed(2),
        alpha: +alpha.toFixed(2),
        S21: +comp.score.toFixed(2),
        S22: +s22.toFixed(2),
        lambda: +lambda.toFixed(3),
        S3: +val.score.toFixed(2)
      },
      breakdown: {
        提示词结构: { 得分: +st.score.toFixed(2), 命中要素: st.hits },
        提示词篇幅: +ln.toFixed(2),
        提示词迭代深度: +it.toFixed(2),
        版本采纳率: +adoptionRate.toFixed(2),
        最终版本是否采纳: finalAdopted === 100,
        操作记录: comp.breakdown,
        有效迭代次数: validIterations,
        时序衰减系数: +lambda.toFixed(3),
        价值落地: val.breakdown
      },
      contractInput: row
    });
  }

  const sheet = {
    version: 1,
    generatedAt: new Date().toISOString(),
    config: CONFIG,
    contributors: detail
  };
  // 递归排序键后取哈希，保证链上 sheetHash 可被任何人独立复现
  const canonical = JSON.stringify(canonicalize(sheet));
  return { scores, sheet, sheetHash: keccak256(toUtf8Bytes(canonical)), detail };
}

module.exports = { scoreWork, CONFIG, ROLE_NAME, toBps };

/* -------------------------------------------------------------------------- */
/*                       直接运行：node scripts/scoring-engine.js               */
/* -------------------------------------------------------------------------- */
if (require.main === module) {
  const t0 = Date.now();
  const now = Math.floor(Date.now() / 1000);
  const A = "0x90F79bf6EB2c4f870365E785982E1f101E93b906";
  const B = "0x15d34AAf54267DB7D7c367839AAf71A00a2C6A65";
  const C = "0x9965507D1a55bcC2695C58ba16FB37d819B0A4dc";
  const D = "0x976EA74026E726554dB657fA54763abd0C3a0aa9";

  const records = [
    { evidenceId: 1, etype: 0, operator: A, uri: "ipfs://p1", timestamp: now - 7200, dataHash: "0x1", payload: { prompt: "一只黑猫" } },
    { evidenceId: 2, etype: 0, operator: B, uri: "ipfs://p2", timestamp: now - 7000, dataHash: "0x2", payload: { prompt: "赛博朋克风格的黑猫，霓虹灯雨夜街道，电影感布光，85mm 镜头，超精细毛发，8k" } },
    { evidenceId: 3, etype: 1, operator: B, uri: "ipfs://p3", timestamp: now - 6900, dataHash: "0x3", payload: { cfg: 8.5, steps: 38 } },
    { evidenceId: 4, etype: 2, operator: A, uri: "ipfs://p4", timestamp: now - 6600, dataHash: "0x4" },
    { evidenceId: 5, etype: 3, operator: C, uri: "ipfs://p5", timestamp: now - 3600, dataHash: "0x5" },
    { evidenceId: 6, etype: 3, operator: C, uri: "ipfs://p6", timestamp: now - 3500, dataHash: "0x6" }
  ];
  const contributors = [
    { account: A, role: 1 },
    { account: B, role: 2 },
    { account: C, role: 5 },
    { account: D, role: 6 }
  ];
  const ctx = {
    adoption: {
      [A]: { versions: 1, adopted: 1, isFinal: false },
      [B]: { versions: 2, adopted: 2, isFinal: true },
      [C]: { versions: 0, adopted: 2, isFinal: false }
    },
    salesCount: { [D]: 12, [C]: 3 }
  };

  const r = scoreWork(records, contributors, ctx);
  console.log("=== 链下评分结果（可直接喂给 lockShares）===\n");
  for (const d of r.detail) {
    console.log(`${d.role}  ${d.account.slice(0, 10)}…  证据 ${d.evidenceCount} 条`);
    console.log("  原始分:", d.raw);
    console.log("  依据  :", JSON.stringify(d.breakdown, null, 0).slice(0, 200), "…\n");
  }
  console.log("合约入参 scores =");
  console.log(JSON.stringify(r.scores, null, 2));
  console.log("\nsheetHash =", r.sheetHash);
  console.log("耗时:", Date.now() - t0, "ms");
}
