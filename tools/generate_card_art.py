"""Create the exact 54 card faces and back at 160 × 240, with atlas gutters."""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

FONTS = Path("C:/Windows/Fonts")
SIZE = (160, 240)
STRIDE = (176, 256)
PADDING = 8
SUITS = "♠♥♣♦"
BLACK = "#20262b"
RED = "#9c2835"
PAPER = "#fcf2db"
BRASS = "#b1935d"
RANK = ImageFont.truetype(str(FONTS / "georgiab.ttf"), 39)
CHINESE = ImageFont.truetype(str(FONTS / "msyhbd.ttc"), 31)
JOKER_WORD = ImageFont.truetype(str(FONTS / "msyhbd.ttc"), 24)
SYMBOL = ImageFont.truetype(str(FONTS / "seguisym.ttf"), 29)
PIP = ImageFont.truetype(str(FONTS / "seguisym.ttf"), 26)
ACE = ImageFont.truetype(str(FONTS / "seguisym.ttf"), 98)
STAR = ImageFont.truetype(str(FONTS / "seguisym.ttf"), 41)


def centered(draw, center, text, font, color):
    left, top, right, bottom = draw.textbbox((0, 0), text, font=font)
    x, y = center
    draw.text((round(x - (left + right) / 2), round(y - (top + bottom) / 2)),
              text, fill=color, font=font)


def index(image, rank, suit, color, joker=False):
    marks = Image.new("RGBA", (61, 76))
    draw = ImageDraw.Draw(marks)
    centered(draw, (28, 23), rank, CHINESE if joker else RANK, color)
    centered(draw, (28, 56), suit, JOKER_WORD if joker else SYMBOL, color)
    image.alpha_composite(marks, (5, 4))
    image.alpha_composite(marks.transpose(Image.Transpose.ROTATE_180), (94, 160))


def body():
    image = Image.new("RGBA", SIZE)
    d = ImageDraw.Draw(image)
    d.rounded_rectangle((0, 0, 159, 239), 9, fill=PAPER, outline="#795d41", width=2)
    d.rounded_rectangle((5, 5, 154, 234), 6, outline="#c2ad83", width=1)
    return image


def pips(rank):
    if rank == 2:
        return [(80, 91), (80, 149)]
    if rank == 3:
        return [(80, y) for y in (79, 120, 161)]
    rows = (79, 161) if rank <= 5 else (79, 120, 161) if rank <= 8 else (76, 106, 134, 164)
    result = [(x, y) for x in (57, 103) for y in rows]
    extras = {5: (120,), 7: (99,), 8: (99, 141), 9: (120,), 10: (91, 149)}
    return result + [(80, y) for y in extras.get(rank, ())]


def court(draw, rank, suit, color):
    draw.rounded_rectangle((45, 72, 115, 168), radius=30, fill="#efe0bd", outline=BRASS, width=2)
    draw.rounded_rectangle((50, 77, 110, 163), radius=26, outline="#bda36f", width=1)
    draw.line((55, 120, 105, 120), fill=BRASS, width=1)
    if rank == 11:
        draw.polygon([(60, 108), (63, 92), (80, 102), (97, 92), (100, 108)], fill=color)
    elif rank == 12:
        draw.polygon([(61, 110), (60, 95), (73, 102), (80, 90), (87, 102), (100, 95), (99, 110)], fill=color)
    else:
        draw.polygon([(61, 110), (59, 92), (71, 101), (80, 87), (89, 101), (101, 92), (99, 110)], fill=color)
        draw.ellipse((77, 99, 83, 105), fill=PAPER)
    centered(draw, (80, 138), suit, SYMBOL, color)


def face(card):
    image = body()
    d = ImageDraw.Draw(image)
    if card >= 52:
        color = RED if card == 53 else BLACK
        mark = "大" if card == 53 else "小"
        d.ellipse((45, 85, 115, 155), outline=BRASS, width=2)
        d.ellipse((51, 91, 109, 149), outline="#cab98a", width=1)
        centered(d, (80, 119), "★", STAR, color)
        index(image, mark, "王", color, joker=True)
        return image

    rank = card // 4 + 3
    suit = SUITS[card % 4]
    color = RED if card % 2 else BLACK
    label = {11: "J", 12: "Q", 13: "K", 14: "A", 15: "2"}.get(rank, str(rank))
    if rank == 14:
        centered(d, (80, 120), suit, ACE, color)
    elif 11 <= rank <= 13:
        court(d, rank, suit, color)
    else:
        for x, y in pips(2 if rank == 15 else rank):
            centered(d, (x, y), suit, PIP, color)
    index(image, label, suit, color)
    return image


def back():
    image = body()
    d = ImageDraw.Draw(image)
    d.rounded_rectangle((6, 6, 153, 233), 6, fill="#17392e", outline=BRASS, width=2)
    d.rounded_rectangle((12, 12, 147, 227), 4, outline="#dec490", width=2)
    for y in range(27, 218, 22):
        for x in range(24, 142, 19):
            d.polygon([(x, y - 7), (x + 6, y), (x, y + 7), (x - 6, y)], outline="#6d855e")
    d.polygon([(80, 49), (135, 120), (80, 191), (25, 120)], fill="#6a2d30", outline="#dabb7e", width=3)
    d.polygon([(80, 58), (128, 120), (80, 182), (32, 120)], outline="#dabb7e", width=1)
    centered(d, (80, 117), "♠", STAR, "#f1d7a2")
    return image


def generate(destination):
    atlas = Image.new("RGBA", (8 * STRIDE[0], 7 * STRIDE[1]))
    for card in range(55):
        x = card % 8 * STRIDE[0] + PADDING
        y = card // 8 * STRIDE[1] + PADDING
        atlas.alpha_composite(back() if card == 54 else face(card), (x, y))
    destination.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(destination, optimize=True)


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[1]
    generate(root / "src/main/resources/assets/bosslandlord/textures/entity/cards_hd.png")
