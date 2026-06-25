$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$configPath = Join-Path $PSScriptRoot "skill-sync.json"

function Get-RelativePath {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Root,
        [Parameter(Mandatory = $true)]
        [string] $Path
    )

    $rootPath = [System.IO.Path]::GetFullPath($Root).TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar
    )
    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $rootWithSeparator = $rootPath + [System.IO.Path]::DirectorySeparatorChar

    if ($fullPath -ne $rootPath -and
        -not $fullPath.StartsWith($rootWithSeparator, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Path is outside root. root=$rootPath path=$fullPath"
    }

    return $fullPath.Substring($rootPath.Length).TrimStart(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar
    )
}

function Assert-SkillName {
    param(
        [Parameter(Mandatory = $true)]
        [string] $SkillName
    )

    if ([string]::IsNullOrWhiteSpace($SkillName) -or
        $SkillName -eq "." -or
        $SkillName -eq ".." -or
        $SkillName -match '[\\/]') {
        throw "Invalid mirrored skill name: $SkillName"
    }
}

if (-not (Test-Path -LiteralPath $configPath)) {
    throw "Missing skill sync config: $configPath"
}

$config = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
$sourceRoot = Join-Path $repoRoot $config.sourceRoot
$targetRoot = Join-Path $repoRoot $config.targetRoot
$skillNames = @($config.skills)

if (-not $config.sourceRoot -or -not $config.targetRoot -or $skillNames.Count -eq 0) {
    throw "Invalid skill sync config: sourceRoot, targetRoot, and skills are required."
}

$duplicates = $skillNames | Group-Object | Where-Object { $_.Count -gt 1 }
if ($duplicates) {
    throw "Duplicate mirrored skills: $($duplicates.Name -join ', ')"
}

if (-not (Test-Path -LiteralPath $sourceRoot)) {
    throw "Missing source skill directory: $sourceRoot"
}

foreach ($skillName in $skillNames) {
    Assert-SkillName -SkillName $skillName

    $sourceDir = Join-Path $sourceRoot $skillName
    $targetDir = Join-Path $targetRoot $skillName

    if (-not (Test-Path -LiteralPath $sourceDir)) {
        throw "Missing source skill directory: $sourceDir"
    }

    New-Item -ItemType Directory -Force -Path $targetDir | Out-Null

    $sourceFiles = Get-ChildItem -LiteralPath $sourceDir -Recurse -File | Sort-Object FullName
    foreach ($sourceFile in $sourceFiles) {
        $relativePath = Get-RelativePath -Root $sourceDir -Path $sourceFile.FullName
        $targetFile = Join-Path $targetDir $relativePath
        $targetFileDir = Split-Path -Parent $targetFile

        New-Item -ItemType Directory -Force -Path $targetFileDir | Out-Null
        Copy-Item -LiteralPath $sourceFile.FullName -Destination $targetFile -Force
    }

    $targetFiles = Get-ChildItem -LiteralPath $targetDir -Recurse -File | Sort-Object FullName
    foreach ($targetFile in $targetFiles) {
        $relativePath = Get-RelativePath -Root $targetDir -Path $targetFile.FullName
        $sourceFile = Join-Path $sourceDir $relativePath

        if (-not (Test-Path -LiteralPath $sourceFile)) {
            Remove-Item -LiteralPath $targetFile.FullName -Force
        }
    }

    $targetDirs = Get-ChildItem -LiteralPath $targetDir -Recurse -Directory |
        Sort-Object FullName -Descending
    foreach ($targetSubDir in $targetDirs) {
        if (-not (Get-ChildItem -LiteralPath $targetSubDir.FullName -Force)) {
            Remove-Item -LiteralPath $targetSubDir.FullName -Force
        }
    }

    Write-Host "Synced skill: $skillName"
}

& (Join-Path $PSScriptRoot "check-skill-sync.ps1")
