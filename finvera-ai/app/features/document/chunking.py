import hashlib
import re
import uuid
from typing import List, Optional, Tuple
from pydantic import BaseModel, Field
from app.core.settings import settings


class ChunkItem(BaseModel):
    id: uuid.UUID = Field(default_factory=uuid.uuid4)
    chunk_index: int
    page_number: Optional[int] = None
    paragraph_index: Optional[int] = None
    content_text: str
    content_hash: str
    vector_point_id: uuid.UUID = Field(default_factory=uuid.uuid4)
    embedding_version: str = Field(default_factory=lambda: settings.embedding_version)


def estimate_token_count(text: str) -> int:
    """
    Estimates token count using a combination of whitespace word count and char count heuristics.
    Roughly 1 token ~= 0.75 words ~= 4 characters for English / Vietnamese syllables.
    """
    cleaned = text.strip()
    if not cleaned:
        return 0
    words = re.findall(r"\S+", cleaned)
    # Average word in Vietnamese/English is ~1.2 tokens
    return max(1, int(len(words) * 1.2))


def compute_sha256(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def chunk_text_by_tokens(
    text: str,
    target_min_tokens: int = 500,
    target_max_tokens: int = 800,
    overlap_ratio: float = 0.15,
) -> List[str]:
    """
    Splits text into chunks of target_min_tokens to target_max_tokens with ~15% overlap.
    Preserves paragraph or sentence boundaries where possible.
    """
    text = text.strip()
    if not text:
        return []

    # If already under max tokens, return single chunk
    total_tokens = estimate_token_count(text)
    if total_tokens <= target_max_tokens:
        return [text] if total_tokens >= 20 else []

    # Split into paragraphs or sentences
    paragraphs = [p.strip() for p in text.split("\n") if p.strip()]
    if not paragraphs:
        paragraphs = [text]

    chunks: List[str] = []
    current_sentences: List[str] = []
    current_tokens = 0

    # Break into sentences/units
    units: List[str] = []
    for p in paragraphs:
        # Split sentences by . ! ?
        sentences = re.split(r"(?<=[.!?])\s+", p)
        for s in sentences:
            if s.strip():
                units.append(s.strip())

    i = 0
    while i < len(units):
        chunk_units: List[str] = []
        chunk_token_count = 0

        # Collect units up to target_max_tokens
        j = i
        while j < len(units):
            u_tokens = estimate_token_count(units[j])
            if chunk_token_count + u_tokens > target_max_tokens and chunk_token_count >= target_min_tokens:
                break
            chunk_units.append(units[j])
            chunk_token_count += u_tokens
            j += 1
            if chunk_token_count >= target_max_tokens:
                break

        chunk_str = " ".join(chunk_units).strip()
        if estimate_token_count(chunk_str) >= 20:
            chunks.append(chunk_str)

        # Calculate overlap step
        overlap_tokens = int(chunk_token_count * overlap_ratio)
        step_tokens = 0
        next_i = j
        for k in range(j - 1, i, -1):
            step_tokens += estimate_token_count(units[k])
            if step_tokens >= overlap_tokens:
                next_i = k
                break

        if next_i <= i:
            next_i = i + 1

        i = next_i

    return chunks


def chunk_document(pages: List[Tuple[int, str]]) -> List[ChunkItem]:
    """
    Chunks a research document page-by-page.
    Chunk boundaries NEVER cross a page boundary (rag-v1).
    Discards chunks under 20 tokens.
    """
    items: List[ChunkItem] = []
    chunk_idx = 0

    for page_num, page_text in pages:
        page_chunks = chunk_text_by_tokens(page_text)
        for chunk_text in page_chunks:
            if estimate_token_count(chunk_text) >= 20:
                items.append(
                    ChunkItem(
                        chunk_index=chunk_idx,
                        page_number=page_num,
                        paragraph_index=None,
                        content_text=chunk_text,
                        content_hash=compute_sha256(chunk_text),
                    )
                )
                chunk_idx += 1

    return items


def chunk_news_article(body: str) -> List[ChunkItem]:
    """
    Chunks a news article, preserving paragraph boundaries where possible.
    paragraph_index is 0-based within the article body.
    Discards chunks under 20 tokens.
    """
    paragraphs = [p.strip() for p in body.split("\n\n") if p.strip()]
    if not paragraphs:
        paragraphs = [p.strip() for p in body.split("\n") if p.strip()]
    if not paragraphs:
        paragraphs = [body.strip()]

    items: List[ChunkItem] = []
    chunk_idx = 0

    for p_idx, p_text in enumerate(paragraphs):
        p_chunks = chunk_text_by_tokens(p_text)
        for chunk_text in p_chunks:
            if estimate_token_count(chunk_text) >= 20:
                items.append(
                    ChunkItem(
                        chunk_index=chunk_idx,
                        page_number=None,
                        paragraph_index=p_idx,
                        content_text=chunk_text,
                        content_hash=compute_sha256(chunk_text),
                    )
                )
                chunk_idx += 1

    return items
