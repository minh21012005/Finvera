import time
import uuid
import pytest
from app.infrastructure.llm.embedding import embedding_adapter
from app.infrastructure.qdrant.collection import qdrant_service
from app.features.rag.retrieval import retrieve_ranked_chunks, RetrieveRequest
from app.features.rag.citations import verify_citation_claims, RawCitationClaim
from qdrant_client import models as qmodels


def test_retrieval_latency_baseline():
    """
    NFR-001 / NFR-002: Verifies that vector retrieval & reranking for top-30 candidates
    completes in under 500ms in offline/local fixture mode.
    """
    owner_id = uuid.uuid4()
    points = []
    for i in range(30):
        chunk_id = uuid.uuid4()
        emb = embedding_adapter.embed_query(f"Passage {i} about Vietnamese equities and company performance.")
        points.append(
            qmodels.PointStruct(
                id=str(uuid.uuid4()),
                vector=emb,
                payload={
                    "owner_id": str(owner_id),
                    "chunk_id": str(chunk_id),
                    "research_item_id": str(uuid.uuid4()),
                    "item_type": "DOCUMENT",
                    "symbol": "FPT" if i % 2 == 0 else "VNM",
                    "document_type": "ANNUAL_REPORT",
                    "publication_date": "2025-01-01",
                    "content_hash": f"hash_{i}",
                    "embedding_version": "gemini-embedding-v1",
                },
            )
        )

    qdrant_service.upsert_chunks(points)

    req = RetrieveRequest(query="Doanh thu tăng trưởng FPT", owner_id=owner_id, top_k=8)

    start = time.perf_counter()
    ranked = retrieve_ranked_chunks(req)
    elapsed_ms = (time.perf_counter() - start) * 1000

    assert len(ranked) <= 8
    # Local processing must complete well within 500ms
    assert elapsed_ms < 500.0, f"Retrieval took too long: {elapsed_ms:.2f}ms"


def test_citation_verification_latency():
    """
    NFR-002: Verifies citation verification algorithm runs in < 50ms.
    """
    chunk_ids = [uuid.uuid4() for _ in range(8)]
    block_map = {i + 1: cid for i, cid in enumerate(chunk_ids)}

    raw_answer = " ".join([f"Claim {i} from source [Block {i+1}]." for i in range(8)])
    raw_claims = [
        RawCitationClaim(claim_text=f"Claim {i} from source", block_refs=[i + 1])
        for i in range(8)
    ]

    start = time.perf_counter()
    result = verify_citation_claims(raw_answer, raw_claims, total_blocks_k=8, block_to_chunk_id_map=block_map)
    elapsed_ms = (time.perf_counter() - start) * 1000

    assert not result.refused
    assert len(result.citations) == 8
    assert elapsed_ms < 50.0, f"Citation verification took too long: {elapsed_ms:.2f}ms"
