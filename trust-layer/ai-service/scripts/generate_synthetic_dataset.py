from __future__ import annotations

import json
import random
from dataclasses import asdict, dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont


SEED = 20260326
GENUINE_COUNT = 10
SPOOF_COUNT = 5
FRAME_COUNT = 3


@dataclass
class Sample:
    sample_id: str
    label: str
    skin_tone_group: str
    lighting_group: str
    document_path: str
    liveness_frame_paths: list[str]
    spoof_image_path: str
    notes: str


def _font(size: int) -> ImageFont.ImageFont:
    try:
        return ImageFont.truetype("DejaVuSans.ttf", size)
    except OSError:
        return ImageFont.load_default()


def _draw_document(path: Path, name: str, dob: str, doc_no: str, nationality: str, tint: tuple[int, int, int]) -> None:
    image = Image.new("RGB", (1200, 760), color=(245, 245, 242))
    draw = ImageDraw.Draw(image)
    title_font = _font(40)
    body_font = _font(30)
    small_font = _font(22)

    draw.rounded_rectangle((60, 60, 1140, 700), radius=20, outline=(32, 32, 32), width=4, fill=(255, 255, 255))
    draw.rectangle((90, 115, 380, 520), fill=tint)
    draw.rectangle((860, 115, 1110, 290), fill=(230, 232, 238))
    draw.text((430, 110), "TRUST LAYER ID", font=title_font, fill=(10, 10, 10))
    draw.text((430, 200), f"NAME: {name}", font=body_font, fill=(10, 10, 10))
    draw.text((430, 260), f"DOB: {dob}", font=body_font, fill=(10, 10, 10))
    draw.text((430, 320), f"DOCUMENT NUMBER: {doc_no}", font=body_font, fill=(10, 10, 10))
    draw.text((430, 380), f"NATIONALITY: {nationality}", font=body_font, fill=(10, 10, 10))
    draw.text((90, 560), "Synthetic benchmark document for OCR and anti-spoof testing.", font=small_font, fill=(50, 50, 50))
    image.save(path, format="PNG")


def _draw_liveness_frames(base_path: Path, skin_tone: str, lighting: str) -> list[str]:
    paths: list[str] = []
    tone_map = {
        "light": (228, 190, 160),
        "medium": (180, 130, 95),
        "dark": (120, 85, 60),
    }
    light_map = {"bright": 1.25, "normal": 1.0, "dim": 0.75}
    base_tone = tone_map[skin_tone]
    exposure = light_map[lighting]
    for index in range(FRAME_COUNT):
        frame = Image.new("RGB", (720, 720), color=(20, 25, 35))
        draw = ImageDraw.Draw(frame)
        x_shift = index * 8
        y_shift = index * 4
        face_color = tuple(max(0, min(255, int(v * exposure))) for v in base_tone)
        draw.ellipse((180 + x_shift, 120 + y_shift, 540 + x_shift, 600 + y_shift), fill=face_color, outline=(25, 25, 25), width=4)
        draw.ellipse((280 + x_shift, 280 + y_shift, 325 + x_shift, 325 + y_shift), fill=(10, 10, 10))
        draw.ellipse((395 + x_shift, 280 + y_shift, 440 + x_shift, 325 + y_shift), fill=(10, 10, 10))
        draw.arc((300 + x_shift, 360 + y_shift, 420 + x_shift, 470 + y_shift), start=15, end=165, fill=(35, 15, 10), width=5)
        output = base_path / f"frame_{index + 1}.png"
        frame.save(output, format="PNG")
        paths.append(str(output.as_posix()))
    return paths


def _draw_spoof(path: Path, source_document: Path) -> None:
    with Image.open(source_document) as doc:
        spoof = doc.resize((960, 610))
        spoof = spoof.filter(ImageFilter.GaussianBlur(radius=1.2))
        overlay = Image.new("RGBA", spoof.size, (255, 255, 255, 40))
        spoof = Image.alpha_composite(spoof.convert("RGBA"), overlay).convert("RGB")
        draw = ImageDraw.Draw(spoof)
        draw.rectangle((20, 20, 300, 70), fill=(250, 250, 250))
        draw.text((30, 30), "SCREEN REPLAY", fill=(20, 20, 20), font=_font(24))
        spoof.save(path, format="PNG")


