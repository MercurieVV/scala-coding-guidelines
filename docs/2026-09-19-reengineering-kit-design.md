# The reengineering kit — design (2026-09-19)

**Status:** design, not yet implemented. Follows a session in `skreeep2` (2026-09-17…19) that fixed a real bug and
closed two partialities, and whose *method* — not its code — is what should transfer to other projects.

**Purpose:** when a Scala project needs reengineering (narrowing modules toward an abstract level, or removing a
partiality the types should have prevented), the kit gives that project rules, enforcement, process skills, a gate and
a verification lane — each in its own layer, none of them a framework.

## What this is not

- Not a new harness. The gate is the existing `scala-arhitect` profile plus a thin per-project `.dsh/`.
- Not a monolith. There is no "adopt the kit" step that pulls everything in; the layers are usable separately.
- Not a publishing project (yet). The shape rule is vendored per project in v1; see *L2*.
- Not a claim that a green gate means a correct design; see *the boundary rule*.

## The layers

| # | Layer | Artifact | Home | Interface it exposes |
|---|---|---|---|---|
| L1 | Rules | prose rules with stable `R-n` IDs (below), the two-level shape, the migration record | this repository's `wiki/`, compiled from `raw/` material | `R-n` identifiers, one sentence each, citable |
| L2 | Enforcement | the `AllowedShape` rule (allowlist of types + denylist of constructs), its registration and build wiring | canonical copy in `scala-llm-template`; vendored per project | config keys `allowedTypes`, `disallowedConstructs`; the rule name `AllowedShape` |
| L3 | Process | four skills: `shaping-a-module`, `reengineering-module`, `reshaping-module-by-linters`, `verify-before-done` | canonical copy in the `scala-arhitect` profile; a pinned copy per project | skill names + the `AGENTS.md` digest block |
| L4 | Harness | the gate (`gate.sh`, `lib.sh`, hooks, `check-harness.sh`, the semantic guard) as a runner over a build contract | engine in the `scala-arhitect` profile; project state in `.dsh/` | the build contract (below) |
| L5 | Verification | the lane protocol: a fresh read-only agent, the diff, the `R-n` list, a verdict per rule | ships with L3 | consumes L1 and L4; produces receipts |

**Amended 2026-09-26 (L4).** The harness engine's home is no longer the `scala-arhitect` profile: it is
`scala-llm-template/kit/harness`, vendored per project as `.dsh/engine/` by `install.sh` with a verifiable `STAMP`,
and project state stays in `.dsh/` (spec `docs/superpowers/specs/2026-09-26-harness-engine-vendoring-design.md`;
scala-llm-template plan `docs/superpowers/plans/2026-09-26-harness-engine-vendoring.md`). Why: Claude Code never
passes through the profile — its wiring runs the project's own `.dsh/` copy — and the profile is generated from the
repository, so the repository was already the source of truth. The engine supports Mill only and calls a
documented set of Mill task names (recipe `kit/recipes/mill.md` §7), which amends interface 2's "the gate never
learns their names" for Mill.

### Two interfaces keep the layers independent

1. **Rule IDs (L1 → L2/L3/L5).** Each rule has a stable identifier and a one-line statement. The scalafix config, the
   skills and the lane's verdicts cite IDs; a rule changes by adding or superseding an ID, never by silently editing
   prose that machinery believes it implements.
2. **The build contract (L4 → any project).** A project declares three task names — `preCommit`, `prePush`,
   `checkFormat` — a **coverage map** (which module is formatted, linted, compiled, tested), and its source roots. The
   gate runs declared tasks only and refuses to claim coverage the map does not grant. Mill gets an implementation
   first; sbt and scala-cli get recipes; the gate never learns their names.

## The rules (first pass — `R-n`)

Generalized from `skreeep2`'s contract (C-1…C-11) plus what this session's work added. The wiki articles are the
canonical text; this list is the mapping they must carry.

| ID | Rule | Origin |
|---|---|---|
| R-1 | Flow is composition: arrows, not ad-hoc plumbing. | C-1 |
| R-2 | The abstract level names no concrete type; it reaches outside its package only through an explicit allowlist. | C-2 |
| R-3 | Nothing imperative where the type system can state it. | C-3 |
| R-4 | A component *names* a shape; it does not spell one. | the shape rule |
| R-5 | Absence is unrepresentable, not checked: a non-empty structure in the type beats a runtime guard. | F-2, the `Tree` closure |
| R-6 | A contract is stated on the abstract member, and every implementation is total against it. | F-2 |
| R-7 | Compiler settings are contract, not style — lowering `-Werror`, strict equality or the fatal warts to pass is a violation. | C-11 |
| R-8 | No completion claim without the gate's own output, and the gate must *run* tests, not only compile them. | C-10, the I4 finding |
| R-9 | Verification is independent: a fresh read-only lane judges the diff against these IDs, not against the author's summary. | this session |
| R-10 | A finding that cannot be fixed honestly is escalated, never suppressed — no `@SuppressWarnings`, no allowlist widening, no threshold bump. | the linter skill |
| R-11 | State the decidable/judgement boundary, and never let a machine-decidable rule stand in for a design judgement. | this session |
| R-12 | The record travels with the code: docs amended as the shape moves, stale claims corrected, open divergences written down as rulings. | this session's doc work |
| R-13 | One module at a time, and a reshape is declared deliberate by an explicit target list rather than by whatever a diff happened to touch. | C-4/C-5, the strictness dial |
| R-14 | Code is read and searched semantically (symbol-level tools), not textually; text search on sources is the exception, with a reason. | C-9 |

