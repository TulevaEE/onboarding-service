# Non-Engineer Onboarding Guide

A guide for non-engineers (PMs, designers, analysts) contributing to the codebase with AI coding agents like Claude Code.

## About You

Fill in your details so Claude can tailor its help:

- **Role:** (e.g., product manager, designer, analyst)
- **Technical background:** (e.g., strong SQL, some HTML — new to Java/Spring)
- **Tools:** (e.g., VS Code, GitHub, Slack, Claude Code)
- **Comfort level:** (e.g., comfortable with terminal, new to command line)

## How to Work

- **Task-driven learning** — learn by doing real tasks, not reading docs
- **Ground-up approach** — start from the smallest useful change and build up
- **Business-first understanding** — understand what code does in terms of user/business impact before diving into technical details

## How Claude Should Help

- **Summarize code in business language** — e.g., "this checks if a user is allowed to switch pension funds" not "this validates the mandate entity against the fund transfer eligibility criteria"
- **Explain before doing** — briefly say what you're about to do and why, so the contributor learns
- **Keep it succinct** — no walls of text, no unnecessary jargon
- **Flag what to understand vs. what to ignore** — not everything is relevant to a non-engineer learning to code
- **Teach the pattern, not just the fix** — when doing something, mention if it's a pattern that appears elsewhere
- **Use existing knowledge as a bridge** — relate Java/Spring concepts to things the contributor already knows (e.g., SQL/database thinking)
- **No assumptions about Java/Spring knowledge** — explain framework concepts briefly when they come up

## Task Writing

Write your task into `temp/task.md` before starting a session with Claude. This is your single running task file — add each new task with an incrementing number so you can track how your task-writing improves over time.

**Format:**
```
## Task 1 — [short title]
[date]

1. requirement
2. requirement
...

## Task 2 — [short title]
[date]
...
```

**Tips for good task definitions:**
- Reference the business documents that define the requirements
- Be explicit about scope — what to build AND what NOT to build
- Specify where results should be stored or how they connect to existing code
- Include links to external APIs or docs when relevant
- Write ALL requirements before starting — adding points mid-session costs rework
- Use separate task files per session (`temp/task2.md`, `temp/task3.md`)

## Working with Claude Code

- **Use plan mode for anything non-trivial** — it catches design issues before code exists. Review the plan, add requirements, then approve.
- **Write a proposal doc first for research tasks** — iterate on a markdown file before writing any code. Changes to docs are free.
- **Be specific about PRs and comments** — say "PR #1511, JordanValdma's comment" not "the recent PR"
- **Say "commit and PR" explicitly** — Claude won't push without you asking
- **Batch related tasks** in one session when they touch the same module — avoids repeated context-building
- **Provide business context** — "EU PEPs are not high risk" shapes design better than "implement check 43"

## Getting Started

- [ ] Set up and run the project locally (see main [README](README.md))
- [ ] Make a first small contribution
- [ ] Build confidence navigating the codebase
- [ ] Improve task-writing skills (review progression in `temp/task.md`)
