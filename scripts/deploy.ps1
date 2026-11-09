#Requires -Version 7.0
[CmdletBinding()]
param(
    [ValidateSet('Prepare','Check','Deploy')][string]$Mode = 'Prepare',
    [string]$ReleaseId,
    [ValidateSet('commit','working-tree')][string]$SourceMode = 'commit',
    [switch]$AllowDirty,
    [string]$SshConfigFile,
    [ValidatePattern('^[a-f0-9]{64}$')][string]$ExpectedManifestSha256,
    [string]$PythonExecutable = 'python'
)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
$OutputEncoding = [Console]::OutputEncoding
$env:PYTHONIOENCODING = 'utf-8'
$env:GIT_TERMINAL_PROMPT = '0'
$Repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Tools = Join-Path $PSScriptRoot 'deploy'
$OutputRoot = Join-Path $Repo '.deploy'
$Target = 'furkan@70.40.139.40'
$SshOptions = @('-o','BatchMode=yes','-o','ConnectTimeout=10','-o','StrictHostKeyChecking=yes')
$script:CurrentStage = 'source'
$script:RemoteResult = $null

function Write-Stage([string]$Stage,[string]$Status,[string]$Message) {
    $script:CurrentStage = $Stage
    Write-Output ('@@VETLY_EVENT@@' + (@{stage=$Stage;status=$Status;message=$Message} | ConvertTo-Json -Compress))
}
function Invoke-Native([string]$File,[string[]]$Arguments,[string]$Directory = $Repo) {
    Push-Location $Directory
    try {
        & $File @Arguments 2>&1 | ForEach-Object { Write-Output $_.ToString() }
        if ($LASTEXITCODE -ne 0) { throw "$File komutu başarısız (çıkış: $LASTEXITCODE)." }
    } finally { Pop-Location }
}
function Invoke-PythonJson([string[]]$Arguments) {
    $lines = & $PythonExecutable (Join-Path $Tools 'release.py') @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'Paket doğrulama/hazırlama başarısız.' }
    return (($lines -join "`n") | ConvertFrom-Json)
}
function Get-Release([string]$Id) {
    if ($Id -notmatch '^[0-9]{8}T[0-9]{6}Z-[a-f0-9]{7,40}-[a-f0-9]{8}$') { throw 'Release kimliği geçersiz.' }
    return Join-Path (Join-Path $OutputRoot 'releases') $Id
}
function Invoke-Remote([string]$Action,[string]$Id,[string]$Hash) {
    $script:RemoteResult = $null
    $helperHash = (Get-FileHash (Join-Path $Tools 'server.py') -Algorithm SHA256).Hash.ToLowerInvariant()
    $releaseHelperHash = (Get-FileHash (Join-Path $Tools 'release.py') -Algorithm SHA256).Hash.ToLowerInvariant()
    $remoteCommand = "sudo -n -- /usr/local/lib/vetly-deploy/server.py $Action --release $Id --manifest-sha256 $Hash --helper-sha256 $helperHash --release-helper-sha256 $releaseHelperHash"
    & ssh @SshOptions $Target $remoteCommand 2>&1 | ForEach-Object {
        $line = $_.ToString()
        if ($line.StartsWith('@@VETLY_RESULT@@')) {
            $script:RemoteResult = $line.Substring('@@VETLY_RESULT@@'.Length) | ConvertFrom-Json
        } else {
            if ($line.StartsWith('@@VETLY_EVENT@@')) {
                $remoteEvent = $line.Substring('@@VETLY_EVENT@@'.Length) | ConvertFrom-Json
                $script:CurrentStage = $remoteEvent.stage
            }
            Write-Output $line
        }
    }
    if ($LASTEXITCODE -ne 0) { throw "Sunucu $Action aşaması başarısız. SSH anahtarı, root-owned yardımcı ve sınırlı sudo kurulumunu kontrol edin." }
    if (-not $script:RemoteResult) { throw 'Sunucudan geçerli sonuç alınamadı.' }
}

