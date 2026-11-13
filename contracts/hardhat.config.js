require("@nomicfoundation/hardhat-ethers");

/**
 * Hardhat 配置
 * - 本地测试：npx hardhat test
 * - 部署到本地节点：npx hardhat node  然后另开终端 npx hardhat run scripts/deploy.js --network localhost
 * - 部署到 Sepolia 测试网：在 .env 里配置 SEPOLIA_RPC_URL / PRIVATE_KEY 后
 *   npx hardhat run scripts/deploy.js --network sepolia
 */
module.exports = {
  solidity: {
    version: "0.8.24",
    settings: {
      optimizer: { enabled: true, runs: 200 },
      evmVersion: "paris"
    }
  },
  networks: {
    hardhat: {
      chainId: 31337,
      allowUnlimitedContractSize: false
    },
    localhost: {
      url: "http://127.0.0.1:8545"
    }
    // sepolia: {
    //   url: process.env.SEPOLIA_RPC_URL || "",
    //   accounts: process.env.PRIVATE_KEY ? [process.env.PRIVATE_KEY] : []
    // }
  },
  mocha: {
    timeout: 120000
  }
};
