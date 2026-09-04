#!/usr/bin/env python3
"""Generate the styled, scan-safe DayLog download QR poster."""

from pathlib import Path

import qrcode
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from qrcode.image.styles.colormasks import SolidFillColorMask
from qrcode.image.styles.moduledrawers.pil import CircleModuleDrawer, RoundedModuleDrawer
from qrcode.image.styledpil import StyledPilImage


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "docs" / "daylog-download-qr.png"
DOWNLOAD_PAGE = "https://github.com/MeowuzZ/DayLog/releases/latest"

WIDTH, HEIGHT = 1200, 1500
INK = (75, 37, 47)
CORAL = (244, 106, 106)
ROSE = (255, 225, 230)
CREAM = (255, 251, 247)
WHITE = (255, 255, 255)


def font(size: int, medium: bool = False) -> ImageFont.FreeTypeFont:
    candidates = [
        "/System/Library/Fonts/STHeiti Medium.ttc" if medium else "/System/Library/Fonts/STHeiti Light.ttc",
        "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    for path in candidates:
        if Path(path).exists():
            return ImageFont.truetype(path, size=size)
    return ImageFont.load_default(size=size)


def vertical_gradient() -> Image.Image:
    image = Image.new("RGB", (WIDTH, HEIGHT))
    pixels = image.load()
    top = (255, 249, 250)
    bottom = (255, 233, 238)
    for y in range(HEIGHT):
        ratio = y / (HEIGHT - 1)
        color = tuple(round(a + (b - a) * ratio) for a, b in zip(top, bottom))
        for x in range(WIDTH):
            pixels[x, y] = color
    return image


def rounded_shadow(canvas: Image.Image, box: tuple[int, int, int, int], radius: int) -> None:
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(shadow)
    shifted = (box[0], box[1] + 18, box[2], box[3] + 18)
    draw.rounded_rectangle(shifted, radius=radius, fill=(99, 52, 62, 42))
    shadow = shadow.filter(ImageFilter.GaussianBlur(28))
    canvas.paste(shadow, (0, 0), shadow)


def make_qr() -> Image.Image:
    qr = qrcode.QRCode(
        version=None,
        error_correction=qrcode.constants.ERROR_CORRECT_H,
        box_size=18,
        border=4,
    )
    qr.add_data(DOWNLOAD_PAGE)
    qr.make(fit=True)
    return qr.make_image(
        image_factory=StyledPilImage,
        module_drawer=CircleModuleDrawer(),
        eye_drawer=RoundedModuleDrawer(radius_ratio=0.9),
        color_mask=SolidFillColorMask(back_color=WHITE, front_color=INK),
    ).convert("RGBA")


def main() -> None:
    canvas = vertical_gradient().convert("RGBA")
    draw = ImageDraw.Draw(canvas)

    # Soft fruit-like ornaments stay outside the QR quiet zone.
    draw.ellipse((-120, 1110, 280, 1510), fill=(255, 192, 202, 120))
    draw.ellipse((970, -110, 1270, 190), fill=(255, 213, 163, 145))
    draw.ellipse((1030, 1130, 1250, 1350), fill=(255, 196, 187, 130))
    draw.ellipse((1080, 1180, 1125, 1225), fill=CORAL)

    draw.text((WIDTH // 2, 92), "DayLog", font=font(74, True), fill=INK, anchor="mm")
    draw.text((WIDTH // 2, 160), "日报纪念册", font=font(34, True), fill=CORAL, anchor="mm")

    card = (74, 226, WIDTH - 74, 1278)
    rounded_shadow(canvas, card, 78)
    draw = ImageDraw.Draw(canvas)
    draw.rounded_rectangle(card, radius=78, fill=CREAM, outline=(255, 255, 255), width=6)

    # The circular frame and dot modules make the code feel round without
    # clipping the square quiet zone required by QR scanners.
    frame = (133, 285, WIDTH - 133, 1219)
    draw.ellipse(frame, fill=ROSE, outline=(255, 255, 255), width=12)
    inner = (170, 322, WIDTH - 170, 1182)
    draw.ellipse(inner, fill=WHITE)

    qr_image = make_qr()
    qr_side = 804
    qr_image = qr_image.resize((qr_side, qr_side), Image.Resampling.LANCZOS)
    qr_x = (WIDTH - qr_side) // 2
    qr_y = 350
    canvas.paste(qr_image, (qr_x, qr_y), qr_image)

    # A caption pill is outside the QR and remains legible in GitHub's preview.
    pill = (264, 1184, WIDTH - 264, 1260)
    draw = ImageDraw.Draw(canvas)
    draw.rounded_rectangle(pill, radius=38, fill=CORAL)
    draw.text((WIDTH // 2, 1222), "微信扫一扫 · 获取最新版", font=font(27, True), fill=WHITE, anchor="mm")

    draw.text((WIDTH // 2, 1352), "Android 8.0+   ·   当前版本 v1.4.1", font=font(27), fill=INK, anchor="mm")
    draw.text((WIDTH // 2, 1410), "github.com/MeowuzZ/DayLog", font=font(23), fill=(127, 89, 97), anchor="mm")

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(OUTPUT, format="PNG", optimize=True)
    print(f"Generated {OUTPUT}")
    print(f"QR payload: {DOWNLOAD_PAGE}")


if __name__ == "__main__":
    main()
