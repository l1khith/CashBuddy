<#
.SYNOPSIS
    CashBuddy Sharded Sequential Benchmark Runner & Report Generator.

.DESCRIPTION
    Runs Macrobenchmark test scenarios sequentially as independent shards:
    1. Cold Start (TTID / TTFD)
    2. Warm Start (TTID)
    3. Hot Start (TTID)
    4. Notification Review Inbox (TTFD)
    5. Receipt Share OCR Intake (TTID)

    Advantages of Sharded Execution:
    - Eliminates runner timeouts and device heat/thermal throttling.
    - Preserves passed shards even if a later shard fails.
    - Allows selective execution of individual shards (-Shard "Hot", -Shard "Cold").
    - Merges shard outputs into a unified master benchmarkData.json.
    - Automatically updates the Multi-Release History Ledger & Audit Report.

.EXAMPLE
    # Run all shards sequentially and generate the final report
    .\tools\run_benchmarks.ps1

    # Run only Cold Start shard
    .\tools\run_benchmarks.ps1 -Shard "Cold"

    # Run without regenerating report
    .\tools\run_benchmarks.ps1 -SkipReport
#>

param(
    [ValidateSet("All", "Cold", "Warm", "Hot", "Review", "Receipt")]
    [string]$Shard = "All",
    [string]$Flavor = "play",
    [int]$Iterations = 10,
    [switch]$SkipReport
)

$adbCmd = Get-Command "adb" -ErrorAction SilentlyContinue
$adb = if ($adbCmd) { $adbCmd.Source } else { $null }
if (-not $adb) {
    $sdkPath = $env:ANDROID_HOME
    if (-not $sdkPath) { $sdkPath = $env:LOCALAPPDATA + "\Android\Sdk" }
    if (Test-Path "$sdkPath\platform-tools\adb.exe") {
        $adb = "$sdkPath\platform-tools\adb.exe"
    } else {
        Write-Error "adb command not found! Please ensure Android SDK platform-tools are in PATH."
        exit 1
    }
}

# Verify device connected
$devices = & $adb devices 2>$null
$hasDevice = ($devices | Where-Object { $_ -match "\tdevice\b" })
if (-not $hasDevice) {
    Write-Error "No active ADB device or emulator connected! Please boot the emulator or connect a device."
    exit 1
}

$shardsDir = "build\intermediates\benchmark-shards\$Flavor"
if (-not (Test-Path $shardsDir)) {
    New-Item -ItemType Directory -Path $shardsDir -Force | Out-Null
}

$masterOutputDir = "benchmark\build\outputs\benchmark-data"
if (-not (Test-Path $masterOutputDir)) {
    New-Item -ItemType Directory -Path $masterOutputDir -Force | Out-Null
}
$masterJsonPath = "$masterOutputDir\com.cashbuddy.benchmark-benchmarkData.json"

# Define Shards
$shardDefs = [ordered]@{
    "Cold" = @{
        Name   = "Cold Start (TTID / TTFD with Baseline Profile)"
        Method = "com.cashbuddy.benchmark.StartupBenchmark#startupColdRegularBaselineProfile"
        File   = "shard_cold.json"
    }
    "Warm" = @{
        Name   = "Warm Start (TTID)"
        Method = "com.cashbuddy.benchmark.StartupBenchmark#startupWarmRegular"
        File   = "shard_warm.json"
    }
    "Hot" = @{
        Name   = "Hot Start (TTID Foreground Resume)"
        Method = "com.cashbuddy.benchmark.StartupBenchmark#startupHotRegular"
        File   = "shard_hot.json"
    }
    "Review" = @{
        Name   = "Notification Review Box Direct Entry (TTFD)"
        Method = "com.cashbuddy.benchmark.StartupBenchmark#startupColdNotificationReviewBox"
        File   = "shard_review.json"
    }
    "Receipt" = @{
        Name   = "Receipt Share OCR Intake Intent (TTID)"
        Method = "com.cashbuddy.benchmark.StartupBenchmark#startupColdShareReceipt"
        File   = "shard_receipt.json"
    }
}

$activeShards = if ($Shard -eq "All") { $shardDefs.Keys } else { @($Shard) }

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " CASHBUDDY SHARDED BENCHMARK RUNNER" -ForegroundColor Cyan
Write-Host " Target Flavor : $Flavor"
Write-Host " Shards to Run : $($activeShards -join ', ')"
Write-Host " Output Dir    : $shardsDir"
Write-Host "=========================================================="

