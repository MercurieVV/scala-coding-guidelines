# Reengineering Kit, Plan C — the harness in the `scala-arhitect` profile

> **Superseded (2026-09-26)** by docs/superpowers/specs/2026-09-26-harness-engine-vendoring-design.md and scala-llm-template docs/superpowers/plans/2026-09-26-harness-engine-vendoring.md. The engine's home is scala-llm-template/kit/harness, vendored per project; not the DSH profile.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the harness from a repository-local script collection into a profile-carried engine plus a per-project **build contract**, so any project can get the same gate — and `skreeep2` becomes its first consumer.

**Architecture:** The engine (`gate.sh`, `lib.sh`, the six hooks, `check-harness.sh`, the semantic guard) moves into the `scala-arhitect` profile; a project keeps only its own state (`hooks.json` as a thin pointer, `frozen-paths`, and a new `contract` file declaring its task names, its coverage-map command and its source roots). The gate stops knowing Mill: it runs declared commands and refuses to claim coverage the project's map does not grant. The template keeps a vendored fallback engine for sessions launched without the profile.

**Tech Stack:** POSIX `sh` (the harness is already `dash`-clean by test), the DSH profile patch file (`~/.dsh/profiles/scala-arhitect/cordis.patch.yml`), Mill for the first consumer's contract, `scala-semantic-mcp` for the read guard.

**Spec:** `scala-coding-guidelines/docs/2026-09-19-reengineering-kit-design.md` (§L4 and the build contract) and the compiled `wiki/reengineering/verification-and-the-gate.md` (R-8, R-9). The harness's own conventions are in `.dsh/README.md`.

## Global Constraints

- **The profile directory is not under version control** (`~/.dsh/profiles/scala-arhitect/` has no `.git`). Task 1 fixes that before anything moves; a change to the engine that cannot be diffed or reverted is the failure this plan exists to avoid.
- **The gate's interface does not change:** `sh <gate> <sub>` with the hook payload on stdin, exit 0 to allow and 2 to block; the same six subs (`health`, `baseline`, `tool-policy`, `semantic-guard`, `post-edit`, `turn-end`). Both wiring dialects (DSH's lowercase names, Claude Code's) keep working.
- **`check-harness.sh` is a host command** — measured: under a session whose own policy is `workspace-write`, composing the profile fails with `EPERM` on `~/.dsh/profiles/…/cordis.yml`. The plan does not try to make it run inside a confined session; it records the requirement and keeps the check where a host shell runs it.
- **The frozen-path semantics are not weakened:** a committed freeze cannot be lifted by a machine-local file, malformed entries fail the check rather than being skipped, and the documented blind spots stay named.
- **No source file is frozen** by this plan: `skreeep2/.dsh/**` is the human's harness, and every edit to it is path-limited and reviewed.
- **Reads stay allowed** for frozen paths, and the `# semantic-fallback:` marker keeps releasing reads only.

## File Structure

| File | Responsibility |
|---|---|
| `~/.dsh/profiles/scala-arhitect/harness/gate.sh`, `lib.sh`, `hooks/*`, `check-harness.sh`, `tests/` | the engine, moved verbatim except for contract indirection |
| `~/.dsh/profiles/scala-arhitect/harness/contract.sh` | reads a project's `.dsh/contract` and resolves its declared commands |
| `~/.dsh/profiles/scala-arhitect/skills/*/SKILL.md` | the kit's skills, loaded from the profile |
| `<project>/.dsh/hooks.json` | a thin pointer at the profile's engine |
| `<project>/.dsh/contract` | task names, coverage-map command, source roots |
| `<project>/.dsh/frozen-paths` | unchanged semantics |
| `scala-llm-template/kit/harness/` | the vendored fallback engine for sessions without the profile |
| `.dsh/README.md` (skreeep2) | split: project-state docs here, engine docs in the profile |

---

### Task 1: Put the profile under version control, and snapshot the engine

**Files:**
- Create: `~/.dsh/profiles/scala-arhitect/.gitignore`, and a git repository at that path
- Modify: nothing else

**Interfaces:**
- Consumes: nothing.
- Produces: a repository whose first commit is the profile exactly as it runs today — the base every later task diffs against.

- [ ] **Step 1: Init and commit the profile as it stands**

```bash
P=~/.dsh/profiles/scala-arhitect
cd "$P" && git init -q . && printf 'node_modules/\n.dsh-module-fallback/\n' > .gitignore
git add -A && git commit -q -m "profile: the scala-arhitect profile as it runs today, before the harness moves in"
git log --oneline -1 && git status --short
```

- [ ] **Step 2: Verify the profile still composes**

