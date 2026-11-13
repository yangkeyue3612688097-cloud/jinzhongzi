// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

/**
 * @title  ContributionMath
 * @notice 贡献量化公式的链上实现（定点数运算，全定点 1e4 = 100.00%）
 * @dev    严格对应大创申请书 3.（2）贡献量化规则：
 *
 *         S1 = α × (W11·S11 + W12·S12)        核心权重维度（提示词质量 / 版本采纳）
 *         S2 = λ × (W21·S21 + W22·S22)        过程投入维度（操作记录完整性 / 有效迭代次数）
 *         S3                                   价值落地维度（最终成果使用情况，直接 0~100）
 *         CI = W1·S1 + W2·S2 + W3·S3           综合贡献指数，取值 0~100
 *
 *         精度约定：所有得分与系数用 1e4 定点表示，即 10000 == 100.00 分，5000 == 0.5000
 *         所有除法均向下取整，误差上界 < 3e-4 分（可忽略且完全确定性，链上链下结果一致）
 */
library ContributionMath {
    /// @notice 定点精度基数，1e4 表示 100.00%
    uint256 internal constant BPS = 10_000;

    error ScoreOutOfRange();
    error FactorOutOfRange();
    error WeightSumMismatch();
    error EmptyArray();
    error ZeroTotalContribution();

    /* ------------------------------------------------------------------ */
    /*                              维度得分                                */
    /* ------------------------------------------------------------------ */

    /**
     * @notice 核心权重维度得分 S1 = α × (W11·S11 + W12·S12)
     * @param alpha 版本采纳权重因子 α ∈ [0,1]（1e4 定点），最终采纳版本为 10000
     * @param w11   提示词质量权重 W11（1e4 定点）
     * @param s11   提示词质量得分 S11 ∈ [0,100]
     * @param w12   版本采纳权重 W12（1e4 定点）
     * @param s12   版本采纳得分 S12 ∈ [0,100]
     * @return      核心维度得分 S1 ∈ [0,100]
     */
    function coreScore(
        uint256 alpha,
        uint256 w11,
        uint256 s11,
        uint256 w12,
        uint256 s12
    ) internal pure returns (uint256) {
        if (s11 > BPS || s12 > BPS) revert ScoreOutOfRange();
        if (alpha > BPS) revert FactorOutOfRange();
        // (W11·S11 + W12·S12) ∈ [0, 2e8]，除以 BPS 归一到 [0, 1e4]
        uint256 inner = (w11 * s11 + w12 * s12) / BPS;
        return (alpha * inner) / BPS;
    }

    /**
     * @notice 过程投入维度得分 S2 = λ × (W21·S21 + W22·S22)
     * @param lambda 时序衰减系数 λ ∈ [0,1]（1e4 定点），迭代越靠后 / 无效迭代越多则越小
     * @param w21    操作记录完整性权重 W21
     * @param s21    操作记录完整性得分 S21 ∈ [0,100]
     * @param w22    有效迭代次数权重 W22
     * @param s22    有效迭代次数得分 S22 ∈ [0,100]
     */
    function processScore(
        uint256 lambda,
        uint256 w21,
        uint256 s21,
        uint256 w22,
        uint256 s22
    ) internal pure returns (uint256) {
        if (s21 > BPS || s22 > BPS) revert ScoreOutOfRange();
        if (lambda > BPS) revert FactorOutOfRange();
        uint256 inner = (w21 * s21 + w22 * s22) / BPS;
        return (lambda * inner) / BPS;
    }

    /**
     * @notice 综合贡献指数 CI = W1·S1 + W2·S2 + W3·S3
     * @param w1,W1 维度权重（W1+W2+W3 必须 = 10000）
     * @param s1    S1 核心维度得分
     * @param s2    S2 过程维度得分
     * @param s3    S3 价值落地得分（0~100，无需修正系数）
     * @return      CI ∈ [0,100]（1e4 定点，10000 表示满分 100）
     */
    function compositeIndex(
        uint256 w1,
        uint256 s1,
        uint256 w2,
        uint256 s2,
        uint256 w3,
        uint256 s3
    ) internal pure returns (uint256) {
        if (s1 > BPS || s2 > BPS || s3 > BPS) revert ScoreOutOfRange();
        return (w1 * s1 + w2 * s2 + w3 * s3) / BPS;
    }

    /* ------------------------------------------------------------------ */
    /*                              分润换算                                */
    /* ------------------------------------------------------------------ */

    /**
     * @notice 将收益总额按各主体 CI 占比切分（比例 = CI_i / ΣCI）
     * @dev    采用「先算后转 + 余数归入最大贡献者」策略，保证 Σamounts == totalAmount，
     *         不产生滞留资金（旧版合约按万分比整除会留下不可提取的尘埃）
     * @param totalAmount 待分配金额（wei）
     * @param cis         各主体综合贡献指数数组
     * @param totalCi     ΣCI（调用方已校验 > 0）
     */
    function allocate(
        uint256 totalAmount,
        uint256[] memory cis,
        uint256 totalCi
    ) internal pure returns (uint256[] memory amounts) {
        if (cis.length == 0) revert EmptyArray();
        if (totalCi == 0) revert ZeroTotalContribution();

        amounts = new uint256[](cis.length);
        uint256 distributed;
        uint256 maxIdx;
        uint256 maxCi;

        for (uint256 i = 0; i < cis.length; ++i) {
            uint256 v = (totalAmount * cis[i]) / totalCi;
            amounts[i] = v;
            distributed += v;
            if (cis[i] > maxCi) {
                maxCi = cis[i];
                maxIdx = i;
            }
        }

        // 整除产生的尘埃（< cis.length wei）补给最大贡献者，确保资金零滞留
        if (distributed < totalAmount) {
            amounts[maxIdx] += totalAmount - distributed;
        }
    }

    /* ------------------------------------------------------------------ */
    /*                              参数校验                                */
    /* ------------------------------------------------------------------ */

    /// @notice 校验三维度权重：和为 10000，且 W1∈[0.5,0.6]、W2∈[0.2,0.3]、W3∈[0.1,0.2]
    function validateDimensionWeights(
        uint256 w1,
        uint256 w2,
        uint256 w3
    ) internal pure returns (bool) {
        if (w1 + w2 + w3 != BPS) revert WeightSumMismatch();
        if (w1 < 5000 || w1 > 6000) revert FactorOutOfRange();
        if (w2 < 2000 || w2 > 3000) revert FactorOutOfRange();
        if (w3 < 1000 || w3 > 2000) revert FactorOutOfRange();
        return true;
    }

    /// @notice 校验核心维度内部权重：W11+W12=10000 且 W12 严格大于 W11（贴合"版本采纳为核心"）
    function validateCoreWeights(uint256 w11, uint256 w12) internal pure returns (bool) {
        if (w11 + w12 != BPS) revert WeightSumMismatch();
        if (w12 <= w11) revert FactorOutOfRange();
        return true;
    }

    /// @notice 校验过程维度内部权重：W21+W22=10000
    function validateProcessWeights(uint256 w21, uint256 w22) internal pure returns (bool) {
        if (w21 + w22 != BPS) revert WeightSumMismatch();
        return true;
    }
}
