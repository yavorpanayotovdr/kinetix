---
name: Commit granularity
description: User wants smaller, incremental commits rather than one large commit per feature
type: feedback
---

When implementing a multi-step plan or large feature, commit incrementally rather than as a single monolithic commit. Break commits by logical layer or step — e.g. backend models, then services, then routes, then UI types, then UI components, then tests. Each commit should be a coherent unit that compiles and passes tests on its own.
