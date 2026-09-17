# 檢查鍵盤的排版監聽器有沒有越掛越多（2026-09-16 的問題：每換一次配色就多留一份舊鍵盤）。
# 用法：戴頭盔換幾次配色、打幾個字，然後執行
#   powershell -File tools\check-layout-listener.ps1            # 直接讀頭盔
#   powershell -File tools\check-layout-listener.ps1 -LogFile x  # 讀存下來的記錄
# 判定：同一次排版（相隔 20ms 內的連續記錄）裡出現超過一個鍵盤 id，就是舊鍵盤的監聽器還活著 → 失敗。
# 結束碼：0 通過、1 失敗、2 記錄不足無法判定（沒有 layout 記錄、或是沒有 id 的舊版記錄）。
param(
    [string]$LogFile,
    [string]$Device = "192.168.1.198:5555",
    [string]$Adb = "C:\Users\may05\AppData\Local\Programs\SideQuest\resources\platform-tools\adb.exe"
)

if ($LogFile) {
    $lines = Get-Content $LogFile -Encoding UTF8
} else {
    $lines = & $Adb -s $Device logcat -d -v time -s ZhuyinIme:D
}

$pattern = '^(\d\d-\d\d \d\d:\d\d:\d\d\.\d{3}).*layout root=\S+ .*?(?:id=([0-9a-f]+))?\s*$'
$entries = @()
foreach ($line in $lines) {
    if ($line -match $pattern) {
        $time = [datetime]::ParseExact("2000-" + $Matches[1], "yyyy-MM-dd HH:mm:ss.fff", $null)
        $entries += [pscustomobject]@{ Time = $time; Id = $Matches[2] }
    }
}

if ($entries.Count -eq 0) {
    Write-Output "無法判定：記錄裡沒有 layout 記錄（開過鍵盤嗎？緩衝區只有 256 KB，太久會被洗掉）"
    exit 2
}
if (@($entries | Where-Object { -not $_.Id }).Count -gt 0) {
    Write-Output "無法判定：有 layout 記錄沒有 id，頭盔上裝的是修改前的版本"
    exit 2
}

# 相隔 20ms 內的記錄視為同一次排版
$bursts = @()
$current = @($entries[0])
for ($i = 1; $i -lt $entries.Count; $i++) {
    if (($entries[$i].Time - $entries[$i - 1].Time).TotalMilliseconds -gt 20) {
        $bursts += , $current
        $current = @()
    }
    $current += $entries[$i]
}
$bursts += , $current

$bad = @($bursts | Where-Object { @($_ | Select-Object -ExpandProperty Id -Unique).Count -gt 1 })
$allIds = @($entries | Select-Object -ExpandProperty Id -Unique)

Write-Output ("layout 記錄 {0} 行、排版 {1} 次、出現過的鍵盤 id {2} 個（每換一次配色會換一個）" -f $entries.Count, $bursts.Count, $allIds.Count)
if (-not $LogFile) {
    $views = & $Adb -s $Device shell dumpsys meminfo tw.pinnedbopomofo.quest | Select-String 'Views:'
    Write-Output ("記憶體：{0}" -f ($views -replace '\s+', ' ').Trim())
}

if ($bad.Count -gt 0) {
    $worst = ($bad | ForEach-Object { @($_ | Select-Object -ExpandProperty Id -Unique).Count } | Measure-Object -Maximum).Maximum
    Write-Output ("失敗：{0} 次排版同時有多個鍵盤在回報，最多 {1} 個；舊鍵盤的監聽器沒有被回收" -f $bad.Count, $worst)
    exit 1
}
Write-Output "通過：每次排版只有目前這一個鍵盤在回報"
exit 0
