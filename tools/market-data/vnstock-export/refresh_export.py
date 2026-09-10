"""Launcher cấu hình retry SDK và giữ bootstrap refresh chạy tự động."""
import runpy
import sys
from pathlib import Path

import provider_retry


def main():
    allowed = {"export_instrument_reference.py", "export_sector_reference_vci.py",
               "export_equity_profile.py", "export_history.py", "export_all_symbols.py"}
    if len(sys.argv) < 2 or sys.argv[1] not in allowed:
        raise SystemExit("Cần tên exporter được cho phép")
    script = Path(__file__).with_name(sys.argv[1])
    if script.name not in {"export_all_symbols.py", "export_equity_profile.py"}:
        provider_retry.install()
    sys.argv = [str(script), *sys.argv[2:]]
    runpy.run_path(str(script), run_name="__main__")


if __name__ == "__main__":
    main()
