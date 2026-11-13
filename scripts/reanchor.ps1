# Re-anchor all local work evidences onto the CURRENT chain.
# Why: Hardhat is an in-memory chain. When it restarts, on-chain anchors are
# wiped but MySQL keeps the records. This script re-submits every work anchor
# so the public verification page stays green after any restart.
# It is safe to run repeatedly: the backend checks the contract first and
# skips anchors that already exist (immutability is preserved).

$ErrorActionPreference = 'Continue'
$base = 'http://127.0.0.1:8080'

function Get-JsonViaCurl {
    param([string]$Url, [string]$Method = 'GET', [hashtable]$Headers = @{}, [string]$BodyFile = $null)
    $args = @('-s', '--noproxy', '*', '-X', $Method, $Url)
    foreach ($k in $Headers.Keys) { $args += @('-H', "$k`: $($Headers[$k])") }
    if ($BodyFile) { $args += @('-H', 'Content-Type: application/json', '--data-binary', "@$BodyFile") }
    # Write the response to a temp file and read it as UTF-8. Some legacy
    # work titles contain non-UTF-8 bytes; piping curl output straight into
    # ConvertFrom-Json would fail on those, even though the request succeeded.
    $outFile = Join-Path $env:TEMP 'reanchor-response.json'
    $args += @('-o', $outFile)
    & curl.exe @args | Out-Null
    if (-not (Test-Path $outFile)) { return $null }
    $text = [IO.File]::ReadAllText($outFile, [Text.Encoding]::UTF8)
    Remove-Item $outFile -Force -ErrorAction SilentlyContinue
    if (-not $text) { return $null }
    try { return ($text | ConvertFrom-Json) } catch { return $null }
}

# Wait until the backend is really ready (login endpoint responds).
$token = $null
$bodyFile = Join-Path $env:TEMP 'reanchor-login.json'
$users = @(
    @{ username = 'demo';    password = '123456' },
    @{ username = 'partner'; password = '123456' }
)

foreach ($u in $users) {
    [IO.File]::WriteAllText($bodyFile, ($u | ConvertTo-Json -Compress), [Text.Encoding]::ASCII)
    for ($i = 0; $i -lt 15; $i++) {
        $r = Get-JsonViaCurl -Url "$base/api/user/login" -Method 'POST' -BodyFile $bodyFile
        if ($r -and $r.code -eq 200 -and $r.data.token) { break }
        Start-Sleep -Seconds 2
    }
    if (-not ($r -and $r.data.token)) {
        Write-Host "[reanchor] backend not ready (user $($u.username)), skipped"
        continue
    }
    $headers = @{ Authorization = "Bearer $($r.data.token)" }
    $list = Get-JsonViaCurl -Url "$base/api/work/list" -Headers $headers
    $records = @()
    if ($list -and $list.data) {
        if ($list.data.records) { $records = @($list.data.records) }
        elseif ($list.data -is [array]) { $records = @($list.data) }
    }
    Write-Host "[reanchor] user $($u.username): $($records.Count) work(s)"
    foreach ($w in $records) {
        $wid = $w.id
        if (-not $wid) { continue }
        try {
            $res = Get-JsonViaCurl -Url "$base/api/work/evidence/$wid/anchor/retry" -Method 'POST' -Headers $headers
            if ($res -and $res.data) {
                Write-Host ("[reanchor]   work {0}: chainStatus={1} block={2}" -f $wid, $res.data.chainStatus, $res.data.blockNumber)
            } else {
                Write-Host "[reanchor]   work $wid : no response"
            }
        } catch {
            Write-Host "[reanchor]   work $wid : $($_.Exception.Message)"
        }
    }
}

Remove-Item $bodyFile -Force -ErrorAction SilentlyContinue
Write-Host "[reanchor] done. Refresh the verification page (F5) to see the result."