### The boundary rule (R-11), stated plainly

Machine-decidable, and therefore L2's business: a concrete type outside the allowlist; an imperative construct;
unformatted code; a red compile; a lint finding; a test that does not run.

Judgement, and therefore L1/L3/L5's: is this the right split; does this component name a shape rather than spell one;
is this contract honest; is this divergence a fix or a design change. The kit says so where an agent is most tempted
to stop — at a green gate.

## L2 in v1: vendored, not published

The rule is 171 lines, config-driven, with no project paths in it; the *policy* (base allowlist, construct denylist,
own-package prefix) lives in each project's build. So:

- The canonical copy (rule + `META-INF/services` registration + the build-side wiring) ships from the template and is
  written into a project with a version marker. A project may tune its own copy — that is the point of vendoring.
- **Policy stays in the project's build**, never inside the rule file. This is the property that makes a later swap a
  dependency change rather than a rewrite.
- **Frozen interface:** the config keys `allowedTypes` / `disallowedConstructs` and the rule name `AllowedShape`.
- **Trigger to extract** into its own scalafix project (with the fixtures it still lacks): the second consumer that
  needs a *behaviour* change rather than config, or the first time two vendored copies diverge at a cost.

## The procedure: applying the kit to a project

1. **Read the ground** — build tool, module map, source roots, which modules carry composition, where an abstract level
   could sit. Output: a proposed module list, one line of purpose each, plus the contract file. A human approves the
   list before anything else starts.
2. **Install the project's kit state** — rule + registration + wiring, the skills copy, the harness pointer
   (`hooks.json` at the profile's gate), frozen paths (empty unless the human is mid-flight), and the `AGENTS.md`
   digest citing `R-n`. One commit; no behaviour change.
3. **Baseline the gate** — run it, record what it says, red or noisy. That baseline is what later diffs are measured
   against. No suppressions (R-10).
4. **Reshape one module** — `shaping-a-module` decides the split before types move; `reengineering-module` narrows it
   and names what happens to dependants; `reshaping-module-by-linters` fixes code rather than config; `verify-before-done`
   earns "done" with a gate receipt plus a lane.
5. **Record** — the project's dial/contract updated as the shape moves, stale claims corrected, divergences recorded as
   rulings (R-12). One commit per module; ledgers are convenience, never the record.
6. **Repeat to the exit condition** — every composition-carrying module has the two-level split with its own allowlist,
   the gate is green, every module has its one-sentence purpose.

## Worked examples (the evidence behind the rules)

All in `skreeep2`, 2026-09-19, and each is quotable as a case study in the wiki article that carries its rule:

| What happened | Commits | Rule it demonstrates |
|---|---|---|
| A control event before the first note killed the events stream; the fix stated the contract on the abstract member, made the implementation total and shipped the regression test | `4a3242c`, `6863013` | R-5, R-6, R-8 |
| Two members built a non-empty structure from a possibly-empty input; the band and the roots' inputs became `Tree`s, so emptiness is unrepresentable | `5aad2f9` | R-5 |
| An independent lane found the contract worded for one implementor only, a lost check direction, and a test that proved less than it claimed | `5170561`, `6863013` | R-9, R-11 |
| A linter finding was fixed by reshaping the code instead of suppressing it — and the rule's own source was read to find out what it decides | `5170561` | R-10 |
| The gate ran the module's own lint and tests only after the module joined the gate lists | `6fbd8e1`, `8d09e1a` | R-8 |
| Docs that described a tree which no longer existed were corrected, and an unreconciled divergence was recorded as a ruling | `f1c4ecd`, `fa8fea2`, `3b38bfa` | R-12 |

## Constraints and open decisions

- **Cross-repo writes.** The artifacts live in three repositories (`scala-coding-guidelines`, `scala-llm-template`,
  the `scala-arhitect` profile). An agent confined to one workspace needs one approval per repository, so the plan
  should finish each repository in a single visit.
- **Gate availability.** Moving the harness engine into the profile makes the gate a profile fact: a session launched
  without the profile has none. The template must keep a working vendored fallback, or this trade is a regression of
  the property the harness existed to provide.
- **The rule's future home** (its own scalafix project) is deferred by the trigger above, not decided.
- **L1's article set** is not final: the `R-n` table above is a mapping to be compiled through the `karpathy-llm-wiki`
  flow (this document is its `raw/` source), with each article one rule or one pattern.
- **The `AllowedShape` rule has no tests today.** Whatever its home, fixtures (a flagged file, a passing file) are part
  of the work, because R-8 applies to the kit's own machinery.
