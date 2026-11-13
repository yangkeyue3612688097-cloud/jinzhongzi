$ErrorActionPreference = 'Stop'

$base = 'http://localhost:8080/api'
$stamp = Get-Date -Format 'HHmmss'
$ownerName = "own_$stamp"
$mateName = "mate_$stamp"
$mate2Name = "mate2_$stamp"
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

function Register-User($name) {
    $result = Invoke-Json 'POST' '/user/register' @{
        username = $name
        password = $password
        email    = "$name@example.com"
    }
    Assert-True ($result.code -eq 200) "register $name"
}

function Login-User($name) {
    $result = Invoke-Json 'POST' '/user/login' @{ username = $name; password = $password }
    Assert-True ($result.code -eq 200) "login $name"
    return $result.data
}

function New-LicenseOrder($collaborationId, $buyerName, $amount, $token) {
    $order = Invoke-Json 'POST' "/collaboration/order/create?collaborationId=$collaborationId&buyerName=$buyerName&amount=$amount" $null $token
    Assert-True ($order.code -eq 200) "create license order with amount $amount"
    $confirmed = Invoke-Json 'POST' "/collaboration/order/confirm/$($order.data.id)" $null $token
    Assert-True ($confirmed.code -eq 200) "confirm license order with amount $amount"
    return $confirmed.data
}

Register-User $ownerName
Register-User $mateName
$owner = Login-User $ownerName
$mate = Login-User $mateName

$workRaw = & curl.exe -s -X POST "$base/work/create" `
    -H "Authorization: Bearer $($owner.token)" `
    --form-string "title=Collab Work $stamp" `
    --form-string "description=agreement e2e" `
    --form-string "prompt=portrait of a lighthouse at dusk" `
    --form-string "modelName=SDXL" `
    --form-string "modelVersion=1.0" `
    --form-string "seed=12345" `
    --form-string "steps=30" `
    --form-string "cfgScale=7.5" `
    --form-string "width=1024" `
    --form-string "height=1024" `
    --form-string "sampler=Euler a" `
    -F "file=@$sampleFile"
$work = $workRaw | ConvertFrom-Json
Assert-True ($work.code -eq 200) "create work"
$workId = $work.data.id
Write-Host "workId=$workId ownerId=$($owner.userId) mateId=$($mate.userId)"

$collab = Invoke-Json 'POST' "/collaboration/create?workId=$workId" $null $owner.token
Assert-True ($collab.code -eq 200) "create collaboration"
$collabId = $collab.data.id
Assert-True ($null -eq $collab.data.blockchainTxHash) "new collaboration has no fake tx hash"

$add = Invoke-Json 'POST' "/collaboration/add-collaborator?collaborationId=$collabId" @{
    userId     = $mate.userId
    shareRatio = 60
} $owner.token
Assert-True ($add.code -eq 200) "add collaborator"

$badDraft = Invoke-Json 'POST' "/collaboration/agreement/draft?collaborationId=$collabId" @{
    items = @(
        @{ userId = $owner.userId; shareRatio = 40 },
        @{ userId = $mate.userId; shareRatio = 50 }
    )
} $owner.token
Assert-True ($badDraft.code -ne 200) "draft with 90% total is rejected"
Assert-True ($badDraft.message -match '100') "rejection message mentions 100%"

$draft = Invoke-Json 'POST' "/collaboration/agreement/draft?collaborationId=$collabId" @{
    items = @(
        @{ userId = $owner.userId; shareRatio = 40 },
        @{ userId = $mate.userId; shareRatio = 60 }
    )
} $owner.token
Assert-True ($draft.code -eq 200) "draft with 100% total is accepted"
$agreement = $draft.data
Assert-True ($agreement.status -eq 0) "new agreement stays pending"
Assert-True ($agreement.allConfirmed -eq $false) "new agreement is not fully confirmed"
$ownerRow = $agreement.items | Where-Object { $_.userId -eq $owner.userId }
$mateRow = $agreement.items | Where-Object { $_.userId -eq $mate.userId }
Assert-True ($ownerRow.confirmed -eq $true) "owner auto-confirms the draft"
Assert-True ($mateRow.confirmed -eq $false) "collaborator still has to confirm"

$payloadBytes = [Text.Encoding]::UTF8.GetBytes($agreement.canonicalPayload)
$recomputed = ([BitConverter]::ToString(
    [Security.Cryptography.SHA256]::Create().ComputeHash($payloadBytes))).Replace('-', '').ToLower()
Assert-True ($recomputed -eq $agreement.agreementHash) "agreement hash matches canonical payload"

$blocked = Invoke-Json 'POST' "/collaboration/distribute?collaborationId=$collabId" $null $owner.token
Assert-True ($blocked.code -ne 200) "settlement is blocked before all confirmations"

$confirmed = Invoke-Json 'POST' "/collaboration/agreement/$($agreement.id)/confirm" $null $mate.token
Assert-True ($confirmed.code -eq 200) "collaborator confirms agreement"
Assert-True ($confirmed.data.status -eq 1) "agreement becomes effective after all confirmations"

$order1 = New-LicenseOrder $collabId "buyer_$stamp" 1000 $owner.token

$settled = Invoke-Json 'POST' "/collaboration/distribute?collaborationId=$collabId" $null $owner.token
Assert-True ($settled.code -eq 200) "settle revenue under effective agreement"

$duplicate = Invoke-Json 'POST' "/collaboration/distribute?collaborationId=$collabId" $null $owner.token
Assert-True ($duplicate.code -ne 200) "distribute again finds no duplicate revenue event"

$sql = "SELECT user_id, amount, chain_status, IFNULL(blockchain_tx_hash,'NULL') AS tx FROM profit_record " +
    "WHERE collaboration_id=$collabId ORDER BY user_id; " +
    "SELECT total_revenue, distributed_amount, current_agreement_id, chain_status, IFNULL(blockchain_tx_hash,'NULL') AS tx " +
    "FROM collaboration WHERE id=$collabId;"
$dbPassword = $env:MYSQL_PASSWORD
if (-not $dbPassword) { throw '请先设置 MYSQL_PASSWORD 环境变量' }
$dbOut = & wsl.exe -d Ubuntu -- bash -lc "mysql -h127.0.0.1 -P3307 -uroot -p'$dbPassword' aigc_evidence -e '$sql' 2>/dev/null"
Write-Host ($dbOut -join "`n")

