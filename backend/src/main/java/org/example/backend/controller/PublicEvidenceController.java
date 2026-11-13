package org.example.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.backend.common.Result;
import org.example.backend.chain.ChainAnchor;
import org.example.backend.chain.ChainAnchorRequest;
import org.example.backend.chain.ChainEncoding;
import org.example.backend.dto.PublicChainVerificationVO;
import org.example.backend.dto.PublicEvidenceVO;
import org.example.backend.entity.ChainAnchorRecord;
import org.example.backend.entity.CollaborationAgreement;
import org.example.backend.entity.Evidence;
import org.example.backend.entity.RevenueEvent;
import org.example.backend.entity.SettlementReceipt;
import org.example.backend.entity.WorkVersion;
import org.example.backend.mapper.CollaborationAgreementMapper;
import org.example.backend.mapper.RevenueEventMapper;
import org.example.backend.mapper.SettlementReceiptMapper;
import org.example.backend.mapper.WorkVersionMapper;
import org.example.backend.service.BlockchainService;
import org.example.backend.service.BusinessAnchorService;
import org.example.backend.service.EvidenceService;
import org.example.backend.util.CertificatePdfBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public verification endpoints. These do not require a login and can be
 * shared with collaborators, buyers, or contest judges.
 */
@RestController
@RequestMapping("/public")
public class PublicEvidenceController {

    @Autowired
    private EvidenceService evidenceService;

    @Autowired
    private BlockchainService blockchainService;

    @Autowired
    private BusinessAnchorService businessAnchorService;

    @Autowired
    private CollaborationAgreementMapper agreementMapper;

    @Autowired
    private RevenueEventMapper revenueEventMapper;

    @Autowired
    private SettlementReceiptMapper settlementReceiptMapper;

