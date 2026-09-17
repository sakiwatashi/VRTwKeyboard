"""產生 SideQuest 上架用的橫幅。

用真實的青玉素材（app/src/main/res/drawable-nodpi/）合成，
九宮格切圖的算式跟 NineSlice.kt 一致，所以橫幅上的鍵盤就是實機的樣子。

用法：  python tools/make-banner.py
輸出：  docs/banner.png
"""

import os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
OUT = os.path.join(ROOT, "docs", "banner.png")

WIDTH, HEIGHT = 1920, 1080
FONT_BOLD = "C:/Windows/Fonts/msjhbd.ttc"
FONT_REG = "C:/Windows/Fonts/msjh.ttc"

# 大千鍵位，跟 ZhuyinImeService.ZHUYIN_ROWS 一致
ROWS = [
    "ㄅㄉˇˋㄓˊ˙ㄚㄞㄢㄦ",
    "ㄆㄊㄍㄐㄔㄗㄧㄛㄟㄣ",
    "ㄇㄋㄎㄑㄕㄘㄨㄜㄠㄤ",
    "ㄈㄌㄏㄒㄖㄙㄩㄝㄡㄥ",
]
FUNCTION = [("123", 1.5), ("⌨", 1.5), ("🎤", 1.2), ("☺", 1.2), ("注音", 3.6), ("，", 1.0), ("。", 1.0)]


