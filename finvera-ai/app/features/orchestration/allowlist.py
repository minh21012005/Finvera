import re
from enum import Enum
from typing import Any, Dict, List, Optional, Tuple, Union
import uuid
from pydantic import BaseModel, Field, ValidationError, field_validator


class ToolName(str, Enum):
    MARKET = "MARKET"
    STOCK = "STOCK"
    TECHNICAL = "TECHNICAL"
    FUNDAMENTAL = "FUNDAMENTAL"
    VALUATION = "VALUATION"
    PORTFOLIO = "PORTFOLIO"
    NEWS = "NEWS"
    RESEARCH_RAG = "RESEARCH_RAG"
    SCREENING = "SCREENING"


_SYMBOL_PATTERN = re.compile(r"^[A-Z0-9]{1,20}$")


def _normalize_symbol(value: str) -> str:
    """Upper-case, then require the venue ticker charset before the value reaches a URL path."""
    s = value.strip().upper()
    if not s:
        raise ValueError("symbol must not be blank")
    if not _SYMBOL_PATTERN.match(s):
        raise ValueError("symbol must match [A-Z0-9]{1,20}")
    return s


class MarketToolArgs(BaseModel):
    owner_id: uuid.UUID


class SymbolToolArgs(BaseModel):
    symbol: str = Field(..., min_length=1, max_length=20)
    owner_id: uuid.UUID

    @field_validator("symbol")
    @classmethod
    def normalize_symbol(cls, v: str) -> str:
        return _normalize_symbol(v)


class TechnicalToolArgs(SymbolToolArgs):
    pass


class FundamentalToolArgs(SymbolToolArgs):
    pass


class ValuationToolArgs(SymbolToolArgs):
    pass


class PortfolioToolArgs(BaseModel):
    owner_id: uuid.UUID
    sub_type: Optional[str] = Field("POSITIONS", pattern="^(POSITIONS|ANALYTICS)$")


class NewsToolArgs(BaseModel):
    owner_id: uuid.UUID
    symbol: Optional[str] = None
    limit: int = Field(5, ge=1, le=20)

    @field_validator("symbol")
    @classmethod
    def normalize_symbol(cls, v: Optional[str]) -> Optional[str]:
        if v is None or not v.strip():
            return None
        return _normalize_symbol(v)


class ResearchRagToolArgs(BaseModel):
    query: str = Field(..., min_length=1, max_length=2000)
    owner_id: uuid.UUID
    symbol: Optional[str] = None
    document_type: Optional[str] = None
    news_category: Optional[str] = None
    date_from: Optional[str] = None
    date_to: Optional[str] = None
    top_k: int = Field(8, ge=1, le=20)

    @field_validator("symbol")
    @classmethod
    def normalize_symbol(cls, v: Optional[str]) -> Optional[str]:
        if v is None or not v.strip():
            return None
        return _normalize_symbol(v)


class ScreeningToolArgs(BaseModel):
    owner_id: uuid.UUID
    filters: Dict[str, Any] = Field(default_factory=dict)
    ambiguityNote: Optional[str] = None


TOOL_ARG_SCHEMAS: Dict[ToolName, type[BaseModel]] = {
    ToolName.MARKET: MarketToolArgs,
    ToolName.STOCK: SymbolToolArgs,
    ToolName.TECHNICAL: TechnicalToolArgs,
    ToolName.FUNDAMENTAL: FundamentalToolArgs,
    ToolName.VALUATION: ValuationToolArgs,
    ToolName.PORTFOLIO: PortfolioToolArgs,
    ToolName.NEWS: NewsToolArgs,
    ToolName.RESEARCH_RAG: ResearchRagToolArgs,
    ToolName.SCREENING: ScreeningToolArgs,
}


def validate_tool_call(
    tool_name_raw: str,
    arguments_raw: Dict[str, Any],
    session_owner_id: uuid.UUID,
) -> Tuple[bool, Optional[ToolName], Optional[BaseModel], Optional[str]]:
    """
    U-1 & U-2: Validates proposed tool call against fixed ToolName allowlist,
    Pydantic typed argument schema, and checks session ownerId match.
    """
    try:
        tool_name = ToolName(tool_name_raw.strip().upper())
    except (ValueError, AttributeError):
        return False, None, None, f"UNLISTED_TOOL: '{tool_name_raw}' is not in the allowlist"

    schema_cls = TOOL_ARG_SCHEMAS.get(tool_name)
    if schema_cls is None:
        return False, None, None, f"UNLISTED_TOOL: No schema found for '{tool_name}'"

    # Inject or verify owner_id
    args_dict = dict(arguments_raw) if arguments_raw else {}
    if "owner_id" not in args_dict or not args_dict["owner_id"]:
        args_dict["owner_id"] = str(session_owner_id)

    try:
        parsed_args = schema_cls.model_validate(args_dict)
    except ValidationError as e:
        return False, tool_name, None, f"INVALID_ARGUMENTS: {e}"

    # U-2 Ownership check
    arg_owner_id = getattr(parsed_args, "owner_id", None)
    if arg_owner_id != session_owner_id:
        return False, tool_name, None, f"OWNER_MISMATCH: Provided {arg_owner_id} != session {session_owner_id}"

    return True, tool_name, parsed_args, None
