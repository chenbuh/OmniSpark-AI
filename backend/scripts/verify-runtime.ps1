param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$RedisCliPath = "E:\Redis\redis-cli.exe",
    [int[]]$AssetProjectIds = @(46, 47, 48),
    [switch]$Login,
    [switch]$FetchAssets,
    [switch]$ClearRiskKeys
)

$ErrorActionPreference = "Stop"

if (-not $Login -and -not $FetchAssets -and -not $ClearRiskKeys) {
    $Login = $true
    $FetchAssets = $true
}

$script:RootDir = Split-Path -Parent $PSScriptRoot
$script:UserAgent = "Codex-Runtime-Verify/1.0"

function Invoke-JsonRequest {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Url,
        [hashtable]$Headers,
        $Body
    )

    $request = [System.Net.HttpWebRequest]::Create($Url)
    $request.Method = $Method
    $request.Accept = "application/json"

    if ($Headers) {
        foreach ($key in $Headers.Keys) {
            $value = [string]$Headers[$key]
            switch -Regex ($key) {
                "^User-Agent$" { $request.UserAgent = $value; break }
                "^Accept$" { $request.Accept = $value; break }
                "^Content-Type$" { $request.ContentType = $value; break }
                default { $request.Headers[$key] = $value; break }
            }
        }
    }

    if ($null -ne $Body) {
        $jsonBody = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 12 -Compress }
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($jsonBody)
        if (-not $request.ContentType) {
            $request.ContentType = "application/json"
        }
        $request.ContentLength = $bytes.Length
        $stream = $request.GetRequestStream()
        try {
            $stream.Write($bytes, 0, $bytes.Length)
        } finally {
            $stream.Dispose()
        }
    }

    try {
        $response = [System.Net.HttpWebResponse]$request.GetResponse()
    } catch [System.Net.WebException] {
        if ($_.Exception.Response) {
            $response = [System.Net.HttpWebResponse]$_.Exception.Response
        } else {
            throw
        }
    }

    $statusCode = [int]$response.StatusCode
    $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
    try {
        $raw = $reader.ReadToEnd()
    } finally {
        $reader.Dispose()
        $response.Dispose()
    }

    $parsed = $null
    if ($raw) {
        try {
            $parsed = $raw | ConvertFrom-Json
        } catch {
            $parsed = $raw
        }
    }
    [pscustomobject]@{
        StatusCode = $statusCode
        Body = $parsed
        Raw = $raw
    }
}

function Get-Sha256Hex {
    param([string]$Text)

    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $safeText = if ($null -eq $Text) { "" } else { $Text }
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($safeText)
        return ([System.BitConverter]::ToString($sha256.ComputeHash($bytes))).Replace("-", "").ToLowerInvariant()
    } finally {
        $sha256.Dispose()
    }
}

function Get-SigningHeaders {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Url,
        [string]$BodyJson = ""
    )

    $challengeResp = Invoke-JsonRequest -Method "GET" -Url "$BaseUrl/api/auth/sign/challenge" -Headers @{ "User-Agent" = $script:UserAgent }
    if ($challengeResp.StatusCode -ne 200 -or $challengeResp.Body.code -ne 200) {
        throw "Sign challenge failed: $($challengeResp.Raw)"
    }

    $challenge = $challengeResp.Body.data
    $timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds().ToString()
    $nonce = [Guid]::NewGuid().ToString("N")
    $uri = [Uri]$Url
    $pathAndQuery = $uri.AbsolutePath
    if ($uri.Query) {
        $pathAndQuery += $uri.Query
    }
    $payload = ($Method.ToUpperInvariant() + "`n" +
            $pathAndQuery + "`n" +
            (Get-Sha256Hex -Text $BodyJson) + "`n" +
            $timestamp + "`n" +
            $nonce + "`n" +
            $challenge.challengeId)

    $secretBytes = [System.Text.Encoding]::UTF8.GetBytes([string]$challenge.challengeSecret)
    $payloadBytes = [System.Text.Encoding]::UTF8.GetBytes($payload)
    $hmac = New-Object System.Security.Cryptography.HMACSHA256 -ArgumentList (,[byte[]]$secretBytes)
    try {
        $signature = [Convert]::ToBase64String($hmac.ComputeHash($payloadBytes))
    } finally {
        $hmac.Dispose()
    }

    return @{
        "User-Agent" = $script:UserAgent
        "X-Timestamp" = $timestamp
        "X-Nonce" = $nonce
        "X-Challenge-Id" = [string]$challenge.challengeId
        "X-Sign" = $signature
    }
}

