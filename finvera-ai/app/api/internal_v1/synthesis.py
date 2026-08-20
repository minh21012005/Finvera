import json
import logging
from typing import List
import uuid
from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field
from app.core.auth import verify_internal_api_key
from app.features.rag.synthesis import SynthesizePassage, synthesize_answer_stream

logger = logging.getLogger(__name__)

router = APIRouter(tags=["Synthesis"])


class SynthesizeRequestDto(BaseModel):
    ownerId: uuid.UUID
    query: str = Field(..., min_length=1, max_length=2000)
    passages: List[SynthesizePassage] = Field(default_factory=list, max_length=20)


@router.post("/synthesize")
async def synthesize_endpoint(
    request: SynthesizeRequestDto,
    api_key: str = Depends(verify_internal_api_key),
):
    """
    POST /internal/v1/synthesize (SSE text/event-stream)
    Streams synthesis delta tokens followed by the final verified AnswerResult event.
    """
    async def sse_event_stream():
        try:
            async for event in synthesize_answer_stream(
                query=request.query,
                passages=request.passages,
                owner_id=request.ownerId,
            ):
                event_json = json.dumps(event, ensure_ascii=False)
                yield f"data: {event_json}\n\n"
        except Exception as e:
            logger.error(f"Error during synthesize streaming: {e}", exc_info=True)
            error_event = {
                "type": "final",
                "final": {
                    "answer": "Không thể hoàn thành câu trả lời do sự cố nội bộ.",
                    "citations": [],
                    "refused": True,
                    "ruleVersion": "rag-v1",
                },
            }
            yield f"data: {json.dumps(error_event, ensure_ascii=False)}\n\n"

    return StreamingResponse(
        sse_event_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
