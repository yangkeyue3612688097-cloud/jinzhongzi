$ErrorActionPreference = 'Stop'

$base = 'http://127.0.0.1:8080/api'
$stamp = Get-Date -Format 'HHmmss'
$username = "cert_$stamp"
$password = $env:TEST_ACCOUNT_PASSWORD
if (-not $password) { throw '请先设置 TEST_ACCOUNT_PASSWORD 环境变量' }
$sampleFile = 'D:\ai_block_project\verify-mobile.png'

function Assert-True($condition, $message) {
    if (-not $condition) {
        throw "ASSERT FAILED: $message"
    }
    Write-Host "PASS: $message"
}

function Invoke-Json($method, $path, $body, $token) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $params = @{
        Uri         = "$base$path"
        Method      = $method
        Headers     = $headers
        ContentType = 'application/json; charset=utf-8'
    }
    if ($null -ne $body) {
        $params['Body'] = ([Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 10 -Compress)))
    }
    return Invoke-RestMethod @params
}

$register = Invoke-Json 'POST' '/user/register' @{
    username = $username
    password = $password
    email    = "$username@example.com"
}
Assert-True ($register.code -eq 200) "register user"

$login = Invoke-Json 'POST' '/user/login' @{ username = $username; password = $password }
Assert-True ($login.code -eq 200) "login user"
$token = $login.data.token

$info = Invoke-Json 'GET' '/user/info' $null $token
Assert-True ($info.code -eq 200) "read user info"
Write-Host "address=$($info.data.blockchainAddress) signed=$($info.data.identityAttested)"

$attest = Invoke-Json 'POST' '/user/attest-identity' $null $token
Assert-True ($attest.code -eq 200) "attest identity"
Write-Host "attested=$($attest.data.identityAttested) address=$($attest.data.blockchainAddress)"

$workRaw = & curl.exe -s -X POST "$base/work/create" `
    -H "Authorization: Bearer $token" `
    --form-string "title=Certificate Smoke $stamp" `
    --form-string "description=certificate pipeline smoke test" `
    --form-string "sourceType=post" `
    --form-string "prompt=a lighthouse at dusk, detailed oil painting" `
    --form-string "negativePrompt=blur, low quality" `
    --form-string "modelName=SD1.5" `
    --form-string "modelVersion=v1-5-pruned-emaonly.safetensors" `
    --form-string "seed=424242" `
    --form-string "steps=20" `
    --form-string "cfgScale=7" `
    --form-string "width=512" `
    --form-string "height=512" `
    --form-string "sampler=Euler a" `
    --form-string "scheduler=normal" `
    --form-string "workflowHash=smoke-workflow-0001" `
    -F "file=@$sampleFile"
$work = $workRaw | ConvertFrom-Json
Assert-True ($work.code -eq 200) "create work with generation params"
$workId = $work.data.id
Write-Host "workId=$workId fileHash=$($work.data.fileHash)"

$evidence = Invoke-Json 'POST' "/work/evidence/$workId" $null $token
Assert-True ($evidence.code -eq 200) "create evidence"
$dataHash = $evidence.data.dataHash
Write-Host "dataHash=$dataHash"

$public = Invoke-Json 'GET' "/public/evidence/$dataHash" $null $null
Assert-True ($public.code -eq 200) "public evidence view"
Assert-True ($public.data.signatureVerified -eq $true) "public view reports verified signature"

$manifestOut = Join-Path $env:TEMP "manifest-$dataHash.json"
$certificateOut = 'D:\ai_block_project\certificate-test.pdf'
curl.exe -s -L -o $manifestOut "http://127.0.0.1:8080/api/public/evidence/$dataHash/manifest"
Assert-True ((Test-Path $manifestOut) -and (Get-Item $manifestOut).Length -gt 100) "download manifest json"
curl.exe -s -L -o $certificateOut "http://127.0.0.1:8080/api/public/evidence/$dataHash/certificate"
Assert-True (Test-Path $certificateOut) "download certificate pdf"
Assert-True ((Get-Item $certificateOut).Length -gt 1000) "certificate is non-trivial pdf"

$versionRaw = & curl.exe -s -X POST "$base/work/version/create?workId=$workId&changeDescription=first%20revision" `
    -H "Authorization: Bearer $token" `
    -F "file=@$sampleFile"
$version = $versionRaw | ConvertFrom-Json
Assert-True ($version.code -eq 200) "create version"
Write-Host "version=$($version.data.versionNumber) versionHash=$($version.data.versionHash)"

$history = Invoke-Json 'GET' "/work/version/history/$workId" $null $token
Assert-True ($history.code -eq 200 -and $history.data.Count -ge 1) "version history returns rows"

Write-Host "SMOKE_CERTIFICATE_OK user=$username workId=$workId dataHash=$dataHash pdf=$certificateOut"
