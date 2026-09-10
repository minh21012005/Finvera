# FR-013/FR-014: đọc AST, không chạy crawl hoặc đụng database.
$ErrorActionPreference = "Stop"
$scriptPath = Join-Path $PSScriptRoot "..\..\refresh-data.ps1"
$tokens = $null
$errors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path $scriptPath), [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw "PowerShell parse failed: $errors" }
$text = $ast.Extent.Text
if ($text -notmatch 'ConvertTimeFromUtc') { throw "Missing explicit market timezone" }
if ($text -notmatch '"--start", \$historyStartDate, "--end", \$historyEndDate, "--lookback-days"') {
    throw "Crawl does not receive the frozen end date"
}
if (($text | Select-String 'refresh_export.py' -AllMatches).Matches.Count -ne 5) {
    throw "All five bootstrap exporters must use the runtime launcher"
}
$crawl = $text.IndexOf('& uv @allSymbolsArgs')
$complete = $text.IndexOf('Complete-Stage $script:RefreshState "1-crawl"')
$import = $text.IndexOf('Invoke-BackendStage -Name "Buoc 2/7')
if (-not ($crawl -lt $complete -and $complete -lt $import)) { throw "Import precedes crawl completion" }

# FR-021: chỉ chạy hàm thuần được trích từ AST, không chạy pipeline thật.
$resolve = $ast.Find({ param($node)
    $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Resolve-HistoryEndDate'
}, $true)
Invoke-Expression $resolve.Extent.Text
if ((Resolve-HistoryEndDate '2026-09-09' '2019-01-01') -ne '2026-09-09') { throw 'Explicit end changed' }
if ((Resolve-HistoryEndDate '2024-02-29' '2019-01-01') -ne '2024-02-29') { throw 'Leap day rejected' }
$zone = [TimeZoneInfo]::FindSystemTimeZoneById('SE Asia Standard Time')
$before = [TimeZoneInfo]::ConvertTimeFromUtc([DateTime]::UtcNow, $zone).ToString('yyyy-MM-dd')
$defaultEnd = Resolve-HistoryEndDate '' '2019-01-01'
$after = [TimeZoneInfo]::ConvertTimeFromUtc([DateTime]::UtcNow, $zone).ToString('yyyy-MM-dd')
if ($defaultEnd -notin @($before, $after)) { throw 'Wrong default end date' }
foreach ($invalid in @('2026-02-30', '2026-9-9', '09/09/2026', '2018-12-31', 'garbage')) {
    $rejected = $false
    try { Resolve-HistoryEndDate $invalid '2019-01-01' | Out-Null } catch { $rejected = $true }
    if (-not $rejected) { throw "Invalid date accepted: $invalid" }
}
if ($text -notmatch '\$historyEndDate = Resolve-HistoryEndDate \$EndDate \$historyStartDate') { throw 'EndDate not wired' }
if ($text -notmatch '"--market-overview", "--start", \$historyStartDate, "--end", \$historyEndDate') { throw 'Index end not wired' }
if (-not ($ast.ParamBlock.Parameters.Name.VariablePath.UserPath -contains 'EndDate')) { throw 'Missing EndDate parameter' }
$runKey = $ast.Find({ param($node)
    $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Get-RunKey'
}, $true)
Invoke-Expression $runKey.Extent.Text
$historyStartDate = '2019-01-01'
$historyEndDate = '2026-09-09'
$firstKey = Get-RunKey
$historyEndDate = '2026-09-10'
if ($firstKey -eq (Get-RunKey)) { throw 'End date missing from resume key' }
Write-Host 'PASS: parser, EndDate/default/validation, both exporters, resume key, five launchers, crawl-before-import'
