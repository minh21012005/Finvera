import logging
from typing import Any, Dict, List, Optional
import uuid
import httpx
from qdrant_client.http import models as qmodels
from app.core.settings import settings
from app.features.document.chunking import ChunkItem, chunk_document, chunk_news_article
from app.infrastructure.llm.embedding import embedding_adapter
from app.infrastructure.loaders.pdf import PdfExtractionError, extract_text_from_pdf
from app.infrastructure.qdrant.collection import qdrant_service

from app.features.document.classification import classify_news_article

logger = logging.getLogger(__name__)


async def send_callback(
    research_item_id: uuid.UUID,
    status: str,
    failure_reason: Optional[str] = None,
    extracted_text: Optional[str] = None,
    chunks: Optional[List[ChunkItem]] = None,
    classification: Optional[Dict[str, Any]] = None,
) -> None:
    """
    Sends terminal status callback to finvera-be (research R-003, R-004).
    Carries the shared X-Internal-Api-Key header.
    """
    url = f"{settings.backend_internal_api_url}/ingestions/{research_item_id}/callback"
    payload: Dict[str, Any] = {
        "status": status,
        "failureReason": failure_reason,
        "extractedText": extracted_text,
        "chunks": [
            {
                "id": str(c.id),
                "chunkIndex": c.chunk_index,
                "pageNumber": c.page_number,
                "paragraphIndex": c.paragraph_index,
                "contentText": c.content_text,
                "contentHash": c.content_hash,
                "vectorPointId": str(c.vector_point_id),
                "embeddingVersion": c.embedding_version,
            }
            for c in (chunks or [])
        ],
        "classification": classification,
    }

    headers = {
        "X-Internal-Api-Key": settings.internal_api_key,
        "Content-Type": "application/json",
    }

    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            resp = await client.patch(url, json=payload, headers=headers)
            if resp.status_code >= 400:
                logger.error(f"Callback to {url} returned status {resp.status_code}: {resp.text}")
    except Exception as e:
        logger.error(f"Failed to deliver callback to {url}: {str(e)}")


async def process_ingestion_job(
    research_item_id: uuid.UUID,
    item_type: str,
    owner_id: uuid.UUID,
    content_bytes: Optional[bytes] = None,
    text_content: Optional[str] = None,
    mime_type: Optional[str] = None,
    symbol: Optional[str] = None,
    document_type: Optional[str] = None,
    publication_date: Optional[str] = None,
) -> None:
    """
    Asynchronous background job for parsing, chunking, embedding, indexing, and reporting back to finvera-be.
    """
    try:
        chunks: List[ChunkItem] = []
        full_text: str = ""

        if item_type == "DOCUMENT":
            if content_bytes and len(content_bytes) > 0:
                # PDF Extraction
                full_text, pages = extract_text_from_pdf(content_bytes)
                chunks = chunk_document(pages)
            elif text_content and text_content.strip():
                full_text = text_content.strip()
                chunks = chunk_document([(1, full_text)])
            else:
                raise ValueError("Document submission has neither file content nor text")

        elif item_type == "NEWS_ARTICLE":
            if not text_content or not text_content.strip():
                raise ValueError("News article submission must have body text")
            full_text = text_content.strip()
            chunks = chunk_news_article(full_text)

        else:
            raise ValueError(f"Unknown item type: {item_type}")

        if not chunks:
            raise ValueError("No usable chunks could be extracted from input")

        # Generate embeddings
        chunk_texts = [c.content_text for c in chunks]
        embeddings = embedding_adapter.embed_documents(chunk_texts)

        # Build Qdrant points
        points: List[qmodels.PointStruct] = []
        for c, emb in zip(chunks, embeddings):
            payload: Dict[str, Any] = {
                "owner_id": str(owner_id),
                "research_item_id": str(research_item_id),
                "chunk_id": str(c.id),
                "item_type": item_type,
                "symbol": symbol.upper() if symbol else None,
                "document_type": document_type,
                "publication_date": publication_date,
                "content_hash": c.content_hash,
                "embedding_version": c.embedding_version,
            }
            points.append(
                qmodels.PointStruct(
                    id=str(c.vector_point_id),
                    vector=emb,
                    payload=payload,
                )
            )

        # Upsert into Qdrant
        qdrant_service.upsert_chunks(points)

        classification_data: Optional[Dict[str, Any]] = None
        if item_type == "NEWS_ARTICLE":
            classification_res = await classify_news_article(
                headline=full_text[:200],
                body_text=full_text,
                symbol=symbol,
            )
            classification_data = classification_res.to_dict()

        # Notify finvera-be with READY status
        await send_callback(
            research_item_id=research_item_id,
            status="READY",
            failure_reason=None,
            extracted_text=full_text,
            chunks=chunks,
            classification=classification_data,
        )

    except PdfExtractionError as e:
        logger.warning(f"PDF extraction failed for {research_item_id}: {str(e)}")
        await send_callback(
            research_item_id=research_item_id,
            status="FAILED",
            failure_reason=str(e),
        )
    except Exception as e:
        logger.error(f"Ingestion processing failed for {research_item_id}: {str(e)}", exc_info=True)
        await send_callback(
            research_item_id=research_item_id,
            status="FAILED",
            failure_reason=str(e),
        )
