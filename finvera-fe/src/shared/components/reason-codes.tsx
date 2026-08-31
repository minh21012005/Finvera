import React from "react";
import { NO_REASON_GIVEN, applicabilityNote, reasonCodeLabel } from "../format/reason-codes";

/**
 * Contract reason-code-presentation-v1, P-1/P-2: wording as text, the code kept on the element
 * (`data-reason-code`, `title`) so provenance stays inspectable and tests can address codes.
 */
export function ReasonCode({ code }: { code: string }): React.ReactElement {
  return (
    <span data-reason-code={code} title={code}>
      {reasonCodeLabel(code)}
    </span>
  );
}

interface ReasonCodesProps {
  codes: readonly string[] | null | undefined;
  /** Text before the list, e.g. "Ghi chú: " — rendered only when there is something to list. */
  prefix?: string;
  /** P-3 fallback when the list is empty; pass `null` to render nothing for an empty list. */
  fallback?: string | null;
}

/** One wording per code, joined with "; " (P-3). */
export function ReasonCodes({ codes, prefix, fallback = NO_REASON_GIVEN }: ReasonCodesProps): React.ReactElement | null {
  if (!codes || codes.length === 0) {
    return fallback === null ? null : <>{prefix}{fallback}</>;
  }
  return (
    <>
      {prefix}
      {codes.map((code, i) => (
        <React.Fragment key={`${code}-${i}`}>
          {i > 0 ? "; " : null}
          <ReasonCode code={code} />
        </React.Fragment>
      ))}
    </>
  );
}

/** P-4: cell text for a metric / indicator / factor without a value; null for DEFINED. */
export function ApplicabilityNote({ applicability, reasonCode }: { applicability: string; reasonCode: string | null | undefined }): React.ReactElement | null {
  const note = applicabilityNote(applicability, reasonCode);
  if (note === null) return null;
  return (
    <span data-reason-code={reasonCode ?? undefined} title={reasonCode ?? undefined}>
      {note}
    </span>
  );
}
