# 整理鍵盤的效能記錄（ZhuyinPerf）：詞庫載入時間、每次按鍵的處理時間，加上頭盔上的記憶體。
# 用法：
#   powershell -File tools\measure-perf.ps1                        # 直接讀頭盔（也讀 dumpsys meminfo）
#   powershell -File tools\measure-perf.ps1 -LogFile x              # 讀背景記錄存下的檔案（沒有記憶體）
#   powershell -File tools\measure-perf.ps1 -OutFile docs\perf.md   # 結果另存一份
# 注意：debug 版會關掉部分執行最佳化，數字比正式版慢，只能跟同樣是 debug 版的量測比。
# 結束碼：0 有資料、2 記錄裡沒有 perf key 行（沒打字，或緩衝區洗掉了）。
param(
    [string]$LogFile,
    [string]$OutFile,
    [string]$Device = "192.168.1.198:5555",
    [string]$Adb = "C:\Users\may05\AppData\Local\Programs\SideQuest\resources\platform-tools\adb.exe"
)

if ($LogFile) {
    $lines = Get-Content $LogFile -Encoding UTF8
} else {
    $lines = & $Adb -s $Device logcat -d -v time -s ZhuyinPerf:D ZhuyinIme:D
}

function Stats([double[]]$values) {
    $sorted = @($values | Sort-Object)
    $n = $sorted.Count
    if ($n -eq 0) { return $null }
    $pick = { param($q) $sorted[[math]::Min($n - 1, [math]::Floor($q * $n))] }
    [pscustomobject]@{
        Count = $n
        Median = & $pick 0.5
        P95 = & $pick 0.95
        Max = $sorted[$n - 1]
    }
}

$loads = @()
$readies = @()
$keys = @()
$creates = @()
$splits = @()
foreach ($line in $lines) {
    if ($line -match 'ZhuyinPerf\(\s*(\d+)\): perf load lexiconMs=([\d.]+) predictorMs=([\d.]+) totalMs=([\d.]+) heapMb=(\d+) pssMb=(\d+)') {
        $loads += [pscustomobject]@{ Pid = $Matches[1]; Lexicon = [double]$Matches[2]; Predictor = [double]$Matches[3]; Total = [double]$Matches[4]; HeapMb = [int]$Matches[5]; PssMb = [int]$Matches[6] }
    } elseif ($line -match 'ZhuyinIme\(\s*(\d+)\): service onCreate id=(\w+)') {
        $creates += [pscustomobject]@{ Pid = $Matches[1]; Id = $Matches[2] }
    } elseif ($line -match 'ZhuyinPerf\(\s*(\d+)\): perf loadSplit (.*)$') {
        $splits += [pscustomobject]@{ Pid = $Matches[1]; Text = $Matches[2].Trim() }
    } elseif ($line -match 'perf ready sinceCreateMs=(\d+)') {
        $readies += [int]$Matches[1]
    } elseif ($line -match 'perf key kind=(\w+) syllables=(\d+) candidates=(\d+) totalMs=([\d.]+) engineMs=([\d.]+) editorMs=([\d.]+) uiMs=([\d.]+) frameMs=([\d.]+)') {
        $keys += [pscustomobject]@{
            Kind = $Matches[1]; Syllables = [int]$Matches[2]; Candidates = [int]$Matches[3]
            Total = [double]$Matches[4]; Engine = [double]$Matches[5]; Editor = [double]$Matches[6]
            Ui = [double]$Matches[7]; Frame = [double]$Matches[8]
        }
    }
}