    @Autowired
    private WorkVersionMapper workVersionMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.public-base-url:}")
    private String publicBaseUrl;

    @GetMapping("/evidence/{dataHash}")
    public Result<PublicEvidenceVO> getEvidence(@PathVariable String dataHash) {
        PublicEvidenceVO vo = evidenceService.publicView(dataHash);
        if (vo == null) {
            return Result.error(404, "Evidence not found");
        }
        return Result.success(vo);
    }

    @GetMapping("/chain-status")
    public Result<Map<String, Object>> chainStatus() {
        return Result.success(blockchainService.status());
    }

    @GetMapping("/chain/verify")
    public Result<PublicChainVerificationVO> verifyChainAnchor(
            @RequestParam String businessType,
            @RequestParam Long businessId,
            @RequestParam String dataHash) {
        String normalizedHash;
        String normalizedBusinessType;
        try {
            normalizedHash = ChainEncoding.normalizeSha256(dataHash);
            normalizedBusinessType = ChainEncoding.normalizeBusinessType(businessType);
            ChainEncoding.businessTypeCode(normalizedBusinessType);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }

        PublicChainVerificationVO result = new PublicChainVerificationVO();
        result.setBusinessType(normalizedBusinessType);
        result.setBusinessId(businessId);
        result.setDataHash(normalizedHash);
        result.setMode(blockchainService.mode());
        result.setLocalEvidenceFound(
                localBusinessFactExists(normalizedBusinessType, businessId, normalizedHash));
        result.setVerificationAvailable("fisco".equalsIgnoreCase(blockchainService.mode()));

        ChainAnchorRecord anchorRecord = findAnchor(
                normalizedBusinessType, businessId, normalizedHash);
        if (anchorRecord != null) {
            result.setAnchorFound(true);
            result.setAnchorStatus(anchorRecord.getChainStatus());
            result.setAnchorMode(anchorRecord.getChainMode());
            result.setBlockchainTxHash(anchorRecord.getBlockchainTxHash());
            result.setBlockNumber(anchorRecord.getBlockNumber());
        }

        if (!result.isVerificationAvailable()) {
            if (!result.isLocalEvidenceFound()) {
                result.setMessage("未找到对应的本地业务事实，无法证明该哈希来自当前系统");
            } else if (!result.isAnchorFound()) {
                result.setMessage("本地业务事实存在，但缺少待上链记录");
            } else {
                result.setMessage("本地业务事实和待上链记录已就绪，等待 FISCO 环境确认");
            }
            return Result.success(result);
        }

        ChainAnchor verification = blockchainService.verify(
                new ChainAnchorRequest(businessId, normalizedHash, normalizedBusinessType, ""));
        result.setOnChainVerified(Boolean.TRUE.equals(verification.getVerified()));
        result.setMessage(verification.getMessage());
        return Result.success(result);
    }

    @GetMapping("/evidence/{dataHash}/manifest")
    public ResponseEntity<byte[]> downloadManifest(@PathVariable String dataHash) {
        PublicEvidenceVO vo = evidenceService.publicView(dataHash);
        if (vo == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> manifest = buildManifest(vo);
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(manifest);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=manifest-" + dataHash + ".json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/evidence/{dataHash}/certificate")
    public ResponseEntity<byte[]> certificate(@PathVariable String dataHash,
                                              HttpServletRequest request) {
        PublicEvidenceVO vo = evidenceService.publicView(dataHash);
        if (vo == null) {
            return ResponseEntity.notFound().build();
        }
        byte[] pdf = CertificatePdfBuilder.build(vo, verifyUrl(request, dataHash));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=certificate-" + dataHash + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private Map<String, Object> buildManifest(PublicEvidenceVO vo) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schema", "aic-provenance-manifest/v1");
        manifest.put("dataHash", vo.getDataHash());
        manifest.put("hashAlgorithm", vo.getHashAlgorithm());
        manifest.put("title", vo.getTitle());
        manifest.put("authorName", vo.getAuthorName());
        manifest.put("fileType", vo.getFileType());
        manifest.put("fileHash", vo.getFileHash());
        manifest.put("creatorAddress", vo.getCreatorAddress());
        manifest.put("creatorSignature", vo.getCreatorSignature());
        manifest.put("signatureVerified", vo.isSignatureVerified());
        manifest.put("createTime", vo.getCreateTime());
        manifest.put("chainStatus", vo.getChainStatus());
        manifest.put("chainMode", vo.getChainMode());
        manifest.put("blockchainTxHash", vo.getBlockchainTxHash());
        manifest.put("blockNumber", vo.getBlockNumber());
        manifest.put("generation", vo.getGeneration());
        manifest.put("canonicalPayload", vo.getCanonicalPayload());
        return manifest;
    }

    private ChainAnchorRecord findAnchor(String businessType, Long businessId, String dataHash) {
        List<ChainAnchorRecord> records = businessAnchorService.list(businessType, businessId);
        for (ChainAnchorRecord record : records) {
            if (record.getDataHash() != null && record.getDataHash().equalsIgnoreCase(dataHash)) {
                return record;
            }
        }
        return null;
    }

    private boolean localBusinessFactExists(String businessType, Long businessId, String dataHash) {
        switch (businessType) {
            case ChainAnchorRecord.TYPE_AGREEMENT:
                CollaborationAgreement agreement = agreementMapper.selectById(businessId);
                return agreement != null && hashEquals(agreement.getAgreementHash(), dataHash);
            case ChainAnchorRecord.TYPE_REVENUE_EVENT:
                RevenueEvent event = revenueEventMapper.selectById(businessId);
                return event != null && hashEquals(event.getEventHash(), dataHash);
            case ChainAnchorRecord.TYPE_SETTLEMENT_RECEIPT:
                SettlementReceipt receipt = settlementReceiptMapper.selectById(businessId);
                return receipt != null && hashEquals(receipt.getReceiptHash(), dataHash);
            case ChainAnchorRecord.TYPE_WORK_EVIDENCE:
                Evidence evidence = evidenceService.findByDataHash(dataHash);
                if (evidence != null && businessId.equals(evidence.getWorkId())) {
                    return true;
                }
                WorkVersion version = workVersionMapper.selectOne(
                        new LambdaQueryWrapper<WorkVersion>()
                                .eq(WorkVersion::getWorkId, businessId)
                                .eq(WorkVersion::getVersionHash, dataHash)
                                .last("LIMIT 1"));
                return version != null;
            default:
                return false;
        }
    }

    private boolean hashEquals(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private String verifyUrl(HttpServletRequest request, String dataHash) {
        if (publicBaseUrl != null && !publicBaseUrl.trim().isEmpty()) {
            return publicBaseUrl.trim().replaceAll("/+$", "") + "/verify/" + dataHash;
        }
        String scheme = request.getScheme();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);
        String origin = scheme + "://" + request.getServerName()
                + (defaultPort ? "" : ":" + port);
        return origin + "/verify/" + dataHash;
    }
}
