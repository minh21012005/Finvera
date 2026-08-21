from __future__ import annotations

import argparse
import asyncio
import base64
import getpass
import hashlib
import json
import os
from pathlib import Path
from typing import Any, Awaitable, Callable

import httpx
from websockets.asyncio.client import connect

from poc_common import schema_of_mapping, utc_now, write_summary


BASE_URL = "https://openapi.tcbs.com.vn"
TOKEN_PATH = "/gaia/v1/oauth2/openapi/token"
REQUEST_OTP_PATH = "/gaia/v1/oauth2/openapi/request-otp"
WS_URL = "wss://openapi.tcbs.com.vn/ws/thesis/v1/stream/normal"
OURANOS_WS_URL = "wss://openapi.tcbs.com.vn/ws/ouranos/v1/stream"
INDEX_NUMBERS = {1, 2, 3, 5}
TIMESTAMP_FIELD_NAMES = frozenset({"eventtime", "observedat", "timestamp", "time", "timesec", "tradingdate"})
ORDERING_FIELD_NAMES = frozenset({"sequence", "seq", "version", "order"})
CORRECTION_FIELD_NAMES = frozenset({"revision", "correction", "corrected", "supersedes"})
OURANOS_C001_REQUIRED_FIELDS = frozenset({"reference", "totalTrading", "totalTradingValue", "timeSec", "unitTimeFrame"})


def stream_field_evidence(schema: dict[str, str]) -> dict[str, list[str]]:
    """Report only observed field names; never infer unavailable semantics."""
    return {
        "timestamp_fields": sorted(
            field for field in schema if field.lower() in TIMESTAMP_FIELD_NAMES
        ),
        "ordering_fields": sorted(
            field for field in schema if field.lower() in ORDERING_FIELD_NAMES
        ),
        "correction_fields": sorted(
            field for field in schema if field.lower() in CORRECTION_FIELD_NAMES
        ),
    }


def ouranos_c001_capture_summary(
    captures: dict[str, list[dict[str, Any]]], requested_symbols: tuple[str, ...]
) -> dict[str, Any]:
    """Create a symbol-anonymous summary; raw payloads never leave this function."""
    requested = tuple(dict.fromkeys(symbol.upper() for symbol in requested_symbols))
    captured = [(symbol, payloads) for symbol, payloads in captures.items() if payloads]
    field_evidence: dict[str, dict[str, Any]] = {}
    update_counts: dict[str, int] = {}
    payload_fingerprints: dict[str, int] = {}
    schemas: dict[str, dict[str, str]] = {}
    complete = len(captured) == len(requested)

    for ordinal, (_, payloads) in enumerate(captured, start=1):
        key = f"symbol_{ordinal}"
        schema = schema_of_mapping(payloads[-1])
        evidence = stream_field_evidence(schema)
        evidence["required_fields_missing"] = sorted(OURANOS_C001_REQUIRED_FIELDS - set(schema))
        field_evidence[key] = evidence
        schemas[key] = schema
        update_counts[key] = len(payloads)
        payload_fingerprints[key] = len({
            hashlib.sha256(json.dumps(payload, sort_keys=True, separators=(",", ":")).encode("utf-8")).hexdigest()
            for payload in payloads
        })
        complete = complete and not evidence["required_fields_missing"]

    return {
        "status": "PASS" if complete else "PARTIAL",
        "requested_symbol_count": len(requested),
        "seen_symbol_count": len(captured),
        "message_schemas": schemas,
        "field_evidence": field_evidence,
        "update_counts": update_counts,
        "distinct_payload_count": payload_fingerprints,
    }


def summarize_response(payload: Any) -> dict[str, Any]:
    if not isinstance(payload, dict):
        return {"status": "FAIL", "reason": "TOP_LEVEL_NOT_OBJECT"}
    data = payload.get("data")
    first = data[0] if isinstance(data, list) and data and isinstance(data[0], dict) else None
    return {
        "status": "PASS",
        "top_level_fields": sorted(payload),
        "top_level_schema": schema_of_mapping(payload),
        "data_count": len(data) if isinstance(data, list) else None,
        "item_schema": schema_of_mapping(first) if first else {},
        "has_trading_date": "tradingDate" in payload,
    }


