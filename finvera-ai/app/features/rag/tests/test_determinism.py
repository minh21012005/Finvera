import uuid
import pytest
from app.infrastructure.llm.embedding import embedding_adapter
from app.infrastructure.qdrant.collection import qdrant_service
from app.features.rag.retrieval import retrieve_ranked_chunks, RetrieveRequest
from app.features.rag.synthesis import synthesize_answer_stream
from app.features.rag.citations import verify_citation_claims, RawCitationClaim
from qdrant_client import models as qmodels


@pytest.mark.asyncio
async def test_embedding_determinism():
    """
    U-6: Verifies that identical text with same embedding model produces identical vectors.
    """
    text1 = "Công ty Cổ phần FPT công bố doanh thu hợp nhất năm 2025."
    text2 = "Công ty Cổ phần FPT công bố doanh thu hợp nhất năm 2025."

    vec1 = embedding_adapter.embed_query(text1)
    vec2 = embedding_adapter.embed_query(text2)

    assert len(vec1) == 768
    assert len(vec2) == 768
    assert vec1 == vec2


def test_retrieval_and_reranking_determinism():
    """
    U-6: Verifies that identical query on identical corpus yields deterministic ranking order.
    """
    owner_id = uuid.uuid4()
    chunk1_id = uuid.uuid4()
    chunk2_id = uuid.uuid4()

    point1_id = uuid.uuid4()
    point2_id = uuid.uuid4()

    text1 = "Báo cáo thường niên FPT năm 2025: Lợi nhuận trước thuế tăng 20%."
    text2 = "Tin tức thị trường chứng khoán VN-Index phiên sáng."

    emb1 = embedding_adapter.embed_query(text1)
    emb2 = embedding_adapter.embed_query(text2)

    points = [
        qmodels.PointStruct(
            id=str(point1_id),
            vector=emb1,
            payload={
                "owner_id": str(owner_id),
                "chunk_id": str(chunk1_id),
                "research_item_id": str(uuid.uuid4()),
                "item_type": "DOCUMENT",
                "symbol": "FPT",
                "document_type": "ANNUAL_REPORT",
                "publication_date": "2025-03-15",
                "content_hash": "hash1",
                "embedding_version": "gemini-embedding-v1",
            },
        ),
        qmodels.PointStruct(
            id=str(point2_id),
            vector=emb2,
            payload={
                "owner_id": str(owner_id),
                "chunk_id": str(chunk2_id),
                "research_item_id": str(uuid.uuid4()),
                "item_type": "NEWS_ARTICLE",
                "symbol": None,
                "document_type": None,
                "publication_date": "2025-03-16",
                "content_hash": "hash2",
                "embedding_version": "gemini-embedding-v1",
            },
        ),
    ]

    qdrant_service.upsert_chunks(points)

    req = RetrieveRequest(query="Lợi nhuận của FPT năm 2025", owner_id=owner_id, symbol="FPT", top_k=5)

    # Run 1
    res1 = retrieve_ranked_chunks(req)
    # Run 2
    res2 = retrieve_ranked_chunks(req)

    assert len(res1) == len(res2)
    assert res1[0].chunk_id == res2[0].chunk_id
    assert res1[0].score == res2[0].score


def test_citation_verification_determinism():
    """
    U-6: Verifies that citation verification algorithm runs deterministically.
    """
    chunk1_id = uuid.uuid4()
    chunk2_id = uuid.uuid4()

    block_map = {1: chunk1_id, 2: chunk2_id}
    raw_answer = "FPT đạt tăng trưởng 20% lợi nhuận [Block 1]. Thị trường biến động nhẹ [Block 2]."
    raw_claims = [
        RawCitationClaim(claim_text="FPT đạt tăng trưởng 20% lợi nhuận", block_refs=[1]),
        RawCitationClaim(claim_text="Thị trường biến động nhẹ", block_refs=[2]),
    ]

    # Run 1
    v1 = verify_citation_claims(raw_answer, raw_claims, total_blocks_k=2, block_to_chunk_id_map=block_map)
    # Run 2
    v2 = verify_citation_claims(raw_answer, raw_claims, total_blocks_k=2, block_to_chunk_id_map=block_map)

    assert v1.refused == v2.refused
    assert len(v1.citations) == len(v2.citations)
    assert v1.citations[0].chunk_id == v2.citations[0].chunk_id
    assert v1.citations[1].chunk_id == v2.citations[1].chunk_id
