const asMandrillStoresIt = (html) =>
  html
    .replace(/\s+/g, ' ')
    .replace(/\s+(\/?>)/g, '$1')
    .trim();

export const matchesLive = (storedCode, builtCode) =>
  asMandrillStoresIt(storedCode) === asMandrillStoresIt(builtCode);
