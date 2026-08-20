from fastapi import APIRouter, Depends
from app.core.auth import verify_internal_api_key
from app.features.rag.retrieval import RetrieveRequest, RetrieveResult, retrieve_ranked_chunks

router = APIRouter(tags=["Retrieval"])


@router.post(
    "/retrieve",
    response_model=RetrieveResult,
    dependencies=[Depends(verify_internal_api_key)],
)
async def retrieve_chunks(request: RetrieveRequest) -> RetrieveResult:
    chunks = retrieve_ranked_chunks(request)
    return RetrieveResult(chunks=chunks)