$recordLines = $dbOut | Where-Object { $_ -match '^\d+\s' }
Assert-True ($recordLines.Count -eq 2) "exactly two profit records were written"
$ownerRecord = $recordLines | Where-Object { $_ -match "^$($owner.userId)\s" }
$mateRecord = $recordLines | Where-Object { $_ -match "^$($mate.userId)\s" }
Assert-True ($ownerRecord -match '400\.00') "owner receives 40%"
Assert-True ($mateRecord -match '600\.00') "collaborator receives 60%"
Assert-True ($ownerRecord -match '\s0\sNULL\s*$') "owner settlement is pending on-chain with no tx hash"
Assert-True ($mateRecord -match '\s0\sNULL\s*$') "collaborator settlement is pending on-chain with no tx hash"

$order2 = New-LicenseOrder $collabId "buyer2_$stamp" 200 $owner.token

$draft2 = Invoke-Json 'POST' "/collaboration/agreement/draft?collaborationId=$collabId" @{
    items = @(
        @{ userId = $owner.userId; shareRatio = 50 },
        @{ userId = $mate.userId; shareRatio = 50 }
    )
} $owner.token
Assert-True ($draft2.code -eq 200) "draft a second agreement version"
Assert-True ($draft2.data.status -eq 0) "second version is pending"

$blocked2 = Invoke-Json 'POST' "/collaboration/distribute?collaborationId=$collabId" $null $owner.token
Assert-True ($blocked2.code -ne 200) "settlement is suspended while the new version is pending"
Assert-True ($blocked2.message -match 'effective agreement') "suspension message explains missing effective agreement"

