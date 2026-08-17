from __future__ import annotations

import argparse
import asyncio
import base64
import getpass
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
INDEX_NUMBERS = {1, 2, 3, 5}


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
            "error": str(exc)[:300],
            "probe": name,
        }


async def websocket_probe(token: str, seconds: int) -> dict[str, Any]:
    seen: dict[int, dict[str, str]] = {}
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
                    seen[int(index_number)] = schema_of_mapping(payload)
        finally:
            stop.set()
            await heartbeat_task

    return {
        "status": "PASS" if set(seen) == INDEX_NUMBERS else "PARTIAL",
        "expected_index_numbers": sorted(INDEX_NUMBERS),
        "seen_index_numbers": sorted(seen),
        "index_message_schemas": {str(key): value for key, value in seen.items()},
        "control_evidence": sorted(controls),
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

        try:
            ws = await websocket_probe(token, args.ws_seconds)
        except Exception as exc:
            ws = {
                "status": "FAIL",
                "error_type": type(exc).__name__,
                "error": str(exc)[:300],
            }
        token = ""
        headers.clear()

    gate_passed = ws.get("status") == "PASS" and all(
        result.get("status") == "PASS" for result in rest.values()
    )
    summary = {
        "probe": "tcbs-live-market",
        "generated_at": utc_now(),
        "gate_passed": gate_passed,
        "contains_credentials_tokens_or_raw_payloads": False,
        "rest": rest,
        "websocket": ws,
    }
    write_summary(args.output_dir, "tcbs-capability-summary.json", summary)
    return 0 if gate_passed else 2


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--otp-method", choices=("totp", "email-sms"), default="totp")
    parser.add_argument("--ws-seconds", type=int, default=20)
    parser.add_argument("--output-dir", type=Path, default=Path("poc-output"))
    return asyncio.run(run(parser.parse_args()))


if __name__ == "__main__":
    raise SystemExit(main())
