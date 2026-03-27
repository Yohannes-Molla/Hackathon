"""
train_transaction_fraud_model.py

Run this script ONCE to train the XGBoost transaction fraud model.
Generates synthetic training data, trains, evaluates, and saves the model.

Usage:
    python train_transaction_fraud_model.py

Output:
    app/models/weights/transaction_fraud_model.json
"""

import os
import numpy as np
import pandas as pd
from xgboost import XGBClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, roc_auc_score, confusion_matrix

np.random.seed(42)

# -----------------------------
# Synthetic data generation
# -----------------------------
# Realistic class imbalance: ~10% fraud rate
N_LEGIT = 5000
N_FRAUD = 500

print("⏳ Generating synthetic training data...")

# --- Legitimate transactions ---
# Daytime, normal amounts, known devices, domestic, low velocity
legit = pd.DataFrame({
    "amount": np.random.lognormal(
        mean=6, sigma=1, size=N_LEGIT
    ).clip(10, 50000),

    "hour_of_day": np.random.choice(
        range(8, 22), N_LEGIT         # business hours
    ),

    "day_of_week": np.random.randint(0, 7, N_LEGIT),

    "is_international": np.random.choice(
        [0, 1], N_LEGIT, p=[0.85, 0.15]
    ),

    "is_new_device": np.random.choice(
        [0, 1], N_LEGIT, p=[0.90, 0.10]
    ),

    # Merchant risk: 0=low risk (grocery), 1=high risk (crypto exchange)
    "merchant_risk": np.random.uniform(0.0, 0.3, N_LEGIT),

    "transaction_count_1hr": np.random.randint(0, 3, N_LEGIT),
    "transaction_count_24hr": np.random.randint(0, 10, N_LEGIT),

    # Established accounts
    "days_since_registration": np.random.randint(30, 1000, N_LEGIT),

    # Amount close to user's historical average
    "amount_vs_avg_ratio": np.random.normal(1.0, 0.3, N_LEGIT).clip(0.1, 5),

    "failed_attempts_today": np.random.choice(
        [0, 1, 2], N_LEGIT, p=[0.93, 0.05, 0.02]
    ),

    # Auth method: 0=silent, 1=pin, 2=biometric
    "auth_method_encoded": np.random.choice(
        [0, 1, 2], N_LEGIT, p=[0.60, 0.25, 0.15]
    ),

    "label": 0
})

# --- Fraudulent transactions ---
# Odd hours, large amounts, new devices, international, high velocity
fraud = pd.DataFrame({
    "amount": np.random.lognormal(
        mean=8, sigma=1.5, size=N_FRAUD
    ).clip(500, 200000),

    "hour_of_day": np.random.choice(
        [0, 1, 2, 3, 4, 22, 23], N_FRAUD  # late night / early morning
    ),

    "day_of_week": np.random.randint(0, 7, N_FRAUD),

    "is_international": np.random.choice(
        [0, 1], N_FRAUD, p=[0.25, 0.75]   # mostly international
    ),

    "is_new_device": np.random.choice(
        [0, 1], N_FRAUD, p=[0.15, 0.85]   # mostly new/unknown device
    ),

    "merchant_risk": np.random.uniform(0.5, 1.0, N_FRAUD),

    # High velocity — many transactions in short time
    "transaction_count_1hr": np.random.randint(3, 20, N_FRAUD),
    "transaction_count_24hr": np.random.randint(10, 60, N_FRAUD),

    # Fresh accounts — registered recently
    "days_since_registration": np.random.randint(0, 14, N_FRAUD),

    # Amount much higher than user's average
    "amount_vs_avg_ratio": np.random.normal(6.0, 2.0, N_FRAUD).clip(2, 30),

    "failed_attempts_today": np.random.choice(
        [0, 1, 2, 3], N_FRAUD, p=[0.30, 0.30, 0.25, 0.15]
    ),

    # Fraudsters often use silent auth (stolen credentials)
    "auth_method_encoded": np.random.choice(
        [0, 1, 2], N_FRAUD, p=[0.75, 0.20, 0.05]
    ),

    "label": 1
})

