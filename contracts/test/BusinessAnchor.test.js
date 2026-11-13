const { expect } = require("chai");
const { ethers } = require("hardhat");

async function expectRevert(promise, errorName) {
  try {
    await promise;
  } catch (error) {
    const message = `${error.shortMessage || ""} ${error.message || ""}`;
    expect(message).to.include(errorName);
    return;
  }
  expect.fail(`Expected ${errorName} but call succeeded`);
}

describe("BusinessAnchor", function () {
  let admin;
  let operator;
  let stranger;
  let contract;

  const businessId = ethers.id("agreement:15:8");
  const dataHash = ethers.id("sha256:agreement-payload");

  beforeEach(async () => {
    [admin, operator, stranger] = await ethers.getSigners();
    const Factory = await ethers.getContractFactory("BusinessAnchor");
    contract = await Factory.deploy(admin.address);
    await contract.waitForDeployment();
    await contract.connect(admin).setOperator(operator.address, true);
  });

  it("anchors a business hash and exposes a verifiable timestamped record", async () => {
    const receipt = await (
      await contract.connect(operator).anchor(businessId, dataHash, 1, "collaboration/15/agreement/8")
    ).wait();
    const event = receipt.logs
      .map((log) => {
        try {
          return contract.interface.parseLog(log);
        } catch (_) {
          return null;
        }
      })
      .find((item) => item && item.name === "BusinessAnchored");
    expect(event).to.not.equal(undefined);
    expect(event.args.businessId).to.equal(businessId);
    expect(event.args.dataHash).to.equal(dataHash);

    const item = await contract.getAnchor(businessId, dataHash, 1);
    expect(item.anchorId).to.equal(1n);
    expect(item.dataHash).to.equal(dataHash);
    expect(item.businessType).to.equal(1n);
    expect(item.operator).to.equal(operator.address);
    expect(Number(item.timestamp)).to.be.greaterThan(0);
    expect(await contract.verify(businessId, dataHash, 1)).to.equal(true);
    expect(await contract.verify(businessId, ethers.id("tampered"), 1)).to.equal(false);
  });

  it("rejects exact duplicates but allows new evidence versions", async () => {
    const nextVersionHash = ethers.id("sha256:work-version-2");
    await contract.connect(operator).anchor(businessId, dataHash, 1, "");
    await expectRevert(
      contract.connect(operator).anchor(businessId, dataHash, 1, ""),
      "AlreadyAnchored"
    );

    await contract.connect(operator).anchor(businessId, nextVersionHash, 1, "");
    expect(await contract.verify(businessId, dataHash, 1)).to.equal(true);
    expect(await contract.verify(businessId, nextVersionHash, 1)).to.equal(true);
    expect(await contract.latestAnchorId(businessId, 1)).to.equal(2n);

    await contract.connect(operator).anchor(businessId, ethers.id("revenue"), 2, "");
    expect(await contract.verify(businessId, ethers.id("revenue"), 2)).to.equal(true);
  });

  it("enforces writer permissions and validates anchor inputs", async () => {
    await expectRevert(
      contract.connect(stranger).anchor(businessId, dataHash, 1, ""),
      "NotOperator"
    );
    await expectRevert(
      contract.connect(operator).anchor(ethers.ZeroHash, dataHash, 1, ""),
      "EmptyBusinessId"
    );
    await expectRevert(
      contract.connect(operator).anchor(businessId, ethers.ZeroHash, 1, ""),
      "EmptyDataHash"
    );
    await expectRevert(
      contract.connect(operator).anchor(businessId, dataHash, 0, ""),
      "InvalidBusinessType"
    );
    await expectRevert(
      contract.connect(operator).anchor(businessId, dataHash, 6, ""),
      "InvalidBusinessType"
    );
    await contract.connect(operator).anchor(
      ethers.id("evidence:work:11"),
      dataHash,
      5,
      "work/11/evidence"
    );
    expect(await contract.verify(ethers.id("evidence:work:11"), dataHash, 5)).to.equal(true);
  });
});
