import React from 'react';

/**
 * Minimal, dependency-free renderer for the subset of Markdown the Analyst emits:
 * paragraphs, `**bold**`, `*italic*`, bullet lists (`* ` / `- `, nested by indentation), numbered
 * lists (`1. `), and Markdown tables (`| ... |`). Output is built from React elements only — model text is never
 * injected as HTML, so it cannot carry markup or scripts into the page (AI-003).
 */
function renderInline(text: string, keyPrefix: string): React.ReactNode[] {
  const parts = text.split(/(\*\*[^*]+\*\*|\*[^*]+\*)/g).filter((p) => p.length > 0);
  return parts.map((part, i) => {
    if (part.startsWith('**') && part.endsWith('**') && part.length > 4) {
      return <strong key={`${keyPrefix}-b${i}`}>{part.slice(2, -2)}</strong>;
    }
    if (part.startsWith('*') && part.endsWith('*') && part.length > 2) {
      return (
        <em key={`${keyPrefix}-i${i}`} className="text-slate-400 not-italic">
          {part.slice(1, -1)}
        </em>
      );
    }
    return <React.Fragment key={`${keyPrefix}-t${i}`}>{part}</React.Fragment>;
  });
}

interface TableBlock {
  kind: 'table';
  headers: string[];
  rows: string[][];
}

interface ListBlock {
  kind: 'ul' | 'ol';
  lines: { text: string; depth: number }[];
}

interface ParagraphBlock {
  kind: 'p';
  lines: { text: string; depth: number }[];
}

type Block = TableBlock | ListBlock | ParagraphBlock;

function isTableRow(line: string): boolean {
  const trimmed = line.trim();
  return trimmed.startsWith('|') && trimmed.endsWith('|') && trimmed.length > 2;
}

function isTableDelimiter(line: string): boolean {
  const trimmed = line.trim();
  if (!isTableRow(trimmed)) return false;
  const inner = trimmed.slice(1, -1);
  const cells = inner.split('|').map((c) => c.trim());
  return cells.length > 0 && cells.every((c) => /^:?-+:?$/.test(c));
}

function parseCells(line: string): string[] {
  const trimmed = line.trim();
  return trimmed
    .slice(1, -1)
    .split('|')
    .map((c) => c.trim());
}

function parseBlocks(text: string): Block[] {
  const blocks: Block[] = [];
  let current: Block | null = null;
  const rawLines = text.split(/\r?\n/);

  for (let idx = 0; idx < rawLines.length; idx++) {
    const raw = rawLines[idx];
    const line = raw.replace(/\s+$/, '');

    if (line.trim() === '') {
      current = null;
      continue;
    }

    // Check if table row
    if (isTableRow(line)) {
      if (current && current.kind === 'table') {
        if (!isTableDelimiter(line)) {
          current.rows.push(parseCells(line));
        }
        continue;
      }

      // Check if this line is a table header followed by a delimiter line
      const nextLine = idx + 1 < rawLines.length ? rawLines[idx + 1].trim() : '';
      if (isTableDelimiter(nextLine)) {
        current = {
          kind: 'table',
          headers: parseCells(line),
          rows: [],
        };
        blocks.push(current);
        idx++; // Skip delimiter line
        continue;
      }
    }

    const bullet = /^(\s*)[*\-•]\s+(.*)$/.exec(line);
    const numbered = /^(\s*)\d+[.)]\s+(.*)$/.exec(line);

    if (bullet) {
      const depth = Math.min(2, Math.floor(bullet[1].length / 2));
      if (!current || current.kind !== 'ul') {
        current = { kind: 'ul', lines: [] };
        blocks.push(current);
      }
      current.lines.push({ text: bullet[2], depth });
    } else if (numbered) {
      const depth = Math.min(2, Math.floor(numbered[1].length / 2));
      if (!current || current.kind !== 'ol') {
        current = { kind: 'ol', lines: [] };
        blocks.push(current);
      }
      current.lines.push({ text: numbered[2], depth });
    } else {
      if (!current || current.kind !== 'p') {
        current = { kind: 'p', lines: [] };
        blocks.push(current);
      }
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
        if (block.kind === 'table') {
          return (
            <div
              key={bi}
              className="overflow-x-auto my-3 rounded-lg border border-slate-700/60 bg-slate-900/40 shadow-sm"
            >
              <table className="min-w-full divide-y divide-slate-700/60 text-xs text-left">
                <thead className="bg-slate-800/80">
                  <tr>
                    {block.headers.map((h, hi) => (
                      <th
                        key={hi}
                        className="px-3.5 py-2.5 font-semibold text-slate-200"
                      >
                        {renderInline(h, `${bi}-th-${hi}`)}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/60">
                  {block.rows.map((row, ri) => (
                    <tr
                      key={ri}
                      className={
                        ri % 2 === 0
                          ? 'bg-slate-900/20 hover:bg-slate-800/40 transition-colors'
                          : 'bg-slate-950/20 hover:bg-slate-800/40 transition-colors'
                      }
                    >
                      {row.map((cell, ci) => (
                        <td
                          key={ci}
                          className={`px-3.5 py-2.5 ${
                            ci === 0 ? 'font-medium text-slate-300' : 'text-slate-200'
                          }`}
                        >
                          {renderInline(cell, `${bi}-r${ri}-c${ci}`)}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          );
        }

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
