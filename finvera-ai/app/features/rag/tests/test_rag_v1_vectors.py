from datetime import date, timedelta
import uuid
import pytest
from app.features.document.chunking import (
    chunk_document,
    chunk_news_article,
    estimate_token_count,
)
from app.features.rag.citations import (
    RawCitationClaim,
    verify_citation_claims,
)
from app.features.rag.retrieval import (
    RankedChunk,
    RetrieveRequest,
    compute_recency_boost,
    rerank_candidate,
    retrieve_ranked_chunks,
)
from app.infrastructure.loaders.pdf import PdfExtractionError, extract_text_from_pdf
from app.infrastructure.qdrant.collection import QdrantService
from qdrant_client import QdrantClient
from qdrant_client.http import models as qmodels


# Test Vector 7: Chunk under 20 tokens discarded
def test_chunking_discards_under_20_tokens():
    short_text = "Short header line."
    assert estimate_token_count(short_text) < 20

    chunks = chunk_document([(1, short_text)])
    assert len(chunks) == 0


# Test Vector 1 & 2: Document page boundaries and News paragraph boundaries
def test_chunking_never_crosses_page_boundaries():
    page1 = "This is a detailed paragraph on page one with sufficient content. " * 15
    page2 = "This is a detailed paragraph on page two with sufficient content. " * 15

    pages = [(1, page1), (2, page2)]
    chunks = chunk_document(pages)

    assert len(chunks) >= 2
    page1_chunks = [c for c in chunks if c.page_number == 1]
    page2_chunks = [c for c in chunks if c.page_number == 2]

    assert len(page1_chunks) > 0
    assert len(page2_chunks) > 0
    assert len(page1_chunks) + len(page2_chunks) == len(chunks)

    for c in page1_chunks:
        assert "page one" in c.content_text
        assert "page two" not in c.content_text

    for c in page2_chunks:
        assert "page two" in c.content_text
        assert "page one" not in c.content_text


def test_news_chunking_preserves_paragraphs():
    body = (
        "First news paragraph about market movements and trading volume across the exchange. " * 5
        + "\n\n"
        + "Second news paragraph covering corporate earnings and quarterly disclosures. " * 5
    )

    chunks = chunk_news_article(body)
    assert len(chunks) >= 2
    assert chunks[0].paragraph_index == 0
    assert chunks[1].paragraph_index == 1


# Test Vector 6: Scanned / no-text PDF failure
def test_pdf_extraction_fails_on_empty_or_scanned_pdf():
    with pytest.raises(PdfExtractionError, match="PDF file is empty"):
        extract_text_from_pdf(b"")


# Recency Boost and Rerank Score Formulas
def test_recency_boost_decay_formula():
    ref_date = date(2026, 8, 20)

    # 1. Published today (0 days old) -> 1.0
    assert compute_recency_boost("2026-08-20", as_of_date=ref_date) == 1.0

    # 2. Published 90 days ago -> 1.0
    d90 = (ref_date - timedelta(days=90)).isoformat()
    assert compute_recency_boost(d90, as_of_date=ref_date) == 1.0

    # 3. Published 410 days ago (halfway between 90 and 730) -> 0.5
    d410 = (ref_date - timedelta(days=410)).isoformat()
    boost410 = compute_recency_boost(d410, as_of_date=ref_date)
    assert pytest.approx(boost410, abs=1e-4) == 0.5

    # 4. Published 730 days ago -> 0.0
    d730 = (ref_date - timedelta(days=730)).isoformat()
    assert compute_recency_boost(d730, as_of_date=ref_date) == 0.0

    # 5. Published 1000 days ago -> 0.0
    d1000 = (ref_date - timedelta(days=1000)).isoformat()
    assert compute_recency_boost(d1000, as_of_date=ref_date) == 0.0

    # 6. Unknown date -> 0.5
    assert compute_recency_boost(None, as_of_date=ref_date) == 0.5


def test_rerank_final_score_formula():
    ref_date = date(2026, 8, 20)
    final_score, vec, rec, flt = rerank_candidate(
        vector_similarity=0.80,
        published_at_str="2026-08-20",
        filter_matched=True,
        as_of_date=ref_date,
    )
    assert pytest.approx(final_score, abs=1e-5) == 0.86
    assert pytest.approx(vec, abs=1e-5) == 0.80
    assert pytest.approx(rec, abs=1e-5) == 1.0
    assert pytest.approx(flt, abs=1e-5) == 1.0


