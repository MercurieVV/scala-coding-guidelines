# Stainless on the pure kernel — vericoding under the gate (roadmap Tier 3, item 9)

**Status:** approved design, 2026-09-27; amended 2026-09-28 with the measured Stainless behaviour and the final review's rulings (added clauses, escapes, snapshot always written and frozen, stage order). Roadmap: `wiki/llm-scala-fusion/roadmap-more-scala-less-llm.md` item 9 —
"the human writes `require`/`ensuring`, the LLM fills the body until Stainless accepts."

## Context (measured 2026-09-27)

- The only Stainless integration is in **ScalaSemanticMCP**: `analysis/lib/stainless-library.jar` as an unmanaged
  dependency, a `stainlessVerify` Mill command shelling `scripts/stainless-verify.sh` (standalone Stainless 0.9.9.3,
  dotty frontend), one verified production file (`PureKernels.scala`, verified in place), wired into `prePush`, failing
  on `invalid` VCs and tolerating `unknown` (wiki `stainless-verification/stainless-mill-integration.md`).
- **skreeep2** has no Stainless. Its gate engine is vendored from `scala-llm-template/kit/harness` (Plan C v2); Mill
  recipes the engine relies on live in `kit/recipes/mill/`.
- `frozen-paths` works per file; a contract and the body it constrains share one `def`, so file freezing cannot give the
  human ownership of the contract while the agent owns the body.

## Decisions

- **Scope:** generic kit feature (recipe + engine stage + skill) with **skreeep2 as the first consumer**.
- **Contract ownership:** a **contract-diff guard** — a syntax-only ScalaSemanticMCP tool extracts every `def`'s
  `require`/`ensuring`; turn-end compares against the session snapshot and blocks any change.
- **Kernel set:** a frozen, human-owned registry `.dsh/policy/kernels.tsv`.
- **Loop in the gate** (not only `prePush`): a turn that touches a kernel is verified before it can end.

## §1 Components

**1. ScalaSemanticMCP — `contract_clauses` (syntax-only, no classpath).**
- Input: `files` (URIs or root-relative paths). Output per file: `uri`, `defs`, `error` (per-file parse error, never a
  whole-call failure).
- Per `def`: `key` = enclosing owner path + name (`PureKernels.toPcm16`; overloads disambiguated by parameter arity and
  names), `line`, `requires` = list of clauses, `ensuring` = clause or null, `decreases` = clause or null. Each clause
  carries `text` (source slice) and `shape` = a hash of the scalameta `.structure` of the clause's term, so formatting
  never counts as a change and any semantic edit does.
- Callable one-shot: `scalasemantic-mcp call contract_clauses '<json>' <root>`, like `literal_tokens`. Docs golden,
  tools table, README updated.

**2. Kit — Stainless Mill recipe** (`kit/recipes/mill/Stainless.mill`, `kit/recipes/mill/stainless-verify.sh`, and a
section in `kit/recipes/mill.md`), lifted from ScalaSemanticMCP and generalised:
- `trait StainlessModule`: `stainless-library.jar` as an unmanaged classpath entry (main and `test`), and
  `stainlessVerify --targets <file,file,…>` (flag form — Mill 1.1.8 rejects positional command arguments).
- The script takes its targets as arguments; prints the per-VC report and the summary; exit **0** no `invalid`,
  **1** any `invalid`, **2** tool missing / download or setup failure. `STAINLESS_VERSION`, `STAINLESS_TIMEOUT`,
  `STAINLESS_CACHE_DIR` overrides kept.

**3. Engine (edited in `scala-llm-template/kit/harness`, vendored as usual).**
- `.dsh/policy/kernels.tsv`: `<file> TAB <module> TAB <reason>`; frozen; seeded header-only; `#` comments allowed.
- SessionStart `baseline`: snapshot `contract_clauses` of every registered kernel file (dirty or not) into
  `.dsh/.state/contracts/<session>.tsv` (frozen by `.dsh/.state/contracts/**`; always written — header-only, or marked `incomplete: <reason>` when the tool could not answer), together with a per-file count of verification escapes (`assume(`, `@extern`, `@ignore`).