Run: `dsh --profile scala-arhitect --dump-config >/dev/null && echo OK`
Expected: `OK` (run from the repository root; a confined session cannot compose it — that is the recorded gap, not a failure of this step).

- [ ] **Step 3: Commit** — nothing further; Step 1's commit is the deliverable.

---

### Task 2: Define the build contract, and read it in `lib.sh`

**Files:**
- Create: `~/.dsh/profiles/scala-arhitect/harness/contract.sh`
- Modify: `~/.dsh/profiles/scala-arhitect/harness/lib.sh` (the Mill-specific lookups become contract calls)
- Create (in the first consumer): `skreeep2/.dsh/contract`

**Interfaces:**
- Consumes: the engine's current Mill assumptions (`./mill moduleMap`, `<module>.checkFormat`, `preCommit`, `prePush`).
- Produces: the contract file format, and `contract_command <key>` / `contract_coverage <path>` helpers every later task calls.

- [ ] **Step 1: Write the contract format down, and the file for this repository**

`.dsh/contract` is one `key = value` per line, `#` comments allowed:

```
# The project's gate interface. The engine never learns a build tool's name: it runs these.
preCommit  = ./mill -j 1 preCommit
prePush    = ./mill -j 1 prePush
checkFormat = ./mill -j 1 --ticker false <module>.checkFormat
fix        = ./mill -j 1 --ticker false <module>.fix --check
coverage   = ./mill -j 1 --ticker false moduleMap
roots      = .
```

`<module>` is substituted by the engine from the coverage map's module id.

- [ ] **Step 2: Write `contract.sh` and make `lib.sh` use it**

