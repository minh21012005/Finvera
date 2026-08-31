const TAG_PATTERN = /\[T\d+:[\w.]+=[^\]]+\]|\[Block\s*\d+\]/gi;

/** Citation tags the synthesis model emits inline; the backend strips them from the final answer, the stream may still carry them. */
export function stripCitationTags(text: string): string {
  return text.replace(TAG_PATTERN, '').replace(/[ \t]+([.,;:!?])/g, '$1').replace(/[ \t]{2,}/g, ' ');
}