function Invoke-SignedJsonRequest {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Url,
        $Body
    )

    $bodyJson = if ($null -eq $Body) { "" } elseif ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 12 -Compress }
    $headers = Get-SigningHeaders -Method $Method -Url $Url -BodyJson $bodyJson
    if ($bodyJson -eq "") {
        return Invoke-JsonRequest -Method $Method -Url $Url -Headers $headers
    }
    return Invoke-JsonRequest -Method $Method -Url $Url -Headers $headers -Body $bodyJson
}

function Get-PortProcessId {
    param([int]$Port)

    try {
        $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop |
            Select-Object -First 1
        if ($connection) {
            return [int]$connection.OwningProcess
        }
    } catch {
    }

    $lines = netstat -ano | Where-Object { $_ -match "LISTENING" -and $_ -match "[:\.]$Port\s" }
    foreach ($line in $lines) {
        if ($line -match "LISTENING\s+(\d+)$") {
            return [int]$Matches[1]
        }
    }
    return $null
}

function Get-BackendStatus {
    $uri = [Uri]$BaseUrl
    $port = if ($uri.IsDefaultPort) { if ($uri.Scheme -eq "https") { 443 } else { 80 } } else { $uri.Port }
    $processId = Get-PortProcessId -Port $port

    $serviceClass = Join-Path $script:RootDir "target\classes\com\example\aihub\infrastructure\service\AntiCrawlerRiskService.class"
    $interceptorClass = Join-Path $script:RootDir "target\classes\com\example\aihub\common\config\AntiCrawlerRiskInterceptor.class"

    $serviceInfo = if (Test-Path $serviceClass) { Get-Item $serviceClass } else { $null }
    $interceptorInfo = if (Test-Path $interceptorClass) { Get-Item $interceptorClass } else { $null }
    $processInfo = if ($processId) { Get-CimInstance Win32_Process -Filter "ProcessId=$processId" } else { $null }

    $latestClassTime = $null
    if ($serviceInfo -and $interceptorInfo) {
        $latestClassTime = @($serviceInfo.LastWriteTime, $interceptorInfo.LastWriteTime) | Sort-Object -Descending | Select-Object -First 1
    } elseif ($serviceInfo) {
        $latestClassTime = $serviceInfo.LastWriteTime
    } elseif ($interceptorInfo) {
        $latestClassTime = $interceptorInfo.LastWriteTime
    }

    $processTime = if ($processInfo) { [DateTime]$processInfo.CreationDate } else { $null }
    $isStale = $false
    if ($processTime -and $latestClassTime) {
        $isStale = $processTime -lt $latestClassTime
    }

    [pscustomobject]@{
        baseUrl = $BaseUrl
        port = $port
        processId = $processId
        processStartTime = $processTime
        antiCrawlerRiskServiceClassTime = if ($serviceInfo) { $serviceInfo.LastWriteTime } else { $null }
        antiCrawlerRiskInterceptorClassTime = if ($interceptorInfo) { $interceptorInfo.LastWriteTime } else { $null }
        latestClassTime = $latestClassTime
        isStaleAgainstClasses = $isStale
    }
}

function Redis-GetRaw {
    param([Parameter(Mandatory = $true)][string]$Key)
    if (-not (Test-Path $RedisCliPath)) {
        throw "Redis CLI not found: $RedisCliPath"
    }
    $value = & $RedisCliPath --raw GET $Key
    if ($LASTEXITCODE -ne 0) {
        throw "redis-cli GET failed for key: $Key"
    }
    return ($value | Out-String).Trim()
}

function Clear-RiskState {
    $patterns = @("risk:*", "rate:uploads:*")
    $deleted = 0
    foreach ($pattern in $patterns) {
        $keys = & $RedisCliPath --raw KEYS $pattern
        foreach ($key in $keys) {
            if ($key) {
                & $RedisCliPath DEL $key | Out-Null
                $deleted++
            }
        }
    }
    return [pscustomobject]@{
        patterns = $patterns
        deletedKeys = $deleted
    }
}

