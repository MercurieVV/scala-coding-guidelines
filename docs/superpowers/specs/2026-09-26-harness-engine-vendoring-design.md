# The harness engine in the template, vendored per project (Reengineering Kit Plan C, v2)

**Status:** approved design, 2026-09-26. Supersedes the architecture of
`docs/2026-09-19-reengineering-kit-plan-c-harness.md` (the engine on the DSH profile). Amends the
Reengineering Kit design's §L4: the engine's home is `scala-llm-template/kit/harness/`, vendored into each
project — not the `scala-arhitect` profile.

## Why the 2026-09-19 plan no longer fits

Measured on 2026-09-26 against skreeep2 `0832f92`:

- **The engine grew.** ~7,800 lines of POSIX sh (`gate.sh`, `lib.sh`, `verify-diff.sh`, `module-map.sh`,
  `check-harness.sh`, `instructions-sync.sh`, seven hooks) and 332 fixture cases. `verify-diff.sh`,
  `policy/*.tsv` and the rewrite stage postdate the plan.
- **Its Task 2 already happened in place.** `dsh_contract_get` / `dsh_contract_for_module` live in skreeep2's
  `lib.sh`; the contract carries `holdout`, `fastTests`, `turnEndTests` beyond the plan's keys.
- **The engine calls Mill tasks by name**, not through the contract: `moduleMap`, `<mod>.fixRanges`,
  `<mod>.fixStrict`, `<mod>.compile`, `preCommit`, `prePush`, `scalaSemanticWriteClasspath`. They live only in
  skreeep2's `buildKit/Layer.mill` and `build.mill`; the template's `kit/recipes/mill` has none of them.
- **The direction of truth is the reverse of the plan's.** skreeep2's `.dsh/bootstrap.sh` generates
  `~/.dsh/profiles/scala-arhitect` from the committed `.dsh/profile/`; the repository is the source.
- **Claude Code never passes through the profile.** Its wiring is `.dsh/cc-settings.json` →
  `.claude/settings.json` → `$CLAUDE_PROJECT_DIR/.dsh/gate.sh`, so the plan's `${GATE_ENGINE:-vendor}` would
  resolve to the vendored copy in every Claude Code session — the "fallback" would be the main path.
- `gate_root` resolves `GATE_ROOT` → `CLAUDE_PROJECT_DIR` → git toplevel → `PWD`, independent of where the
  engine file sits, so the engine can move into a subdirectory without path surgery.

## §1 Layout and wiring

**Template — `scala-llm-template/kit/harness/`, the only place the engine is edited:**

- `gate.sh`, `lib.sh`, `verify-diff.sh`, `module-map.sh`, `check-harness.sh`, `instructions-sync.sh`,
  `hooks/*.sh`
- `tests/` — `run.sh`, `lib-test.sh`, `cases/`, `bin/` stubs
- `README.md` — engine doc: contract keys, subs, the notes table, blind spots, the Mill interface
- `install.sh <project>` — the vendoring script (§2); `Setup.scala`'s `kit` feature calls it

**Project — `.dsh/`, state plus the vendored engine:**

