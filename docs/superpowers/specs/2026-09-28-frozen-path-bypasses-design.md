# Closing the gate's frozen-path bypasses, and capping turn-end tests

**Status:** approved design, 2026-09-28. Engine: `scala-llm-template/kit/harness` (vendored into projects as `.dsh/engine/`).

## Context (measured)

- `tool-policy.sh` already denies operand-shaped writes naming a frozen path: `rm`, `mv`, `ln`, `truncate`, `patch`,
  `git checkout|restore|rm|mv|apply`, `perl -i`, `find -delete` / `find -exec rm`; the semantic guard covers redirections
  and `sed -i`-style writes. Its header states the limit: "Text, not shell semantics".
- What passes today: writes performed by an interpreter or a shell given inline code (`python3 -c`, `perl -e` without
  `-i`, `node -e`, `ruby -e`, `sh -c '…'`) and any path the shell computes (variables, globs, a script's own `cd`). The
  2026-09-28 pilot's live agent edited a `.scala` file through `python3`; the vericoding review measured that a
  `python3 -c` rewrite of a frozen `.dsh/.state` record is allowed.
- `turnEndTests` run without their own time cap before the vericoding stage; the Stop hook's 600 s timeout ends a turn
  with no verdict.

## Decisions

Both a post-hoc check (ground truth, route-independent) and a cheap text rule (early denial for the common literal case).
No automatic restoration of frozen files.

## §1 Post-hoc check at turn-end

Runs first in turn-end, before every other stage:

1. **Tracked and untracked frozen files** — from the set of paths this session changed (the existing session-baseline
   logic: clean at SessionStart and dirty now, or pre-dirty with a hash different from the baseline's), every path that
   matches a frozen pattern (plain rows, directory `**` rows, `sealed` rows) → **block**. Git reports edits, deletions and
   new files alike, so all three are covered.
2. **Git-ignored frozen files** — git cannot see them (`.dsh/.state/**` records: contract snapshots, receipts, the
   module map). SessionStart writes `.dsh/.state/frozen/<session>.tsv`: `<sha256> TAB <path>` for every existing
   git-ignored file matching a frozen pattern; always written (header-only if none; `incomplete: <reason>` when hashing
   fails); a second SessionStart keeps the first. It is itself frozen by `.dsh/.state/frozen/**`. At turn-end each
   recorded file is re-hashed: changed or missing → **block**; the record absent → **block** (always written, so absence
   means deletion); `incomplete:` → named note "frozen ignored paths not compared".
   Files the gate itself writes during the session (its own records, caches) are excluded by listing them explicitly.
3. **No frozen rows** → nothing is checked, nothing is recorded beyond a header-only file.

Block headlines: `frozen path changed this turn: <path>`, `frozen path removed this turn: <path>`,
`frozen record missing: <path>`. Remedy: the agent cannot restore a frozen file (writing it is what is denied) — "stop
and ask the human; `git diff -- <path>` shows what changed; the human restores or keeps it". The existing attempt cap
applies.

**Blind spot (documented):** an agent with arbitrary code execution can rewrite both a git-ignored frozen file and the
hash record. The record is frozen against Write/Edit and the text rules, not against an interpreter that computes its
path. Only a sandbox closes that.

## §2 Text rule in `tool-policy`

A Bash segment whose command word (the existing `command_position_words` parser) is `python`/`python3`/`perl`/`ruby`/
`node`, or `sh`/`bash`/`zsh`/`dash` with `-c`, or `awk` whose program contains `print >` / `printf >` / `system(` — is
**denied** when the full command text names a frozen path, literally or via a directory prefix of a frozen `**` row.
Heredoc bodies are part of the command text. The message gives the freeze's reason and says an interpreter can write
where the shell rules cannot see; for reading, use the Read tool (allowed). Not covered (documented; §1 catches them):
computed paths and interpreters run from a script file.

## §3 `turnEndTests` time cap

Each module's `turnEndTests` run goes through `dsh_bounded_probe` with cap `min(DSH_TESTS_CAP (default 300),
DSH_STOP_BUDGET (default 540) − elapsed)`. On expiry: kill, named note `turnEndTests: <m> did not finish within <N> s
— not verified`, no block. With no budget left (≤ 10 s), remaining modules are not started and are noted. The
vericoding stage keeps its existing budget rule (it runs after, on what is left). `DSH_TESTS_CAP` accepts decimals only,
leading zeros stripped, like the other knobs.

## §4 Testing and delivery

- Fixtures (engine `tests/cases`), test-first:
  - text rule: `python3 -c "open('<frozen>','w')…"`, `python3 - <<EOF … <frozen> … EOF`, `perl -e`, `node -e`,
    `ruby -e`, `sh -c '… > <frozen>'`, `bash -c`, `awk '{print > "<frozen>"}'` → denied; the same naming only unfrozen
    paths → allowed; `python3 script.py` whose text names no frozen path → allowed.
  - post-hoc: a frozen tracked file edited by a route the text rule does not see (the fixture's `post_git`) → block; a
    frozen file deleted → block; a new file under a frozen directory → block; pre-dirty frozen file untouched → no block,
    changed further → block; a frozen git-ignored `.dsh/.state` record changed → block; its hash record missing → block,
    `incomplete:` → note; no frozen rows → nothing.
  - cap: a sleeping stub over a small `DSH_TESTS_CAP` → note; budget exhausted → next module skipped with a note.
- `lib-test` rows for the new helpers; `check-harness`: when frozen rows exist, `.dsh/.state/frozen/**` must itself be
  frozen (FAIL otherwise); the new seed row in `seed/frozen-paths`.
- Delivery: one plan in scala-llm-template (subagent-driven); re-vendor into skreeep2 and add the frozen row there; a
  live gated check that the agent's `python3` edit of a frozen file is denied.

## Invariants

The gate never silently passes; frozen-path semantics only get stricter; reads stay allowed through the Read tool; the
gate's interface is unchanged.

## Out of scope

Sandboxing; the semantic guard's "edit `.scala` through the MCP write path" rule for non-frozen files (an interpreter
edit of an unfrozen `.scala` file remains possible — it is not a freeze); automatic restoration.