$runnerClass = "com.cashbuddy.benchmark/androidx.test.runner.AndroidJUnitRunner"
$remotePath = "/storage/emulated/0/Android/media/com.cashbuddy.benchmark/com.cashbuddy.benchmark-benchmarkData.json"

$shardIndex = 0
foreach ($sKey in $activeShards) {
    $shardIndex++
    $def = $shardDefs[$sKey]
    $shardJson = "$shardsDir\$($def.File)"

    Write-Host ""
    Write-Host "[$shardIndex/$($activeShards.Count)] Running Shard: $($def.Name)..." -ForegroundColor Yellow
    Write-Host "    Target Method: $($def.Method)" -ForegroundColor Gray

    # Clean remote benchmark json before running this shard
    & $adb shell rm -f $remotePath 2>$null

    # Execute single shard
    $amCmd = "& `"$adb`" shell am instrument -w -r -e additionalTestOutputDir /storage/emulated/0/Android/media/com.cashbuddy.benchmark -e androidx.benchmark.suppressErrors `"EMULATOR,LOW-BATTERY`" -e class $($def.Method) $runnerClass"
    $output = Invoke-Expression $amCmd

    # Check execution status
    if ($output -match "OK \(1 test\)" -or $output -match "INSTRUMENTATION_CODE: -1") {
        Write-Host "    ✅ Shard completed successfully." -ForegroundColor Green
        
        # Pull output to local shard file
        & $adb pull $remotePath $shardJson 2>$null | Out-Null
        if (Test-Path $shardJson) {
            $parsed = Get-Content -Path $shardJson -Raw | ConvertFrom-Json
            if ($parsed.benchmarks.Count -gt 0) {
                $b = $parsed.benchmarks[0]
                $ttidMed = $b.metrics.timeToInitialDisplayMs.median
                $ttfdMed = $b.metrics.timeToFullDisplayMs.median
                Write-Host "    📊 Observed: TTID p50 = $([Math]::Round($ttidMed, 1)) ms $(if ($ttfdMed) { "| TTFD p50 = " + [Math]::Round($ttfdMed, 1) + " ms" })" -ForegroundColor Cyan
            }
        }
    } else {
        Write-Warning "    ⚠️ Shard execution reported errors. Retaining any existing shard data."
    }
}

# -------------------------------------------------------------
# Merge All Shard JSONs into Consolidated Master JSON
# -------------------------------------------------------------
Write-Host ""
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " AGGREGATING SHARD DATA & CONSOLIDATING BENCHMARK JSON" -ForegroundColor Cyan
Write-Host "=========================================================="

$mergedBenchmarks = [System.Collections.Generic.List[PSObject]]::new()
$mergedContext = $null

foreach ($sKey in $shardDefs.Keys) {
    $sFile = "$shardsDir\$($shardDefs[$sKey].File)"
    if (Test-Path $sFile) {
        try {
            $sData = Get-Content -Path $sFile -Raw | ConvertFrom-Json
            if (-not $mergedContext -and $sData.context) {
                $mergedContext = $sData.context
            }
            if ($sData.benchmarks) {
                foreach ($bm in $sData.benchmarks) {
                    $mergedBenchmarks.Add($bm)
                }
            }
        } catch {
            Write-Warning "Could not read shard file: $sFile"
        }
    }
}

$masterData = [PSCustomObject]@{
    context    = $mergedContext
    benchmarks = $mergedBenchmarks
}

$masterData | ConvertTo-Json -Depth 10 | Set-Content -Path $masterJsonPath -Encoding UTF8
Write-Host "✅ Consolidated $($mergedBenchmarks.Count) benchmark scenario(s) into:" -ForegroundColor Green
Write-Host "   $masterJsonPath" -ForegroundColor Gray

# -------------------------------------------------------------
# Generate Release Audit & History Report
# -------------------------------------------------------------
if (-not $SkipReport) {
    Write-Host ""
    Write-Host "==========================================================" -ForegroundColor Cyan
    Write-Host " GENERATING MULTI-RELEASE AUDIT REPORT & HISTORY LEDGER" -ForegroundColor Cyan
    Write-Host "=========================================================="
    
    $compareScript = "tools\compare_release.ps1"
    if (Test-Path $compareScript) {
        & powershell -ExecutionPolicy Bypass -File $compareScript -Flavor $Flavor -BenchmarkJson $masterJsonPath
    } else {
        Write-Warning "Could not find $compareScript"
    }
}
