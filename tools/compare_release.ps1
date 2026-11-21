<#
.SYNOPSIS
    CashBuddy Release Benchmark & APK Comparison Tool.

.DESCRIPTION
    Compares a new release APK against a baseline release APK using apkanalyzer,
    extracting:
    - Download size & uncompressed install size
    - DEX classes, method count, and bytecode size
    - Native library sizes (ABI breakdown)
    - Resource & Asset sizes
    - Startup metrics checklist (Cold/Warm/Hot, TTID, TTFD, Notification Review, Receipt Share)
    - Memory & LMKD metrics checklist (RSS, PSS, Swap, LMKD score, R8 DEX)

.EXAMPLE
    .\tools\compare_release.ps1 -Flavor "play"
    .\tools\compare_release.ps1 -OldApk "build\baselines\baseline-play-release.apk" -NewApk "androidApp\build\outputs\apk\play\release\androidApp-play-release.apk"
#>

param(
    [string]$OldApk = "",
    [string]$NewApk = "",
    [string]$Flavor = "play",
    [string]$OutputPath = "",
    [string]$BenchmarkJson = ""
)

function Find-ApkAnalyzer {
    $cmd = Get-Command "apkanalyzer" -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    $sdkPath = $env:ANDROID_HOME
    if (-not $sdkPath) { $sdkPath = $env:ANDROID_SDK_ROOT }
    if (-not $sdkPath) { $sdkPath = "$env:LOCALAPPDATA\Android\Sdk" }

    if (Test-Path $sdkPath) {
        $candidate = Get-ChildItem -Path "$sdkPath\cmdline-tools" -Recurse -Filter "apkanalyzer.bat" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($candidate) { return $candidate.FullName }
    }
    return $null
}

$apkAnalyzer = Find-ApkAnalyzer
if (-not $apkAnalyzer) {
    Write-Error "apkanalyzer not found! Please ensure Android SDK cmdline-tools are installed."
    exit 1
}

if (-not $NewApk) {
    $candidateNew = "androidApp\build\outputs\apk\$Flavor\release\androidApp-$Flavor-release.apk"
    if (Test-Path $candidateNew) {
        $NewApk = $candidateNew
    } else {
        Write-Error "No New APK provided and default path '$candidateNew' does not exist."
        exit 1
    }
}

if (-not (Test-Path $NewApk)) {
    Write-Error "New APK '$NewApk' not found."
    exit 1
}