$out = New-Object System.Collections.Generic.List[string]
$out.Add("# 鍵盤效能量測（$(Get-Date -Format 'yyyy-MM-dd HH:mm')）")
$out.Add("")
$out.Add("## 啟動（詞庫載入，背景執行緒）")
if ($loads.Count -eq 0) {
    $out.Add("記錄裡沒有載入記錄（鍵盤程序這段時間沒有重新啟動）。")
} else {
    $out.Add("| 次 | 程序 | 詞庫 ms | 聯想詞 ms | 合計 ms | heap MB | PSS MB |")
    $out.Add("|---|---|---|---|---|---|---|")
    $i = 0
    foreach ($l in $loads) { $i++; $out.Add("| $i | $($l.Pid) | $($l.Lexicon) | $($l.Predictor) | $($l.Total) | $($l.HeapMb) | $($l.PssMb) |") }
    if ($readies.Count -gt 0) { $out.Add(""); $out.Add("程序啟動到可以選字：$($readies -join '、') ms") }
    # 細分：read＝讀檔含解壓、parse＝org.json 解析、build＝轉成查詢用的表、collect＝取出詞、index＝首符號索引
    foreach ($sp in $splits) {
        $out.Add("")
        $out.Add("載入細分（程序 $($sp.Pid)，ms）：")
        $out.Add("| 段落 | ms |")
        $out.Add("|---|---|")
        foreach ($pair in ($sp.Text -split ' ')) {
            $name, $value = $pair -split '='
            $out.Add("| $name | $value |")
        }
    }
    $out.Add("")
    # 同一個程序應該只載入一次（SharedLoader）；2026-09-16 基準記錄裡同一個程序載入了兩次
    $duplicated = @($loads | Group-Object Pid | Where-Object { $_.Count -gt 1 })
    if ($duplicated.Count -gt 0) {
        foreach ($g in $duplicated) { $out.Add("**重複載入**：程序 $($g.Name) 載入了 $($g.Count) 次。") }
    } else {
        $out.Add("每個程序只載入一次。")
    }
}
if ($creates.Count -gt 0) {
    foreach ($g in ($creates | Group-Object Pid)) {
        $out.Add("程序 $($g.Name) 建立服務實體 $($g.Count) 次（id：$(($g.Group | ForEach-Object Id) -join '、')）。")
    }
}

$out.Add("")
$out.Add("## 每次按鍵（ms）")
$out.Add("total＝按鍵處理（engine＋editor＋ui）；frame＝從按下到新畫面在主執行緒排版繪製完。")
$out.Add("")
if ($keys.Count -eq 0) {
    $out.Add("記錄裡沒有按鍵記錄。")
} else {
    $out.Add("| 種類 | 音節數 | 次數 | total 中位 | total p95 | total 最大 | engine 中位 | engine p95 | ui 中位 | frame 中位 | frame p95 | frame 最大 |")
    $out.Add("|---|---|---|---|---|---|---|---|---|---|---|---|")
    $groups = $keys | Group-Object {
        $bucket = if ($_.Syllables -le 2) { "0-2" } elseif ($_.Syllables -le 5) { "3-5" } else { "6+" }
        "$($_.Kind)|$bucket"
    } | Sort-Object Name
    foreach ($g in $groups) {
        $kind, $bucket = $g.Name -split '\|'
        $t = Stats ($g.Group | ForEach-Object Total)
        $e = Stats ($g.Group | ForEach-Object Engine)
        $u = Stats ($g.Group | ForEach-Object Ui)
        $f = Stats ($g.Group | ForEach-Object Frame)
        $out.Add("| $kind | $bucket | $($t.Count) | $($t.Median) | $($t.P95) | $($t.Max) | $($e.Median) | $($e.P95) | $($u.Median) | $($f.Median) | $($f.P95) | $($f.Max) |")
    }
    $all = Stats ($keys | ForEach-Object Frame)
    $out.Add("")
    $out.Add("全部 $($all.Count) 次：frame 中位 $($all.Median)、p95 $($all.P95)、最大 $($all.Max) ms；超過 100 ms 的有 $(@($keys | Where-Object { $_.Frame -gt 100 }).Count) 次。")
}

if (-not $LogFile) {
    $out.Add("")
    $out.Add("## 記憶體（dumpsys meminfo，量測當下）")
    $mem = & $Adb -s $Device shell dumpsys meminfo tw.pinnedbopomofo.quest
    foreach ($m in ($mem | Select-String 'Java Heap:|Native Heap:|Code:|TOTAL PSS:|Views:')) {
        $out.Add("- " + (($m.Line -replace '\s+', ' ').Trim()))
    }
}

$out | ForEach-Object { Write-Output $_ }
if ($OutFile) {
    [IO.File]::WriteAllLines($OutFile, $out, (New-Object Text.UTF8Encoding($false)))
    Write-Output ""
    Write-Output "已另存：$OutFile"
}
if ($keys.Count -eq 0) { exit 2 }
exit 0
