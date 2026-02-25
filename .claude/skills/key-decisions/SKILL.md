# Key Decisions

Please read my conversation history from ~/.claude/history.jsonl and extract all key decisions, focusing on:

## What to Extract

**Architectural Decisions**
- Technology/framework choices and why
- Database schema decisions
- API design patterns chosen
- File/folder structure decisions

**Implementation Decisions**
- Libraries or packages selected (and alternatives rejected)
- Patterns chosen (e.g., "went with repository pattern over active record")
- Trade-offs discussed and conclusions reached

**Configuration Decisions**
- Environment setup choices
- Build/deployment configurations
- Testing strategies decided on

## Output Format

Group decisions by project, then by category. For each decision show:

PROJECT: [project-name]
Date: [when]
Decision: [what was decided]
Reasoning: [why, if discussed]
Session: [session-id to revisit]

---

Focus on the last 7 days of conversations. Skip routine changes - only surface meaningful choices where alternatives were considered or reasoning was discussed.

If I need to revisit a decision, I can run: claude --resume <session-id>
