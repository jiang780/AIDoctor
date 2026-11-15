param(
    [Parameter(Mandatory = $true)][string]$ImagePath
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Test-Cjk([char]$c) {
    $code = [int]$c
    if ($code -ge 0x4E00 -and $code -le 0x9FFF) { return $true }
    if ($code -ge 0x3400 -and $code -le 0x4DBF) { return $true }
    if ($code -ge 0xF900 -and $code -le 0xFAFF) { return $true }
    if ($code -ge 0x3000 -and $code -le 0x303F) { return $true }
    if ($code -ge 0xFF00 -and $code -le 0xFF65) { return $true }
    return $false
}

# 把同一视觉行上的词按 X 拼成一句：字距小就不加空格，跨列才留空格
function Join-RowWords($rowWords, $rowHeight) {
    $sb = New-Object System.Text.StringBuilder
    $prevRight = $null
    foreach ($w in $rowWords) {
        $t = [string]$w.Text
        if ($t.Length -eq 0) { continue }
        if ($sb.Length -eq 0) {
            [void]$sb.Append($t)
        } else {
            $gap = $w.X - $prevRight
            $last = $sb.ToString()[$sb.Length - 1]
            $first = $t[0]
            $sep = ''
            if ($gap -gt ($rowHeight * 1.30)) {
                $sep = '   '
            } elseif ($gap -gt ($rowHeight * 0.42)) {
                if ((Test-Cjk $last) -and (Test-Cjk $first)) { $sep = '' } else { $sep = ' ' }
            }
            [void]$sb.Append($sep).Append($t)
        }
        $prevRight = $w.X + $w.W
    }
    $text = $sb.ToString()
    # 参考范围里的连字符常被识别成「一」「－」「～」，数字之间的统一还原成 -
    $text = [regex]::Replace($text, '(?<=\d)\s*[一－—–~～]\s*(?=\d)', '-')
    return $text.Trim()
}

try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime | Out-Null

    $asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and
        $_.GetParameters().Count -eq 1 -and
        $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    })[0]

    function Await($WinRtTask, $ResultType) {
        $asTask = $asTaskGeneric.MakeGenericMethod($ResultType)
        $netTask = $asTask.Invoke($null, @($WinRtTask))
        $netTask.Wait(-1) | Out-Null
        $netTask.Result
    }

    [Windows.Storage.StorageFile, Windows.Storage, ContentType = WindowsRuntime] | Out-Null
    [Windows.Graphics.Imaging.BitmapDecoder, Windows.Graphics.Imaging, ContentType = WindowsRuntime] | Out-Null
    [Windows.Media.Ocr.OcrEngine, Windows.Media.Ocr, ContentType = WindowsRuntime] | Out-Null

    $engine = $null
    $chinese = [Windows.Media.Ocr.OcrEngine]::AvailableRecognizerLanguages |
        Where-Object { $_.LanguageTag -like 'zh-Hans*' } |
        Select-Object -First 1
    if ($chinese) {
        $engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromLanguage($chinese)
    }
    if (-not $engine) {
        $engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromUserProfileLanguages()
    }
    if (-not $engine) {
        Write-Output 'ERR_NO_ENGINE'
        exit 0
    }

    $file = Await ([Windows.Storage.StorageFile]::GetFileFromPathAsync($ImagePath)) ([Windows.Storage.StorageFile])
    $stream = Await ($file.OpenAsync([Windows.Storage.FileAccessMode]::Read)) ([Windows.Storage.Streams.IRandomAccessStream])
    $decoder = Await ([Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync($stream)) ([Windows.Graphics.Imaging.BitmapDecoder])
    $bitmap = Await ($decoder.GetSoftwareBitmapAsync()) ([Windows.Graphics.Imaging.SoftwareBitmap])
    $result = Await ($engine.RecognizeAsync($bitmap)) ([Windows.Media.Ocr.OcrResult])

    $stream.Dispose()

    # 收集所有词及其坐标：直接按引擎返回的行输出会变成「整列一列」，
    # 表格类图片必须先按 Y 聚成视觉行，再按 X 排序拼句。
    $words = New-Object System.Collections.ArrayList
    foreach ($line in $result.Lines) {
        foreach ($word in $line.Words) {
            $rect = $word.BoundingRect
            [void]$words.Add([pscustomobject]@{
                Text = [string]$word.Text
                X    = [double]$rect.X
                Y    = [double]$rect.Y
                W    = [double]$rect.Width
                H    = [double]$rect.Height
            })
        }
    }

    $rows = New-Object System.Collections.ArrayList
    $current = $null
    $currentCenter = 0.0
    $currentHeight = 0.0

    foreach ($w in ($words | Sort-Object -Property Y, X)) {
        if ($null -eq $w) { continue }
        $center = $w.Y + ($w.H / 2.0)
        $sameRow = $false
        if ($null -ne $current) {
            $tolerance = [Math]::Max($currentHeight, $w.H) * 0.55
            if ([Math]::Abs($center - $currentCenter) -le $tolerance) { $sameRow = $true }
        }
        if ($sameRow) {
            [void]$current.Add($w)
            $currentCenter = ($currentCenter * ($current.Count - 1) + $center) / $current.Count
            $currentHeight = [Math]::Max($currentHeight, $w.H)
        } else {
            if ($null -ne $current) { [void]$rows.Add($current) }
            $current = New-Object System.Collections.ArrayList
            [void]$current.Add($w)
            $currentCenter = $center
            $currentHeight = $w.H
        }
    }
    if ($null -ne $current) { [void]$rows.Add($current) }

    Write-Output "ENGINE $($engine.RecognizerLanguage.LanguageTag)"
    Write-Output "SIZE $($decoder.PixelWidth)x$($decoder.PixelHeight)"
    Write-Output 'TEXT_BEGIN'
    foreach ($row in $rows) {
        $sorted = $row | Sort-Object -Property X
        $height = ($row | Measure-Object -Property H -Maximum).Maximum
        $text = Join-RowWords $sorted $height
        if ($text.Length -gt 0) { Write-Output $text }
    }
    Write-Output 'TEXT_END'
} catch {
    Write-Output "ERR_EXCEPTION $($_.Exception.Message)"
}
