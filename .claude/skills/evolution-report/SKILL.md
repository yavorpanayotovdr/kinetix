---
name: evolution-report
description: Reads conversation history from ~/.claude/history.jsonl and generates a comprehensive evolution report for the current project, telling the story of how it was built.
user-invocable: true
allowed-tools: Bash, Read, Glob, Grep, Task
---

# Evolution Report

Generate a comprehensive evolution report for the project in the current working directory.

## Data Source

Read the conversation history from `~/.claude/history.jsonl`. Each line is a JSON object with these fields:

- `display` -- the user's prompt text
- `pastedContents` -- any pasted content (may be empty)
- `timestamp` -- Unix epoch in milliseconds
- `project` -- absolute path to the project directory
- `sessionId` -- UUID identifying the conversation session

**Filter to the current project only.** Use the current working directory path to match entries where `project` equals the working directory. Discard all entries from other projects.

## Step 1 -- Extract & Group History

Use a Bash command with Python to:

1. Read `~/.claude/history.jsonl`
2. Filter entries where `project` matches the current working directory
3. Convert timestamps to human-readable dates
4. Group entries by session and by date
5. Output a structured summary (date, sessionId, prompt text) as JSON

## Step 2 -- Analyse Git History

Run `git log` to get the full commit history with dates and messages. Cross-reference commits with conversation sessions to build the timeline.

## Step 3 -- Generate the Report

Produce the report in the structure below. Write it as a readable narrative, not just bullet points -- tell the story of how this project came together.

---

### 1. Project Timeline

Create a chronological narrative of the project's development:

**[Date] - [Phase/Milestone Name]**
Brief description of what was built or changed, and why.

### 2. Initial Vision vs Current State

- What was the original goal/prompt that started this project?
- How has the scope evolved?
- What pivots or direction changes occurred?

### 3. Technical Evolution

**Stack Changes**
- What technologies were added, removed, or swapped out?
- Why were changes made?

**Architecture Shifts**
- How did the structure evolve?
- What refactors happened and what triggered them?

**Key Integrations**
- External services/APIs added over time
- How integration approaches evolved

### 4. Problems & Solutions Log

| Problem Encountered | Solution Implemented | Date |
|---|---|---|
| ... | ... | ... |

Capture the debugging journeys and how issues were resolved.

### 5. Abandoned Approaches

What was tried and didn't work? This is valuable context for future work.

### 6. Current State Summary

- What's working
- What's in progress
- Known issues or tech debt

### 7. Session References

List key session IDs for deep-diving into specific phases:

- Initial setup: [session-id]
- Major refactor: [session-id]
- etc.

---

## Output

Present the final report directly to the user as formatted markdown. Do NOT write it to a file unless the user explicitly asks for that.