def summarize_error_response(response: httpx.Response) -> dict[str, Any]:
    result: dict[str, Any] = {"http_status": response.status_code}
    try:
        payload = response.json()
    except (json.JSONDecodeError, UnicodeDecodeError):
        return result
    if not isinstance(payload, dict):
        return result

    result["response_schema"] = schema_of_mapping(payload)
    provider_code = payload.get("code")
    if isinstance(provider_code, (str, int)):
        safe_code = str(provider_code)
        if safe_code.isascii() and safe_code.isalnum() and len(safe_code) <= 32:
            result["provider_code"] = safe_code
    return result


async def safe_rest_probe(
    name: str, call: Callable[[], Awaitable[httpx.Response]]
) -> dict[str, Any]:
    try:
        response = await call()
        if response.status_code != 200:
            return {"status": "FAIL", "http_status": response.status_code, "probe": name}
        return summarize_response(response.json())
    except Exception as exc:
        return {
            "status": "FAIL",
            "error_type": type(exc).__name__,
            "probe": name,
        }


async def websocket_probe(token: str, seconds: int) -> dict[str, Any]:
    seen: dict[int, dict[str, str]] = {}
    update_counts: dict[int, int] = {}
    distinct_payload_fingerprints: dict[int, set[str]] = {}
    controls: set[str] = set()
    stop = asyncio.Event()

    async with connect(
        WS_URL,
        ping_interval=None,
        ping_timeout=None,
        open_timeout=10,
        close_timeout=5,
        max_size=1_048_576,
    ) as websocket:
        encoded = base64.b64encode(token.encode("utf-8")).decode("ascii")
        await websocket.send(f"d|a|||{encoded}")

        authenticated = False
        deadline = asyncio.get_running_loop().time() + 10
        while asyncio.get_running_loop().time() < deadline:
            message = await asyncio.wait_for(websocket.recv(), timeout=10)
            if not isinstance(message, str):
                continue
            if message.startswith("d|0|"):
                control = json.loads(message.split("|", 2)[2])
                if not control.get("success"):
                    return {"status": "FAIL", "reason": "WS_AUTH_REJECTED"}
                authenticated = True
                controls.add("AUTH_SUCCESS")
                break
            if message.startswith("d|33|"):
                controls.add("HEARTBEAT_CONFIG")
        if not authenticated:
            return {"status": "FAIL", "reason": "WS_AUTH_TIMEOUT"}

        async def heartbeat() -> None:
            while not stop.is_set():
                await websocket.send("d|p|||")
                try:
                    await asyncio.wait_for(stop.wait(), timeout=2)
                except TimeoutError:
                    pass

        heartbeat_task = asyncio.create_task(heartbeat())
        try:
            await websocket.send("d|s|si|rt|1,2,3,5")
            deadline = asyncio.get_running_loop().time() + seconds
            while asyncio.get_running_loop().time() < deadline and set(seen) != INDEX_NUMBERS:
                timeout = max(0.1, deadline - asyncio.get_running_loop().time())
                try:
                    message = await asyncio.wait_for(websocket.recv(), timeout=timeout)
                except TimeoutError:
                    break
                if not isinstance(message, str):
                    continue
                if message.startswith("d|34|"):
                    controls.add("SUBSCRIBE_ACK")
                    continue
                if not message.startswith("s|8|"):
                    continue
                payload = json.loads(message.split("|", 2)[2])
                index_number = payload.get("indexNumber")
                if isinstance(index_number, (int, float)):
                    normalized_index_number = int(index_number)
                    seen[normalized_index_number] = schema_of_mapping(payload)
                    update_counts[normalized_index_number] = update_counts.get(normalized_index_number, 0) + 1
                    canonical = json.dumps(payload, sort_keys=True, separators=(",", ":"))
                    distinct_payload_fingerprints.setdefault(normalized_index_number, set()).add(
                        hashlib.sha256(canonical.encode("utf-8")).hexdigest()
                    )
        finally:
            stop.set()
            await heartbeat_task

    return {
        "status": "PASS" if set(seen) == INDEX_NUMBERS else "PARTIAL",
        "expected_index_numbers": sorted(INDEX_NUMBERS),
        "seen_index_numbers": sorted(seen),
        "index_message_schemas": {str(key): value for key, value in seen.items()},
        "stream_field_evidence": {str(key): stream_field_evidence(value) for key, value in seen.items()},
        "update_counts": {str(key): value for key, value in sorted(update_counts.items())},
        "distinct_payload_count": {
            str(key): len(value) for key, value in sorted(distinct_payload_fingerprints.items())
        },
        "control_evidence": sorted(controls),
    }


