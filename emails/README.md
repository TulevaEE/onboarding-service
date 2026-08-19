# Tuleva email templates

The email templates Tuleva sends through Mandrill, version-controlled. **This directory is the
source of truth**: templates are written in [MJML](https://mjml.io) under `src/`, compiled to
email-client-compatible HTML in `dist/`, and CI publishes changed templates to Mandrill when a
change reaches master. Do not edit templates in the Mandrill web editor — the next publish
overwrites those edits.

## Editing a template (also works great by just asking Claude Code)

1. Edit the template in `src/<name>.mjml` (shared design lives in `src/partials/`).
2. `npm run preview`
3. Open `preview/index.html` in your browser — every template, every content branch, both
   languages, rendered with the sample data from `fixtures/`.
4. Commit `src/` **and** `dist/` (the build rewrites `dist/`; CI fails if they don't match).
5. Open a PR. When it merges to master, CI publishes the changed templates to Mandrill.

Conditional content uses Mandrill's merge language: `*|IF:variable|* … *|ELSEIF:other|* …
*|ELSE:|* … *|END:IF|*` and `*|variable|*` for values. Keep those tags exactly as they are —
they are resolved by Mandrill at send time. If you add a new branch, add a matching variant to
the template's file in `fixtures/` so it shows up in the preview gallery.

## Adding a template

1. Create `src/<name>_et.mjml` and `src/<name>_en.mjml` (copy an existing one).
2. Add both to `manifest.json` with their subject line and sender.
3. Add `fixtures/<name>_et.json` / `_en.json` with one variant per content branch.
4. Build, preview, commit, PR.

The template only starts sending once something in `onboarding-service` references its name
(see `EmailType`).

## Directory map

| Path | What |
|---|---|
| `src/` | MJML sources — the files humans edit |
| `src/partials/` | Shared design: head (fonts, colors, button), header (logo), footers |
| `dist/` | Compiled HTML, committed, published verbatim to Mandrill |
| `fixtures/` | Sample merge data per template; drives the preview gallery |
| `manifest.json` | Which templates CI manages + subject/sender for each |
| `exported/` | Read-only snapshot of all legacy Mandrill templates not yet ported |
| `preview/` | Generated gallery (gitignored) |

## CI

`emails-build` runs on every branch and fails when `dist/` is stale. `emails-publish` runs on
master only and updates+publishes exactly the templates in `manifest.json` whose content or
subject differs from what is live; unchanged templates are untouched. It needs the
`MANDRILL_API_KEY` environment variable in CircleCI project settings.

Rollback = revert the commit; CI republishes the previous version.

## Porting a legacy template

Pick one from `exported/`, rewrite the content section in MJML on top of the shared partials
(the layout collapses ~800 lines of table soup into ~80 lines of content), add fixtures +
manifest entries, compare the preview against the exported original side by side, and delete
the `exported/` file once it is live from `dist/`.
