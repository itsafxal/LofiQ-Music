$rawLines = Get-Content (Join-Path $PSScriptRoot "test_raw_results.jsonl")
$tests = @{}
$passed = 0
$failed = 0
$skipped = 0

foreach ($line in $rawLines) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    try {
        $entry = $line | ConvertFrom-Json
        if ($entry.type -eq "testStart" -and -not $entry.test.name.StartsWith("loading ")) {
            $tests[$entry.test.id] = [ordered]@{
                id = $entry.test.id
                name = $entry.test.name
                result = "pending"
                time = 0
            }
        } elseif ($entry.type -eq "testDone" -and -not $entry.hidden) {
            if ($tests.ContainsKey($entry.testID)) {
                $tests[$entry.testID].result = $entry.result
                $tests[$entry.testID].time = $entry.time
                if ($entry.result -eq "success") {
                    $passed++
                } elseif ($entry.skipped -eq $true) {
                    $skipped++
                } else {
                    $failed++
                }
            }
        }
    } catch {}
}

$summary = [ordered]@{
    suite = "LofiQ Windows Desktop Test Suite"
    generatedAt = (Get-Date).ToString("o")
    totalTests = $tests.Count
    passed = $passed
    failed = $failed
    skipped = $skipped
    success = ($failed -eq 0 -and $passed -gt 0)
    tests = @($tests.Values)
}

$json = $summary | ConvertTo-Json -Depth 5
Set-Content -Path (Join-Path $PSScriptRoot "test_summary.json") -Value $json -Encoding UTF8

Write-Host "=== TEST SUMMARY PARSED ===" -ForegroundColor Green
Write-Host "Total Tests: $($tests.Count)"
Write-Host "Passed:      $passed"
Write-Host "Failed:      $failed"
Write-Host "Skipped:     $skipped"
Write-Host "Success:     $($summary.success)"