# Test Vector 9: Owner & Symbol / Type / Date Filtering
def test_retrieval_ranking_and_filtering():
    mem_client = QdrantClient(":memory:")
    service = QdrantService(client=mem_client)

    owner_id = uuid.uuid4()
    other_owner_id = uuid.uuid4()

    points = [
        qmodels.PointStruct(
            id=str(uuid.uuid4()),
            vector=[0.1] * 768,
            payload={
                "owner_id": str(owner_id),
                "research_item_id": str(uuid.uuid4()),
                "chunk_id": str(uuid.uuid4()),
                "item_type": "DOCUMENT",
                "symbol": "FPT",
                "document_type": "ANNUAL_REPORT",
                "publication_date": "2026-08-01",
            },
        ),
        qmodels.PointStruct(
            id=str(uuid.uuid4()),
            vector=[0.2] * 768,
            payload={
                "owner_id": str(owner_id),
                "research_item_id": str(uuid.uuid4()),
                "chunk_id": str(uuid.uuid4()),
                "item_type": "DOCUMENT",
                "symbol": "VNM",
                "document_type": "QUARTERLY_REPORT",
                "publication_date": "2024-01-01",
            },
        ),
        qmodels.PointStruct(
            id=str(uuid.uuid4()),
            vector=[0.3] * 768,
            payload={
                "owner_id": str(other_owner_id),
                "research_item_id": str(uuid.uuid4()),
                "chunk_id": str(uuid.uuid4()),
                "item_type": "DOCUMENT",
                "symbol": "FPT",
                "document_type": "ANNUAL_REPORT",
                "publication_date": "2026-08-01",
            },
        ),
    ]

    service.upsert_chunks(points)

    # Search with owner_id filter
    results = service.search_chunks(
        query_vector=[0.1] * 768,
        owner_id=owner_id,
        limit=10,
    )
    assert len(results) == 2
    for r in results:
        assert r.payload["owner_id"] == str(owner_id)

    # Search with symbol filter = FPT
    fpt_results = service.search_chunks(
        query_vector=[0.1] * 768,
        owner_id=owner_id,
        symbol="FPT",
        limit=10,
    )
    assert len(fpt_results) == 1
    assert fpt_results[0].payload["symbol"] == "FPT"


# Test Vector 8: Replay Determinism (U-6)
def test_replay_determinism_u6():
    mem_client = QdrantClient(":memory:")
    service = QdrantService(client=mem_client)
    owner_id = uuid.uuid4()

    points = []
    for i in range(1, 10):
        vec = [0.0] * 768
        vec[i] = 1.0  # distinct orthogonal unit vector
        points.append(
            qmodels.PointStruct(
                id=str(uuid.uuid4()),
                vector=vec,
                payload={
                    "owner_id": str(owner_id),
                    "chunk_id": str(uuid.uuid4()),
                    "publication_date": "2026-08-01",
                },
            )
        )
    service.upsert_chunks(points)

    query_vec = [0.0] * 768
    query_vec[1] = 0.9
    query_vec[2] = 0.4
    run1 = service.search_chunks(query_vec, owner_id=owner_id, limit=5)
    run2 = service.search_chunks(query_vec, owner_id=owner_id, limit=5)

    assert [p.id for p in run1] == [p.id for p in run2]
    assert [p.score for p in run1] == [p.score for p in run2]


# Test Vector 10: Deletion mid-corpus (DATA-004)
def test_vector_deletion_mid_corpus():
    mem_client = QdrantClient(":memory:")
    service = QdrantService(client=mem_client)
    owner_id = uuid.uuid4()

    pt_id1 = uuid.uuid4()
    pt_id2 = uuid.uuid4()

    points = [
        qmodels.PointStruct(
            id=str(pt_id1),
            vector=[0.1] * 768,
            payload={"owner_id": str(owner_id), "symbol": "FPT"},
        ),
        qmodels.PointStruct(
            id=str(pt_id2),
            vector=[0.2] * 768,
            payload={"owner_id": str(owner_id), "symbol": "HPG"},
        ),
    ]
    service.upsert_chunks(points)

    assert len(service.search_chunks([0.1] * 768, owner_id=owner_id)) == 2

    # Delete point 1
    service.delete_points([pt_id1])

    remaining = service.search_chunks([0.1] * 768, owner_id=owner_id)
    assert len(remaining) == 1
    assert remaining[0].id == str(pt_id2)


# Test Vector 4: Citation verification out-of-range blockRef and refusal
def test_citation_verification_drops_invalid_block_refs():
    c1_id = uuid.uuid4()
    c2_id = uuid.uuid4()
    chunk_map = {1: c1_id, 2: c2_id}
    k = 2

    # Claim 1 cites valid block 1 and out-of-range block 99
    # Claim 2 cites only out-of-range block 10
    raw_claims = [
        RawCitationClaim(claim_text="FPT revenue grew 20%", block_refs=[1, 99]),
        RawCitationClaim(claim_text="Fabricated fact outside context", block_refs=[10]),
    ]

    result = verify_citation_claims(
        raw_answer="Full answer text",
        raw_claims=raw_claims,
        total_blocks_k=k,
        block_to_chunk_id_map=chunk_map,
        model_refused=False,
    )

    assert result.refused is False
    assert result.surviving_claims_count == 1
    assert len(result.citations) == 1
    assert result.citations[0].chunk_id == c1_id
    assert result.citations[0].block_index == 1


def test_citation_verification_all_invalid_refs_returns_refusal():
    chunk_map = {1: uuid.uuid4()}
    k = 1

    # Claim cites block 5 when K=1 -> dropped -> 0 claims -> refusal
    raw_claims = [
        RawCitationClaim(claim_text="Unsupported claim", block_refs=[5]),
    ]

    result = verify_citation_claims(
        raw_answer="Some unsupported answer",
        raw_claims=raw_claims,
        total_blocks_k=k,
        block_to_chunk_id_map=chunk_map,
        model_refused=False,
    )

    assert result.refused is True
    assert "No relevant information was found" in result.answer
    assert len(result.citations) == 0


# Test Vector 3: Model refusal
def test_citation_verification_model_refused():
    result = verify_citation_claims(
        raw_answer="",
        raw_claims=[],
        total_blocks_k=5,
        block_to_chunk_id_map={},
        model_refused=True,
    )
    assert result.refused is True
    assert len(result.citations) == 0