function New-CaptchaTrail {
    param(
        [string]$Type,
        $Answer,
        $Captcha
    )

    switch ($Type) {
        "rotate" {
            return @(@(160, 90, 420))
        }
        "sequence" {
            $trail = @()
            $time = 360
            foreach ($target in $Answer.targets) {
                $trail += ,@([int]$target[0], [int]$target[1], $time)
                $time += 170
            }
            return $trail
        }
        "track" {
            $trackY = if ($null -ne $Captcha.data.trackY) { [int]$Captcha.data.trackY } else { 90 }
            $endX = [int]$Answer.endX
            return @(
                @(26, $trackY, 180),
                @(58, ($trackY + 1), 340),
                @(112, ($trackY - 1), 520),
                @(174, ($trackY + 2), 730),
                @($endX, $trackY, 980)
            )
        }
        default {
            throw "Unsupported captcha type: $Type"
        }
    }
}

function Get-CaptchaTicket {
    $generate = Invoke-JsonRequest -Method "GET" -Url "$BaseUrl/api/auth/captcha/generate"
    if ($generate.StatusCode -ne 200 -or $generate.Body.code -ne 200) {
        throw "Captcha generate failed: $($generate.Raw)"
    }

    $captcha = $generate.Body
    $captchaId = $captcha.data.captchaId
    $answerRaw = Redis-GetRaw -Key ("captcha:answer:" + $captchaId)
    if (-not $answerRaw) {
        throw "Captcha answer missing in Redis for id: $captchaId"
    }
    $answer = $answerRaw | ConvertFrom-Json
    $trail = New-CaptchaTrail -Type $captcha.data.type -Answer $answer -Captcha $captcha

    $verifyBody = @{
        captchaId = $captchaId
        trail = $trail
    }
    switch ($captcha.data.type) {
        "rotate" { $verifyBody["angle"] = [int]$answer.angle }
        "sequence" { $verifyBody["points"] = $answer.targets }
        "track" { $verifyBody["x"] = [int]$answer.endX }
    }

    $verify = Invoke-SignedJsonRequest -Method "POST" -Url "$BaseUrl/api/auth/captcha/verify" -Body $verifyBody
    if ($verify.StatusCode -ne 200 -or $verify.Body.code -ne 200) {
        throw "Captcha verify failed: $($verify.Raw)"
    }

    [pscustomobject]@{
        captchaId = $captchaId
        type = $captcha.data.type
        ticket = $verify.Body.data.ticket
    }
}

function Login-WithCaptcha {
    param(
        [Parameter(Mandatory = $true)][string]$User,
        [Parameter(Mandatory = $true)][string]$Pass
    )

    $ticketInfo = Get-CaptchaTicket
    $loginBody = @{
        username = $User
        password = $Pass
        captchaTicket = $ticketInfo.ticket
    }
    $login = Invoke-SignedJsonRequest -Method "POST" -Url "$BaseUrl/api/auth/login" -Body $loginBody
    if ($login.StatusCode -ne 200 -or $login.Body.code -ne 200) {
        throw "Login failed: $($login.Raw)"
    }

    [pscustomobject]@{
        captcha = $ticketInfo
        token = $login.Body.data.token
        user = $login.Body.data.user
    }
}

function Fetch-AssetSmoke {
    param([Parameter(Mandatory = $true)][string]$Token)

    $results = @()
    foreach ($projectId in $AssetProjectIds) {
        $headers = @{
            "User-Agent" = $script:UserAgent
            "satoken" = $Token
        }
        $resp = Invoke-JsonRequest -Method "GET" -Url "$BaseUrl/api/assets?projectId=$projectId&limit=3" -Headers $headers
        $results += [pscustomobject]@{
            projectId = $projectId
            statusCode = $resp.StatusCode
            code = if ($resp.Body) { $resp.Body.code } else { $null }
            count = if ($resp.Body -and $resp.Body.data) { @($resp.Body.data).Count } else { $null }
        }
    }
    return $results
}

$summary = [ordered]@{
    checkedAt = Get-Date
    status = Get-BackendStatus
}

if ($ClearRiskKeys) {
    $summary["clearedRiskState"] = Clear-RiskState
}

$loginInfo = $null
if ($Login -or $FetchAssets) {
    $loginInfo = Login-WithCaptcha -User $Username -Pass $Password
    $summary["login"] = [pscustomobject]@{
        username = $Username
        captchaType = $loginInfo.captcha.type
        tokenPrefix = if ($loginInfo.token.Length -gt 12) { $loginInfo.token.Substring(0, 12) } else { $loginInfo.token }
        userId = $loginInfo.user.id
    }
}

if ($FetchAssets) {
    $summary["assetSmoke"] = Fetch-AssetSmoke -Token $loginInfo.token
}

$summary | ConvertTo-Json -Depth 12