$oldStatus = & wsl.exe -d Ubuntu -- bash -lc "mysql -h127.0.0.1 -P3307 -uroot -p'$dbPassword' aigc_evidence -N -e 'SELECT status FROM collaboration_agreement WHERE id=$($agreement.id);' 2>/dev/null"
Assert-True (($oldStatus | Out-String).Trim() -eq '2') "previous effective agreement is superseded"

$confirmed2 = Invoke-Json 'POST' "/collaboration/agreement/$($draft2.data.id)/confirm" $null $mate.token
Assert-True ($confirmed2.code -eq 200 -and $confirmed2.data.status -eq 1) "second version becomes effective"
$settled2 = Invoke-Json 'POST' "/collaboration/distribute?collaborationId=$collabId" $null $owner.token
Assert-True ($settled2.code -eq 200) "settlement resumes under the second version"

Register-User $mate2Name
$mate2 = Login-User $mate2Name
$add2 = Invoke-Json 'POST' "/collaboration/add-collaborator?collaborationId=$collabId" @{
    userId     = $mate2.userId
    shareRatio = 10
} $owner.token
Assert-True ($add2.code -eq 200) "add a third collaborator"
$order3 = New-LicenseOrder $collabId "buyer3_$stamp" 50 $owner.token

$blocked3 = Invoke-Json 'POST' "/collaboration/distribute?collaborationId=$collabId" $null $owner.token
Assert-True ($blocked3.code -ne 200) "settlement is blocked when the agreement misses a collaborator"
Assert-True ($blocked3.message -match 'cover') "coverage failure message is explicit"

$draft3 = Invoke-Json 'POST' "/collaboration/agreement/draft?collaborationId=$collabId" @{
    items = @(
        @{ userId = $owner.userId; shareRatio = 30 },
        @{ userId = $mate.userId; shareRatio = 30 },
        @{ userId = $mate2.userId; shareRatio = 40 }
    )
} $owner.token
Assert-True ($draft3.code -eq 200) "draft an agreement covering all three collaborators"
$confirmMate = Invoke-Json 'POST' "/collaboration/agreement/$($draft3.data.id)/confirm" $null $mate.token
Assert-True ($confirmMate.code -eq 200 -and $confirmMate.data.status -eq 0) "agreement waits for the last collaborator"
$confirmMate2 = Invoke-Json 'POST' "/collaboration/agreement/$($draft3.data.id)/confirm" $null $mate2.token
Assert-True ($confirmMate2.code -eq 200 -and $confirmMate2.data.status -eq 1) "third version becomes effective"
$settled3 = Invoke-Json 'POST' "/collaboration/distribute?collaborationId=$collabId" $null $owner.token
Assert-True ($settled3.code -eq 200) "settlement works for all three collaborators"

$sumSql = "SELECT user_id, SUM(amount) AS total, COUNT(*) AS rows_count FROM profit_record " +
    "WHERE collaboration_id=$collabId GROUP BY user_id ORDER BY user_id;"
$sumOut = & wsl.exe -d Ubuntu -- bash -lc "mysql -h127.0.0.1 -P3307 -uroot -p'$dbPassword' aigc_evidence -e '$sumSql' 2>/dev/null"
Write-Host ($sumOut -join "`n")
$ownerTotal = $sumOut | Where-Object { $_ -match "^$($owner.userId)\s" }
$mateTotal = $sumOut | Where-Object { $_ -match "^$($mate.userId)\s" }
$mate2Total = $sumOut | Where-Object { $_ -match "^$($mate2.userId)\s" }
Assert-True ($ownerTotal -match '515\.00') "owner total across three agreements is 515.00"
Assert-True ($mateTotal -match '715\.00') "collaborator total across three agreements is 715.00"
Assert-True ($mate2Total -match '20\.00') "new collaborator receives 20.00 under the third agreement"

Write-Host "E2E_COLLABORATION_OK collaborationId=$collabId agreements=$($agreement.id),$($draft2.data.id),$($draft3.data.id)"
