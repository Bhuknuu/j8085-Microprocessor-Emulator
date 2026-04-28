<#
.SYNOPSIS
    j8085 Microprocessor Simulator — Build, Verify & Package Script
.DESCRIPTION
    Compiles all Java sources, runs basic sanity checks, and optionally
    packages the output into a runnable JAR. Idempotent (safe to re-run).
.EXAMPLE
    .\build.ps1               # Compile + verify
    .\build.ps1 -Package      # Compile + verify + create JAR
#>

param(
    [switch]$Package,
    [switch]$Clean
)

$ErrorActionPreference = "Stop"
$SrcDir  = Join-Path $PSScriptRoot "src"
$OutDir  = Join-Path $PSScriptRoot "out"
$JarName = "j8085-simulator.jar"
$JarPath = Join-Path $PSScriptRoot $JarName

Write-Host "═══════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  j8085 Microprocessor Simulator — Build"    -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════" -ForegroundColor Cyan

# ── Step 0: Check Prerequisites ──────────────────────────────
Write-Host "`n[0/4] Checking prerequisites..." -ForegroundColor Yellow
$javac = Get-Command javac -ErrorAction SilentlyContinue
$java  = Get-Command java  -ErrorAction SilentlyContinue
if (-not $javac) { Write-Host 'ERROR: javac not found. Install a JDK (17+).' -ForegroundColor Red; exit 1 }
if (-not $java)  { Write-Host 'ERROR: java not found. Ensure JAVA_HOME is set.' -ForegroundColor Red; exit 1 }

$javaVersion = & java --version 2>&1 | Select-Object -First 1
Write-Host "  Java: $javaVersion" -ForegroundColor DarkGray

# ── Step 1: Clean (optional) ─────────────────────────────────
if ($Clean) {
    Write-Host "`n[1/4] Cleaning output directory..." -ForegroundColor Yellow
    if (Test-Path $OutDir) { Remove-Item -Path $OutDir -Recurse -Force }
} else {
    Write-Host "`n[1/4] Clean skipped (use -Clean to force)" -ForegroundColor DarkGray
}

# ── Step 2: Compile ──────────────────────────────────────────
Write-Host "`n[2/4] Compiling sources..." -ForegroundColor Yellow
if (-not (Test-Path $OutDir)) { New-Item -Path $OutDir -ItemType Directory -Force | Out-Null }

$sourceFiles = Get-ChildItem -Path $SrcDir -Filter "*.java" -Recurse
$fileCount   = $sourceFiles.Count
Write-Host "  Found $fileCount source files in $SrcDir" -ForegroundColor DarkGray

& javac -d $OutDir ($sourceFiles | ForEach-Object { $_.FullName })

if ($LASTEXITCODE -ne 0) {
    Write-Host "COMPILATION FAILED." -ForegroundColor Red
    exit $LASTEXITCODE
}

$classCount = (Get-ChildItem -Path $OutDir -Filter "*.class" -Recurse).Count
Write-Host "  Compiled: $classCount class files" -ForegroundColor Green

# ── Step 3: Sanity Check ─────────────────────────────────────
Write-Host "`n[3/4] Verifying critical classes exist..." -ForegroundColor Yellow
$requiredClasses = @("Main.class", "Architecture.class", "Assembler.class",
                     "MainFrame.class", "EmulatorBridge.class", "DashboardPanel.class",
                     "EditorPanel.class", "TerminalPanel.class", "Theme.class",
                     "ThemeManager.class", "ALU.class", "OpcodeTable.class")
$missing = @()
foreach ($cls in $requiredClasses) {
    $path = Join-Path $OutDir $cls
    if (-not (Test-Path $path)) { $missing += $cls }
}
if ($missing.Count -gt 0) {
    Write-Host "  MISSING: $($missing -join ', ')" -ForegroundColor Red
    exit 1
} else {
    Write-Host "  All $($requiredClasses.Count) critical classes verified." -ForegroundColor Green
}

# ── Step 4: Package JAR (optional) ───────────────────────────
if ($Package) {
    Write-Host "`n[4/4] Packaging into $JarName..." -ForegroundColor Yellow
    $manifestDir = Join-Path $PSScriptRoot "META-INF"
    $manifestFile = Join-Path $manifestDir "MANIFEST.MF"
    if (-not (Test-Path $manifestDir)) { New-Item -Path $manifestDir -ItemType Directory -Force | Out-Null }
    Set-Content -Path $manifestFile -Value "Manifest-Version: 1.0`nMain-Class: Main`n" -NoNewline

    # Resolve jar from JAVA_HOME or same dir as javac
    $jarExe = $null
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin\jar.exe"
        if (Test-Path $candidate) { $jarExe = $candidate }
    }
    if (-not $jarExe) {
        $javacCmd = Get-Command javac -ErrorAction SilentlyContinue
        if ($javacCmd -and $javacCmd.Source) {
            $candidate = Join-Path (Split-Path $javacCmd.Source) "jar.exe"
            if (Test-Path $candidate) { $jarExe = $candidate }
        }
    }
    if (-not $jarExe) { $jarExe = "jar" } # fallback to PATH

    if ($jarExe -and (Get-Command $jarExe -ErrorAction SilentlyContinue)) {
        Push-Location $OutDir
        & $jarExe cfm $JarPath $manifestFile *.class
        Pop-Location

        if ($LASTEXITCODE -eq 0 -and (Test-Path $JarPath)) {
            $size = [math]::Round((Get-Item $JarPath).Length / 1KB, 1)
            Write-Host "  JAR created: $JarPath (${size} KB)" -ForegroundColor Green
        } else {
            Write-Host "  JAR packaging failed." -ForegroundColor Red
            exit 1
        }
    } else {
        Write-Host "  WARNING: 'jar' utility not found. Ensure full JDK is installed and in PATH. Packaging skipped." -ForegroundColor Yellow
    }

    # Cleanup temp manifest
    Remove-Item -Path $manifestDir -Recurse -Force -ErrorAction SilentlyContinue
} else {
    Write-Host "`n[4/4] Packaging skipped (use -Package to create JAR)" -ForegroundColor DarkGray
}

# ── Summary ──────────────────────────────────────────────────
Write-Host "`n═══════════════════════════════════════════" -ForegroundColor Green
Write-Host "  BUILD SUCCESSFUL" -ForegroundColor Green
Write-Host "  Sources:  $fileCount   Classes: $classCount" -ForegroundColor DarkGray
Write-Host "  To run:   java -cp out Main" -ForegroundColor DarkGray
if ($Package) {
    Write-Host "  Or JAR:   java -jar $JarName" -ForegroundColor DarkGray
}
Write-Host "═══════════════════════════════════════════" -ForegroundColor Green
