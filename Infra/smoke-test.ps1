<#
.SYNOPSIS
Smoke test for the deployed Memory Layer API.

.DESCRIPTION
Always tests GET /api/v1/health (public, unauthenticated). If $env:SMOKE_ACCESS_TOKEN is set
to a valid, manually-obtained Cognito access token, also exercises GET /api/v1/documents,
POST /api/v1/search, and POST /api/v1/ask against the real, deployed API Gateway route -
including its actual JWT authorizer, not a bypass.

The token must be obtained manually (e.g. copied from browser devtools after signing in
through the real app). This project deliberately does not build a Hosted UI login automation
harness to acquire one programmatically (per the approved Phase 7 plan) - the Cognito app
client is a public PKCE client with no password-grant flow, by design.

Written for Windows PowerShell 5.1 compatibility (no -SkipHttpErrorCheck, added only in
PowerShell 7.4+) - status codes on a non-2xx response are read from the caught exception.

.PARAMETER ApiBaseUrl
Override the API base URL. If omitted, it's discovered from the deployed
MemoryLayerApiStack's ApiEndpoint CloudFormation output via the AWS CLI.

.EXAMPLE
./smoke-test.ps1

.EXAMPLE
$env:SMOKE_ACCESS_TOKEN = "<token>"; ./smoke-test.ps1
#>
param(
    [string]$ApiBaseUrl
)

$ErrorActionPreference = "Stop"
$script:failures = 0

function Invoke-SmokeCheck {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers = @{},
        [string]$Body = $null,
        [int[]]$ExpectedStatus = @(200)
    )

    $statusCode = $null
    try {
        $params = @{
            Method          = $Method
            Uri             = $Url
            Headers         = $Headers
            UseBasicParsing = $true
        }
        if ($Body) {
            $params["Body"] = $Body
            $params["ContentType"] = "application/json"
        }
        $response = Invoke-WebRequest @params
        $statusCode = [int]$response.StatusCode
    }
    catch {
        # Windows PowerShell 5.1's Invoke-WebRequest throws on non-2xx responses - the real
        # status code is still recoverable from the exception's own response object.
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
        }
        else {
            Write-Host "[FAIL] $Name -> no response (exception: $($_.Exception.Message))" -ForegroundColor Red
            $script:failures++
            return
        }
    }

    if ($ExpectedStatus -contains $statusCode) {
        Write-Host "[PASS] $Name -> $statusCode" -ForegroundColor Green
    }
    else {
        Write-Host "[FAIL] $Name -> $statusCode (expected one of: $($ExpectedStatus -join ', '))" -ForegroundColor Red
        $script:failures++
    }
}

if (-not $ApiBaseUrl) {
    Write-Host "Discovering API base URL from MemoryLayerApiStack..." -ForegroundColor Cyan
    $ApiBaseUrl = aws cloudformation describe-stacks --stack-name MemoryLayerApiStack --region ap-south-1 `
        --query "Stacks[0].Outputs[?OutputKey=='ApiEndpoint'].OutputValue" --output text
    if ([string]::IsNullOrWhiteSpace($ApiBaseUrl)) {
        Write-Error "Could not discover the API base URL from CloudFormation. Pass -ApiBaseUrl explicitly."
        exit 1
    }
}
Write-Host "API base URL: $ApiBaseUrl" -ForegroundColor Cyan
Write-Host ""

Write-Host "== Public route ==" -ForegroundColor Cyan
Invoke-SmokeCheck -Name "GET /api/v1/health" -Method GET -Url "$ApiBaseUrl/api/v1/health" -ExpectedStatus @(200)

$token = $env:SMOKE_ACCESS_TOKEN
if ([string]::IsNullOrWhiteSpace($token)) {
    Write-Host ""
    Write-Host "SMOKE_ACCESS_TOKEN is not set - skipping authenticated route checks." -ForegroundColor Yellow
    Write-Host "To cover /documents, /search, and /ask, sign in through the real app, copy the" -ForegroundColor Yellow
    Write-Host "access token (e.g. from browser devtools -> Application/Storage), and re-run:" -ForegroundColor Yellow
    Write-Host '  $env:SMOKE_ACCESS_TOKEN = "<token>"; ./smoke-test.ps1' -ForegroundColor Yellow
}
else {
    Write-Host ""
    Write-Host "== Authenticated routes (real API Gateway JWT authorizer) ==" -ForegroundColor Cyan
    $authHeaders = @{ "Authorization" = "Bearer $token" }

    Invoke-SmokeCheck -Name "GET /api/v1/documents" -Method GET -Url "$ApiBaseUrl/api/v1/documents" `
        -Headers $authHeaders -ExpectedStatus @(200)
    Invoke-SmokeCheck -Name "POST /api/v1/search" -Method POST -Url "$ApiBaseUrl/api/v1/search" `
        -Headers $authHeaders -Body '{"query":"smoke test"}' -ExpectedStatus @(200)
    Invoke-SmokeCheck -Name "POST /api/v1/ask" -Method POST -Url "$ApiBaseUrl/api/v1/ask" `
        -Headers $authHeaders -Body '{"question":"smoke test"}' -ExpectedStatus @(200)
}

Write-Host ""
if ($script:failures -eq 0) {
    Write-Host "== Smoke test passed ==" -ForegroundColor Green
    exit 0
}
else {
    Write-Host "== Smoke test failed: $($script:failures) check(s) did not pass ==" -ForegroundColor Red
    exit 1
}
