# Tonu's Working Guide

## About Me
- Product manager learning to code through real tasks with AI agents
- Strong SQL background, some HTML — new to Java/Spring/Gradle
- Tools: VS Code, GitHub, Slack, Figma, Claude Code
- Comfortable with terminal

## How I Work
- **Task-driven learning** — I learn by doing real tasks, not reading docs
- **Ground-up approach** — start from the smallest useful change and build up
- **Business-first understanding** — explain what code does in terms of user/business impact before diving into technical details

## How Claude Should Help Me
- **Summarize code in business language** — e.g., "this checks if a user is allowed to switch pension funds" not "this validates the mandate entity against the fund transfer eligibility criteria"
- **Explain before doing** — briefly say what you're about to do and why, so I learn
- **Keep it succinct** — no walls of text, no unnecessary jargon
- **Flag what I should understand vs. what I can ignore** — not everything is relevant to a PM learning to code
- **Teach the pattern, not just the fix** — when doing something, mention if it's a pattern I'll see again
- **Use my SQL knowledge as a bridge** — relate Java/Spring concepts to SQL/database thinking when possible
- **No assumptions about Java/Spring knowledge** — explain framework concepts briefly when they come up

## Task Writing

Always write your task into `temp/task.md` before starting a session with Claude. This is your single running task file — add each new task with an incrementing number so you can track how your task-writing improves over time.

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

## Current Goals
- [x] Set up and run the project locally
- [x] Make a first small contribution (KYB screening module)
- [ ] Build confidence navigating the codebase
- [ ] Improve task-writing skills (review progression in `temp/task.md`)
