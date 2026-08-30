"""Test bootstrap: supply the mandatory shared secret before app.core.settings loads.

Production refuses a blank or placeholder INTERNAL_API_KEY at startup; tests must
provide a real-looking value rather than rely on a default that no longer exists.
"""
import os

os.environ.setdefault("INTERNAL_API_KEY", "test-internal-api-key")
