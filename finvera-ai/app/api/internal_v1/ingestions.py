from datetime import datetime, timezone
from typing import Optional
import uuid
from fastapi import APIRouter, BackgroundTasks, Depends, File, Form, UploadFile, status
from pydantic import BaseModel
from app.core.auth import verify_internal_api_key
from app.features.document.ingestion import process_ingestion_job

router = APIRouter(prefix="/ingestions", tags=["Ingestion"])


class IngestionAccepted(BaseModel):
    researchItemId: uuid.UUID
    status: str
    submittedAt: datetime


@router.post(
    "",
    status_code=status.HTTP_202_ACCEPTED,
    response_model=IngestionAccepted,
    dependencies=[Depends(verify_internal_api_key)],
)
async def submit_ingestion(
    background_tasks: BackgroundTasks,
    researchItemId: uuid.UUID = Form(...),
    itemType: str = Form(...),
    ownerId: uuid.UUID = Form(...),
    content: Optional[UploadFile] = File(None),
    text: Optional[str] = Form(None),
    mimeType: Optional[str] = Form(None),
    symbol: Optional[str] = Form(None),
    documentType: Optional[str] = Form(None),
    publicationDate: Optional[str] = Form(None),
):
    content_bytes = None
    if content:
        content_bytes = await content.read()

    # Dispatch async background task (NFR-003, research R-004)
    background_tasks.add_task(
        process_ingestion_job,
        research_item_id=researchItemId,
        item_type=itemType,
        owner_id=ownerId,
        content_bytes=content_bytes,
        text_content=text,
        mime_type=mimeType,
        symbol=symbol,
        document_type=documentType,
        publication_date=publicationDate,
    )

    return IngestionAccepted(
        researchItemId=researchItemId,
        status="PROCESSING",
        submittedAt=datetime.now(timezone.utc),
    )
