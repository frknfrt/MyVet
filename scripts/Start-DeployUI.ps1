#Requires -Version 5.1
[CmdletBinding()]
param([ValidateRange(1024,65535)][int]$Port=9177,[switch]$NoBrowser)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$python=Get-Command python -ErrorAction Stop
$pwshCommand=Get-Command pwsh -ErrorAction SilentlyContinue
$pwshPath=if($pwshCommand) {$pwshCommand.Source} else {$null}
if(-not $pwshPath) {
    $candidates=@(
        (Join-Path $env:ProgramFiles 'PowerShell/7/pwsh.exe'),
        (Join-Path $env:USERPROFILE '.cache/codex-runtimes/codex-primary-runtime/dependencies/native/powershell/pwsh.exe')
    )
    $pwshPath=$candidates | Where-Object {Test-Path -LiteralPath $_} | Select-Object -First 1
}
if(-not $pwshPath) {throw 'PowerShell 7 bulunamadı. PowerShell 7 kurup tekrar başlatın.'}
& $python.Source -c 'import sys; sys.exit(0 if sys.version_info >= (3,10) else 1)'
if($LASTEXITCODE -ne 0) {throw 'Python 3.10 veya üzeri gerekiyor.'}
$uri="http://127.0.0.1:$Port"
$sha=[Security.Cryptography.SHA256]::Create()
try { $repoId=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($repo.ToLowerInvariant())))).Replace('-','').ToLowerInvariant() } finally {$sha.Dispose()}
$running=$false
try {
    $health=Invoke-RestMethod "$uri/api/health" -TimeoutSec 2
    if($health.app -ne 'vetly-deploy-ui' -or $health.repoId -ne $repoId) {throw 'Port başka bir uygulamaya ait.'}
    $running=$true
} catch { if($_.Exception.Message -eq 'Port başka bir uygulamaya ait.') {throw} }
if(-not $running) {
    $state=Join-Path $repo '.deploy/ui'
    New-Item -ItemType Directory -Force $state | Out-Null
    $ui=Join-Path $PSScriptRoot 'deploy/ui_server.py'
    foreach($value in @($ui,$repo,$pwshPath)) {if($value.Contains('"')) {throw 'Dosya yolunda çift tırnak kullanılamaz.'}}
    $arguments='"{0}" --repo "{1}" --powershell "{2}" --port {3}' -f $ui,$repo,$pwshPath,$Port
    Start-Process -FilePath $python.Source -ArgumentList $arguments -WorkingDirectory $repo -WindowStyle Hidden -RedirectStandardOutput (Join-Path $state 'server.stdout.log') -RedirectStandardError (Join-Path $state 'server.stderr.log') | Out-Null
    $ready=$false
    for($i=0;$i -lt 30;$i++) {
        try { $health=Invoke-RestMethod "$uri/api/health" -TimeoutSec 1; if($health.app -eq 'vetly-deploy-ui') {$ready=$true;break} } catch {}
        Start-Sleep -Milliseconds 300
    }
    if(-not $ready) {throw "Panel açılamadı. $state/server.stderr.log dosyasını kontrol edin."}
}
Write-Host "Vetly deployment paneli: $uri"
if(-not $NoBrowser) {Start-Process $uri}
