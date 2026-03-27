from __future__ import annotations

import json
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import requests


AI_SERVICE_URL = os.getenv("AI_SERVICE_URL", "http://localhost:5000")
MANIFEST_PATH = Path(__file__).resolve().parents[1] / "dataset" / "manifests" / "synthetic_dataset_manifest.json"
OUTPUT_PATH = Path(__file__).resolve().parents[1] / "dataset" / "manifests" / "evaluation_report.json"


@dataclass
class Counts:
    tp: int = 0
    tn: int = 0
    fp: int = 0
    fn: int = 0


def _safe_div(num: float, den: float) -> float:
    if den == 0:
        return 0.0
    return num / den


def _evaluate_document(session: requests.Session, document_path: Path) -> float:
    with document_path.open("rb") as doc_f:
        response = session.post(
            f"{AI_SERVICE_URL}/api/ai/verify-document",
            files={"document": (document_path.name, doc_f, "image/png")},
            timeout=30,
        )
    response.raise_for_status()
    data = response.json()
    return float(data.get("confidence", 0.0))


def _evaluate_liveness(session: requests.Session, frame_paths: list[Path]) -> float:
    files: list[tuple[str, tuple[str, bytes, str]]] = []
    handles = []
    try:
        for frame_path in frame_paths:
            handle = frame_path.open("rb")
            handles.append(handle)
            files.append(("frames", (frame_path.name, handle.read(), "image/png")))
        response = session.post(f"{AI_SERVICE_URL}/api/ai/verify-liveness", files=files, timeout=30)
        response.raise_for_status()
        data = response.json()
        return float(data.get("liveness_score", 0.0))
    finally:
        for handle in handles:
            handle.close()


def _evaluate_spoof(session: requests.Session, image_path: Path) -> float:
    with image_path.open("rb") as spoof_f:
        response = session.post(
            f"{AI_SERVICE_URL}/api/ai/anti-spoof",
            files={"image": (image_path.name, spoof_f, "image/png")},
            timeout=30,
        )
    response.raise_for_status()
    data = response.json()
    return float(data.get("spoof_probability", 1.0))


def _predict_label(document_confidence: float, liveness_score: float, spoof_probability: float) -> str:
    if document_confidence >= 0.55 and liveness_score >= 0.40 and spoof_probability < 0.50:
        return "genuine"
    return "spoof"


def _load_manifest() -> dict[str, Any]:
    if not MANIFEST_PATH.exists():
        raise FileNotFoundError(f"Dataset manifest not found: {MANIFEST_PATH}")
    return json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))


def main() -> None:
    manifest = _load_manifest()
    samples = manifest.get("samples", [])
    if not samples:
        raise RuntimeError("No samples found in dataset manifest.")

    metrics = Counts()
    by_group: dict[str, dict[str, int]] = {}
    results: list[dict[str, Any]] = []

    with requests.Session() as session:
        for sample in samples:
            expected = sample["label"]
            doc_conf = _evaluate_document(session, Path(sample["document_path"]))
            live_score = _evaluate_liveness(session, [Path(p) for p in sample["liveness_frame_paths"]])
            spoof_prob = _evaluate_spoof(session, Path(sample["document_path"]))
            predicted = _predict_label(doc_conf, live_score, spoof_prob)

            if expected == "genuine" and predicted == "genuine":
                metrics.tp += 1
            elif expected == "spoof" and predicted == "spoof":
                metrics.tn += 1
            elif expected == "spoof" and predicted == "genuine":
                metrics.fp += 1
            else:
                metrics.fn += 1

            group_key = f"{sample['skin_tone_group']}|{sample['lighting_group']}"
            if group_key not in by_group:
                by_group[group_key] = {"total": 0, "correct": 0}
            by_group[group_key]["total"] += 1
            if expected == predicted:
                by_group[group_key]["correct"] += 1

            results.append(
                {
                    "sample_id": sample["sample_id"],
                    "expected": expected,
                    "predicted": predicted,
                    "document_confidence": round(doc_conf, 4),
                    "liveness_score": round(live_score, 4),
                    "spoof_probability": round(spoof_prob, 4),
                }
            )

    precision = _safe_div(metrics.tp, metrics.tp + metrics.fp)
    recall = _safe_div(metrics.tp, metrics.tp + metrics.fn)
    f1 = _safe_div(2 * precision * recall, precision + recall)
    far = _safe_div(metrics.fp, metrics.fp + metrics.tn)  # false accepts / all spoof
    frr = _safe_div(metrics.fn, metrics.fn + metrics.tp)  # false rejects / all genuine

    group_accuracy = {
        key: round(_safe_div(values["correct"], values["total"]), 4) for key, values in sorted(by_group.items())
    }
    report = {
        "dataset": {
            "seed": manifest.get("seed"),
            "genuine_count": manifest.get("genuine_count"),
            "spoof_count": manifest.get("spoof_count"),
            "total_samples": len(samples),
        },
        "metrics": {
            "precision": round(precision, 4),
            "recall": round(recall, 4),
            "f1": round(f1, 4),
            "far": round(far, 4),
            "frr": round(frr, 4),
            "confusion_matrix": {
                "true_positive": metrics.tp,
                "true_negative": metrics.tn,
                "false_positive": metrics.fp,
                "false_negative": metrics.fn,
            },
        },
        "group_accuracy": group_accuracy,
        "sample_results": results,
    }
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_PATH.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(f"Wrote evaluation report: {OUTPUT_PATH}")
    print(json.dumps(report["metrics"], indent=2))


if __name__ == "__main__":
    main()
