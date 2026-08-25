# Email templates — rules for working in this directory

- Edit only `src/*.mjml`, `src/partials/*.mjml`, `fixtures/*.json`, `manifest.json`. Never edit
  `dist/*.html` by hand.
- After any source change run `npm run preview` and tell the user to open
  `emails/preview/index.html` in their browser to see the result. `dist/` is generated and
  gitignored — never commit it; CI rebuilds it on every branch and at publish time.
- Preserve Mandrill merge tags verbatim: `*|FNAME|*`, `*|IF:x|*`, `*|ELSEIF:x|*`, `*|ELSE:|*`,
  `*|END:IF|*`. They must sit inside `<mj-raw>` when they wrap MJML components. A typo like
  `*IELSEIF` silently breaks the branch chain in sent emails — treat merge tags as code.
- Every conditional branch needs a variant in the template's `fixtures/` file, otherwise it is
  invisible in the preview and will ship unreviewed.
- Shared design (fonts, colors, buttons, header, footer) lives in `src/partials/` — change it
  there once, never inline per-template. One button per nudge; follow the tõuke ja toe model
  (support text first, one call to action after).
- The design authority is Taimar's Mailchimp base template "Tuleva UUS põhi" (folder
  "New campaign templates"): 660px, 196px logo, #212529 text, #0081EE buttons and links,
  italic 13px disclaimers, divider → social icons (Website/Facebook/Instagram/Spotify) →
  divider → 13px #6C757D centered address. Never use `target="_blank"` on any link.
- Footers are composed per template: `contact_*` partial (or an inline case-specific contact
  sentence), inline signature, a `disclaimer_*` partial only when that template family has
  one, then `footer_bottom_fund` (Tuleva Fondid AS) or `footer_bottom_coop`
  (Tulundusühistu Tuleva) by sender entity.
- Copy style: follow `tuleva` repo `knowledge/stiil/stiilijuhend-et.md`. Estonian uses
  non-breaking spaces in "II&nbsp;sammas", "III&nbsp;sammas" and before "€".
- Publishing happens from CI on master only. Never call the Mandrill API directly from here.
