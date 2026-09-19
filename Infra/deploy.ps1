<#
.SYNOPSIS
Canonical deploy wrapper for the Memory Layer CDK app - the one sanctioned way to deploy.

.DESCRIPTION
Exists specifically to prevent repeats of two real incidents this project has already hit:

1. A stale Backend/target/backend.jar was deployed because `cdk deploy` was run without
   rebuilding it first (Phase 5). This script always runs `mvn clean package` before touching
   CDK, so a stale jar is not possible via this path.
2. `GOOGLE_OAUTH_CLIENT_ID` was unset, so InfraApp silently fell back to a placeholder value -
   which then got deployed to the real Cognito Google identity provider, briefly breaking
   Google Sign-In, compounded by `cdk deploy` pulling MemoryLayerAuthStack in as an undeclared
   dependency of another stack (Phase 5). Phase 7 removed the silent placeholder fallback
   entirely (InfraApp now fails synth outright if it's unset) and this script validates it
   before that point too, plus always passes `--exclusively` so a dependency stack is never
   silently included.

See Docs/OPERATIONS.md for the full deployment procedure this script implements.

.PARAMETER Stacks
One or more CDK stack names to deploy, e.g. MemoryLayerApiStack. Passed to a single
`cdk deploy ... --exclusively` call - not a loop - so CDK still resolves correct ordering
between the stacks you named, while never including one you didn't.

.PARAMETER SkipTests
Skip the Backend/Infra test suites. Not recommended; only for a fast iteration loop after
already validating in the same session.

.EXAMPLE
./deploy.ps1 -Stacks MemoryLayerApiStack

.EXAMPLE
./deploy.ps1 -Stacks MemoryLayerIngestionStack, MemoryLayerApiStack
#>
param(
    [Parameter(Mandatory = $true)]
    [string[]]$Stacks,

    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"

# Normalizes -Stacks regardless of how it arrived: a real PowerShell console splits a
# comma-separated list into array elements before this script ever sees it, but invoking via
# `powershell.exe -File` from a non-PowerShell caller does not - a comma-joined value binds as
# ONE array element containing the whole string, and separate space-separated tokens fail to
# bind at all ("positional parameter cannot be found"). Splitting the joined-then-rejoined
# value handles both cases identically.
$Stacks = ($Stacks -join ',') -split ',' | Where-Object { $_ }

$infraDir = $PSScriptRoot
$repoRoot = Split-Path -Parent $infraDir
$backendDir = Join-Path $repoRoot "Backend"

function Assert-RequiredEnv {
    param([string]$Name)
    $value = [System.Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        Write-Error "Required environment variable '$Name' is not set. Aborting before touching CDK."
        exit 1
    }
    return $value
}

Write-Host "== Validating required environment variables ==" -ForegroundColor Cyan
$googleClientId = Assert-RequiredEnv "GOOGLE_OAUTH_CLIENT_ID"
Assert-RequiredEnv "ALARM_EMAIL" | Out-Null

if ($googleClientId -like "*placeholder*") {
    Write-Error "GOOGLE_OAUTH_CLIENT_ID looks like a placeholder value ('$googleClientId'). Aborting."
    exit 1
}
Write-Host "GOOGLE_OAUTH_CLIENT_ID and ALARM_EMAIL are set and look real." -ForegroundColor Green

Write-Host "== Rebuilding Backend/target/backend.jar (mvn clean package) ==" -ForegroundColor Cyan
Push-Location $backendDir
try {
    if ($SkipTests) {
        mvn -q clean package "-DskipTests"
    }
    else {
        mvn -q clean package
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Backend build failed (exit $LASTEXITCODE). Aborting - refusing to deploy a stale or unbuilt jar."
    }
}
finally {
    Pop-Location
}
Write-Host "Backend build succeeded; the jar on disk now matches current source." -ForegroundColor Green

if (-not $SkipTests) {
    Write-Host "== Running Infra tests ==" -ForegroundColor Cyan
    Push-Location $infraDir
    try {
        mvn -q test
        if ($LASTEXITCODE -ne 0) {
            throw "Infra tests failed (exit $LASTEXITCODE). Aborting."
        }
    }
    finally {
        Pop-Location
    }
    Write-Host "Infra tests passed." -ForegroundColor Green
}

Write-Host "== cdk diff for: $($Stacks -join ', ') ==" -ForegroundColor Cyan
Push-Location $infraDir
try {
    npx cdk diff @Stacks --exclusively
}
finally {
    Pop-Location
}

Write-Host ""
$confirmation = Read-Host "Review the diff above carefully. Type 'deploy' to proceed, anything else to abort"
if ($confirmation -ne "deploy") {
    Write-Host "Aborted - nothing was deployed." -ForegroundColor Yellow
    exit 0
}

Write-Host "== Deploying: $($Stacks -join ', ') (--exclusively) ==" -ForegroundColor Cyan
Push-Location $infraDir
try {
    npx cdk deploy @Stacks --exclusively --require-approval never
    if ($LASTEXITCODE -ne 0) {
        throw "cdk deploy failed (exit $LASTEXITCODE)."
    }
}
finally {
    Pop-Location
}

Write-Host "== Deployment complete ==" -ForegroundColor Green