async def ouranos_c001_probe(token: str, symbols: tuple[str, ...], seconds: int) -> dict[str, Any]:
    """Bounded C001 probe for schema evidence; stores no raw provider message."""
    requested = tuple(dict.fromkeys(symbol.upper() for symbol in symbols))
    captures: dict[str, list[dict[str, Any]]] = {}
    stop = asyncio.Event()
    try:
        async with connect(
            OURANOS_WS_URL,
            ping_interval=None,
            ping_timeout=None,
            open_timeout=10,
            close_timeout=5,
            max_size=1_048_576,
        ) as websocket:
            encoded = base64.b64encode(token.encode("utf-8")).decode("ascii")
            await websocket.send(f"d|a|||{encoded}")
            authenticated = False
            deadline = asyncio.get_running_loop().time() + 10
            while asyncio.get_running_loop().time() < deadline:
                message = await asyncio.wait_for(websocket.recv(), timeout=10)
                if not isinstance(message, str):
                    continue
                if message.startswith("d|0|"):
                    control = json.loads(message.split("|", 2)[2])
                    if not control.get("success"):
                        return {"status": "FAIL", "reason": "OURANOS_AUTH_REJECTED"}
                    authenticated = True
                    break
            if not authenticated:
                return {"status": "FAIL", "reason": "OURANOS_AUTH_TIMEOUT"}

            async def heartbeat() -> None:
                while not stop.is_set():
                    await websocket.send("d|po")
                    try:
                        await asyncio.wait_for(stop.wait(), timeout=2)
                    except TimeoutError:
                        pass

            heartbeat_task = asyncio.create_task(heartbeat())
            try:
                await websocket.send(f"d|st|C001|{','.join(requested)}")
                deadline = asyncio.get_running_loop().time() + seconds
                while asyncio.get_running_loop().time() < deadline:
                    try:
                        message = await asyncio.wait_for(
                            websocket.recv(), timeout=max(0.1, deadline - asyncio.get_running_loop().time())
                        )
                    except TimeoutError:
                        break
                    if not isinstance(message, str) or message.startswith("d|"):
                        continue
                    parts = message.split("|", 2)
                    if len(parts) != 3 or parts[0] != "C001" or parts[1] not in requested:
                        continue
                    payload = json.loads(parts[2])
                    if isinstance(payload, dict):
                        captures.setdefault(parts[1], []).append(payload)
            finally:
                stop.set()
                await heartbeat_task
    except Exception as exc:
        return {"status": "FAIL", "error_type": type(exc).__name__}

    return ouranos_c001_capture_summary(captures, requested)


async def bounded_rate_probe(
    client: httpx.AsyncClient, headers: dict[str, str], requests: int, interval_seconds: float
) -> dict[str, Any]:
    statuses: dict[str, int] = {}
    for attempt in range(requests):
        try:
            response = await client.get(
                "/tartarus/v1/tickerCommons", params={"index": 1}, headers=headers
            )
            key = str(response.status_code)
        except Exception as exc:
            key = f"EXCEPTION_{type(exc).__name__}"
        statuses[key] = statuses.get(key, 0) + 1
        if attempt + 1 < requests:
            await asyncio.sleep(interval_seconds)
    return {
        "status": "PASS" if statuses == {"200": requests} else "FAIL",
        "request_count": requests,
        "interval_seconds": interval_seconds,
        "status_counts": statuses,
    }


