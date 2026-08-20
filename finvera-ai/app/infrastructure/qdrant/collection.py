import uuid
from typing import Any, Dict, List, Optional
from qdrant_client import QdrantClient
from qdrant_client.http import models as qmodels
from app.core.settings import settings


class QdrantService:
    def __init__(self, client: Optional[QdrantClient] = None):
        if client:
            self._client = client
        elif settings.qdrant_url == ":memory:":
            self._client = QdrantClient(":memory:")
        elif settings.qdrant_url:
            self._client = QdrantClient(url=settings.qdrant_url)
        else:
            try:
                self._client = QdrantClient(host=settings.qdrant_host, port=settings.qdrant_port, timeout=5)
                # Quick health check; if server unreachable, fall back to memory for testing
                self._client.get_collections()
            except Exception:
                self._client = QdrantClient(":memory:")

        self.collection_name = settings.qdrant_collection_name
        self.dimension = settings.embedding_dimension
        self.ensure_collection_exists()

    def ensure_collection_exists(self) -> None:
        """Initializes the research_chunks_v1 collection and payload indexes if not already present."""
        collections = self._client.get_collections().collections
        exists = any(c.name == self.collection_name for c in collections)
        if not exists:
            self._client.create_collection(
                collection_name=self.collection_name,
                vectors_config=qmodels.VectorParams(
                    size=self.dimension,
                    distance=qmodels.Distance.COSINE,
                ),
            )
            # Create payload indexes for filtering (research R-006)
            for field in ["owner_id", "symbol", "document_type", "news_category", "item_type", "research_item_id"]:
                try:
                    self._client.create_payload_index(
                        collection_name=self.collection_name,
                        field_name=field,
                        field_schema=qmodels.PayloadSchemaType.KEYWORD,
                    )
                except Exception:
                    pass

    def upsert_chunks(
        self,
        points: List[qmodels.PointStruct],
    ) -> None:
        if not points:
            return
        self._client.upsert(
            collection_name=self.collection_name,
            points=points,
        )

    def search_chunks(
        self,
        query_vector: List[float],
        owner_id: uuid.UUID,
        symbol: Optional[str] = None,
        document_type: Optional[str] = None,
        news_category: Optional[str] = None,
        date_from: Optional[str] = None,
        date_to: Optional[str] = None,
        limit: int = 30,
    ) -> List[qmodels.ScoredPoint]:
        """
        Performs vector search in Qdrant with owner-scoping and optional metadata filtering (FR-006, U-2).
        """
        must_filters: List[qmodels.FieldCondition] = [
            qmodels.FieldCondition(
                key="owner_id",
                match=qmodels.MatchValue(value=str(owner_id)),
            )
        ]

        if symbol:
            must_filters.append(
                qmodels.FieldCondition(
                    key="symbol",
                    match=qmodels.MatchValue(value=symbol.upper()),
                )
            )

        if document_type:
            must_filters.append(
                qmodels.FieldCondition(
                    key="document_type",
                    match=qmodels.MatchValue(value=document_type),
                )
            )

        if news_category:
            must_filters.append(
                qmodels.FieldCondition(
                    key="news_category",
                    match=qmodels.MatchValue(value=news_category),
                )
            )

        # Date range filtering if dates are present in payload
        if date_from or date_to:
            range_cond: Dict[str, Any] = {}
            if date_from:
                range_cond["gte"] = date_from
            if date_to:
                range_cond["lte"] = date_to
            must_filters.append(
                qmodels.FieldCondition(
                    key="publication_date",
                    range=qmodels.Range(**range_cond),
                )
            )

        query_filter = qmodels.Filter(must=must_filters)

        return self._client.query_points(
            collection_name=self.collection_name,
            query=query_vector,
            query_filter=query_filter,
            limit=limit,
        ).points

    def delete_points(self, point_ids: List[uuid.UUID]) -> None:
        """Deletes vector points by their UUIDs (DATA-004, FR-016)."""
        if not point_ids:
            return
        str_ids = [str(pid) for pid in point_ids]
        self._client.delete(
            collection_name=self.collection_name,
            points_selector=qmodels.PointIdsList(points=str_ids),
        )


qdrant_service = QdrantService()
