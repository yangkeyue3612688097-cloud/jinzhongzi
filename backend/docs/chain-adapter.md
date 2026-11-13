# 链上适配说明（给负责合约的同学）

后端已经把所有链上调用收敛到 `org.example.backend.chain.EvidenceChainGateway`。
合约部署完成后，只需要新增一个实现类，业务代码不用改。

## 需要实现的方法

```java
String mode();                 // 返回 "fisco"
ChainAnchor anchor(String dataHash, String dataType, String description);
ChainAnchor query(String dataHash);
```

`anchor` 对应合约里的 `saveEvidence(string _dataHash, string _dataType, string _description)`，
成功后返回 `ChainAnchor.anchored("fisco", 交易哈希, 区块号)`。
`query` 对应 `verifyEvidence(string _dataHash)`。

## 切换方式

1. `application.yml` 里把 `app.chain.mode` 改成 `fisco`；
2. 填 `fisco.contract.evidence-address`；
3. 新实现类加上 `@ConditionalOnProperty(name = "app.chain.mode", havingValue = "fisco")`。

`pending` 实现只写本地证据，不产生任何交易哈希，所以演示时不会出现假的 txHash。

## 哈希约定

`dataHash = SHA-256(UTF-8(规范化 JSON))`，规范化规则：键名升序、无空白、所有标量为字符串。
存证原文写在 `evidence.canonical_payload`，公开验证页会原样展示，第三方可以复算。
合约只需要存 `dataHash`，不要在前端或后端二次加工。
