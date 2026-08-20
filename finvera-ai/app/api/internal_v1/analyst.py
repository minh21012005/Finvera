import json
import logging
from typing import AsyncIterator
from fastapi import APIRouter, Depends, Header, HTTPException, status
from fastapi.responses import StreamingResponse

from app.core.settings import settings
from app.features.chat.service import ChatOrchestrationService, OrchestrateAskRequest

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/analyst", tags=["analyst"])


def verify_internal_key(x_internal_api_key: str = Header(..., alias="X-Internal-Api-Key")) -> None:
    expected = settings.internal_api_key
    if not expected or x_internal_api_key != expected:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing X-Internal-Api-Key header",
        )


async def sse_event_generator(
    service: ChatOrchestrationService,
    request: OrchestrateAskRequest,
) -> AsyncIterator[str]:
    try:
        async for event in service.orchestrate_stream(request):
            data_str = json.dumps(event, default=str)
            yield f"data: {data_str}\n\n"
    except Exception as e:
        logger.exception("Error during analyst ask streaming")
        error_event = {
            "type": "final",
            "final": {
                "answer": "Lỗi xử lý luồng câu hỏi phân tích.",
                "structuredClaims": [],
                "documentClaims": [],
                "refused": True,
                "toolCalls": [],
                "toolCallBoundReached": False,
                "ruleVersion": "orchestration-v1",
            },
        }
        yield f"data: {json.dumps(error_event)}\n\n"


@router.post("/ask", dependencies=[Depends(verify_internal_key)])
async def ask_analyst_stream(
    request: OrchestrateAskRequest,
) -> StreamingResponse:
    """
    FR-001, FR-014: Internal SSE streaming endpoint for AI Analyst multi-tool ask orchestration.
    """
    service = ChatOrchestrationService()
    return StreamingResponse(
        sse_event_generator(service, request),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
