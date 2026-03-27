# Synthetic AI Evaluation Dataset

This folder contains a reproducible synthetic benchmark used for Phase 3C.

## Dataset Shape

- `documents/`: generated document images used for OCR checks.
- `liveness/`: 3 frames per sample for liveness scoring.
- `spoof/`: synthetic spoof artifacts (printed/screen-replay style).
- `manifests/synthetic_dataset_manifest.json`: metadata for all samples.
- `manifests/evaluation_report.json`: generated metrics output.

## Composition

- 10 `genuine` samples.
- 5 `spoof` samples.
- Genuine samples are distributed across skin-tone groups (`light`, `medium`, `dark`) and lighting groups (`bright`, `normal`, `dim`) for fairness checks.

## Re-generate

Run from `trust-layer/ai-service`:

```bash
python scripts/generate_synthetic_dataset.py
```

## Evaluate

1. Start AI service on port `5000`.
2. Execute:

```bash
python scripts/evaluate_dataset.py
```

The script computes precision, recall, F1, FAR, FRR, confusion matrix, and per-group accuracy.
