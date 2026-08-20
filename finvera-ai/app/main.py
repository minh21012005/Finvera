from fastapi import FastAPI
from app.api.internal_v1.router import internal_v1_router
from app.core.settings import settings

app = FastAPI(
    title="Finvera AI Internal Service",
    version="1.0.0",
    description="Internal RAG and document intelligence service for Finvera",
)

# Register internal routes
app.include_router(internal_v1_router)


@app.get("/health", tags=["Health"])
async def health_check():
    return {
        "status": "UP",
        "service": settings.app_name,
        "environment": settings.environment,
    }
