from poc_tcbs import ouranos_c001_capture_summary, stream_field_evidence


def test_stream_field_evidence_reports_available_and_missing_provenance_fields() -> None:
    evidence = stream_field_evidence(
        {
            "index": "number",
            "indexNumber": "integer",
            "session": "string",
            "eventTime": "string",
            "sequence": "integer",
        }
    )

    assert evidence["timestamp_fields"] == ["eventTime"]
    assert evidence["ordering_fields"] == ["sequence"]
    assert evidence["correction_fields"] == []


def test_stream_field_evidence_does_not_infer_missing_semantics() -> None:
    evidence = stream_field_evidence({"index": "number", "session": "string"})

    assert evidence == {
        "timestamp_fields": [],
        "ordering_fields": [],
        "correction_fields": [],
    }


def test_ouranos_c001_summary_proves_documented_fields_without_retaining_values() -> None:
    summary = ouranos_c001_capture_summary(
        {
            "TCB": [
                {
                    "symbol": "TCB",
                    "closePrice": 38600.0,
                    "reference": 37500.0,
                    "totalTrading": 24277600.0,
                    "totalTradingValue": 933400610000.0,
                    "timeSec": "1755144180",
                    "unitTimeFrame": "60",
                }
            ]
        },
        ("TCB",),
    )

    assert summary["status"] == "PASS"
    assert summary["seen_symbol_count"] == 1
    assert summary["update_counts"] == {"symbol_1": 1}
    assert summary["field_evidence"]["symbol_1"]["timestamp_fields"] == ["timeSec"]
    assert summary["field_evidence"]["symbol_1"]["required_fields_missing"] == []
    assert "TCB" not in str(summary)
    assert "38600" not in str(summary)


def test_ouranos_c001_summary_marks_missing_required_fields_partial() -> None:
    summary = ouranos_c001_capture_summary(
        {"TCB": [{"symbol": "TCB", "timeSec": "1755144180"}]}, ("TCB", "VNM")
    )

    assert summary["status"] == "PARTIAL"
    assert summary["seen_symbol_count"] == 1
    assert summary["field_evidence"]["symbol_1"]["required_fields_missing"] == [
        "reference",
        "totalTrading",
        "totalTradingValue",
        "unitTimeFrame",
    ]
