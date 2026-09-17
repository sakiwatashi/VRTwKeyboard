# 下載建置需要、但不進版控的相依檔。
#
# sherpa-onnx 的 AAR 有 50 MB，官方沒有發佈 Maven artifact，只提供 GitHub Releases。
# 授權是 Apache-2.0，可以自由取得，只是不適合放進 git。
#
# 用法： .\tools\fetch-deps.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$libs = Join-Path $root "app\libs"
$target = Join-Path $libs "sherpa-onnx-1.13.8.aar"
$url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar"
$expected = "633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"

if (-not (Test-Path $libs)) { New-Item -ItemType Directory -Path $libs | Out-Null }

if (Test-Path $target) {
    $have = (Get-FileHash $target -Algorithm SHA256).Hash.ToLower()
    if ($have -eq $expected) {
        Write-Host "sherpa-onnx AAR 已存在且雜湊相符，略過下載。"
        exit 0
    }
    Write-Host "現有檔案雜湊不符，重新下載。"
}

# curl.exe 比 Invoke-WebRequest 快很多（從台灣連 GitHub 發行檔約 65 KB/s，這個檔要十幾分鐘）
Write-Host "下載 sherpa-onnx 1.13.8 AAR（50 MB，可能要十幾分鐘）..."
curl.exe -L --fail --progress-bar -o $target $url

$actual = (Get-FileHash $target -Algorithm SHA256).Hash.ToLower()
if ($actual -ne $expected) {
    Remove-Item $target
    throw "SHA256 不符：預期 $expected，實得 $actual。檔案已刪除。"
}
Write-Host "完成，雜湊驗證通過。"