# Combine and shuffle
df = pd.concat([legit, fraud]).sample(frac=1, random_state=42).reset_index(drop=True)

FEATURE_COLS = [
    "amount",
    "hour_of_day",
    "day_of_week",
    "is_international",
    "is_new_device",
    "merchant_risk",
    "transaction_count_1hr",
    "transaction_count_24hr",
    "days_since_registration",
    "amount_vs_avg_ratio",
    "failed_attempts_today",
    "auth_method_encoded",
]

X = df[FEATURE_COLS]
y = df["label"]

print(f"   Total samples : {len(df)}")
print(f"   Legitimate    : {(y == 0).sum()}")
print(f"   Fraud         : {(y == 1).sum()}")
print(f"   Fraud rate    : {(y == 1).mean():.1%}")

# -----------------------------
# Train / test split
# stratify=y ensures same fraud ratio in both splits
# -----------------------------
X_train, X_test, y_train, y_test = train_test_split(
    X, y,
    test_size=0.20,
    stratify=y,
    random_state=42
)

print(f"\n⏳ Training XGBoost model...")

# scale_pos_weight handles class imbalance:
# tells XGBoost to weight fraud samples more heavily
scale = (y_train == 0).sum() / (y_train == 1).sum()

model = XGBClassifier(
    n_estimators          = 300,
    max_depth             = 5,
    learning_rate         = 0.05,
    subsample             = 0.8,
    colsample_bytree      = 0.8,
    min_child_weight      = 3,
    gamma                 = 0.1,
    scale_pos_weight      = scale,
    eval_metric           = "auc",
    early_stopping_rounds = 30,
    random_state          = 42,
)

model.fit(
    X_train, y_train,
    eval_set=[(X_test, y_test)],
    verbose=False,
)

print(f"   Best iteration : {model.best_iteration}")

# -----------------------------
# Evaluation
# -----------------------------
y_pred  = model.predict(X_test)
y_proba = model.predict_proba(X_test)[:, 1]

print("\n=== Classification Report ===")
print(classification_report(y_test, y_pred, target_names=["Legit", "Fraud"]))

print(f"ROC-AUC Score : {roc_auc_score(y_test, y_proba):.4f}")
print("(1.0 = perfect, 0.5 = random — aim for >= 0.90)")

print("\n=== Confusion Matrix ===")
cm = confusion_matrix(y_test, y_pred)
print(f"                  Predicted Legit  Predicted Fraud")
print(f"  Actual Legit         {cm[0][0]:<6}           {cm[0][1]}")
print(f"  Actual Fraud         {cm[1][0]:<6}           {cm[1][1]}")

# -----------------------------
# Feature importance
# -----------------------------
print("\n=== Feature Importance (top signals) ===")
importance = dict(zip(FEATURE_COLS, model.feature_importances_))
for feat, score in sorted(importance.items(), key=lambda x: x[1], reverse=True):
    bar = "█" * int(score * 50)
    print(f"  {feat:<30} {score:.4f}  {bar}")

# -----------------------------
# Save model
# -----------------------------
save_dir = "app/models/weights"
os.makedirs(save_dir, exist_ok=True)
save_path = os.path.join(save_dir, "transaction_fraud_model.json")
model.save_model(save_path)

print(f"\n✅ Model saved: {save_path}")
print(f"   Feature columns saved to config — do not change order")

# Save feature column order — model expects exact same order at inference
import json
meta = {
    "feature_cols":  FEATURE_COLS,
    "model_version": "xgboost_v1",
    "n_train":       len(X_train),
    "roc_auc":       round(roc_auc_score(y_test, y_proba), 4),
}
meta_path = os.path.join(save_dir, "transaction_fraud_model_meta.json")
with open(meta_path, "w") as f:
    json.dump(meta, f, indent=2)
print(f"   Metadata saved : {meta_path}")