- Turn-end **vericoding stage** (§3).
- `check-harness`: `kernels.tsv` parses; every file exists; every module is in the module map; the file itself is frozen;
  the launcher answers `contract_clauses`.

**4. Skill `vericoding`** (`kit/skills/vericoding/SKILL.md`): read contracts through `contract_clauses`; fill the body
(`hole_context` for `???`); verify with `<module>.stainlessVerify --targets …`; read the counterexample; never edit a
`require`/`ensuring`/`decreases`; if a contract looks wrong, stop and report it to the human.

**5. skreeep2 pilot.** `core/src/com/github/mercurievv/skreeep2/PureKernels.scala` (dependency-free) holds the kernels of
§2; `AudioFormat.toPcm16`, `framesToTime` and `RelativeTime.fromSampleOffset` delegate to it; `core` mixes
`StainlessModule`; `kernels.tsv` gets the row; `prePush` runs `core.stainlessVerify`.

## §2 Pilot contracts (human-owned on spec approval)

**K1 — `toPcm16(v: Double): Short`** (16-bit saturation):
```scala
ensuring(r =>
  (v.isNaN ==> (r == 0)) &&
  (!v.isNaN ==> (
    (v >= 32767.0  ==> (r == 32767)) &&
    (v <= -32768.0 ==> (r == -32768)) &&
    ((v > -32768.0 && v < 32767.0) ==> (r.toDouble <= v + 1.0 && r.toDouble >= v - 1.0)))))
```
Both saturation bounds pinned as exact values; truncation within 1. NaN keeps today's result (`0`); no `require`.

**K2 — `framesToNanos(frames: Int, sampleRate: Int): Long`** (from `framesToTime`):
```scala
require(frames >= 0 && sampleRate > 0)
ensuring(r => r >= 0 && (frames == 0 ==> (r == 0)))
```

**K3 — `samplesToNanos(samples: Long, sampleRate: Int): Long`** (from `RelativeTime.fromSampleOffset`):
```scala
require(samples >= 0 && sampleRate > 0 && samples <= Long.MaxValue / 1_000_000_000L)
ensuring(r => r >= 0)
```
The `require` makes today's silent overflow bound visible and proven (≈9.2e9 samples, ≈58 h at 44.1 kHz). Stainless's
`require` is erased at runtime; changing runtime behaviour past the bound is out of scope.

**Feasibility is measured first.** Plan task 1 runs K1–K3 (and a `???` body) through Stainless 0.9.9.3. Any contract the
tool cannot express or discharge is reformulated into one the human approves — never silently dropped. Division by a
variable is nonlinear and may yield `unknown`, which the gate notes (§3).

**Measured 2026-09-27 (Stainless 0.9.9.3, `scala-llm-template` `kit/recipes/mill/stainless-feasibility.md`) and decided by the human:**
- `==>` binds like `==`, so the implications are bracketed (`a ==> (r == c)`) — as written above.
- K1: the original ensuring compares `v` with NaN itself (3 invalid VCs, counterexample `v = NaN`), which no body can fix — measured in the skreeep2 live session 2026-09-28. **Decision (revised):** the original three clauses are guarded by `!v.isNaN` and `NaN ==> (r == 0)` pins today's runtime result, as written above; Stainless: 49 valid, 0 invalid, 2 unknown (K2/K3 overflow).
- K2, K3: the postconditions are provable; the `Long` multiplication overflow check stays `unknown` (solver timeout at 10 s and 60 s). **Decision:** accepted — the gate reports it as a named `unknown` note (§3), not a block.
- A typed `(??? : T)` body is reported by Stainless as a missing implementation and exits 0 — the gate must treat it as unverified, never as a pass.

