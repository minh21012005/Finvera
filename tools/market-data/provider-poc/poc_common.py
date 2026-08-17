from __future__ import annotations

import hashlib
import json
from datetime import UTC, datetime
from pathlib import Path
from typing import Any


def utc_now() -> str:
    return datetime.now(UTC).isoformat().replace("+00:00", "Z")


def value_type(value: Any) -> str:
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "boolean"
    if isinstance(value, int):
        return "integer"
    if isinstance(value, float):
        return "number"
    if isinstance(value, str):
        return "string"
    if isinstance(value, list):
        return "array"
    if isinstance(value, dict):
        return "object"
    return type(value).__name__


def schema_of_mapping(value: dict[str, Any]) -> dict[str, str]:
    return {key: value_type(item) for key, item in sorted(value.items())}


def write_summary(output_dir: Path, filename: str, summary: dict[str, Any]) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    encoded = json.dumps(summary, ensure_ascii=False, indent=2, sort_keys=True)
    path = output_dir / filename
    file_content = encoded + "\n"
    file_bytes = file_content.encode("utf-8")
    path.write_bytes(file_bytes)
    digest = hashlib.sha256(file_bytes).hexdigest()
    print(f"Wrote sanitized summary: {path}")
    print(f"Summary SHA-256: {digest}")
    return path
