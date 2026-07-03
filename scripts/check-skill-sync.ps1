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

function Report-Failure {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Message
    )

    Write-Host "ERROR: $Message"
    $script:failed = $true
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

$script:failed = $false

foreach ($skillName in $skillNames) {
    Assert-SkillName -SkillName $skillName

    $sourceDir = Join-Path $sourceRoot $skillName
    $targetDir = Join-Path $targetRoot $skillName

    if (-not (Test-Path -LiteralPath $sourceDir)) {
        Report-Failure "Missing source skill directory: $sourceDir"
        continue
    }

    if (-not (Test-Path -LiteralPath $targetDir)) {
        Report-Failure "Missing target skill directory: $targetDir"
        continue
    }

    $sourceFiles = Get-ChildItem -LiteralPath $sourceDir -Recurse -File | Sort-Object FullName
    $targetFiles = Get-ChildItem -LiteralPath $targetDir -Recurse -File | Sort-Object FullName

    foreach ($sourceFile in $sourceFiles) {
        $relativePath = Get-RelativePath -Root $sourceDir -Path $sourceFile.FullName
        $targetFile = Join-Path $targetDir $relativePath

        if (-not (Test-Path -LiteralPath $targetFile)) {
            Report-Failure "Missing target skill file: $targetFile"
            continue
        }

        $sourceHash = (Get-FileHash -LiteralPath $sourceFile.FullName -Algorithm SHA256).Hash
        $targetHash = (Get-FileHash -LiteralPath $targetFile -Algorithm SHA256).Hash

        if ($sourceHash -ne $targetHash) {
            Report-Failure "Skill file is out of sync: $skillName/$relativePath"
        }
    }

    foreach ($targetFile in $targetFiles) {
        $relativePath = Get-RelativePath -Root $targetDir -Path $targetFile.FullName
        $sourceFile = Join-Path $sourceDir $relativePath

        if (-not (Test-Path -LiteralPath $sourceFile)) {
            Report-Failure "Target skill file has no source copy: $targetFile"
        }
    }
}

if ($script:failed) {
    exit 1
}

Write-Host "Skill copies are in sync."
