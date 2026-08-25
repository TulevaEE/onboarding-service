const TAG = /\*\|(IF|ELSEIF|ELSE|END):?([A-Za-z0-9_]*)\|\*/g;

export function renderMergeLanguage(html, vars) {
  const output = [];
  const stack = [];

  const emitting = () => stack.every((frame) => frame.emitting);
  let lastIndex = 0;

  for (const match of html.matchAll(TAG)) {
    if (emitting()) {
      output.push(html.slice(lastIndex, match.index));
    }
    lastIndex = match.index + match[0].length;
    const [, keyword, variable] = match;

    if (keyword === 'IF') {
      const on = Boolean(vars[variable]);
      stack.push({ emitting: emitting() && on, satisfied: on });
    } else if (keyword === 'ELSEIF') {
      const frame = stack[stack.length - 1];
      const on = !frame.satisfied && Boolean(vars[variable]);
      frame.emitting = stack.slice(0, -1).every((f) => f.emitting) && on;
      frame.satisfied = frame.satisfied || on;
    } else if (keyword === 'ELSE') {
      const frame = stack[stack.length - 1];
      const on = !frame.satisfied;
      frame.emitting = stack.slice(0, -1).every((f) => f.emitting) && on;
      frame.satisfied = true;
    } else if (keyword === 'END') {
      stack.pop();
    }
  }
  if (emitting()) {
    output.push(html.slice(lastIndex));
  }

  return output
    .join('')
    .replace(/\*\|([A-Za-z0-9_]+)\|\*/g, (whole, name) =>
      vars[name] !== undefined ? String(vars[name]) : whole,
    );
}
