"""Encode the supplied 欢乐斗地主 tracks as Minecraft sound resources."""

import argparse
import json
from pathlib import Path
import re
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("folder", type=Path, help="Folder containing the original MP3s")
    folder = parser.parse_args().folder

    groups = {
        "游戏中": ("game", 3, True),
        "激烈": ("intense", 2, True),
        "胜利": ("victory", 2, False),
        "失败": ("defeat", 2, False),
    }
    pattern = re.compile(r"欢乐斗地主-(游戏中|激烈|胜利|失败)(\d+)\.mp3$", re.I)
    tracks = {}
    for path in folder.iterdir():
        match = pattern.search(path.name)
        if not path.is_file() or not match:
            continue
        key = (match[1], int(match[2]))
        if key in tracks:
            raise ValueError(f"Duplicate track: {key}")
        tracks[key] = path
    expected = {(label, i) for label, (_, count, _) in groups.items() for i in range(1, count + 1)}
    if tracks.keys() != expected:
        raise ValueError(f"Missing tracks: {expected - tracks.keys()}. Unexpected tracks: {tracks.keys() - expected}.")

    assets = Path(__file__).resolve().parents[1] / "src/main/resources/assets/bosslandlord"
    output = assets / "sounds/music"
    output.mkdir(parents=True, exist_ok=True)
    manifest_path = assets / "sounds.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    for label, (cue, count, streaming) in groups.items():
        sounds = []
        for i in range(1, count + 1):
            name = f"{cue}_{i}"
            subprocess.run([
                "ffmpeg", "-v", "error", "-y", "-i", str(tracks[label, i]),
                "-map", "0:a:0", "-map_metadata", "0", "-c:a", "libvorbis", "-q:a", "5",
                str(output / f"{name}.ogg"),
            ], check=True)
            sounds.append({"name": f"bosslandlord:music/{name}", "stream": streaming})
        manifest[f"music.{cue}"] = {"sounds": sounds}
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Encoded {len(tracks)} tracks and assigned all four playback groups.")


if __name__ == "__main__":
    main()