- `engine/` — the vendored copy, with `engine/STAMP`
- `contract`, `frozen-paths`, `policy/*.tsv`, `instructions/*.tsv`, `skills/`, `.state/`
- `profile/`, `bootstrap.sh`, `dsh.sh` — DSH-specific, unchanged
- `hooks.json`, `cc-settings.json` — every command is `sh "$CLAUDE_PROJECT_DIR/.dsh/engine/gate.sh" <sub>`
- `README.md` — project doc (its contract, freezes, policies), linking to the engine README
- `tests/` — project-only suites (e.g. `wiring-test.sh`, which reads this project's wiring files)

**Split rule:** what the engine reads at a fixed path under `.dsh/` (contract, frozen-paths, policy,
instructions, `.state`) is project state; everything executable is engine. `instructions/purrism.tsv` is
project state — its version follows the project's purrism dependency.

**Frozen:** the project's `frozen-paths` includes `.dsh/engine/**`; the vendored engine is never edited in
place by an agent.

**Dropped from the 2026-09-19 plan:** the profile as engine home (Task 1's profile repository stays, but
carries no engine); skills loaded from the profile (Task 4 — projects load `.dsh/skills` at rank 100 and Plan
B's installer vendors them); `GATE_ENGINE` and the fallback indirection — there is one path.

## §2 Vendoring, update, drift

**`sh kit/harness/install.sh <project>`**, run from a template checkout:

- Replaces `<project>/.dsh/engine/` wholesale: built in a temporary directory beside it, then renamed into
  place. No merge; no stale files survive.
- Writes `engine/STAMP`, three `key=value` lines: `template=<sha>`, `dirty=<0|1>` (the template's
  `kit/harness` had uncommitted changes), `tree=<hash>` (sha256 over the sorted relative file list and each
  file's content, STAMP excluded).
- Seeds project state **only when absent, never overwriting**: `contract`, `frozen-paths` (containing
  `.dsh/engine/**`), `policy/*.tsv`, `instructions/local.tsv`, `instructions/scalac-kinds.tsv`, `hooks.json`,
  `cc-settings.json`. Prints one line per file: `seeded`, `kept`, or `replaced` (the engine only).
- Exits non-zero, naming the cause, when the target is not a git repository, the template has no
  `kit/harness`, or no sha256 hasher exists.
- `Setup.scala kit` runs it as its own step; that step's JSON `status` reflects `install.sh`'s exit.

**Drift — `check-harness.sh` (a host command):**

| Condition | Result |
|---|---|
| engine tree hash ≠ `STAMP.tree` | FAIL: "vendored engine edited in place; edit in scala-llm-template/kit/harness and re-run install.sh" |
| `STAMP` missing | FAIL |
| `dirty=1` | note: vendored from an uncommitted template |
| `SCALA_LLM_TEMPLATE=<checkout>` set and its engine hash ≠ vendored | note: the template has moved ahead |

**Health (SessionStart):** a missing or mismatching STAMP is a `dnote` — "vendored engine does not match its
stamp" — never a block. The gate still runs, and says so.

**Engine workflow:** edit `kit/harness` in the template; run `sh kit/harness/tests/run.sh` there; commit;
`install.sh ../skreeep2`; commit the vendored copy in the project as `dsh: vendor engine <sha>`.

**Tests:** engine fixtures are self-contained (each case sets `GATE_ROOT` to its own root) and run in the
template. Project suites stay in the project. `check-harness.sh` runs `engine/tests` and the project's
`tests/` when present.

## §3 Build side — the Mill recipe

The engine supports Mill only. These task names are the engine's documented **Mill interface** (not contract
keys): `moduleMap`, `<mod>.fixRanges --rangesFile <path>`, `<mod>.fixStrict`, `<mod>.compile`, `preCommit`,
`prePush`, `scalaSemanticWriteClasspath`. The contract's existing command lines are unchanged.

**`kit/recipes/mill/Gate.mill`** — copied into a project's `buildKit/` beside `ArchitectureShapeModule.mill`:

- `trait GateLayer extends ScalafixModule` — `fixStrict` and `fixRanges`, moved verbatim from skreeep2's
  `Layer.mill`. A project's layer trait mixes it in; the project's own scalafix rule dependencies stay in the
  project's trait.
- `object Gate` — pure helpers: `moduleMapRows(root, main, scalafix, testCompile)` (today's tree walk and
  capability logic) and `ClasspathMetadata` (moved verbatim).

**Root tasks as recipe fragments** — a new section of `kit/recipes/mill.md`, "The gate's tasks": `preCommit`,
`prePush`, `moduleMap`, `scalaSemanticWriteClasspath`, each a few lines delegating to `Gate` over the
project's own module lists (`mainModules`, `scalafixModules`, `testCompileModules`, the pre-push test list).
Fragments, not a trait: the lists encode project decisions (e.g. a module deliberately exempt from scalafix),
and whether a Mill 1.x root module can extend a trait is not assumed — the plan measures it.

**skreeep2 consumes the kit's copy**, so one `fixRanges` exists: `Layer.mill` mixes in `GateLayer` and drops
its own `fixStrict`/`fixRanges`; `build.mill`'s `moduleMap` and `scalaSemanticWriteClasspath` delegate to
`Gate`. Acceptance: `moduleMap` output byte-identical to the pre-change capture; `preCommit` green.

**Non-Mill projects:** no `./mill` and no `build.mill` → `health` adds a `dnote`: "gate engine supports Mill
builds only; found: <build.sbt | project.scala | none> — checks stand down". Never silent, never a block. The
existing turn-end block for a Mill project missing its wrapper is unchanged.

## §4 Migration order and verification

Three repositories; each step is one commit per repository and independently revertible.

1. **Template — import the engine verbatim** from skreeep2 `.dsh/` at `0832f92` into `kit/harness/` (commit
   message names the source sha). Gate: `sh kit/harness/tests/run.sh` and `dash kit/harness/tests/run.sh`
   green over every case, and `lib-test.sh` green, run in the template. A fixture that reads skreeep2 files
   is either made self-contained or moved to the project suite, by recorded ruling.
2. **Template — `install.sh`, STAMP, drift checks, the non-Mill note**, test-first. New fixtures: install
   into an empty repository (seeds correct); reinstall (state kept, engine replaced); engine edited in place
   (check-harness FAIL); STAMP missing (FAIL); `dirty=1` (note); an sbt project (non-Mill dnote).
   `Setup.scala kit` calls `install.sh`.
3. **Template — `Gate.mill` and the recipe section**, compile-checked the way Plan B's recipe checks are
   (the plan measures which).
4. **skreeep2 — first consumer**, one commit `dsh: vendor engine <sha>; project keeps state`: delete the
   engine files from `.dsh/`, run `install.sh`, update `hooks.json` and `cc-settings.json`, add
   `.dsh/engine/**` to `frozen-paths`, split the README, move project-only suites, and apply the
   `Layer.mill`/`build.mill` delegation. Gates: host `check-harness.sh` green (engine and project suites);
   `moduleMap` byte-identical; `./mill -j 1 preCommit` exit 0; live Claude Code session in skreeep2 — a
   `.scala` Read denied, a Write to a frozen path denied, a dirty turn-end runs the checks.
   **Human step:** `bootstrap.sh` installs `.claude/settings.json` only when absent, and the agent may not
   edit that file; the human replaces it from the new `cc-settings.json` with a command the plan gives.
5. **Second-project smoke** — `kit/harness/tests/smoke-second-project.sh`, re-runnable: `Setup.scala kit` on
   a scratch one-module Mill project; a `.scala` Read denied with a healthy index, stand-down with an unhealthy
   one, the declared `preCommit` runs, a path outside the coverage map stands down by name, a module without
   `tests` never has tests claimed; an sbt scratch project gets the non-Mill dnote.
6. **Docs** — `kit/harness/README.md` (engine doc, full notes table, contract, Mill interface);
   `kit/apply.md` gains the harness step; the 2026-09-19 Plan C marked superseded by this spec; the kit
   design's §L4 amended; wiki `verification-and-the-gate` and the roadmap updated via the karpathy-llm-wiki
   skill.

## Invariants

- The gate's interface is unchanged: `sh <engine>/gate.sh <sub>`, payload on stdin, exit 0 allow / 2 block,
  the six subs, both wiring dialects.
- The gate never silently passes; every new condition here is a FAIL (check-harness) or a named `dnote`.
- Frozen-path semantics are not weakened; reads of frozen paths stay allowed.
- Engine behaviour changes only by: the STAMP/health note, the non-Mill note, and path moves.
- `check-harness.sh` stays a host command.

## Out of scope

sbt / scala-cli engine support; profile-loaded skills; the item-6 follow-ups (atomic purrism rewrites, the
`git stash create` baseline, the `fixRanges` restore fixture); any other gate behaviour change.

## Risk

Fixtures with hidden skreeep2 paths — step 1's gate runs in the template before anything is deleted from
skreeep2, so they surface while the old engine still works.
