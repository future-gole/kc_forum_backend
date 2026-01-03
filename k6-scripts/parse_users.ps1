$logFile = "raw_users_stdout.log"
$outFile = "users.json"
if (Test-Path $logFile) {
    $lines = Get-Content $logFile
    foreach ($line in $lines) {
        if ($line -match 'DATA_JSON=(.*)') {
            $escapedJson = $matches[1]
            [System.IO.File]::WriteAllText($outFile, $escapedJson)
            Write-Host "Successfully extracted JSON to $outFile"
            exit 0
        }
    }
    Write-Error "Could not find DATA_JSON in log file"
    exit 1
} else {
    Write-Error "Log file not found"
    exit 1
}
