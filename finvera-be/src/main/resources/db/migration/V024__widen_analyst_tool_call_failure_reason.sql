-- Widen failure_reason on analyst_tool_call to allow detailed error diagnostics
ALTER TABLE analyst_tool_call ALTER COLUMN failure_reason TYPE VARCHAR(1000);
