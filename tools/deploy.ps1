#requires -version 5.1
<#
.SYNOPSIS
一鍵部署到 Quest：下載語音模型、安裝 APK、推模型、啟用輸入法。

.DESCRIPTION
這支腳本存在的原因：**App 本身沒有網路權限，不會自己下載模型**。
輸入法能碰到你打的每一個字，所以刻意不給它連網能力——
模型改由這支在電腦上跑的腳本下載，再用 adb 推進去。

模型約 226 MB，下載一次就會快取在 .models\，之後重跑會跳過。

.PARAMETER ApkPath
要安裝的 APK。預設是 debug 建置的產物。

.PARAMETER SkipModels
只裝 APK，不處理語音模型。

.PARAMETER Serial
指定裝置（`adb devices` 看得到的序號）。只接一台時不用給。

.EXAMPLE
.\tools\deploy.ps1

.EXAMPLE
.\tools\deploy.ps1 -ApkPath .\VRTwKeyboard.apk
#>
[CmdletBinding()]
param(
    [string] $ApkPath,
    [switch] $SkipModels,
    [string] $Serial
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$package = "tw.pinnedbopomofo.quest"
$imeId = "$package/.ZhuyinImeService"

# Hugging Face 上釘住的 revision。換版本時連同 SHA256 一起改，不要只改其中一個。
$revision = "8e40c43232a1c5c66c82111efc5820d3accca11b"
$modelBase = "https://huggingface.co/csukuangfj/sherpa-onnx-streaming-paraformer-bilingual-zh-en/resolve/$revision"
$models = @(
    @{ Name = "tokens.txt";        Size = 75756;     Sha256 = "59aba8873a2ed1e122c25fee421e25f283b63290efbde85c1f01a853d83cb6e6" }
    @{ Name = "decoder.int8.onnx"; Size = 71664561;  Sha256 = "f3cca9f77bb9d93c8fcbfb63ae617b6b1ee96818df3aa3b151c40658fe38594f" }
    @{ Name = "encoder.int8.onnx"; Size = 165462184; Sha256 = "81a70226a8934e6ed92aa1d4fc486b428b5398e2f2619ed4897b7294cab90e9a" }
)
$deviceModelDir = "/sdcard/Android/data/$package/files/asr/paraformer-bilingual-zh-en"

function Find-Adb {
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    # SideQuest 自帶一份，Quest 使用者多半已經有
    $sideQuest = Join-Path $env:LOCALAPPDATA "Programs/SideQuest/resources/platform-tools/adb.exe"
    if (Test-Path $sideQuest) { return $sideQuest }
    $sdk = Join-Path $env:LOCALAPPDATA "Android/Sdk/platform-tools/adb.exe"
    if (Test-Path $sdk) { return $sdk }
    throw "找不到 adb。請安裝 Android Platform Tools 或 SideQuest，或把 adb 加進 PATH。"
}

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments)] [string[]] $Arguments)
    $prefix = if ($Serial) { @("-s", $Serial) } else { @() }
    & $adb @prefix @Arguments
    if ($LASTEXITCODE -ne 0) { throw "adb $($Arguments -join ' ') 失敗（exit $LASTEXITCODE）" }
}

function Test-Sha256 {
    param([string] $Path, [string] $Expected)
    if (-not (Test-Path $Path)) { return $false }
    (Get-FileHash $Path -Algorithm SHA256).Hash.ToLower() -eq $Expected
}

# ── 1. 找到 adb 與裝置 ────────────────────────────────────────────
$adb = Find-Adb
Write-Host "adb： $adb"

$devices = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "\S" }
$online = @($devices | Where-Object { $_ -match "\sdevice\s*$" })
if ($online.Count -eq 0) {
    Write-Host ""
    Write-Host "找不到已連線的裝置。檢查："
    Write-Host "  1. 頭盔開了開發者模式（手機 Meta Horizon App → 裝置 → 開發者模式）"
    Write-Host "  2. USB 線接好，戴上頭盔按了「允許 USB 偵錯」"
    Write-Host "  3. 無線連線的話，重開機後要插線重跑： adb tcpip 5555"
    throw "沒有可用的裝置。"
}
if ($online.Count -gt 1 -and -not $Serial) {
    Write-Host ($online -join "`n")
    throw "接了多台裝置，請用 -Serial 指定。"
}
Write-Host "裝置： $($online[0])"

