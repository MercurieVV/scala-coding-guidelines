# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository purpose

This repo has two unrelated parts:

1. **A scala-cli single-file project** (`main.scala`, `project.scala`) — currently a placeholder (`Hello world!`).
2. **A karpathy-llm-wiki knowledge base** (`raw/`, `wiki/`) — a compounding, LLM-maintained wiki of Scala 3 / Typelevel / cats-effect architecture research. This is the actual content of the repo today.

## Scala project commands

Build tool is scala-cli (`project.scala` declares `//> using scala 3.9.0`), not sbt/mill — no `build.sbt` exists.

```bash
scala-cli run .        # run main.scala
scala-cli compile .    # compile only
scala-cli test .       # run tests (none exist yet)
```

## Knowledge base (raw/ + wiki/)

This is a `karpathy-llm-wiki`-skill-managed knowledge base: "the LLM writes and maintains the wiki; the human reads and asks questions." Two directories:

- **`raw/`** — immutable source material, one topic subdirectory deep (`raw/<topic>/<file>.md`). Never edit these files' content once written; each carries a metadata header (Source, Collected, Published).
- **`wiki/`** — compiled knowledge articles, one topic subdirectory deep (`wiki/<topic>/<article>.md`). `wiki/index.md` is the global index (one row per article); `wiki/log.md` is an append-only operation log.

Currently one topic: `scala-typelevel-fp` — Scala 3 + Typelevel/cats-effect architecture conventions (tagless-final, hand-wired `Resource` DI, opaque types, cats category-theory patterns, module/testing layout). Start at `wiki/index.md`.

**Grounding invariant:** every number, date, and direct quote in a `wiki/` article must exist verbatim in the `raw/` file(s) linked in that article's `Raw:` field. Verify with:

```bash
python3 ~/.claude/skills/karpathy-llm-wiki/scripts/check_evidence.py .
```

Use the `karpathy-llm-wiki` skill (not ad hoc edits) for all wiki operations — ingesting new sources, querying, archiving answers, and linting. It defines the exact file formats, triage rules, and cascade-update behavior; the templates are in `~/.claude/skills/karpathy-llm-wiki/references/`.
