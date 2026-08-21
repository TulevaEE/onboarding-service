# Email templates — rules for working in this directory

- Edit only `src/*.mjml`, `src/partials/*.mjml`, `fixtures/*.json`, `manifest.json`. Never edit
  `dist/*.html` by hand and never edit `exported/*` at all (read-only legacy snapshot).
- After any source change run `npm run preview` and tell the user to open
  `emails/preview/index.html` in their browser to see the result. Always commit the rebuilt
  `dist/` together with `src/`.
- Preserve Mandrill merge tags verbatim: `*|FNAME|*`, `*|IF:x|*`, `*|ELSEIF:x|*`, `*|ELSE:|*`,
  `*|END:IF|*`. They must sit inside `<mj-raw>` when they wrap MJML components. A typo like
  `*IELSEIF` silently breaks the branch chain in sent emails — treat merge tags as code.
- Every conditional branch needs a variant in the template's `fixtures/` file, otherwise it is
  invisible in the preview and will ship unreviewed.
- Shared design (fonts, colors, buttons, header, footer) lives in `src/partials/` — change it
  there once, never inline per-template. One button per nudge; follow the tõuke ja toe model
  (support text first, one call to action after).
- Copy style: follow `tuleva` repo `knowledge/stiil/stiilijuhend-et.md`. Estonian uses
  non-breaking spaces in "II&nbsp;sammas", "III&nbsp;sammas" and before "€".
- Publishing happens from CI on master only. Never call the Mandrill API directly from here.
