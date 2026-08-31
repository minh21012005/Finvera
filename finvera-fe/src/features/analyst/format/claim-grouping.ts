import type { PublicStructuredClaim } from '../api/analyst';

/**
 * One verified sentence can carry several [T<n>:<field>=<value>] tags (e.g. direction and
 * strategyCode in the same sentence). The verifier keeps one claim per tag; the UI shows the
 * sentence once with every verified field, instead of repeating it per field.
 */
export interface ClaimGroup {
  claimText: string;
  sequenceNo: number;
  toolName: string;
  sourceFields: string[];
  asOf: string;
}

export function groupClaimsBySentence(claims: PublicStructuredClaim[]): ClaimGroup[] {
  const groups: ClaimGroup[] = [];
  const index = new Map<string, ClaimGroup>();
  for (const claim of claims) {
    const key = `${claim.sequenceNo}|${claim.claimText}`;
    const existing = index.get(key);
    if (existing) {
      if (!existing.sourceFields.includes(claim.sourceField)) existing.sourceFields.push(claim.sourceField);
      continue;
    }
    const group: ClaimGroup = {
      claimText: claim.claimText,
      sequenceNo: claim.sequenceNo,
      toolName: claim.toolName,
      sourceFields: [claim.sourceField],
      asOf: claim.asOf,
    };
    index.set(key, group);
    groups.push(group);
  }
  return groups;
}
