import asyncio
import logging
import os
import re
from typing import Any, AsyncIterator, Dict, List, Optional
from app.core.settings import settings

logger = logging.getLogger(__name__)


class GeminiGenerationAdapter:
    """
    LLM Generation Adapter for Gemini (ADR-0008).
    Supports streaming generation with offline deterministic fallback for test environments.
    """

    def __init__(self, api_key: Optional[str] = None, model: Optional[str] = None):
        self.api_key = api_key or settings.gemini_api_key
        self.model = model or settings.gemini_generation_model
        self._client = None

        if self.api_key and self.api_key not in ("mock", "fixture") and not os.environ.get("PYTEST_CURRENT_TEST"):
            try:
                from google import genai
                self._client = genai.Client(api_key=self.api_key)
            except Exception as e:
                logger.warning(f"Could not initialize Google GenAI client: {e}")
                self._client = None

    async def generate_stream(
        self,
        prompt: str,
        system_instruction: Optional[str] = None,
    ) -> AsyncIterator[str]:
        """
        Streams generated text deltas.
        """
        if self._client and not os.environ.get("PYTEST_CURRENT_TEST"):
            try:
                async for chunk in self.generate_stream_raw(prompt, system_instruction):
                    yield chunk
                return
            except Exception as e:
                logger.error(f"Online Gemini generation failed, falling back to offline: {type(e).__name__}: {e}", exc_info=True)

        # Deterministic offline streaming generator for test/offline environments
        for chunk in self._offline_stream(prompt):
            yield chunk

    async def generate_stream_raw(
        self,
        prompt: str,
        system_instruction: Optional[str] = None,
    ) -> AsyncIterator[str]:
        """Q-51: one quota-aware retry (provider's own retryDelay, bounded) before failing over."""
        try:
            async for chunk in self._generate_stream_raw_once(prompt, system_instruction):
                yield chunk
            return
        except Exception as e:  # noqa: BLE001 - decide on the provider's own retry hint
            delay = quota_retry_delay_seconds(e)
            if delay is None:
                raise
            logger.info("Gemini quota hit during synthesis; retrying once in %.0fs", delay)
            await asyncio.sleep(delay)
        async for chunk in self._generate_stream_raw_once(prompt, system_instruction):
            yield chunk

    async def _generate_stream_raw_once(
        self,
        prompt: str,
        system_instruction: Optional[str] = None,
    ) -> AsyncIterator[str]:
        """
        Streams generated text deltas from the real online provider only — raises on
        any failure rather than silently degrading to the offline shim. Callers whose
        offline fallback needs a different shape than generate_stream's generic
        rag-v1-oriented one (e.g. orchestration's tool-tagged synthesis) should call
        this directly and handle their own fallback.
        """
        if not self._client:
            raise RuntimeError("No online Gemini client configured")
        from google.genai import types

        config = types.GenerateContentConfig(
            system_instruction=system_instruction,
            temperature=0.2,
        )
        response_stream = self._client.models.generate_content_stream(
            model=self.model,
            contents=prompt,
            config=config,
        )
        for chunk in response_stream:
            if chunk.text:
                yield chunk.text

    @property
    def is_online(self) -> bool:
        """Whether a real Gemini client is configured (vs. the offline/test fallback)."""
        return self._client is not None

    async def propose_tool_calls(
        self,
        prompt: str,
        system_instruction: str,
        tool_declarations: List[Dict[str, Any]],
    ) -> Optional[List[Dict[str, Any]]]:
        """
        Uses Gemini's native function-calling to propose zero or more tool calls
        (orchestration-v1's "model's function-calling output"). Returns None — never
        raises — when no real provider is configured or the call fails, so the caller
        can fall back to its own deterministic tool-selection heuristic; this mirrors
        every other offline-fallback adapter in this module.
        """
        if not self._client:
            return None
        try:
            from google.genai import types

            tool = types.Tool(
                function_declarations=[
                    types.FunctionDeclaration(
                        name=d["name"],
                        description=d.get("description", ""),
                        parameters=d.get("parameters", {}),
                    )
                    for d in tool_declarations
                ]
            )
            config = types.GenerateContentConfig(
                system_instruction=system_instruction,
                temperature=0.1,
                tools=[tool],
            )
            try:
                response = self._client.models.generate_content(
                    model=self.model,
                    contents=prompt,
                    config=config,
                )
            except Exception as first:  # noqa: BLE001 - Q-51: honour the provider's retry hint once
                delay = quota_retry_delay_seconds(first)
                if delay is None:
                    raise
                logger.info("Gemini quota hit during tool proposal; retrying once in %.0fs", delay)
                await asyncio.sleep(delay)
                response = self._client.models.generate_content(
                    model=self.model,
                    contents=prompt,
                    config=config,
                )

            function_calls = getattr(response, "function_calls", None)
            if not function_calls:
                function_calls = []
                for cand in response.candidates or []:
                    parts = getattr(cand.content, "parts", None) or []
                    for part in parts:
                        fc = getattr(part, "function_call", None)
                        if fc is not None:
                            function_calls.append(fc)

            proposed: List[Dict[str, Any]] = []
            for fc in function_calls:
                args = dict(fc.args) if getattr(fc, "args", None) else {}
                proposed.append({"tool_name": fc.name, "arguments": args})
            return proposed
        except Exception as e:
            logger.warning(f"Online Gemini tool-call proposal failed, falling back: {e}")
            return None

    async def generate_text(
        self,
        prompt: str,
        system_instruction: Optional[str] = None,
    ) -> str:
        """
        Generates full text string.
        """
        parts = []
        async for chunk in self.generate_stream(prompt, system_instruction):
            parts.append(chunk)
        return "".join(parts)

    async def generate_text_strict(
        self,
        prompt: str,
        system_instruction: Optional[str] = None,
    ) -> str:
        """
        Generates full text string from the real online provider only, raising on any
        failure instead of silently degrading to the RAG-shaped offline shim. Callers
        outside the RAG/chat context (e.g. FR-006 explain) should use this so a real
        provider failure (invalid key, network, quota) surfaces as an actual error
        rather than being mistaken for a legitimate answer.
        """
        parts = []
        async for chunk in self.generate_stream_raw(prompt, system_instruction):
            parts.append(chunk)
        return "".join(parts)

    def _offline_stream(self, prompt: str) -> AsyncIterator[str]:
        """
        Produces realistic grounded streaming tokens for testing without external API calls.
        """
        # Look for [Block X] markers in the prompt context
        block_matches = re.findall(r"\[Block\s*(\d+)\]:\s*(.*?)(?=\[Block|\n\nQuery|\Z)", prompt, re.DOTALL)
        
        # Check if structured financial calculations are requested
        if any(term in prompt.lower() for term in ["rsi", "macd", "p/e", "định giá", "p/b", "tỷ suất sinh lời", "danh mục"]):
            text = (
                "Các chỉ số tài chính và tính toán định lượng chuyên sâu (như RSI, MACD, P/E, định giá) "
                "được tính toán bằng engine tài chính tất định trong các mô-đun chuyên biệt của hệ thống, "
                "không được ước tính sơ bộ bằng mô hình ngôn ngữ."
            )
        elif not block_matches:
            text = "Không tìm thấy thông tin hoặc đoạn trích phù hợp trong kho tài liệu của bạn để trả lời câu hỏi này."
        else:
            first_block_num = block_matches[0][0]
            first_block_content = block_matches[0][1].strip()
            # Take snippet
            snippet = first_block_content[:150]
            text = f"Dựa trên tài liệu đã tiếp nhận, {snippet} [Block {first_block_num}]."

        # Stream words in small chunks
        words = text.split(" ")
        for i, w in enumerate(words):
            yield w + (" " if i < len(words) - 1 else "")