def load(name, target_width):
    """裁掉透明邊再縮小，跟 JadeArt.load 同樣的順序。"""
    image = Image.open(os.path.join(RES, name + ".png")).convert("RGBA")
    alpha = np.array(image)[:, :, 3]
    ys, xs = np.where(alpha > 24)
    image = image.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
    sample = 1
    while image.width // (sample * 2) >= target_width:
        sample *= 2
    if sample > 1:
        image = image.resize((image.width // sample, image.height // sample), Image.LANCZOS)
    return image


def nine_slice(image, dest_width, dest_height, inset_x, inset_y):
    """跟 NineSlice.kt 一樣：角落共用同一個倍率，且最多各佔四成。"""
    bw, bh = image.size
    six = max(1, int(bw * min(max(inset_x, 0), 0.5)))
    siy = max(1, int(bh * min(max(inset_y, 0), 0.5)))
    scale = min(1.0, dest_width * 0.4 / six, dest_height * 0.4 / siy)
    dix, diy = six * scale, siy * scale
    src_cols = [0, six, bw - six, bw]
    src_rows = [0, siy, bh - siy, bh]
    dst_cols = [0, dix, dest_width - dix, dest_width]
    dst_rows = [0, diy, dest_height - diy, dest_height]
    out = Image.new("RGBA", (max(1, int(round(dest_width))), max(1, int(round(dest_height)))), (0, 0, 0, 0))
    for row in range(3):
        for col in range(3):
            w = int(round(dst_cols[col + 1] - dst_cols[col]))
            h = int(round(dst_rows[row + 1] - dst_rows[row]))
            if w <= 0 or h <= 0:
                continue
            piece = image.crop((src_cols[col], src_rows[row], src_cols[col + 1], src_rows[row + 1]))
            out.alpha_composite(piece.resize((w, h), Image.LANCZOS), (int(round(dst_cols[col])), int(round(dst_rows[row]))))
    return out


def halo_text(canvas, draw, x, y, text, font, colour, radius=4, strength=185):
    """深色字先鋪白光暈——對應程式裡 setShadowLayer 的白色版本。"""
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).text((x, y), text, font=font, fill=(255, 255, 255, strength), anchor="mm")
    canvas.alpha_composite(layer.filter(ImageFilter.GaussianBlur(radius)), (0, 0))
    draw.text((x, y), text, font=font, fill=colour, anchor="mm")


def main():
    canvas = Image.new("RGBA", (WIDTH, HEIGHT), (0x10, 0x1A, 0x17, 255))

    # 背景：把面板底圖放大鋪滿，再壓暗當底
    backdrop = Image.open(os.path.join(RES, "jade_panel.png")).convert("RGBA")
    backdrop = backdrop.resize((WIDTH, int(WIDTH * backdrop.height / backdrop.width)), Image.LANCZOS)
    backdrop = backdrop.filter(ImageFilter.GaussianBlur(18))
    dark = Image.new("RGBA", backdrop.size, (0x0C, 0x16, 0x13, 150))
    backdrop.alpha_composite(dark)
    canvas.alpha_composite(backdrop, (0, (HEIGHT - backdrop.height) // 2))

    # 鍵盤：擺在右下，像是浮在空間裡
    kb_w = int(WIDTH * 0.62)
    kb_h = int(kb_w / 2.22)
    panel = load("jade_panel", kb_w)
    key = load("jade_key", int(kb_w / 12.6))
    space = load("jade_space", int(kb_w / 12.6 * 4))

    board = Image.new("RGBA", (kb_w, kb_h), (0, 0, 0, 0))
    board.alpha_composite(nine_slice(panel, kb_w, kb_h, 0.16, 0.22), (0, 0))
    bd = ImageDraw.Draw(board)

    inset_l, inset_r, inset_t, inset_b = 0.060, 0.071, 0.118, 0.050
    left, right = kb_w * inset_l, kb_w * (1 - inset_r)
    top, bottom = kb_h * inset_t, kb_h * (1 - inset_b)
    inner_w, inner_h = right - left, bottom - top
    row_h = inner_h / 5
    main_w = inner_w * 11.0 / 12.6
    gap = max(2, int(kb_w * 0.003))

    glyph = ImageFont.truetype(FONT_BOLD, int(row_h * 0.44))
    small = ImageFont.truetype(FONT_BOLD, int(row_h * 0.28))
    ink = (0x14, 0x26, 0x1F, 255)

    def cap(x, y, w, h, label, font, colour, art=key, ix=0.20, iy=0.20):
        board.alpha_composite(nine_slice(art, w - gap * 2, h - gap * 2, ix, iy), (int(x + gap), int(y + gap)))
        halo_text(board, bd, x + w / 2, y + h / 2, label, font, colour, radius=3)

    for r, row in enumerate(ROWS):
        kw = main_w / len(row)
        for c, ch in enumerate(row):
            cap(left + c * kw, top + r * row_h, kw, row_h, ch, glyph, ink)

    total = sum(w for _, w in FUNCTION)
    x = left
    for label, weight in FUNCTION:
        w = main_w * weight / total
        is_space = label == "注音"
        cap(
            x, top + 4 * row_h, w, row_h, label,
            small if len(label) > 1 else glyph,
            (0x33, 0x4E, 0x44, 255) if is_space else ink,
            art=space if is_space else key,
            ix=0.06 if is_space else 0.20,
            iy=0.30 if is_space else 0.20,
        )
        x += w

    side_x, side_w = left + main_w, inner_w - main_w
    for label, tall, offset in [("⌫", 1, 0), ("↵", 2, 1), ("切換", 1, 3), ("收起", 1, 4)]:
        cap(side_x, top + offset * row_h, side_w, row_h * tall, label,
            small if len(label) > 1 else glyph, ink)

    canvas.alpha_composite(board, (WIDTH - kb_w - 60, HEIGHT - kb_h - 70))

    # 文字：左上
    draw = ImageDraw.Draw(canvas)
    title = ImageFont.truetype(FONT_BOLD, 96)
    sub = ImageFont.truetype(FONT_REG, 40)
    tag = ImageFont.truetype(FONT_REG, 30)
    draw.text((90, 120), "臺灣注音輸入法", font=title, fill=(0xEC, 0xF5, 0xF0, 255))
    draw.text((92, 245), "for Meta Quest", font=sub, fill=(0xA8, 0xC9, 0xB8, 255))
    for index, line in enumerate(["大千鍵位 · 整句選字", "離線語音輸入", "十一套配色 · 八種特效"]):
        draw.text((92, 330 + index * 52), line, font=tag, fill=(0x8F, 0xB3, 0xA3, 255))

    canvas.convert("RGB").save(OUT, quality=95)
    print("輸出", OUT, canvas.size)


if __name__ == "__main__":
    main()
