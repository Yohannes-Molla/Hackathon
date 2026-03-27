import os
os.environ["TF_ENABLE_ONEDNN_OPTS"] = "0"   # must be set before any TF/DeepFace import

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.config import validate_config, APP_NAME, APP_VERSION, DEBUG
from app.api.routes import router


# -------------------------------------------------------
# Validate config at startup — fails loudly if anything
# is wrong before any request comes in
# -------------------------------------------------------
validate_config()


# -------------------------------------------------------
# FastAPI app
# -------------------------------------------------------
app = FastAPI(
    title=APP_NAME,
    version=APP_VERSION,
    description="eKYC AI Service — OCR, Face Verification, Liveness, Fraud Scoring",
    docs_url="/docs",       # Swagger UI at http://localhost:8000/docs
    redoc_url="/redoc",
    debug=DEBUG,
)


# -------------------------------------------------------
# CORS
# Allow BE (Node.js) to call AI service from a different port/host
# In production, replace "*" with the actual BE origin
# -------------------------------------------------------
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],        # TODO: restrict to BE origin in production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# -------------------------------------------------------
# Register all routes under /api/v1
# -------------------------------------------------------
app.include_router(router, prefix="/api/v1")


# -------------------------------------------------------
# Root
# -------------------------------------------------------
@app.get("/")
async def root():
    return {
        "service": APP_NAME,
        "version": APP_VERSION,
        "docs":    "/docs",
        "health":  "/api/v1/health",
    }