generation_adapter = GeminiGenerationAdapter()


QUOTA_RETRY_MAX_SECONDS = 60.0
TRANSIENT_503_RETRY_SECONDS = 5.0
_RETRY_DELAY_PATTERNS = (
    re.compile(r"retryDelay['\"]?\s*[:=]\s*['\"]?(\d+(?:\.\d+)?)s"),
    re.compile(r"retry in (\d+(?:\.\d+)?)\s*s", re.IGNORECASE),
)


def quota_retry_delay_seconds(exc: BaseException) -> Optional[float]:
    """Q-51: seconds to wait before ONE retry when the provider answered 429 RESOURCE_EXHAUSTED
    with a retry hint that fits inside QUOTA_RETRY_MAX_SECONDS; None for every other failure
    (auth, 5xx, a daily quota with a long hint) so the caller fails over immediately."""
    text = str(exc)
    if "503" in text and ("UNAVAILABLE" in text or "high demand" in text):
        return TRANSIENT_503_RETRY_SECONDS  # provider-side spike, usually gone within seconds
    if "RESOURCE_EXHAUSTED" not in text and "429" not in text:
        return None
    if re.search(r"PerDay|per[\s_-]?day|daily", text, re.IGNORECASE):
        return None  # a daily quota does not recover inside the hint; fail over immediately
    for pattern in _RETRY_DELAY_PATTERNS:
        m = pattern.search(text)
        if m:
            delay = float(m.group(1))
            return delay + 1.0 if 0 <= delay <= QUOTA_RETRY_MAX_SECONDS else None
    return None
