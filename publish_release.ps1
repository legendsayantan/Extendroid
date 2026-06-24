<#
.SYNOPSIS
    Publishes a new GitHub Release or updates an existing one with the latest app-release.apk.

.DESCRIPTION
    This script relies on the GitHub CLI (gh) to authenticate and interact with GitHub securely.
    It reads the versionName from app/build.gradle.kts and uses it as the release tag (e.g. v1.0.5).
#>

$ErrorActionPreference = "Stop"

# Paths
$buildFile = "app\build.gradle.kts"
$apkPath = "app\release\app-release.apk"

# 1. Check for GitHub CLI
if (-not (Get-Command "gh" -ErrorAction SilentlyContinue)) {
    Write-Host "GitHub CLI (gh) is not installed." -ForegroundColor Red
    Write-Host "Please install it from https://cli.github.com/ and log in using 'gh auth login'." -ForegroundColor Yellow
    exit 1
}

# 2. Read Version Name from gradle file
if (-not (Test-Path $buildFile)) {
    Write-Host "Could not find $buildFile. Ensure you run this script from the project root." -ForegroundColor Red
    exit 1
}

$versionName = $null
$content = Get-Content $buildFile
foreach ($line in $content) {
    if ($line -match 'versionName\s*=\s*"([^"]+)"') {
        $versionName = $matches[1]
        break
    }
}

if ([string]::IsNullOrWhiteSpace($versionName)) {
    Write-Host "Could not extract versionName from $buildFile." -ForegroundColor Red
    exit 1
}

$branchName = git rev-parse --abbrev-ref HEAD
if (-not $branchName) {
    Write-Host "Warning: Could not determine current git branch." -ForegroundColor Yellow
    $branchName = "unknown"
}

$tagName = "v$versionName-$branchName"
$renamedApkName = "Extendroid_v${versionName}_${branchName}.apk"
$renamedApkPath = "app\release\$renamedApkName"

Write-Host "Detected app version: " -NoNewline
Write-Host "v$versionName" -ForegroundColor Cyan
Write-Host "Target Release Tag: " -NoNewline
Write-Host $tagName -ForegroundColor Cyan

# 3. Check if APK exists
if (-not (Test-Path $apkPath)) {
    Write-Host "Signed Release APK not found at: $apkPath" -ForegroundColor Red
    Write-Host "A Signed Release APK must be built before running this script." -ForegroundColor Yellow
    exit 1
}

# 3.5. Verify APK version matches Gradle version
$androidHome = $env:ANDROID_HOME
if (-not $androidHome) {
    $androidHome = "$env:LOCALAPPDATA\Android\Sdk"
}

$aaptPath = Get-ChildItem -Path "$androidHome\build-tools" -Filter "aapt.exe" -Recurse -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName

if (-not $aaptPath) {
    Write-Host "Warning: Could not find aapt.exe in Android SDK to verify APK version. Skipping check." -ForegroundColor Yellow
} else {
    Write-Host "Verifying APK version matches Gradle..." -ForegroundColor Cyan
    $badging = & $aaptPath dump badging $apkPath | Out-String
    if ($badging -match "versionName='([^']+)'") {
        $apkVersion = $matches[1]
        if ($apkVersion -ne $versionName) {
            Write-Host "Error: Version mismatch!" -ForegroundColor Red
            Write-Host "Gradle build.gradle.kts says: '$versionName'" -ForegroundColor Yellow
            Write-Host "Your compiled APK says:       '$apkVersion'" -ForegroundColor Yellow
            Write-Host "You likely forgot to build a new APK after bumping the version. Please build a new Signed Release APK before publishing." -ForegroundColor Red
            exit 1
        } else {
            Write-Host "Verified! APK version matches Gradle version ($versionName)." -ForegroundColor Green
        }
    } else {
        Write-Host "Warning: Could not extract versionName from APK." -ForegroundColor Yellow
    }
}

