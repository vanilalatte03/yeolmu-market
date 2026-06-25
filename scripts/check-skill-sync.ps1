$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$agentSkillsRoot = Join-Path $repoRoot ".agents/skills"
$claudeSkillsRoot = Join-Path $repoRoot ".claude/skills"

if (-not (Test-Path -LiteralPath $agentSkillsRoot)) {
    throw "Missing source skill directory: $agentSkillsRoot"
}

$failed = $false
$agentSkillFiles = Get-ChildItem -LiteralPath $agentSkillsRoot -Recurse -File -Filter "SKILL.md" |
    Sort-Object FullName

foreach ($agentSkillFile in $agentSkillFiles) {
    $relativeSkillDir = Resolve-Path -LiteralPath $agentSkillFile.DirectoryName -Relative
    $skillName = Split-Path $relativeSkillDir -Leaf
    $claudeSkillFile = Join-Path $claudeSkillsRoot "$skillName/SKILL.md"

    if (-not (Test-Path -LiteralPath $claudeSkillFile)) {
        Write-Error "Missing Claude skill copy: $claudeSkillFile"
        $failed = $true
        continue
    }

    $agentHash = (Get-FileHash -LiteralPath $agentSkillFile.FullName -Algorithm SHA256).Hash
    $claudeHash = (Get-FileHash -LiteralPath $claudeSkillFile -Algorithm SHA256).Hash

    if ($agentHash -ne $claudeHash) {
        Write-Error "Skill copy is out of sync: $skillName"
        Write-Error "  source: $($agentSkillFile.FullName)"
        Write-Error "  copy:   $claudeSkillFile"
        $failed = $true
    }
}

$claudeSkillFiles = Get-ChildItem -LiteralPath $claudeSkillsRoot -Recurse -File -Filter "SKILL.md" |
    Sort-Object FullName

foreach ($claudeSkillFile in $claudeSkillFiles) {
    $relativeSkillDir = Resolve-Path -LiteralPath $claudeSkillFile.DirectoryName -Relative
    $skillName = Split-Path $relativeSkillDir -Leaf
    $agentSkillFile = Join-Path $agentSkillsRoot "$skillName/SKILL.md"

    if (-not (Test-Path -LiteralPath $agentSkillFile)) {
        Write-Error "Claude skill has no source copy: $($claudeSkillFile.FullName)"
        $failed = $true
    }
}

if ($failed) {
    exit 1
}

Write-Host "Skill copies are in sync."
