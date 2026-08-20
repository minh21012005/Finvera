from typing import Optional
from fastapi import Header, HTTPException, status
from app.core.settings import settings


async def verify_internal_api_key(
    x_internal_api_key: Optional[str] = Header(None, alias="X-Internal-Api-Key", description="Shared internal API key"),
) -> str:
    """
    Validates that the caller provided the correct X-Internal-Api-Key header (SEC-001, research R-003).
    Rejects missing or invalid keys with HTTP 401 Unauthorized.
    """
    if not x_internal_api_key or x_internal_api_key != settings.internal_api_key:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing X-Internal-Api-Key",
        )
    return x_internal_api_key
