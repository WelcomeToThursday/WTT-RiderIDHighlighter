param(
    [Parameter(Mandatory = $true)]
    [string]$DatabasePath
)

$ErrorActionPreference = 'Stop'
$database = @('', 'database', 'SPT_Data/database', 'SPT_Runtime/SPT_Data/database') |
    ForEach-Object { Join-Path $DatabasePath $_ } |
    Where-Object { Test-Path -LiteralPath (Join-Path $_ 'templates/items.json') } |
    Select-Object -First 1
if (-not $database) { throw 'Select an SPT installation, SPT_Data folder, or database folder.' }

$items = Get-Content -LiteralPath (Join-Path $database 'templates/items.json') -Raw | ConvertFrom-Json -AsHashtable
$english = Get-Content -LiteralPath (Join-Path $database 'locales/global/en.json') -Raw | ConvertFrom-Json -AsHashtable
$outputDirectory = Join-Path $PSScriptRoot '../src/main/resources/spt/item-names'
$questOutputDirectory = Join-Path $PSScriptRoot '../src/main/resources/spt/quest-names'
New-Item -ItemType Directory -Path $questOutputDirectory -Force | Out-Null
$quests = @{}
$questPath = Join-Path $database 'templates/quests.json'
if (Test-Path -LiteralPath $questPath) { $quests = Get-Content -LiteralPath $questPath -Raw | ConvertFrom-Json -AsHashtable }
$traderOutputDirectory = Join-Path $PSScriptRoot '../src/main/resources/spt/trader-names'
New-Item -ItemType Directory -Path $traderOutputDirectory -Force | Out-Null
$traders = @{}
$traderDirectory = Join-Path $database 'traders'
if (Test-Path -LiteralPath $traderDirectory) {
    foreach ($directory in Get-ChildItem -LiteralPath $traderDirectory -Directory) {
        $file = Join-Path $directory.FullName 'base.json'
        if (Test-Path -LiteralPath $file) {
            $trader = Get-Content -LiteralPath $file -Raw | ConvertFrom-Json -AsHashtable
            if ($trader['_id'] -cmatch '^[a-fA-F0-9]{24}$') { $traders[$trader['_id']] = $trader }
        }
    }
}
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
$ids = @($items.Keys | Where-Object { $_ -cmatch '^[a-fA-F0-9]{24}$' } | Sort-Object)
$categoryIds = @()
$handbookPath = Join-Path $database 'templates/handbook.json'
if (Test-Path -LiteralPath $handbookPath) {
    $handbook = Get-Content -LiteralPath $handbookPath -Raw | ConvertFrom-Json -AsHashtable
    $categoryIds = @($handbook.Categories | ForEach-Object Id |
        Where-Object { $_ -cmatch '^[a-fA-F0-9]{24}$' -and -not $items.ContainsKey($_) } | Sort-Object -Unique)
}

ConvertTo-Json -InputObject @($categoryIds | ForEach-Object { $_.ToLowerInvariant() }) |
    Set-Content -LiteralPath (Join-Path $outputDirectory '../handbook-category-ids.json') -Encoding utf8NoBOM

foreach ($localeFile in Get-ChildItem -LiteralPath (Join-Path $database 'locales/global') -Filter '*.json') {
    $locale = Get-Content -LiteralPath $localeFile.FullName -Raw | ConvertFrom-Json -AsHashtable
    $names = [ordered]@{}
    foreach ($id in $ids) {
        $name = $locale["$id Name"]
        if ([string]::IsNullOrWhiteSpace($name)) { $name = $english["$id Name"] }
        if ([string]::IsNullOrWhiteSpace($name)) { $name = $items[$id]['_name'] }
        if ([string]::IsNullOrWhiteSpace($name)) { $name = $id }
        $names[$id.ToLowerInvariant()] = $name
    }
    foreach ($id in $categoryIds) {
        $name = $locale[$id]
        if ([string]::IsNullOrWhiteSpace($name)) { $name = $english[$id] }
        if ([string]::IsNullOrWhiteSpace($name)) { $name = $id }
        $names[$id.ToLowerInvariant()] = $name
    }
    $names | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $outputDirectory $localeFile.Name) -Encoding utf8NoBOM
    $questNames = [ordered]@{}
    foreach ($id in @($quests.Keys | Where-Object { $_ -cmatch '^[a-fA-F0-9]{24}$' } | Sort-Object)) {
        $key = $quests[$id]['name']
        if ([string]::IsNullOrWhiteSpace($key)) { $key = "$id name" }
        $name = $locale[$key]
        if ([string]::IsNullOrWhiteSpace($name)) { $name = $english[$key] }
        if ([string]::IsNullOrWhiteSpace($name)) { $name = $quests[$id]['QuestName'] }
        if ([string]::IsNullOrWhiteSpace($name)) { $name = $id }
        $questNames[$id.ToLowerInvariant()] = $name
    }
    $questNames | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $questOutputDirectory $localeFile.Name) -Encoding utf8NoBOM
    $traderNames = [ordered]@{}
    foreach ($id in @($traders.Keys | Sort-Object)) {
        $name = $locale["$id Nickname"]
        if ([string]::IsNullOrWhiteSpace($name)) { $name = $english["$id Nickname"] }
        if ([string]::IsNullOrWhiteSpace($name)) { $name = $traders[$id]['nickname'] }
        if ([string]::IsNullOrWhiteSpace($name)) { $name = $id }
        $traderNames[$id.ToLowerInvariant()] = $name
    }
    $traderNames | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $traderOutputDirectory $localeFile.Name) -Encoding utf8NoBOM
    Write-Output "$($localeFile.BaseName): $($ids.Count) item IDs and $($categoryIds.Count) handbook category IDs"
}