`contract.sh` (POSIX `sh`, sourced by `lib.sh`) exposes: `contract_file` (the project's `.dsh/contract`, in `GATE_ROOT`), `contract_get <key>` (prints the value or fails with the missing key named), and `contract_coverage <changed-path>` (runs the `coverage` command, caches it the way `module-map.sh` caches `modules.tsv`, and prints `<module id>` + capabilities, or `-`). Every `./mill …` literal in `lib.sh` becomes a `contract_get`, and the coverage lookup calls `contract_coverage`.

- [ ] **Step 3: Prove the indirection is real**

Run, with `GATE_ROOT` pointed at `skreeep2`: `sh -c '. harness/lib.sh; contract_get preCommit'`
Expected: the exact command string from `.dsh/contract`.

Run: `sh -c '. harness/lib.sh; contract_coverage src/com/github/mercurievv/skreeep2/engine/Topology.scala'`
Expected: `engine.shape<TAB>fmt,scalafix,test` (the same answer `module-map.sh` gives today).

- [ ] **Step 4: Commit**

```bash
git add harness/contract.sh harness/lib.sh && git commit -m "harness: the build contract — the gate runs declared commands, not Mill's"
```

---

### Task 3: Move the engine into the profile

**Files:**
- Create: `~/.dsh/profiles/scala-arhitect/harness/{gate.sh,lib.sh,hooks/*,check-harness.sh,tests/*}`
- Modify: `~/.dsh/profiles/scala-arhitect/cordis.patch.yml` (the `hooks-project` row's `configPath` stays `<project>/.dsh/hooks.json`)
- Modify: `.dsh/hooks.json` (its commands become the profile's engine path, with a vendored fallback)

**Interfaces:**
- Consumes: Task 2's contract helpers.
- Produces: one engine, on the profile; projects hold state only.

- [ ] **Step 1: Copy the engine verbatim, then fix its own paths**

```bash
S=/Users/viktorskalinins/IdeaProjects/skreeep2/.dsh
P=~/.dsh/profiles/scala-arhitect/harness
mkdir -p "$P/hooks" "$P/tests"
cp "$S/gate.sh" "$S/lib.sh" "$S/check-harness.sh" "$P/"
cp "$S/hooks/"*.sh "$P/hooks/"
cp -R "$S/tests/." "$P/tests/"
```

Then replace every `$here/…`-relative assumption that named project files with `$GATE_ROOT/…` (the engine keeps `GATE_ROOT` = the repository the gate runs for; `check-harness.sh` gains an explicit `--project <dir>` argument, defaulting to `$GATE_ROOT`).

- [ ] **Step 2: Point the project's wiring at the engine, with the fallback**

`.dsh/hooks.json` in a project becomes:

```json
{
  "SessionStart": [ { "hooks": [ { "type": "command", "command": "sh \"${GATE_ENGINE:-$CLAUDE_PROJECT_DIR/.dsh/vendor}/gate.sh\" health", "timeout": 120 } ] } ],
  "PreToolUse": [ { "matcher": "bash", "hooks": [ { "type": "command", "command": "sh \"${GATE_ENGINE:-$CLAUDE_PROJECT_DIR/.dsh/vendor}/gate.sh\" tool-policy", "timeout": 30 } ] } ],
  "PostToolUse": [ { "matcher": "write|edit", "hooks": [ { "type": "command", "command": "sh \"${GATE_ENGINE:-$CLAUDE_PROJECT_DIR/.dsh/vendor}/gate.sh\" post-edit", "timeout": 600 } ] } ],
  "Stop": [ { "hooks": [ { "type": "command", "command": "sh \"${GATE_ENGINE:-$CLAUDE_PROJECT_DIR/.dsh/vendor}/gate.sh\" turn-end", "timeout": 600 } ] } ]
}
```

with the same matcher set the file has today (`read|grep|glob|bash|write|edit` for `semantic-guard`, and the MCP matcher). `GATE_ENGINE` is set by the profile to `~/.dsh/profiles/scala-arhitect/harness`; `.dsh/vendor/` is the fallback the installer copies from `scala-llm-template/kit/harness/` (Task 6).

- [ ] **Step 3: Verify the gate still runs from the profile**

In a live session launched by `.dsh/dsh.sh`: attempt a `.scala` text read (expect the guard's refusal), attempt a write to a path listed in `.dsh/frozen-paths` (expect the denial naming the reason), and end a turn dirty (expect `mill preCommit` to run). Record all three in the report.

- [ ] **Step 4: Commit**

```bash
git add harness cordis.patch.yml && git commit -m "harness: the engine lives on the profile; a project keeps only its state"
```

---

### Task 4: The skills load from the profile

**Files:**
- Create: `~/.dsh/profiles/scala-arhitect/skills/*/SKILL.md` (the kit's five, from `scala-llm-template/kit/skills/`)
- Modify: `~/.dsh/profiles/scala-arhitect/cordis.patch.yml` (the `skill-filesystem` row's `customSkillDirs`)

**Interfaces:**
- Consumes: Plan B's `kit/skills/`.
- Produces: the same five skills available in any project the profile runs in.

- [x] **Step 1: The loader, established 2026-09-19 from the installed tree** (was this plan's one unknown; do not re-derive it):

  - `<projectRoot>/.dsh/skills` is scanned by DSH's own `@deepseek-ai/dsh-skill-filesystem` provider at **rank 100** (`project-dsh`). That is what loads skreeep2's six vendored skills today — with plain names and no profile configuration at all.
  - The `cc-skills` row is **not** that loader: `dsh-cc-loader` reads `<projectRoot>/.claude/skills` (rank 150) and `~/.claude/skills` (rank 160, which this profile turns off), plus `pluginRoots` (rank 170, where names come back namespaced as `plugin-<plugin>-<name>`). skreeep2 has no `.claude/skills`.
  - The same filesystem provider takes **`customSkillDirs`** — "additional local skill roots, after project roots and before user roots", i.e. **rank 300** — and `@deepseek-ai/dsh-base` already mounts that row as `id: skill-filesystem` with no config. So this is a config patch on an existing row, exactly like this profile's `cc-skills` and `sandbox-policy` rows.
  - Consequence for Step 3: rank 300 coming after rank 100 *is* the precedence — a project's own `.dsh/skills/<name>` wins over the profile's copy because the provider ranks say so, not because anything is arranged. Frontmatter is validated on load (`name` kebab-case, `description` required), which is the same assertion `check-harness.sh` group F makes for vendored skills.

- [ ] **Step 2: Patch the existing `skill-filesystem` row** to add the profile's root. Leave the `cc-skills` row's `enableGlobal: false` alone — that governs `~/.claude`, a different layer. Do **not** route the kit's skills through `pluginRoots`: that path renames them.

```yaml
# ── Skills: the kit's own, in every project this profile runs ──────────────
# Rank 300 — after the project's .dsh/skills (100), before ~/.dsh/skills (400).
- id: skill-filesystem
  config:
    customSkillDirs:
      - !!js process.env.HOME + '/.dsh/profiles/scala-arhitect/skills'
```

- [ ] **Step 3: Verify in a live session** that all five skills are listed and loadable, and that a project-local copy under `.dsh/skills/` still wins when both exist.

- [ ] **Step 4: Commit**

```bash
git add skills cordis.patch.yml && git commit -m "profile: the kit's five skills load from the profile, project copies still win"
```

---

### Task 5: `skreeep2` becomes the first consumer

**Files:**
- Modify: `skreeep2/.dsh/hooks.json`, `skreeep2/.dsh/README.md`
- Delete: `skreeep2/.dsh/{gate.sh,lib.sh,check-harness.sh,hooks/,tests/,module-map.sh}` (they now live on the profile; `module-map.sh`'s job is the contract's `coverage` command)
- Create: `skreeep2/.dsh/contract` (Task 2's file) and `skreeep2/.dsh/vendor/` (the fallback engine)

**Interfaces:**
- Consumes: Tasks 2–4.
- Produces: a project whose `.dsh/` holds only `hooks.json`, `contract`, `frozen-paths` and `vendor/`.

- [ ] **Step 1: Adapt the project state, then delete the copied engine**, in one commit whose message says which files moved where.

- [ ] **Step 2: Verify on the host** (not inside a confined session):

```bash
sh ~/.dsh/profiles/scala-arhitect/harness/check-harness.sh --project /Users/viktorskalinins/IdeaProjects/skreeep2
./mill -j 1 preCommit
```

Expected: the harness check green (the same suites, now run against the project's state), `preCommit` exit 0.

- [ ] **Step 3: Verify the freeze semantics survived** — with a synthetic entry in `.dsh/frozen-paths`, a write to that path is refused with the reason; a malformed entry (no reason) fails the check rather than being skipped.

- [ ] **Step 4: Commit** (path-limited to `.dsh/`)

```bash
git add .dsh && git commit -m "dsh: the project keeps its state; the engine moved to the profile"
```

---

### Task 6: The vendored fallback, and a second-project smoke test

**Files:**
- Create: `scala-llm-template/kit/harness/` (the engine copy the installer writes to `<project>/.dsh/vendor/`)
- Modify: `scala-llm-template/Setup.scala` (the `kit` feature also installs `vendor/`)

**Interfaces:**
- Consumes: Task 3's engine; Plan B's installer.
- Produces: a project that is gated with or without the profile.

- [ ] **Step 1: Vendor the engine** — copy `harness/` into `kit/harness/` (one more copy, deliberately: the fallback must exist for sessions that never load the profile), and tell the installer to write it to `.dsh/vendor/`.

- [ ] **Step 2: Smoke-test a second project** — a scratch Mill project with `.dsh/contract` (its own `coverage` command, e.g. `./mill --ticker false resolve _`), the vendored engine, and one module: confirm the gate denies a `.scala` text read when the index is healthy, stands down with the module and its compile command when it is not, and runs the declared `preCommit`.

- [ ] **Step 3: Confirm the coverage refusal** — a changed path outside the coverage map makes the gate stand down naming the module, and a module whose capabilities lack `test` never has tests claimed for it.

- [ ] **Step 4: Commit** in the template repository.

```bash
git add kit/harness Setup.scala && git commit -m "kit(harness): the vendored fallback engine, and the installer writes it"
```

---

### Task 7: Document the split

**Files:**
- Modify: `skreeep2/.dsh/README.md` (project-state only, pointing at the profile for the engine)
- Create: `~/.dsh/profiles/scala-arhitect/harness/README.md` (the engine's own doc: the contract, the subs, the blind spots)
- Modify: `scala-coding-guidelines/docs/technical-goal/CAMPAIGN.md` (the harness carried gaps: the sandbox finding and the engine's new home)

**Interfaces:**
- Consumes: everything above.
- Produces: the record that survives the session (R-12).

- [ ] **Step 1: Split the README** — move the engine sections verbatim into the profile's copy, leave the project-state sections (frozen paths, the contract, the two commands) in the project, and cross-link both ways.

- [ ] **Step 2: Update the campaign's carried gaps** with: the harness engine's home; the `check-harness.sh` host requirement (already recorded — confirm the wording matches what this plan actually did); and the vendored fallback's existence.

- [ ] **Step 3: Commit** in both repositories, path-limited.

## Self-review notes

- **Spec coverage:** design L4 (the gate as a runner over a build contract; engine in the profile; project state in `.dsh/`) → Tasks 2, 3, 5; the skills half of L3 → Task 4; the design's "gate availability must not regress" (the vendored fallback) → Task 6; the design's cross-repo-write constraint → every task's path-limited commit; the sandbox finding → the Global Constraints and Task 7.
- **Deliberately not here:** any change to the frozen-path rules themselves, the semantic guard's index coverage work, and the harness's own test suites beyond keeping them green — those are the harness's existing campaign items.
- **The one unknown was resolved before execution, not during it:** Task 4 Step 1 read the installed loader and the `dsh-base` row list, so the plan now names `skill-filesystem` + `customSkillDirs` (rank 300) as the mechanism and states why the project copy still wins. It was the only guess in this plan; the rest is paths, keys and commands taken from files that exist.
- **Placeholder scan:** every path, sub, key and command is exact; the engine is copied by commands rather than transcribed; the contract file is given in full.
- **Known risk, stated rather than hidden:** moving `lib.sh`'s Mill literals behind the contract may surface assumptions that only a second project's build exposes. Task 6's smoke test is the net, and a failure there is a contract-format fix, not a rewrite of the engine.