try {
    if (-not $IsWindows) { throw 'Bu giriş betiği yerel Windows / PowerShell 7 içindir.' }
    if ($SshConfigFile) {
        $resolvedSshConfig = (Resolve-Path -LiteralPath $SshConfigFile).Path
        $SshOptions = @('-F', $resolvedSshConfig) + $SshOptions
    }
    foreach ($tool in @('git','node','npm.cmd','ssh','scp')) { if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "Gerekli araç bulunamadı: $tool" } }
    $oldTemp = $env:TEMP; $oldTmp = $env:TMP; $oldApi = $env:VITE_API_BASE_URL
    try {
        if ($Mode -eq 'Prepare' -or ($Mode -eq 'Deploy' -and -not $ReleaseId)) {
            Write-Stage 'source' 'start' "Kaynak hazırlanıyor ($SourceMode); Graphify ve secrets hariç"
            $snapshotArgs = @('snapshot','--repo',$Repo,'--output',$OutputRoot,'--source-mode',$SourceMode)
            if ($AllowDirty) { $snapshotArgs += '--allow-dirty' }
            $snapshot = Invoke-PythonJson $snapshotArgs
            $ReleaseId = $snapshot.releaseId
            Write-Stage 'source' 'ok' "Kaynak hazır: $($snapshot.commit.Substring(0,12)) / $($snapshot.sourceMode). Dışarıda bırakılan yerel değişiklik: $($snapshot.ignoredLocalChangeCount)"
            $temp = Join-Path $snapshot.work 'temp'
            New-Item -ItemType Directory -Force $temp | Out-Null
            $env:TEMP = $temp; $env:TMP = $temp
            $env:VITE_API_BASE_URL = 'https://uygulama.vetly.com.tr'
            Write-Stage 'build' 'start' 'Backend, TARBİL eklentisi ve frontend derleniyor'
            Invoke-Native (Join-Path $snapshot.source 'backend/mvnw.cmd') @('-DskipTests','package') (Join-Path $snapshot.source 'backend')
            Invoke-Native 'npm.cmd' @('ci','--prefer-offline') (Join-Path $snapshot.source 'extension')
            Invoke-Native 'npm.cmd' @('run','package') (Join-Path $snapshot.source 'extension')
            Invoke-Native 'npm.cmd' @('ci','--prefer-offline') (Join-Path $snapshot.source 'frontend')
            Invoke-Native 'npm.cmd' @('run','build') (Join-Path $snapshot.source 'frontend')
            Write-Stage 'build' 'ok' 'Derlemeler başarılı. Bu işlem backend test süitini çalıştırmaz.'
            Write-Stage 'package' 'start' 'SHA-256, gerçek JAR migration kataloğu ve frontend arşivi hazırlanıyor'
            $package = Invoke-PythonJson @('pack','--work',$snapshot.work,'--output',$OutputRoot)
            Write-Stage 'package' 'ok' "Paket hazır: $ReleaseId"
        } else {
            $path = Get-Release $ReleaseId
            $null = Invoke-PythonJson @('verify','--directory',$path)
            Write-Stage 'source' 'skip' 'Seçilen hazır kaynak paketi kullanılacak'
            Write-Stage 'build' 'skip' 'Paket yeniden derlenmeyecek'
            Write-Stage 'package' 'ok' 'Yerel paket ve checksum doğrulandı'
        }
    } finally { $env:TEMP=$oldTemp; $env:TMP=$oldTmp; $env:VITE_API_BASE_URL=$oldApi }
    $releasePath = Get-Release $ReleaseId
    $manifest = Invoke-PythonJson @('verify','--directory',$releasePath)
    $manifestHash = (Get-FileHash (Join-Path $releasePath 'release.json') -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($ExpectedManifestSha256 -and $manifestHash -cne $ExpectedManifestSha256) { throw 'Paket manifest hash değeri derleme işiyle uyuşmuyor.' }
    if ($Mode -ne 'Prepare') {
        if ($manifest.dirty.Count -gt 0 -and -not $AllowDirty) { throw 'Bu paket yerel değişiklik içeriyor; dahil etme seçeneğini açıkça seçin.' }
        Write-Stage 'source' 'start' 'Uzak main ile kaynak uyumu kontrol ediliyor; pull/merge yapılmayacak'
        Invoke-Native 'git' @('fetch','--quiet','origin','main')
        & git -C $Repo merge-base --is-ancestor 'origin/main' $manifest.commit
        if ($LASTEXITCODE -ne 0) { throw 'Paket uzak main değişikliklerini içermiyor. Kaynakları güncelleyip yeni paket hazırlayın.' }
        Write-Stage 'source' 'ok' 'Paket commit kimliği uzak main değişikliklerini içeriyor'
        Write-Stage 'connect' 'start' 'SSH anahtarlı bağlantı ve bilinen host doğrulanıyor'
        Invoke-Native 'ssh' ($SshOptions + @($Target,'true'))
        $remote = "/home/furkan/vetly-deploy/incoming/$ReleaseId"
        Invoke-Native 'ssh' ($SshOptions + @($Target,"umask 077 && mkdir -p -- $remote"))
        foreach ($name in @('app.jar','frontend.tar.gz','migrations.json','source-files.json','release.json')) {
            Invoke-Native 'scp' ($SshOptions + @((Join-Path $releasePath $name),"${Target}:$remote/$name"))
        }
        Write-Stage 'connect' 'ok' 'Paket staging alanına yüklendi; çalışan uygulama henüz değiştirilmedi'
        Invoke-Remote 'check' $ReleaseId $manifestHash
        if ($Mode -eq 'Deploy') { Invoke-Remote 'apply' $ReleaseId $manifestHash }
    }
    $result = @{status= if ($Mode -eq 'Prepare') {'prepared'} elseif ($Mode -eq 'Check') {'checked'} else {'succeeded'}; releaseId=$ReleaseId; path=$releasePath; manifestSha256=$manifestHash; remote=$script:RemoteResult}
    $resultPath = Join-Path $releasePath ('last-' + $Mode.ToLowerInvariant() + '.json')
    $result | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $resultPath -Encoding utf8
    Write-Output ('@@VETLY_RESULT@@' + ($result | ConvertTo-Json -Compress -Depth 12))
} catch {
    Write-Stage $script:CurrentStage 'error' $_.Exception.Message
    Write-Output ('@@VETLY_RESULT@@' + (@{status='failed';releaseId=$ReleaseId;error=$_.Exception.Message;remote=$script:RemoteResult} | ConvertTo-Json -Compress -Depth 12))
    exit 1
}
