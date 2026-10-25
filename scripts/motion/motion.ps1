[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Check', 'Open', 'Pull', 'Review', 'Setup', 'Prepare', 'Train')]
    [string]$Action,
    [string]$Serial,
    [string]$AdbPath,
    [string]$PythonPath,
    [string]$DatasetDirectory,
    [ValidateRange(1, 65535)][int]$Port = 8765,
    [ValidateRange(1, 10000)][int]$Epochs = 40,
    [switch]$NoBrowser
)
$ErrorActionPreference = 'Stop'
$RepositoryRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
if (-not $DatasetDirectory) { $DatasetDirectory = Join-Path $RepositoryRoot 'motion-dataset\verified-trials' }
$DatasetDirectory = [System.IO.Path]::GetFullPath($DatasetDirectory)
$EnvironmentDirectory = Join-Path $RepositoryRoot '.venv-motion'
$EnvironmentPython = Join-Path $EnvironmentDirectory 'Scripts\python.exe'
$PreparedData = Join-Path $RepositoryRoot 'motion-dataset\loose-drop.npz'
$TrainingOutput = Join-Path $RepositoryRoot 'motion-output\loose-drop'

function Invoke-CheckedPython {
    param([string]$Executable, [string[]]$ToolArguments)
    & $Executable @ToolArguments
    if ($LASTEXITCODE -ne 0) { throw "Motion command failed (exit $LASTEXITCODE). See the message above." }
}

function Get-GeneralPython {
    if ($PythonPath) { return (Get-Command $PythonPath -ErrorAction Stop).Source }
    return (Get-Command 'python.exe' -ErrorAction Stop).Source
}

if ($Action -eq 'Setup') {
    if (-not (Test-Path -LiteralPath $EnvironmentPython)) {
        $SetupPython = $null
        if ($PythonPath) {
            $SetupPython = Get-GeneralPython
        } else {
            $Launcher = Get-Command 'py.exe' -ErrorAction SilentlyContinue
            if ($Launcher) {
                try {
                    $Candidate = & $Launcher.Source -3.11 -c 'import sys; print(sys.executable)' 2>$null
                    if ($LASTEXITCODE -eq 0 -and $Candidate) { $SetupPython = [string]$Candidate }
                } catch { $SetupPython = $null }
            }
            if (-not $SetupPython) { $SetupPython = Get-GeneralPython }
        }
        & $SetupPython -c 'import sys; assert sys.version_info[:2] in [(3,10),(3,11)], "Python 3.10 or 3.11 is required for TensorFlow 2.13"'
        if ($LASTEXITCODE -ne 0) {
            throw 'Install Python 3.11, then rerun Setup. You can select it with -PythonPath C:\path\to\Python311\python.exe. Recording, export and browser review also work with Python 3.12.'
        }
        Invoke-CheckedPython $SetupPython @('-m', 'venv', $EnvironmentDirectory)
    }
    Invoke-CheckedPython $EnvironmentPython @('-c', 'import sys; assert sys.version_info[:2] in [(3,10),(3,11)], "Existing .venv-motion must use Python 3.10/3.11"')
    Invoke-CheckedPython $EnvironmentPython @('-m', 'pip', 'install', '-r', (Join-Path $PSScriptRoot 'requirements.txt'))
    Write-Host 'Training environment ready. No app build or training was started.'
    return
}

if ($Action -in @('Check', 'Open', 'Pull')) {
    if (-not $AdbPath) {
        $AdbPath = Join-Path $RepositoryRoot '.android-sdk\platform-tools\adb.exe'
        if (-not (Test-Path -LiteralPath $AdbPath)) { $AdbPath = (Get-Command 'adb.exe' -ErrorAction Stop).Source }
    }
    $ToolArguments = @((Join-Path $PSScriptRoot 'device.py'), $Action.ToLowerInvariant(), '--adb', $AdbPath, '--output', $DatasetDirectory)
    if ($Serial) { $ToolArguments += @('--serial', $Serial) }
    Invoke-CheckedPython (Get-GeneralPython) $ToolArguments
    return
}

if ($Action -eq 'Review') {
    $ToolArguments = @((Join-Path $PSScriptRoot 'review_trials.py'), $DatasetDirectory, '--port', [string]$Port)
    if ($NoBrowser) { $ToolArguments += '--no-browser' }
    Invoke-CheckedPython (Get-GeneralPython) $ToolArguments
    return
}

if (-not (Test-Path -LiteralPath $EnvironmentPython)) {
    throw 'Run motion.ps1 -Action Setup first to create the Python 3.11 training environment.'
}
if ($Action -eq 'Prepare') {
    $Reviews = Join-Path $DatasetDirectory 'reviews.json'
    if (-not (Test-Path -LiteralPath $Reviews)) { throw 'Run Review and approve your recordings first; reviews.json is missing.' }
    Invoke-CheckedPython $EnvironmentPython @((Join-Path $PSScriptRoot 'prepare_loose_drop.py'), $DatasetDirectory, '--reviews', $Reviews, '--output', $PreparedData)
    Write-Host "Prepared data: $PreparedData"
} elseif ($Action -eq 'Train') {
    if (-not (Test-Path -LiteralPath $PreparedData)) { throw 'Run Prepare first; the verified dataset is missing.' }
    Invoke-CheckedPython $EnvironmentPython @((Join-Path $PSScriptRoot 'train.py'), $PreparedData, '--loose-drop', '--output', $TrainingOutput, '--epochs', [string]$Epochs)
    Write-Host "Model and evaluation: $TrainingOutput"
    Write-Host 'The model has not been copied into app assets. No app build was started.'
}