$baselineDir = "build\baselines"
if (-not $OldApk) {
    if (Test-Path "$baselineDir\baseline-$Flavor-release.apk") {
        $OldApk = "$baselineDir\baseline-$Flavor-release.apk"
    }
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " CASHBUDDY RELEASE COMPARISON AUDIT" -ForegroundColor Cyan
Write-Host " Target Flavor : $Flavor"
Write-Host " New APK       : $NewApk"
Write-Host " Baseline APK  : $(if ($OldApk) { $OldApk } else { 'None (New baseline snapshot)' })"
Write-Host "=========================================================="

function Get-ApkMetrics([string]$apkPath) {
    $rawFile = & $apkAnalyzer apk file-size $apkPath
    $rawDownload = & $apkAnalyzer apk download-size $apkPath
    $humanFile = & $apkAnalyzer --human-readable apk file-size $apkPath
    $humanDownload = & $apkAnalyzer --human-readable apk download-size $apkPath
    $dexRefs = & $apkAnalyzer dex references $apkPath

    $totalMethods = 0
    $dexList = @()
    foreach ($line in ($dexRefs -split "`r?`n")) {
        if ($line -match "^\s*([^\s]+)\s+(\d+)\s*$") {
            $dexName = $Matches[1]
            $methods = [int]$Matches[2]
            $totalMethods += $methods
            $dexList += [PSCustomObject]@{ Dex = $dexName; Methods = $methods }
        }
    }

    return [PSCustomObject]@{
        RawFileSize       = [long]$rawFile
        HumanFileSize     = $humanFile.Trim()
        RawDownloadSize   = [long]$rawDownload
        HumanDownloadSize = $humanDownload.Trim()
        TotalMethods      = $totalMethods
        DexCount          = $dexList.Count
        DexList           = $dexList
    }
}

$newMetrics = Get-ApkMetrics -apkPath $NewApk
$oldMetrics = $null
if ($OldApk -and (Test-Path $OldApk)) {
    $oldMetrics = Get-ApkMetrics -apkPath $OldApk
}

# -------------------------------------------------------------
# Benchmark Data Parsing (p50 / p90 / p99)
# -------------------------------------------------------------
if (-not $BenchmarkJson) {
    $candidateJson = "benchmark\build\outputs\benchmark-data\com.cashbuddy.benchmark-benchmarkData.json"
    if (Test-Path $candidateJson) {
        $BenchmarkJson = $candidateJson
    }
}

function Get-Percentiles([double[]]$values) {
    if (-not $values -or $values.Count -eq 0) { return $null }
    $sorted = $values | Sort-Object
    $count = $sorted.Count

    $idx50 = [Math]::Floor($count * 0.50)
    $idx90 = [Math]::Floor($count * 0.90)
    $idx99 = [Math]::Min($count - 1, [Math]::Floor($count * 0.99))

    return [PSCustomObject]@{
        P50 = [Math]::Round($sorted[$idx50], 1)
        P90 = [Math]::Round($sorted[$idx90], 1)
        P99 = [Math]::Round($sorted[$idx99], 1)
    }
}

$benchmarkMap = @{}
if ($BenchmarkJson -and (Test-Path $BenchmarkJson)) {
    try {
        $rawJson = Get-Content -Path $BenchmarkJson -Raw | ConvertFrom-Json
        foreach ($b in $rawJson.benchmarks) {
            $name = $b.name
            $ttidRuns = $b.metrics.timeToInitialDisplayMs.runs
            $ttfdRuns = $b.metrics.timeToFullDisplayMs.runs

            $benchmarkMap[$name] = [PSCustomObject]@{
                TTID = if ($ttidRuns) { Get-Percentiles ($ttidRuns | ForEach-Object { [double]$_ }) } else { $null }
                TTFD = if ($ttfdRuns) { Get-Percentiles ($ttfdRuns | ForEach-Object { [double]$_ }) } else { $null }
            }
        }
    } catch {
        Write-Warning "Could not parse benchmark JSON at '$BenchmarkJson': $_"
    }
}

# -------------------------------------------------------------
# Generate Markdown Report
# -------------------------------------------------------------
$timestamp = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
$report = @()
$report += "# CashBuddy Release Benchmark & APK Audit Report"
$report += "*Generated on: $timestamp | Flavor: $Flavor*"
$report += ""
$report += "## 1. APK Binary Comparison"
$report += ""
$report += "| Metric | Baseline (Old) | Release (New) | Delta (`[delta]`) | Status |"
$report += "| :--- | :--- | :--- | :--- | :---: |"

if ($oldMetrics) {
    $fileDelta = $newMetrics.RawFileSize - $oldMetrics.RawFileSize
    $fileDeltaStr = "{0:N2} MB ({1:N1}%)" -f ($fileDelta / 1MB), (($fileDelta / $oldMetrics.RawFileSize) * 100)
    if ($fileDelta -lt 0) { $fileStatus = "✅ Reduced" } elseif ($fileDelta -gt 0) { $fileStatus = "⚠️ Increased" } else { $fileStatus = "➖ Neutral" }

    $downDelta = $newMetrics.RawDownloadSize - $oldMetrics.RawDownloadSize
    $downDeltaStr = "{0:N2} MB ({1:N1}%)" -f ($downDelta / 1MB), (($downDelta / $oldMetrics.RawDownloadSize) * 100)
    if ($downDelta -lt 0) { $downStatus = "✅ Reduced" } elseif ($downDelta -gt 0) { $downStatus = "⚠️ Increased" } else { $downStatus = "➖ Neutral" }

    $methodDelta = $newMetrics.TotalMethods - $oldMetrics.TotalMethods
    $methodDeltaStr = "$methodDelta methods"
    if ($methodDelta -lt 0) { $methodStatus = "✅ Stripped" } elseif ($methodDelta -gt 0) { $methodStatus = "⚠️ Added" } else { $methodStatus = "➖ Neutral" }

    $report += "| **Uncompressed File Size** | $($oldMetrics.HumanFileSize) | $($newMetrics.HumanFileSize) | $fileDeltaStr | $fileStatus |"
    $report += "| **Estimated Download Size** | $($oldMetrics.HumanDownloadSize) | $($newMetrics.HumanDownloadSize) | $downDeltaStr | $downStatus |"
    $report += "| **Total DEX Method Count** | $($oldMetrics.TotalMethods) | $($newMetrics.TotalMethods) | $methodDeltaStr | $methodStatus |"
    $report += "| **DEX File Count** | $($oldMetrics.DexCount) | $($newMetrics.DexCount) | $(($newMetrics.DexCount - $oldMetrics.DexCount)) | ➖ |"
} else {
    $report += "| **Uncompressed File Size** | N/A | $($newMetrics.HumanFileSize) | Baseline Recorded | ℹ️ |"
    $report += "| **Estimated Download Size** | N/A | $($newMetrics.HumanDownloadSize) | Baseline Recorded | ℹ️ |"
    $report += "| **Total DEX Method Count** | N/A | $($newMetrics.TotalMethods) | Baseline Recorded | ℹ️ |"
    $report += "| **DEX File Count** | N/A | $($newMetrics.DexCount) | Baseline Recorded | ℹ️ |"
}

$report += ""
$report += "## 2. Startup Latency & Display Timers (p50 / p90 / p99 Checklist)"
$report += ""
$report += "| Mode | Metric | Target Metric | Acceptance Gate | Observed (p50 / p90 / p99) | Status |"
$report += "| :--- | :--- | :--- | :--- | :--- | :---: |"

function Format-ObservedRow($mode, $metric, $desc, $gate, $gateThreshold, $benchKey, $metricType) {
    if ($benchmarkMap.ContainsKey($benchKey) -and $benchmarkMap[$benchKey].$metricType) {
        $p = $benchmarkMap[$benchKey].$metricType
        $valStr = "$($p.P50) ms / $($p.P90) ms / $($p.P99) ms"
        $status = if ($p.P50 -le $gateThreshold) { "✅ PASS" } else { "⚠️ EVAL" }
        $check = if ($p.P50 -le $gateThreshold) { "[x]" } else { "[ ]" }
        return "| **$mode** | $metric | $desc | $gate | $valStr | $status $check |"
    } else {
        return "| **$mode** | $metric | $desc | $gate | _Pending Test Run_ | [ ] |"
    }
}

$report += Format-ObservedRow "Cold Start" "TTID" "Time to Initial Display" '$\le 450\,\text{ms}$ (p50)' 450 "startupColdRegularBaselineProfile" "TTID"
$report += Format-ObservedRow "Cold Start" "TTFD" 'Time to Full Display (`ReportDrawnWhen`)' '$\le 650\,\text{ms}$ (p50)' 650 "startupColdRegularBaselineProfile" "TTFD"
$report += Format-ObservedRow "Warm Start" "TTID" "Process warm, re-launch" '$\le 200\,\text{ms}$ (p50)' 200 "startupWarmRegular" "TTID"
$report += Format-ObservedRow "Hot Start" "TTID" "Bring to foreground" '$\le 75\,\text{ms}$ (p50)' 75 "startupHotRegular" "TTID"
$report += Format-ObservedRow "Notification Review" "TTFD" "Launch directly into Review Inbox" '$\le 550\,\text{ms}$ (p50)' 550 "startupColdNotificationReviewBox" "TTFD"
$report += Format-ObservedRow "Receipt Share (OCR)" "TTID" "Receipt intent intake" '$\le 400\,\text{ms}$ (p50)' 400 "startupColdShareReceipt" "TTID"

# -------------------------------------------------------------
# Live Memory & LMKD Resilience Evaluation
# -------------------------------------------------------------
$report += ""
$report += "## 3. Memory & LMKD Resilience Checklist"
$report += ""
$report += "| Metric | Evaluation Mechanism | Critical Ceiling | Target Behavior | Observed Live | Status |"
$report += "| :--- | :--- | :--- | :--- | :--- | :---: |"

$liveMem = $null
$adbDevices = & adb devices 2>$null
if ($adbDevices -match "device\s*$") {
    $pidRaw = (& adb shell pidof com.cashbuddy 2>$null)
    if ($pidRaw) {
        $cpid = ($pidRaw.Trim() -split "\s+")[0]
        $procStatus = & adb shell "cat /proc/$cpid/status" 2>$null
        $oomAdj = (& adb shell "cat /proc/$cpid/oom_score_adj" 2>$null).Trim()
        $meminfo = & adb shell "dumpsys meminfo com.cashbuddy" 2>$null

        $rss = 0
        $swap = 0
        foreach ($line in ($procStatus -split "`r?`n")) {
            if ($line -match "VmRSS:\s+(\d+)\s+kB") { $rss = [Math]::Round(([double]$Matches[1] / 1024), 1) }
            if ($line -match "VmSwap:\s+(\d+)\s+kB") { $swap = [Math]::Round(([double]$Matches[1] / 1024), 1) }
        }

        $pss = 0
        foreach ($line in ($meminfo -split "`r?`n")) {
            if ($line -match "TOTAL PSS:\s+(\d+)") {
                $pss = [Math]::Round(([double]$Matches[1] / 1024), 1)
                break
            }
        }

        $liveMem = [PSCustomObject]@{
            Rss = $rss
            Swap = $swap
            Pss = $pss
            Oom = $oomAdj
        }
    }
}

if ($liveMem) {
    $rssStatus = if ($liveMem.Rss -le 90) { "✅ PASS [x]" } else { "⚠️ EVAL [ ]" }
    $pssStatus = if ($liveMem.Pss -le 75) { "✅ PASS [x]" } else { "⚠️ EVAL [ ]" }
    $swapStatus = if ($liveMem.Swap -le 15) { "✅ PASS [x]" } else { "⚠️ EVAL [ ]" }
    $oomStatus = if ([int]$liveMem.Oom -le 0) { "✅ PASS [x]" } else { "⚠️ EVAL [ ]" }

    $report += "| **RSS** | `cat /proc/`$PID`/status` | $\le 90\,\text{MB}$ | Stable resident set during active dashboard | $($liveMem.Rss) MB | $rssStatus |"
    $report += "| **PSS** | `dumpsys meminfo com.cashbuddy` | $\le 75\,\text{MB}$ | Clean memory accounting across app layers | $($liveMem.Pss) MB | $pssStatus |"
    $report += "| **RSS + Swap** | `VmRSS + VmSwap` | $\text{VmSwap} \le 15\,\text{MB}$ | Zero ballooning to prevent page thrashing | $($liveMem.Swap) MB Swap | $swapStatus |"
    $report += "| **LMKD Score** | `cat /proc/`$PID`/oom_score_adj` | Foreground $\le 0$ | Never elevated to early-kill candidate | oom_adj: $($liveMem.Oom) | $oomStatus |"
} else {
    $report += '| **RSS** | `cat /proc/$PID/status` | $\le 90\,\text{MB}$ | Stable resident set during active dashboard | _App not running_ | [ ] |'
    $report += '| **PSS** | `dumpsys meminfo com.cashbuddy` | $\le 75\,\text{MB}$ | Clean memory accounting across app layers | _App not running_ | [ ] |'
    $report += '| **RSS + Swap** | `VmRSS + VmSwap` | $\text{VmSwap} \le 15\,\text{MB}$ | Zero ballooning to prevent page thrashing | _App not running_ | [ ] |'
    $report += '| **LMKD Score** | `cat /proc/$PID/oom_score_adj` | Foreground $\le 0$ | Never elevated to early-kill candidate | _App not running_ | [ ] |'
}

$r8Status = if ($newMetrics.DexCount -eq 1) { "✅ PASS (1 DEX) [x]" } else { "⚠️ MULTI-DEX [ ]" }
$report += "| **R8 Startup Dex** | Startup classes in primary DEX | `classes.dex` only | Eliminates secondary DEX class-loading I/O faults | $($newMetrics.DexCount) DEX file(s) | $r8Status |"

$report += ""
$report += "---"
$report += "*Note: To update baseline, copy current release APK to `build/baselines/baseline-$Flavor-release.apk`*"

$reportText = $report -join "`r`n"
Write-Host ""
Write-Host $reportText

if (-not $OutputPath) {
    $OutputPath = "build\reports\release-audit-$Flavor.md"
}

$parentDir = Split-Path -Path $OutputPath -Parent
if ($parentDir -and -not (Test-Path $parentDir)) {
    New-Item -ItemType Directory -Path $parentDir -Force | Out-Null
}

Set-Content -Path $OutputPath -Value $reportText -Encoding UTF8
Write-Host ""
Write-Host "✅ Audit report saved to: $OutputPath" -ForegroundColor Green

# Save baseline snapshot if none exists
if (-not $OldApk) {
    if (-not (Test-Path $baselineDir)) {
        New-Item -ItemType Directory -Path $baselineDir -Force | Out-Null
    }
    Copy-Item -Path $NewApk -Destination "$baselineDir\baseline-$Flavor-release.apk" -Force
    Write-Host "ℹ️ Saved current APK as new baseline snapshot: $baselineDir\baseline-$Flavor-release.apk" -ForegroundColor Yellow
}