# 4. Check if GitHub Release already exists
$releaseExists = $false
try {
    # Suppress output, we only care if it succeeds
    $null = gh release view $tagName 2>&1
    if ($LASTEXITCODE -eq 0) {
        $releaseExists = $true
    }
} catch {
    $releaseExists = $false
}

# Reset LASTEXITCODE
$global:LASTEXITCODE = 0

# 4.5 Fetch previous release notes to use as placeholder
$defaultBody = ""
try {
    if ($releaseExists) {
        $defaultBody = gh release view $tagName --json body --jq .body
    } else {
        $latestTag = gh release list --limit 1 --json tagName --jq ".[0].tagName"
        if (-not [string]::IsNullOrWhiteSpace($latestTag)) {
            $defaultBody = gh release view $latestTag --json body --jq .body
        }
    }
} catch {
    # Ignore errors fetching previous body
}
$global:LASTEXITCODE = 0

if ($releaseExists) {
    Write-Host "Release " -NoNewline
    Write-Host $tagName -ForegroundColor Cyan -NoNewline
    Write-Host " already exists on GitHub." -ForegroundColor Yellow
    
    $updateChoice = Read-Host "Do you want to UPDATE this existing release with the latest APK? (y/n)"
    
    if ($updateChoice -eq 'y') {
        Write-Host "Opening Notepad to edit release notes. Close Notepad when done to continue..." -ForegroundColor Cyan
        $tempFile = [System.IO.Path]::GetTempFileName()
        Set-Content -Path $tempFile -Value $defaultBody
        Start-Process notepad.exe -ArgumentList $tempFile -Wait
        $releaseBody = Get-Content -Path $tempFile -Raw
        Remove-Item -Path $tempFile -Force

        Write-Host "Renaming APK to $renamedApkName..." -ForegroundColor Cyan
        Copy-Item $apkPath -Destination $renamedApkPath -Force
        
        Write-Host "Uploading new APK to existing release $tagName..." -ForegroundColor Cyan
        # The --clobber flag allows overwriting files with the same name
        gh release upload $tagName $renamedApkPath --clobber
        
        Write-Host "Updating release notes..." -ForegroundColor Cyan
        gh release edit $tagName --notes "$releaseBody"

        Write-Host "Cleaning up renamed APK..." -ForegroundColor Cyan
        Remove-Item -Path $renamedApkPath -Force -ErrorAction SilentlyContinue

        Write-Host "Successfully updated release $tagName with the new APK and notes!" -ForegroundColor Green
    } else {
        Write-Host "Operation cancelled." -ForegroundColor Yellow
    }
} else {
    Write-Host "Release " -NoNewline
    Write-Host $tagName -ForegroundColor Cyan -NoNewline
    Write-Host " does not exist." -ForegroundColor Green
    
    $createChoice = Read-Host "Do you want to CREATE a new release for $tagName and upload the APK? (y/n)"
    
    if ($createChoice -eq 'y') {
        Write-Host "Opening Notepad to edit release notes. Save & Close Notepad when done to continue..." -ForegroundColor Cyan
        $tempFile = [System.IO.Path]::GetTempFileName()
        Set-Content -Path $tempFile -Value $defaultBody
        Start-Process notepad.exe -ArgumentList $tempFile -Wait
        $releaseBody = Get-Content -Path $tempFile -Raw
        Remove-Item -Path $tempFile -Force

        Write-Host "Renaming APK to $renamedApkName..." -ForegroundColor Cyan
        Copy-Item $apkPath -Destination $renamedApkPath -Force

        Write-Host "Creating new release $tagName and uploading APK..." -ForegroundColor Cyan
        gh release create $tagName $renamedApkPath --title "Extendroid $tagName" --notes "$releaseBody"
        
        Write-Host "Cleaning up renamed APK..." -ForegroundColor Cyan
        Remove-Item -Path $renamedApkPath -Force -ErrorAction SilentlyContinue

        Write-Host "Successfully published new release $tagName!" -ForegroundColor Green
    } else {
        Write-Host "Operation cancelled." -ForegroundColor Yellow
    }
}

Write-Host "Done." -ForegroundColor Cyan