def main() -> None:
    random.seed(SEED)
    root = Path(__file__).resolve().parents[1]
    dataset_root = root / "dataset"
    documents_dir = dataset_root / "documents"
    liveness_dir = dataset_root / "liveness"
    spoof_dir = dataset_root / "spoof"
    manifests_dir = dataset_root / "manifests"

    for directory in (documents_dir, liveness_dir, spoof_dir, manifests_dir):
        directory.mkdir(parents=True, exist_ok=True)

    identities = [
        ("ABEBE TESFAYE", "11-09-1997", "ETA-10391", "ETHIOPIAN"),
        ("MARTA GEBRU", "02-02-1994", "ETB-32711", "ETHIOPIAN"),
        ("SAMUEL ALI", "15-12-1992", "ETA-77821", "ETHIOPIAN"),
        ("HANA KEBEDE", "06-06-1999", "ETC-28177", "ETHIOPIAN"),
        ("NATNAEL BELAY", "29-01-1991", "ETD-18371", "ETHIOPIAN"),
        ("SARA HASSEN", "03-08-1996", "ETE-84210", "ETHIOPIAN"),
        ("DANIEL WAKO", "13-04-1993", "ETF-33219", "ETHIOPIAN"),
        ("LIDYA YOHANNES", "24-10-1995", "ETG-52190", "ETHIOPIAN"),
        ("MERON TILAHUN", "09-05-1998", "ETH-49022", "ETHIOPIAN"),
        ("YONAS TAFERE", "21-07-1990", "ETI-11820", "ETHIOPIAN"),
    ]

    skin_groups = ["light", "medium", "dark"]
    lighting_groups = ["bright", "normal", "dim"]
    tints = [(180, 205, 230), (220, 195, 175), (175, 210, 180), (230, 210, 150)]

    samples: list[Sample] = []
    for index in range(GENUINE_COUNT):
        sample_id = f"genuine_{index + 1:02d}"
        person = identities[index]
        skin = skin_groups[index % len(skin_groups)]
        light = lighting_groups[index % len(lighting_groups)]
        doc_path = documents_dir / f"{sample_id}.png"
        _draw_document(doc_path, person[0], person[1], person[2], person[3], tints[index % len(tints)])

        liveness_sample_dir = liveness_dir / sample_id
        liveness_sample_dir.mkdir(parents=True, exist_ok=True)
        frame_paths = _draw_liveness_frames(liveness_sample_dir, skin, light)

        spoof_path = spoof_dir / f"{sample_id}.png"
        _draw_spoof(spoof_path, doc_path)

        samples.append(
            Sample(
                sample_id=sample_id,
                label="genuine",
                skin_tone_group=skin,
                lighting_group=light,
                document_path=str(doc_path.as_posix()),
                liveness_frame_paths=frame_paths,
                spoof_image_path=str(spoof_path.as_posix()),
                notes="Synthetic identity sample used for OCR/liveness baseline.",
            )
        )

    for index in range(SPOOF_COUNT):
        sample_id = f"spoof_{index + 1:02d}"
        source_idx = index % GENUINE_COUNT
        source = samples[source_idx]
        spoof_doc = documents_dir / f"{sample_id}.png"
        _draw_spoof(spoof_doc, Path(source.document_path))
        spoof_liveness_dir = liveness_dir / sample_id
        spoof_liveness_dir.mkdir(parents=True, exist_ok=True)
        frame_paths = _draw_liveness_frames(spoof_liveness_dir, "light", "bright")
        for frame_path in frame_paths:
            with Image.open(frame_path) as frame:
                blurred = frame.filter(ImageFilter.GaussianBlur(radius=2.5))
                glare = Image.new("RGBA", blurred.size, (255, 255, 255, 50))
                Image.alpha_composite(blurred.convert("RGBA"), glare).convert("RGB").save(frame_path, format="PNG")

        samples.append(
            Sample(
                sample_id=sample_id,
                label="spoof",
                skin_tone_group="n/a",
                lighting_group="bright",
                document_path=str(spoof_doc.as_posix()),
                liveness_frame_paths=frame_paths,
                spoof_image_path=str(spoof_doc.as_posix()),
                notes="Synthetic spoof example approximating printed/screen replay artifacts.",
            )
        )

    manifest = {
        "seed": SEED,
        "genuine_count": GENUINE_COUNT,
        "spoof_count": SPOOF_COUNT,
        "samples": [asdict(sample) for sample in samples],
    }
    manifest_path = manifests_dir / "synthetic_dataset_manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(f"Wrote manifest: {manifest_path}")
    print(f"Total samples: {len(samples)}")


if __name__ == "__main__":
    main()
