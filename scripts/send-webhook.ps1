param(
    [string]$Url = 'http://localhost:8080/hooks/github',
    [string]$Secret = $env:WEBHOOK_SECRET
)
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($Secret)) { throw 'WEBHOOK_SECRET 환경 변수를 설정하세요.' }
$body = [Text.Encoding]::UTF8.GetBytes('{"action":"demo","message":"Hello webhook"}')
$hmac = [Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($Secret))
try {
    $signature = 'sha256=' + ([BitConverter]::ToString($hmac.ComputeHash($body))).Replace('-', '').ToLowerInvariant()
    Invoke-RestMethod -Uri $Url -Method Post -ContentType 'application/json' -Body $body -Headers @{
        'X-Hub-Signature-256' = $signature
        'X-GitHub-Event' = 'ping'
    }
} finally { $hmac.Dispose() }
