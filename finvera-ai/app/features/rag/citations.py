from typing import Dict, List, Optional, Tuple
import uuid
from pydantic import BaseModel, Field


class RawCitationClaim(BaseModel):
    claim_text: str
    block_refs: List[int]


class VerifiedCitation(BaseModel):
    chunk_id: uuid.UUID
    claim_text: str
    block_index: int


class CitationVerificationResult(BaseModel):
    answer: str
    citations: List[VerifiedCitation]
    refused: bool
    refusal_reason: Optional[str] = None
    surviving_claims_count: int


def verify_citation_claims(
    raw_answer: str,
    raw_claims: List[RawCitationClaim],
    total_blocks_k: int,
    block_to_chunk_id_map: Dict[int, uuid.UUID],
    model_refused: bool = False,
) -> CitationVerificationResult:
    """
    Executes normative citation verification steps 1-5 from contracts/rag-v1.md:
    1. Verify 1 <= blockRef <= K.
    2. Drop invalid blockRefs outside [1, K].
    3. If a claim has 0 surviving valid blockRefs, drop the claim.
    4. If 0 surviving claims remain or model_refused is True, return refusal state.
    5. Resolve surviving blockRefs to chunk IDs.
    """
    if model_refused:
        return CitationVerificationResult(
            answer="No relevant information was found in the provided research documents or news articles.",
            citations=[],
            refused=True,
            refusal_reason="Model refused / insufficient grounded evidence",
            surviving_claims_count=0,
        )

    verified_citations: List[VerifiedCitation] = []
    surviving_claims: List[str] = []

    for claim in raw_claims:
        valid_refs = [r for r in claim.block_refs if 1 <= r <= total_blocks_k]
        if not valid_refs:
            # Claim has 0 valid blockRefs -> drop claim (rag-v1 step 3)
            continue

        surviving_claims.append(claim.claim_text)
        for ref in valid_refs:
            chunk_id = block_to_chunk_id_map.get(ref)
            if chunk_id:
                verified_citations.append(
                    VerifiedCitation(
                        chunk_id=chunk_id,
                        claim_text=claim.claim_text,
                        block_index=ref,
                    )
                )

    if not surviving_claims:
        # Zero surviving claims -> return refusal state (rag-v1 step 4)
        return CitationVerificationResult(
            answer="No relevant information was found in the provided research documents or news articles.",
            citations=[],
            refused=True,
            refusal_reason="No verifiable claims survived citation filtering",
            surviving_claims_count=0,
        )

    return CitationVerificationResult(
        answer=raw_answer,
        citations=verified_citations,
        refused=False,
        refusal_reason=None,
        surviving_claims_count=len(surviving_claims),
    )
