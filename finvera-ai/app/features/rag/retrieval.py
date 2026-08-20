from datetime import date, datetime, timezone
from typing import List, Optional
import uuid
from pydantic import BaseModel, Field
from app.core.settings import settings
from app.infrastructure.llm.embedding import embedding_adapter
from app.infrastructure.qdrant.collection import qdrant_service


class RankedChunk(BaseModel):
    chunk_id: uuid.UUID
    vector_point_id: uuid.UUID
    score: float
    vector_score: float
    recency_score: float
    filter_score: float
    item_type: str
    research_item_id: uuid.UUID
    symbol: Optional[str] = None
    document_type: Optional[str] = None
    news_category: Optional[str] = None
    published_at: Optional[str] = None


class RetrieveRequest(BaseModel):
    query: str
    owner_id: uuid.UUID
    symbol: Optional[str] = None
    document_type: Optional[str] = None
    news_category: Optional[str] = None
    date_from: Optional[str] = None
    date_to: Optional[str] = None
    top_k: int = 8


class RetrieveResult(BaseModel):
    chunks: List[RankedChunk]


def compute_recency_boost(published_at_str: Optional[str], as_of_date: Optional[date] = None) -> float:
    """
    Computes recencyBoost(c) per contracts/rag-v1.md:
    1.0 if item date is within last 90 days, linearly decaying to 0.0 at 2 years (730 days) or older,
    0.5 if date is unknown.
    """
    if not published_at_str:
        return 0.5

    try:
        if "T" in published_at_str:
            pub_date = datetime.fromisoformat(published_at_str.replace("Z", "+00:00")).date()
        else:
            pub_date = date.fromisoformat(published_at_str)
    except Exception:
        return 0.5

    now_date = as_of_date or datetime.now(timezone.utc).date()
    days_old = (now_date - pub_date).days

    if days_old < 0:
        return 1.0
    if days_old <= 90:
        return 1.0
    if days_old >= 730:
        return 0.0

    return 1.0 - (days_old - 90.0) / (730.0 - 90.0)


def rerank_candidate(
    vector_similarity: float,
    published_at_str: Optional[str],
    filter_matched: bool = True,
    as_of_date: Optional[date] = None,
) -> tuple[float, float, float, float]:
    """
    Computes finalScore(c) per rag-v1:
    finalScore = 0.70 * vectorSimilarity + 0.20 * recencyBoost + 0.10 * filterMatchBoost
    Returns (final_score, vector_similarity, recency_boost, filter_boost)
    """
    recency_boost = compute_recency_boost(published_at_str, as_of_date)
    filter_boost = 1.0 if filter_matched else 0.0
    final_score = 0.70 * vector_similarity + 0.20 * recency_boost + 0.10 * filter_boost
    return final_score, vector_similarity, recency_boost, filter_boost


def retrieve_ranked_chunks(
    request: RetrieveRequest,
    as_of_date: Optional[date] = None,
) -> List[RankedChunk]:
    """
    Performs vector search in Qdrant (top 30) and applies deterministic reranking formula (rag-v1),
    returning the top-K (default 8) candidates.
    """
    query_vector = embedding_adapter.embed_query(request.query)

    scored_points = qdrant_service.search_chunks(
        query_vector=query_vector,
        owner_id=request.owner_id,
        symbol=request.symbol,
        document_type=request.document_type,
        news_category=request.news_category,
        date_from=request.date_from,
        date_to=request.date_to,
        embedding_version=settings.embedding_version,
        limit=30,
    )

    ranked_candidates: List[RankedChunk] = []

    for pt in scored_points:
        payload = pt.payload or {}
        pub_date_str = payload.get("publication_date") or payload.get("published_at")
        final_score, vec_score, rec_score, flt_score = rerank_candidate(
            vector_similarity=float(pt.score),
            published_at_str=pub_date_str,
            filter_matched=True,
            as_of_date=as_of_date,
        )

        ranked_candidates.append(
            RankedChunk(
                chunk_id=uuid.UUID(payload.get("chunk_id", str(pt.id))),
                vector_point_id=uuid.UUID(str(pt.id)),
                score=round(final_score, 6),
                vector_score=round(vec_score, 6),
                recency_score=round(rec_score, 6),
                filter_score=round(flt_score, 6),
                item_type=payload.get("item_type", "DOCUMENT"),
                research_item_id=uuid.UUID(payload.get("research_item_id", str(pt.id))),
                symbol=payload.get("symbol"),
                document_type=payload.get("document_type"),
                news_category=payload.get("news_category"),
                published_at=pub_date_str,
            )
        )

    # Sort descending by finalScore, tie-break deterministically on chunk_id (U-6)
    ranked_candidates.sort(key=lambda c: (-c.score, str(c.chunk_id)))

    return ranked_candidates[: request.top_k]
