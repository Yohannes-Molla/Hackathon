# AI Model Documentation

## Purpose

The AI service supports Trust Layer eKYC decisions with:

- document OCR extraction (`/api/ai/verify-document`)
- liveness scoring (`/api/ai/verify-liveness`)
- anti-spoof scoring (`/api/ai/anti-spoof`)
- combined risk scoring (`/api/ai/risk-score`)

This document records the Phase 3C benchmark dataset, evaluation method, and baseline metrics.

## Model/Heuristic Architecture

The current implementation is deterministic heuristics (not a trained deep model):

- **OCR**: `pytesseract` confidence from word-level confidence averages.
- **Liveness**: per-frame sharpness, motion variability, brightness variance, and edge density normalized into `liveness_score`.
- **Anti-spoof**: texture and contrast as realness proxies, minus reflection/exposure penalties; converted to `spoof_probability`.
- **Risk score**: weighted combination of low OCR confidence, low liveness, and spoof probability.

Decision thresholds currently used by evaluation:

- document confidence >= `0.55`
- liveness score >= `0.55`
- spoof probability < `0.45`

## Synthetic Test Dataset

Dataset location: `trust-layer/ai-service/dataset/`

Composition:

- 10 genuine identities
- 5 spoof attempts (printed/screen replay style)
- 3 liveness frames per sample

Fairness-oriented metadata dimensions:

- skin tone group: `light`, `medium`, `dark`
- lighting group: `bright`, `normal`, `dim`

Generation command:

```bash
cd trust-layer/ai-service
python scripts/generate_synthetic_dataset.py
```

Manifest output:

- `dataset/manifests/synthetic_dataset_manifest.json`

## Metrics Capture

Prerequisites:

1. AI service running on `http://localhost:5000`
2. Dataset generated

Run:

```bash
cd trust-layer/ai-service
AI_SERVICE_URL=http://127.0.0.1:5000 python scripts/evaluate_dataset.py
```

Report output:

- `dataset/manifests/evaluation_report.json`

Computed metrics:

- precision
- recall
- F1 score
- FAR (false accepts over all spoof samples)
- FRR (false rejects over all genuine samples)
- confusion matrix
- grouped accuracy by `skin_tone_group|lighting_group`

## Baseline Results (Current Run)

Latest captured metrics from `dataset/manifests/evaluation_report.json`:

- precision: `0.00`
- recall: `0.00`
- F1: `0.00`
- FAR: `0.00`
- FRR: `1.00`
- confusion matrix: TP `0`, TN `5`, FP `0`, FN `10`

Interpretation:

- current heuristics are strongly conservative and classify all evaluated genuine samples as spoof
- spoof rejection is currently strong on this synthetic set
- threshold and anti-spoof calibration must be tuned before production/demo claims

## Bias Mitigation Approach

Current safeguards:

- balanced synthetic samples across skin tone and lighting groups
- per-group accuracy reporting in every evaluation run
- no demographic attributes are used as model inputs

Planned improvements:

- expand beyond synthetic data with consented, representative real captures
- add subgroup-level FAR/FRR parity thresholds as release gates
- include adverse conditions (backlight, low-resolution camera, motion blur)
- track drift over time and re-tune thresholds with audit trail

## Limitations

- synthetic dataset does not fully represent production camera noise and demographics
- heuristics can be brittle vs. high-quality spoof artifacts
- OCR quality depends heavily on document layout and language
- metrics are baseline only; production-readiness requires larger external validation

## Reproducibility Notes

- dataset generation seed is fixed (`20260326`)
- all dependencies are pinned in `ai-service/requirements.txt`
- scripts are deterministic except for external OCR behavior variations across system packages
