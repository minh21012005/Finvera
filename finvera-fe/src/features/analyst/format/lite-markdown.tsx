import React from 'react';

/**
 * Minimal, dependency-free renderer for the subset of Markdown the Analyst emits:
 * paragraphs, `**bold**`, bullet lists (`* ` / `- `, nested by indentation), numbered
 * lists (`1. `). Output is built from React elements only — model text is never
 * injected as HTML, so it cannot carry markup or scripts into the page (AI-003).
 */
function renderInline(text: string, keyPrefix: string): React.ReactNode[] {
  const parts = text.split(/(\*\*[^*]+\*\*)/g).filter((p) => p.length > 0);
  return parts.map((part, i) => {
    if (part.startsWith('**') && part.endsWith('**') && part.length > 4) {
      return <strong key={`${keyPrefix}-b${i}`}>{part.slice(2, -2)}</strong>;
    }
    return <React.Fragment key={`${keyPrefix}-t${i}`}>{part}</React.Fragment>;
  });
}

interface Block {
  kind: 'p' | 'ul' | 'ol';
  lines: { text: string; depth: number }[];
}

function parseBlocks(text: string): Block[] {
  const blocks: Block[] = [];
  let current: Block | null = null;
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.replace(/\s+$/, '');
    if (line.trim() === '') {
      current = null;
      continue;
    }
    const bullet = /^(\s*)[*\-•]\s+(.*)$/.exec(line);
    const numbered = /^(\s*)\d+[.)]\s+(.*)$/.exec(line);
    if (bullet) {
      const depth = Math.min(2, Math.floor(bullet[1].length / 2));
      if (!current || current.kind !== 'ul') { current = { kind: 'ul', lines: [] }; blocks.push(current); }
      current.lines.push({ text: bullet[2], depth });
    } else if (numbered) {
      const depth = Math.min(2, Math.floor(numbered[1].length / 2));
      if (!current || current.kind !== 'ol') { current = { kind: 'ol', lines: [] }; blocks.push(current); }
      current.lines.push({ text: numbered[2], depth });
    } else {
      if (!current || current.kind !== 'p') { current = { kind: 'p', lines: [] }; blocks.push(current); }
      current.lines.push({ text: line.trim(), depth: 0 });
    }
  }
  return blocks;
}

export function LiteMarkdown({ text }: { text: string }): React.ReactElement {
  const blocks = parseBlocks(text);
  return (
    <div className="lite-markdown space-y-2">
      {blocks.map((block, bi) => {
        if (block.kind === 'p') {
          return (
            <p key={bi} className="leading-relaxed">
              {block.lines.map((l, li) => (
                <React.Fragment key={li}>
                  {renderInline(l.text, `${bi}-${li}`)}
                  {li < block.lines.length - 1 ? <br /> : null}
                </React.Fragment>
              ))}
            </p>
          );
        }
        const ListTag = block.kind === 'ul' ? 'ul' : 'ol';
        const listClass = block.kind === 'ul' ? 'list-disc' : 'list-decimal';
        return (
          <ListTag key={bi} className={`${listClass} pl-5 space-y-1`}>
            {block.lines.map((l, li) => (
              <li key={li} className={l.depth === 0 ? '' : `ml-${l.depth * 4} list-[circle]`}>
                {renderInline(l.text, `${bi}-${li}`)}
              </li>
            ))}
          </ListTag>
        );
      })}
    </div>
  );
}
