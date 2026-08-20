import hashlib
import math
from typing import List, Optional
from app.core.settings import settings


class EmbeddingAdapter:
    """
    Embedding adapter interface and Gemini implementation (ADR-0008).
    Supports asymmetric task types: RETRIEVAL_DOCUMENT vs RETRIEVAL_QUERY.
    Provides offline deterministic fallback when GEMINI_API_KEY is not configured (for unit/integration testing).
    """

    def __init__(self, api_key: Optional[str] = None, model: Optional[str] = None):
        self.api_key = api_key or settings.gemini_api_key
        self.model = model or settings.gemini_embedding_model
        self.dimension = settings.embedding_dimension
        self._client = None

        if self.api_key and self.api_key != "mock" and self.api_key != "fixture":
            try:
                from google import genai
                self._client = genai.Client(api_key=self.api_key)
            except Exception:
                self._client = None

    def embed_documents(self, texts: List[str]) -> List[List[float]]:
        """Embeds document chunks with RETRIEVAL_DOCUMENT task type."""
        if not texts:
            return []

        if self._client:
            try:
                # Use Google GenAI SDK
                from google.genai import types
                results: List[List[float]] = []
                for text in texts:
                    response = self._client.models.embed_content(
                        model=self.model,
                        contents=text,
                        config=types.EmbedContentConfig(
                            task_type="RETRIEVAL_DOCUMENT",
                            output_dimensionality=self.dimension,
                        ),
                    )
                    results.append(response.embedding.values)
                return results
            except Exception as e:
                # Fall back to offline deterministic embedding on API failure in tests
                pass

        # Offline deterministic embedding fallback
        return [self._deterministic_pseudo_embedding(t) for t in texts]

    def embed_query(self, query: str) -> List[float]:
        """Embeds a search query with RETRIEVAL_QUERY task type."""
        if self._client:
            try:
                from google.genai import types
                response = self._client.models.embed_content(
                    model=self.model,
                    contents=query,
                    config=types.EmbedContentConfig(
                        task_type="RETRIEVAL_QUERY",
                        output_dimensionality=self.dimension,
                    ),
                )
                return response.embedding.values
            except Exception:
                pass

        return self._deterministic_pseudo_embedding(query)

    def _deterministic_pseudo_embedding(self, text: str) -> List[float]:
        """
        Produces a normalized 768-dimensional pseudo-embedding derived deterministically
        from text hash and word content, ensuring cosine similarity behaves predictably in tests.
        """
        words = set(text.lower().split())
        vector = [0.0] * self.dimension

        # Distribute hash seeds
        h = hashlib.sha256(text.encode("utf-8")).digest()
        for i in range(self.dimension):
            byte_val = h[i % len(h)]
            vector[i] = (byte_val / 128.0) - 1.0

        # Word-based semantic signal: words map to specific dimensions
        for word in words:
            word_hash = int(hashlib.md5(word.encode("utf-8")).hexdigest(), 16)
            dim_idx = word_hash % self.dimension
            vector[dim_idx] += 2.0

        # L2 normalize
        norm = math.sqrt(sum(v * v for v in vector))
        if norm > 0:
            vector = [v / norm for v in vector]

        return vector


embedding_adapter = EmbeddingAdapter()
