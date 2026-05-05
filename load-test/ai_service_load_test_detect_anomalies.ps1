param(
  [int]$Requests = 30,
  [string]$BaseUrl = "http://localhost:4003"
)

$ErrorActionPreference = "Stop"

function Get-Percentile([double[]]$values, [double]$percentile) {
  if ($values.Count -eq 0) { return [double]::NaN }
  $sorted = $values | Sort-Object
  $index = [int][Math]::Floor(($percentile / 100.0) * ($sorted.Count - 1))
  return $sorted[$index]
}

$endpoint = "$BaseUrl/ai/detect-anomalies"

# Email must be valid to pass @Valid request validation.
$requestObject = @{
  patientId = "123e4567-e89b-12d3-a456-426614174001"
  name = "John Doe"
  email = "john.doe@example.com"
  dateOfBirth = "1980-01-15"
  address = "123 Main St"
  # Intentionally mismatched to trigger anomaly factor (but still passes validation).
  age = 10
  medicalHistory = "Diabetes, Hypertension"
  currentMedications = "Metformin, Lisinopril"
  allergies = "Penicillin"
}

$requestJson = $requestObject | ConvertTo-Json -Depth 10

$latenciesMs = New-Object System.Collections.Generic.List[double]
$successCount = 0
$failureCount = 0

for ($i = 0; $i -lt $Requests; $i++) {
  $sw = [System.Diagnostics.Stopwatch]::StartNew()
  try {
    $null = Invoke-RestMethod -Method Post -Uri $endpoint -ContentType "application/json" -Body $requestJson
    $sw.Stop()
    $latenciesMs.Add($sw.Elapsed.TotalMilliseconds)
    $successCount++
  } catch {
    $sw.Stop()
    $failureCount++
  }
}

$avg = if ($latenciesMs.Count -gt 0) { ($latenciesMs | Measure-Object -Average).Average } else { [double]::NaN }
$p50 = Get-Percentile $latenciesMs.ToArray() 50
$p90 = Get-Percentile $latenciesMs.ToArray() 90
$p95 = Get-Percentile $latenciesMs.ToArray() 95
$p99 = Get-Percentile $latenciesMs.ToArray() 99

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outDir = Join-Path -Path "." -ChildPath "load-test/results"
if (!(Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }
$outFile = Join-Path -Path $outDir -ChildPath ("ai_service_load_test_detect_anomalies_{0}.txt" -f $timestamp)

$report = @"
AI Service Load Test (detect-anomalies)
Requests: $Requests
Success: $successCount
Failures: $failureCount

Latency (ms):
  Avg: $avg
  P50: $p50
  P90: $p90
  P95: $p95
  P99: $p99

Endpoint: $endpoint
Timestamp: $(Get-Date -Format o)
"@

$report | Out-File -FilePath $outFile -Encoding UTF8
Write-Output $report

