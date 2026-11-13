param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^\d+\.\d+\.\d+$')]
    [string]$Version,

    [string]$Branch = "main"
)

$ErrorActionPreference = "Stop"
$tag = "v$Version"

if (-not (Test-Path ".git")) {
    throw "请在 Git 仓库根目录运行此脚本。"
}

$changes = git status --porcelain
if ($changes) {
    throw "工作区仍有未提交修改。请先提交，再发布版本。"
}

$currentBranch = git branch --show-current
if ($currentBranch -ne $Branch) {
    throw "当前分支是 $currentBranch，预期分支是 $Branch。"
}

$existingTag = git tag --list $tag
if ($existingTag) {
    throw "标签 $tag 已存在，不能重复创建。"
}

$versionFile = (Get-Content "VERSION" -Raw).Trim()
if ($versionFile -ne $Version) {
    throw "VERSION 文件是 $versionFile，与参数 $Version 不一致。"
}

git tag -a $tag -m "Release $tag"
git push origin $Branch
git push origin $tag

Write-Host "已发布 $tag" -ForegroundColor Green

