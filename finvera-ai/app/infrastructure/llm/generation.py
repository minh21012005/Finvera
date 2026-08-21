import asyncio
import logging
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

        if self.api_key and self.api_key != "mock" and self.api_key != "fixture":
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
        if self._client:
            try:
                async for chunk in self.generate_stream_raw(prompt, system_instruction):
                    yield chunk
                return
            except Exception as e:
                logger.warning(f"Online Gemini generation failed, falling back: {e}")

        # Deterministic offline streaming generator for test/offline environments
        for chunk in self._offline_stream(prompt):
            yield chunk

    async def generate_stream_raw(
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
