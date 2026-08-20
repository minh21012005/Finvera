from fastapi import APIRouter
from app.api.internal_v1.ingestions import router as ingestions_router
from app.api.internal_v1.retrieval import router as retrieval_router
from app.api.internal_v1.vectors import router as vectors_router

internal_v1_router = APIRouter(prefix="/internal/v1")
internal_v1_router.include_router(ingestions_router)
internal_v1_router.include_router(retrieval_router)
internal_v1_router.include_router(vectors_router)