**Callers stay behaviour-identical**: `toPcm16` forwarder; `framesToTime` = `framesToNanos(...).nanos`;
`fromSampleOffset` via `samplesToNanos`. Existing skreeep2 tests stay green.

## §3 Gate semantics

Runs at turn-end after the module's compile/checks and after the contract's turn-end tests (so Stainless gets only the Stop hook's remaining budget), only when the turn touched a file listed in `kernels.tsv`, and not for a module whose checks or tests already blocked:

1. **Contract guard.** `contract_clauses` on the touched kernel files vs the session snapshot, keyed by `key`:
   - a changed clause `shape`, or a removed clause/def-with-contract → **block**, naming the def with old and new clause
     text: "contracts are the human's — ask in chat";
   - a new def with a contract (lemma/helper) → allowed;
   - a clause ADDED to a def the snapshot already knows (e.g. a new `require`) → **block** (adding a precondition weakens what callers may rely on);
   - no snapshot record for the session → **block** (SessionStart always writes one, so absence means deletion); a record marked `incomplete:` → named note (cannot compare); the Stainless check still runs;
   - more verification escapes (`assume(`, `@extern`, `@ignore`) in a kernel than at snapshot → **block**;
   - tool failure / launcher missing → `dnote` stand-down of the guard (named).
2. **Stainless.** `<module>.stainlessVerify --targets <all kernel files of that module>`:
   - exit 1 → **block** listing each `invalid` VC (function, VC kind, counterexample; capped), remedy "fix the body; if
     the contract is wrong, ask the human";
   - `unknown` VCs → named note;
   - exit 2 → `dnote` stand-down naming the cause.
3. The existing attempt cap and release path apply.

Human contract edits: the snapshot is taken at SessionStart over every kernel file, so a contract the human edits
before or between sessions is the baseline; a block fires only for changes between snapshot and turn-end.

A `???` body: an untyped `{ …; ??? }` does not compile (Stainless rejects the kernel → **block**, "Stainless cannot compile <file>:<l>"); a typed `(??? : T)` is reported as a missing implementation with exit 0 (measured in task 1) → **block** "a kernel body is not implemented yet". A body outside the Stainless-supported subset is the same compile block. Stainless runs with `--no-server`, inside one time budget for the whole stage (480 s cap, 540 s Stop budget); running out is a named "not verified" note.

## §4 Testing and delivery

- **ScalaSemanticMCP:** unit suite — extraction per def; `shape` stable under reformatting, different under a semantic
  change; multi-line clause; infix `ensuring`; `decreases`; parse error per file. Docs golden; one-shot `call` test.
- **Engine fixtures** (stub launcher + stub Mill): changed contract blocks; removed contract blocks; new lemma passes;
  body-only change runs Stainless only; `invalid` blocks with counterexample text; `unknown` notes; exit 2 notes; no
  kernel touched → nothing runs; no snapshot record → block; `incomplete:` record → note; added clause → block; added `assume` → block. `lib-test` + `check-harness` for `kernels.tsv` parse/freeze rules.
- **Real Stainless:** a recipe smoke over one scratch kernel (valid and invalid); skreeep2 `core.stainlessVerify` green.
- **Live smoke in skreeep2:** the agent fills a `???` body under the gate; one live contract change is blocked.

Delivery order (as items 5–6): (1) ScalaSemanticMCP `contract_clauses` → `installLocal` (human-run); (2) template —
recipe, engine stage, skill, seeds; (3) skreeep2 — re-vendor + pilot; (4) wiki.

## Invariants

- The gate never silently passes: every new condition blocks, notes, or stands down by name.
- Contracts are never weakened by the agent without a block; the human edits them freely.
- The engine's interface and existing stages are unchanged; the new stage runs only for registered kernels.

## Out of scope

Runtime checks past K3's bound; sbt/scala-cli Stainless recipes; kernels outside skreeep2 `core`; proofs beyond
`require`/`ensuring`/`decreases` (lemmas are allowed but not required); the item-6 follow-ups.
