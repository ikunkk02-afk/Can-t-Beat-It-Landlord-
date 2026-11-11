"""Synthesize original card foley and short cues, then encode Minecraft Vorbis assets."""
import json
import math
from pathlib import Path
import random
import struct
import subprocess
import tempfile
import wave

RATE = 24_000
ASSETS = Path(__file__).resolve().parents[1] / "src/main/resources/assets/bosslandlord"
RNG = random.Random(22517)


def track(seconds):
    return [0.0] * round(seconds * RATE)


def add(samples, at, duration, signal):
    start = round(at * RATE)
    for i in range(min(round(duration * RATE), len(samples) - start)):
        samples[start + i] += signal(i / RATE)


def noise(samples, at, duration, volume, smoothing, decay=4):
    low = 0.0
    def signal(t):
        nonlocal low
        raw = RNG.uniform(-1, 1)
        low += (raw - low) * smoothing
        return volume * low * min(1, t * 1000) * math.exp(-decay * t / duration)
    add(samples, at, duration, signal)


def tone(samples, at, duration, volume, frequency, end=None, decay=3):
    end = frequency if end is None else end
    def signal(t):
        phase = 2 * math.pi * (frequency * t + (end - frequency) * t * t / (2 * duration))
        envelope = min(t * 220, 1) * math.exp(-decay * t / duration) * min(1, (duration - t) * 80)
        return volume * envelope * (math.sin(phase) + .2 * math.sin(2 * phase))
    add(samples, at, duration, signal)


def card(samples, at, volume):
    noise(samples, at, .084, volume, .63)
    noise(samples, at + .033, .043, volume * .8, .22)
    tone(samples, at + .035, .045, volume * .26, 300, 210)


sounds = {}
sounds["shuffle"] = track(1.38)
for start in (.07, .68):
    for i in range(10):
        card(sounds["shuffle"], start + i * .038, .11 + i * .013)
    card(sounds["shuffle"], start + .43, .39)

sounds["deal"] = track(1.55)
for i in range(15):
    card(sounds["deal"], .016 + .097 * i, .20 + .03 * (i % 3))

sounds["play"] = track(.24)
noise(sounds["play"], 0, .075, .26, .57, 2)
card(sounds["play"], .057, .45)

sounds["pass"] = track(.17)
noise(sounds["pass"], .005, .042, .20, .41)
tone(sounds["pass"], .005, .1, .23, 344, 194, 5)

sounds["bid"] = track(.38)
for i, frequency in enumerate((465, 622)):
    tone(sounds["bid"], .065 * i, .25, .27, frequency)

sounds["stake"] = track(.68)
for i, frequency in enumerate((440, 554, 659, 880)):
    tone(sounds["stake"], .096 * i, .34, .24, frequency)
    noise(sounds["stake"], .096 * i, .045, .16, .60)

sounds["airplane"] = track(.85)
tone(sounds["airplane"], .015, .63, .25, 200, 560, 1.1)
tone(sounds["airplane"], .14, .58, .16, 270, 145, 2.0)
noise(sounds["airplane"], .06, .63, .31, .30, 1.6)

sounds["bomb"] = track(.94)
tone(sounds["bomb"], .025, .69, .64, 110, 40, 3)
noise(sounds["bomb"], .025, .69, .65, .18, 3)
for i in range(5):
    noise(sounds["bomb"], .19 + i * .078, .12, .14, .63)

sounds["rocket"] = track(1.08)
tone(sounds["rocket"], .0, .34, .25, 240, 880, 1.1)
tone(sounds["rocket"], .08, .32, .22, 345, 1170, 1.2)
for i, frequency in enumerate((520, 690, 891, 1035)):
    tone(sounds["rocket"], .29 + i * .09, .51, .20, frequency, frequency * .79)
    noise(sounds["rocket"], .27 + i * .09, .22, .34, .54)

for name, notes in (("win", (392, 494, 587, 784)), ("loss", (440, 349, 262))):
    sounds[name] = track(.94)
    for i, frequency in enumerate(notes):
        tone(sounds[name], .135 * i, .46, .26, frequency, decay=2)

folder = ASSETS / "sounds"
folder.mkdir(parents=True, exist_ok=True)
with tempfile.TemporaryDirectory() as tmp:
    wav = Path(tmp) / "cue.wav"
    for name, samples in sounds.items():
        pcm = [round(32760 * math.tanh(sample)) for sample in samples]
        with wave.open(str(wav), "wb") as out:
            out.setparams((1, 2, RATE, 0, "NONE", "not compressed"))
            out.writeframes(struct.pack(f"<{len(pcm)}h", *pcm))
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(wav), "-c:a", "libvorbis",
                        "-q:a", "4", str(folder / (name + ".ogg"))], check=True)

manifest_path = ASSETS / "sounds.json"
manifest = json.loads(manifest_path.read_text(encoding="utf-8")) if manifest_path.exists() else {}
manifest.update({name: {"subtitle": f"subtitles.bosslandlord.{name}", "sounds": [f"bosslandlord:{name}"]}
                 for name in sounds})
manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
labels = {
    "zh_cn": ("洗牌", "发牌", "出牌", "过牌", "叫地主", "提高赌注", "飞机掠过", "炸弹爆开", "王炸", "牌局获胜", "牌局落败"),
    "en_us": ("Cards shuffle", "Cards dealt", "Cards played", "Pass", "Landlord bid", "Wager raised",
              "Airplane flies", "Bomb bursts", "Rocket played", "Match won", "Match lost"),
}
for locale, values in labels.items():
    path = ASSETS / "lang" / (locale + ".json")
    translations = json.loads(path.read_text(encoding="utf-8"))
    translations.update({f"subtitles.bosslandlord.{name}": value for name, value in zip(sounds, values)})
    path.write_text(json.dumps(translations, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Encoded {len(sounds)} original card-table cues.")
