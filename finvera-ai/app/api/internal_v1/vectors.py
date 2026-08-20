from typing import List
import uuid
from fastapi import APIRouter, Depends, status
from pydantic import BaseModel
from app.core.auth import verify_internal_api_key
from app.infrastructure.qdrant.collection import qdrant_service

router = APIRouter(tags=["Vectors"])


class DeleteVectorsRequest(BaseModel):
    vectorPointIds: List[uuid.UUID]


@router.delete(
    "/vectors",
    status_code=status.HTTP_204_NO_CONTENT,
    dependencies=[Depends(verify_internal_api_key)],
)
async def delete_vectors(request: DeleteVectorsRequest) -> None:
    qdrant_service.delete_points(request.vectorPointIds)