async def run(args: argparse.Namespace) -> int:
    api_key = os.getenv("TCBS_API_KEY") or getpass.getpass("TCBS API key: ")
    if not api_key:
        raise SystemExit("TCBS API key is required")

    timeout = httpx.Timeout(15, connect=10)
    async with httpx.AsyncClient(base_url=BASE_URL, timeout=timeout) as client:
        token_payload_request: dict[str, str] = {"apiKey": api_key}
        if args.otp_method == "totp":
            otp = getpass.getpass("Current TCBS app iOTP/TOTP: ")
            if not otp:
                raise SystemExit("Current TCBS app iOTP/TOTP is required")
            token_payload_request["otp"] = otp
        else:
            request_otp_response = await client.post(
                REQUEST_OTP_PATH, json={"apiKey": api_key}
            )
            if request_otp_response.status_code != 200:
                write_summary(
                    args.output_dir,
                    "tcbs-capability-summary.json",
                    {
                        "probe": "tcbs-live-market",
                        "generated_at": utc_now(),
                        "gate_passed": False,
                        "contains_credentials_tokens_or_raw_payloads": False,
                        "authentication": {
                            "status": "FAIL",
                            "step": "REQUEST_EMAIL_SMS_OTP",
                            **summarize_error_response(request_otp_response),
                        },
                    },
                )
                print("TCBS email/SMS OTP request failed; response body suppressed")
                return 2
            request_otp_payload = request_otp_response.json()
            otp_id = (
                request_otp_payload.get("otpId")
                if isinstance(request_otp_payload, dict)
                else None
            )
            if isinstance(request_otp_payload, dict):
                request_otp_payload.clear()
            if not isinstance(otp_id, str) or not otp_id:
                write_summary(
                    args.output_dir,
                    "tcbs-capability-summary.json",
                    {
                        "probe": "tcbs-live-market",
                        "generated_at": utc_now(),
                        "gate_passed": False,
                        "contains_credentials_tokens_or_raw_payloads": False,
                        "authentication": {
                            "status": "FAIL",
                            "step": "REQUEST_EMAIL_SMS_OTP",
                            "reason": "OTP_ID_NOT_CONFIRMED",
                        },
                    },
                )
                print("TCBS email/SMS OTP response contained no usable OTP ID; payload suppressed")
                return 2
            otp = getpass.getpass("OTP received via TCBS email/SMS: ")
            if not otp:
                raise SystemExit("OTP received via TCBS email/SMS is required")
            token_payload_request.update({"otp": otp, "otpId": otp_id})

        token_response = await client.post(TOKEN_PATH, json=token_payload_request)
        token_payload_request.clear()
        otp = ""
        api_key = ""
        if token_response.status_code != 200:
            write_summary(
                args.output_dir,
                "tcbs-capability-summary.json",
                {
                    "probe": "tcbs-live-market",
                    "generated_at": utc_now(),
                    "gate_passed": False,
                    "contains_credentials_tokens_or_raw_payloads": False,
                    "authentication": {
                        "status": "FAIL",
                        "step": "EXCHANGE_TOKEN",
                        **summarize_error_response(token_response),
                    },
                },
            )
            print(f"TCBS token exchange failed with HTTP {token_response.status_code}; body suppressed")
            return 2
        token_payload = token_response.json()
        token = token_payload.get("token")
        if not isinstance(token, str) or not token:
            auth_schema = schema_of_mapping(token_payload) if isinstance(token_payload, dict) else {}
            if isinstance(token_payload, dict):
                token_payload.clear()
            write_summary(
                args.output_dir,
                "tcbs-capability-summary.json",
                {
                    "probe": "tcbs-live-market",
                    "generated_at": utc_now(),
                    "gate_passed": False,
                    "contains_credentials_tokens_or_raw_payloads": False,
                    "authentication": {
                        "status": "FAIL",
                        "reason": "TOKEN_FIELD_NOT_CONFIRMED",
                        "response_schema": auth_schema,
                    },
                },
            )
            print("TCBS token exchange returned no usable token; payload suppressed")
            return 2
        token_payload.clear()

        headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
        rest: dict[str, Any] = {}
        for index_number in sorted(INDEX_NUMBERS):
            rest[f"ticker_commons_index_{index_number}"] = await safe_rest_probe(
                f"ticker_commons_index_{index_number}",
                lambda index_number=index_number: client.get(
                    "/tartarus/v1/tickerCommons",
                    params={"index": index_number},
                    headers=headers,
                ),
            )
        rest["security_TCB"] = await safe_rest_probe(
            "security_TCB",
            lambda: client.get(
                "/ananke/v1/securities",
                params={"fields": "all", "filter": "symbol=TCB"},
                headers=headers,
            ),
        )
        if args.quote_symbols:
            # G-03 (specs/002-stock-detail-analysis/research.md R-012): confirms the same
            # tickerCommons endpoint already approved for index subjects (index=) also serves
            # individual equity subjects via tickers=, per TCBS's official OpenAPI docs.
            joined = ",".join(args.quote_symbols)
            rest["ticker_commons_quote_symbols"] = await safe_rest_probe(
                "ticker_commons_quote_symbols",
                lambda: client.get(
                    "/tartarus/v1/tickerCommons",
                    params={"tickers": joined},
                    headers=headers,
                ),
            )

        try:
            ws = await websocket_probe(token, args.ws_seconds)
        except Exception as exc:
            ws = {
                "status": "FAIL",
                "error_type": type(exc).__name__,
            }
        rate = (
            await bounded_rate_probe(client, headers, args.rate_probe_requests, args.rate_probe_interval_seconds)
            if args.rate_probe_requests else None
        )
        ouranos = (
            await ouranos_c001_probe(token, args.ouranos_symbols, args.ouranos_seconds)
            if args.ouranos_symbols else None
        )
        token = ""
        headers.clear()

    gate_passed = ws.get("status") == "PASS" and all(
        result.get("status") == "PASS" for result in rest.values()
    ) and (rate is None or rate.get("status") == "PASS")
    summary = {
        "probe": "tcbs-live-market",
        "generated_at": utc_now(),
        "gate_passed": gate_passed,
        "contains_credentials_tokens_or_raw_payloads": False,
        "rest": rest,
        "websocket": ws,
    }
    if rate is not None:
        summary["bounded_rate_probe"] = rate
    if ouranos is not None:
        summary["ouranos_c001"] = ouranos
    write_summary(args.output_dir, "tcbs-capability-summary.json", summary)
    return 0 if gate_passed else 2


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--otp-method", choices=("totp", "email-sms"), default="totp")
    parser.add_argument("--ws-seconds", type=int, default=20)
    parser.add_argument("--rate-probe-requests", type=int, default=0)
    parser.add_argument("--rate-probe-interval-seconds", type=float, default=1.0)
    parser.add_argument("--ouranos-symbols", type=lambda value: tuple(filter(None, value.upper().split(","))))
    parser.add_argument("--ouranos-seconds", type=int, default=90)
    parser.add_argument(
        "--quote-symbols",
        type=lambda value: tuple(filter(None, value.upper().split(","))),
        default=(),
        help="G-03: probe per-symbol current price via tickerCommons?tickers=SYMBOL[,SYMBOL...] "
             "instead of the index basket, e.g. --quote-symbols VNM,TCB",
    )
    parser.add_argument("--output-dir", type=Path, default=Path("poc-output"))
    args = parser.parse_args()
    if not 0 <= args.rate_probe_requests <= 5:
        parser.error("--rate-probe-requests must be between 0 and 5")
    if args.rate_probe_interval_seconds < 1:
        parser.error("--rate-probe-interval-seconds must be at least 1")
    if args.ouranos_symbols and (not 10 <= args.ouranos_seconds <= 180):
        parser.error("--ouranos-seconds must be between 10 and 180 when --ouranos-symbols is set")
    if len(args.quote_symbols) > 5:
        parser.error("--quote-symbols accepts at most 5 symbols for a bounded probe")
    return asyncio.run(run(args))


if __name__ == "__main__":
    raise SystemExit(main())
