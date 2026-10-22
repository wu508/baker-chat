# Scale Baker UI textures to the sizes the mod actually draws them at.
# Usage: powershell -ExecutionPolicy Bypass -File tools\prepare-textures.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$src  = Join-Path (Split-Path -Parent $root) 'baker-dx\assets'
$dst  = Join-Path $root 'src\main\resources\assets\baker\textures\ui'
New-Item -ItemType Directory -Force -Path $dst | Out-Null

# source, output name, target width, target height
$items = @(
    @('deco\achievement_main_deco05.png',                        'title_deco',      44, 10),
    @('deco\session_title_left_bar.png',                         'header_left',     46,  1),
    @('deco\session_title_right_bar.png',                        'header_right',   180, 26),
    @('extracted\decorate\sns_list_decorate_2.png',              'list_marks',      16,  4),
    @('extracted\decorate\deco_sns_tweet_decorate_02.png',       'card_watermark',  90, 20),
    @('extracted\decorate\deco_sns_tweet_decorate.png',          'line_deco',       24,  3),
    @('extracted\mask\mask_snscharentry_head.png',               'avatar_bg',       32, 32),
    @('extracted\bg\bg_snscharentry_head_Line.png',              'avatar_frame',    34, 34),
    @('extracted\icon\icon_sns_message_02.png',                  'icon_send',       16, 12),
    @('deco\input_area_emoticon.png',                            'icon_sticker',    14, 14),
    @('deco\input_area_more.png',                                'icon_more',       14, 14),
    @('deco\icon_new_session.png',                               'icon_new',        14,  8),
    @('deco\colors.png',                                         'colors',         160,  2),
    @('extracted\decorate\deco_sns_tweet_decorate_10.png',       'corner_deco',     80, 29),
    @('extracted\decorate\deco_sns_tweet_decorate_11.png',       'hb_deco',         40,  4),
    @('deco\top-deco-right.png',                                 'top_deco_right', 120,  4),
    @('extracted\icon\btn_SNS.png',                              'btn_sns',         16, 16),
    @('deco\bg_message_left.png',                                'bubble_left',     79, 57),
    @('deco\bg_message_right.png',                               'bubble_right',    79, 57)
)

foreach ($it in $items) {
    $in  = Join-Path $src $it[0]
    $out = Join-Path $dst ($it[1] + '.png')
    if (-not (Test-Path $in)) { Write-Warning "missing $($it[0])"; continue }

    $img = [System.Drawing.Image]::FromFile($in)
    $bmp = New-Object System.Drawing.Bitmap($it[2], $it[3], [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g   = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode  = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode    = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.SmoothingMode      = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $g.Clear([System.Drawing.Color]::Transparent)
    $g.DrawImage($img, 0, 0, $it[2], $it[3])
    $g.Dispose()
    $bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    $img.Dispose()
    "{0,-16} {1,4}x{2,-4} <- {3}" -f $it[1], $it[2], $it[3], $it[0]
}

# 表情：38 个 64x64，缩到 24x24 供 10px 内联绘制
$emojiDst = Join-Path $dst 'emoji'
New-Item -ItemType Directory -Force -Path $emojiDst | Out-Null
Get-ChildItem (Join-Path $src 'extracted\emoji') -Filter 'sns_emoji_*.png' | ForEach-Object {
    $img = [System.Drawing.Image]::FromFile($_.FullName)
    $bmp = New-Object System.Drawing.Bitmap(24, 24, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g   = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode  = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $g.Clear([System.Drawing.Color]::Transparent)
    $g.DrawImage($img, 0, 0, 24, 24)
    $g.Dispose()
    $bmp.Save((Join-Path $emojiDst $_.Name), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    $img.Dispose()
}
"emoji: {0} files" -f (Get-ChildItem $emojiDst).Count

Get-ChildItem $dst -Recurse -File | Measure-Object Length -Sum | ForEach-Object {
    "total: {0} files, {1:N1} KB" -f $_.Count, ($_.Sum / 1KB)
}