# ── 2. 安裝 APK ──────────────────────────────────────────────────
if (-not $ApkPath) {
    $ApkPath = Join-Path $root "app/build/outputs/apk/debug/app-debug.apk"
}
if (-not (Test-Path $ApkPath)) {
    throw "找不到 APK： $ApkPath`n先建置（gradle assembleDebug）或用 -ApkPath 指定下載回來的檔案。"
}
Write-Host ""
Write-Host "安裝 $ApkPath ..."
Invoke-Adb install -r $ApkPath

# ── 3. 語音模型 ──────────────────────────────────────────────────
if ($SkipModels) {
    Write-Host ""
    Write-Host "略過語音模型（-SkipModels）。注音打字可以用，語音輸入不會出字。"
} else {
    $cache = Join-Path $root ".models/paraformer-bilingual-zh-en"
    if (-not (Test-Path $cache)) { New-Item -ItemType Directory -Path $cache -Force | Out-Null }

    Write-Host ""
    Write-Host "語音模型（合計約 226 MB，只需下載一次）"
    foreach ($model in $models) {
        $local = Join-Path $cache $model.Name
        if (Test-Sha256 -Path $local -Expected $model.Sha256) {
            Write-Host "  $($model.Name) 已在快取且雜湊相符"
            continue
        }
        Write-Host "  下載 $($model.Name)（$([math]::Round($model.Size / 1MB)) MB）..."
        # curl.exe 比 Invoke-WebRequest 快得多；-C - 支援續傳
        curl.exe -L --fail --progress-bar -C - -o $local "$modelBase/$($model.Name)"
        if ($LASTEXITCODE -ne 0) { throw "下載 $($model.Name) 失敗。" }
        if (-not (Test-Sha256 -Path $local -Expected $model.Sha256)) {
            $got = (Get-FileHash $local -Algorithm SHA256).Hash.ToLower()
            Remove-Item $local
            throw "$($model.Name) 的 SHA256 不符：預期 $($model.Sha256)，實得 $got。檔案已刪除。"
        }
        Write-Host "  $($model.Name) 雜湊驗證通過"
    }

    Write-Host ""
    Write-Host "推送模型到頭盔..."
    Invoke-Adb shell mkdir -p $deviceModelDir
    foreach ($model in $models) {
        $local = Join-Path $cache $model.Name
        Write-Host "  $($model.Name)"
        Invoke-Adb push $local "$deviceModelDir/$($model.Name)"
    }
}

# ── 4. 啟用輸入法 ────────────────────────────────────────────────
Write-Host ""
Write-Host "啟用輸入法..."
& $adb $(if ($Serial) { "-s"; $Serial }) shell ime enable $imeId 2>&1 | Out-Null
& $adb $(if ($Serial) { "-s"; $Serial }) shell ime set $imeId 2>&1 | Out-Null

$enabled = & $adb $(if ($Serial) { "-s"; $Serial }) shell ime list -s
if ($enabled -match [regex]::Escape($imeId)) {
    Write-Host "輸入法已啟用。"
} else {
    Write-Host "自動啟用沒成功，請在頭盔上手動開啟："
    Write-Host "  adb shell am start -n com.android.settings/.Settings`$AvailableVirtualKeyboardActivity"
}

# ── 5. 驗收 ──────────────────────────────────────────────────────
Write-Host ""
Write-Host "── 驗收 ──"
$installed = & $adb $(if ($Serial) { "-s"; $Serial }) shell pm list packages | Select-String $package
Write-Host "APK：$(if ($installed) { '已安裝' } else { '沒有找到' })"

if (-not $SkipModels) {
    $onDevice = & $adb $(if ($Serial) { "-s"; $Serial }) shell ls $deviceModelDir 2>&1
    $missing = @($models | Where-Object { $onDevice -notmatch [regex]::Escape($_.Name) })
    if ($missing.Count -eq 0) {
        Write-Host "語音模型：三個檔都在頭盔上"
    } else {
        Write-Host "語音模型：缺少 $($missing.Name -join '、')"
    }
}

Write-Host ""
Write-Host "還有一步要在頭盔裡做：開啟「注音輸入法」App 授予麥克風權限。"
Write-Host "輸入法自己跳不出權限對話框，沒給的話語音輸入不會有反應。"
