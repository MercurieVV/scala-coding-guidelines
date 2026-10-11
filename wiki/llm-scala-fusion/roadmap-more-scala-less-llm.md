# Roadmap: More Scala, Less LLM

> Sources: synthesis over the topic's sources — Blinn et al., 2024-09-02; Khati et al., 2026-01-27; Zhao et al. (SpecBench), 2026-05-20; Zhong et al. (ImpossibleBench), 2025-10-23; He et al., 2025-06-23; Lang et al. (FPEval), 2026-01-05; Anthropic Engineering, 2025-11-04; Bursuc et al., 2025-09-26; Mündler et al., 2025-04-12; Hassani et al., 2025-08-28; Feng et al., 2026-05-04; Alonso et al. (TDAD), 2026-03-18; Chen et al., 2026-02-08; Liang et al., 2026-07-28; Mughal et al., 2026-04-22; Wang et al. (CodeAct), 2024-02-01; Patel et al., 2026-08-06; Anthropic Engineering, 2025-11-24; Felendler et al., 2026-02-17; Maxim (Bifrost), Unknown; local probe, 2026-10-01; Chen et al. (TestPrune), 2025-10-21; Yan et al. (TensorBench), 2026-06-04; local skreeep2 measurement, 2026-10-01; local gate wrapper-bypass probe, 2026-10-01; local live gated check in skreeep2, 2026-10-01; local structural holdout seal measurement, 2026-10-03; Chen et al. (FeatBench), 2026-02-18; Zhou et al. (FeatureBench), 2026-02-11; Lam et al. (SWE-Chain), 2026-05-14; Orlanski et al. (SlopCodeBench), 2026-03-25; Advani, 2026-06-01; Zhao et al., 2026-07-10; Trivedi and Schmitt (SonarSource), 2026-05-19
> Raw: [Typed holes (Hazel)](../../raw/llm-scala-fusion/2024-09-02-statically-contextualizing-llms-with-typed-holes.md); [Local gate feature requests](../../raw/llm-scala-fusion/2026-10-10-local-gate-feature-requests.md); [Local Scala interpreter-edit rule](../../raw/llm-scala-fusion/2026-10-11-local-scala-interp-edit-rule.md); [Deterministic AST hallucination correction](../../raw/llm-scala-fusion/2026-01-27-deterministic-ast-hallucination-detection-correction.md); [SpecBench](../../raw/llm-scala-fusion/2026-05-20-specbench-reward-hacking-long-horizon-coding-agents.md); [ImpossibleBench](../../raw/llm-scala-fusion/2025-10-23-impossiblebench-llm-test-exploitation.md); [Property-Generated Solver](../../raw/llm-scala-fusion/2025-06-23-property-generated-solver-property-oriented-feedback.md); [Perish or Flourish (FPBench)](../../raw/llm-scala-fusion/2026-01-05-perish-or-flourish-llm-functional-programming-fpbench.md); [Code execution with MCP](../../raw/llm-scala-fusion/2025-11-04-anthropic-code-execution-with-mcp.md); [Vericoding benchmark](../../raw/llm-scala-fusion/2025-09-26-vericoding-benchmark-formally-verified-program-synthesis.md); [Type-constrained code generation](../../raw/llm-scala-fusion/2025-04-12-type-constrained-code-generation-pldi-2025.md); [From Law to Gherkin](../../raw/llm-scala-fusion/2025-08-28-from-law-to-gherkin-llm-behavioural-specifications.md); [Structured spec-driven generation (Gherkin vs NL)](../../raw/llm-scala-fusion/2026-05-04-structured-spec-driven-repo-level-generation-gherkin.md); [TDAD](../../raw/llm-scala-fusion/2026-03-18-tdad-test-driven-agentic-development.md); [Agent-generated tests](../../raw/llm-scala-fusion/2026-02-08-rethinking-value-of-agent-generated-tests.md); [Do code LMs follow tests](../../raw/llm-scala-fusion/2026-07-28-do-code-lms-follow-tests-paired-interventions.md); [Duplicate Gherkin steps](../../raw/llm-scala-fusion/2026-04-22-duplicate-gherkin-steps-1-1m-step-benchmark.md); [CodeAct](../../raw/llm-scala-fusion/2024-02-01-codeact-executable-code-actions-llm-agents.md); [Bitter Lesson of Tool Calling](../../raw/llm-scala-fusion/2026-08-06-bitter-lesson-of-tool-calling.md); [Advanced tool use](../../raw/llm-scala-fusion/2025-11-24-anthropic-advanced-tool-use.md); [MCP design choices — results](../../raw/llm-scala-fusion/2026-02-17-mcp-design-choices-results-excerpts.md); [Bifrost code mode](../../raw/llm-scala-fusion/bifrost-code-mode-token-savings.md); [Local probe: code execution](../../raw/llm-scala-fusion/2026-10-01-local-probe-scalasemantic-code-execution.md); [TestPrune](../../raw/llm-scala-fusion/2025-10-21-testprune-old-tests-new-tricks-swe-issues.md); [TensorBench](../../raw/llm-scala-fusion/2026-06-04-tensorbench-coding-agents-tensor-framework.md); [TDAD results and limitations](../../raw/llm-scala-fusion/2026-03-18-tdad-results-and-limitations.md); [Local skreeep2 downstream-tests measurement](../../raw/llm-scala-fusion/2026-10-01-local-skreeep2-downstream-tests-measurement.md); [Local gate wrapper-bypass probe](../../raw/llm-scala-fusion/2026-10-01-local-gate-wrapper-bypass-probe.md); [Local live gated check in skreeep2](../../raw/llm-scala-fusion/2026-10-01-local-live-gated-check-skreeep2.md); [Local fixStrict config-error probe](../../raw/llm-scala-fusion/2026-10-01-local-fixstrict-config-error-probe.md); [Delete-only turn probe](../../raw/llm-scala-fusion/2026-10-01-local-delete-only-turn-probe.md); [Holdout cap probe](../../raw/llm-scala-fusion/2026-10-01-local-holdout-cap-probe.md); [Script-file seal probe](../../raw/llm-scala-fusion/2026-10-02-local-script-file-seal-probe.md); [Structural holdout seal](../../raw/llm-scala-fusion/2026-10-03-local-structural-holdout-seal.md); [Gate A/B evaluation](../../raw/llm-scala-fusion/2026-10-08-local-gate-ab-evaluation.md); [Gate ablation: turn-end](../../raw/llm-scala-fusion/2026-10-09-local-gate-ablation-turn-end.md); [Same-day ungated control](../../raw/llm-scala-fusion/2026-10-09-local-gate-same-day-ungated-control.md); [Gate-visible tasks screen](../../raw/llm-scala-fusion/2026-10-09-local-gate-visible-tasks-screen.md); [FeatBench](../../raw/llm-scala-fusion/2026-02-18-featbench-feature-level-code-generation-excerpts.md); [FeatureBench](../../raw/llm-scala-fusion/2026-02-11-featurebench-agentic-feature-development-excerpts.md); [SWE-Chain](../../raw/llm-scala-fusion/2026-05-14-swe-chain-chained-release-upgrades-excerpts.md); [SlopCodeBench](../../raw/llm-scala-fusion/2026-03-25-slopcodebench-agent-degradation-long-horizon.md); [False success in LLM agents](../../raw/llm-scala-fusion/2026-06-01-false-success-llm-agents-silent-failure.md); [Failure as a process](../../raw/llm-scala-fusion/2026-07-10-failure-as-a-process-cli-coding-agent-trajectories.md); [Code cleanliness minimal pairs](../../raw/llm-scala-fusion/2026-05-19-code-cleanliness-coding-agents-minimal-pair.md)
> Updated: 2026-10-11

## Overview

The prioritised list of what to build or change, ranked by (evidence strength × defect classes closed) ÷ effort. Each
item names the defect class it attacks (see [the taxonomy](llm-code-defect-taxonomy.md)), the mechanism (see
[types and properties](where-types-and-properties-buy-most.md)) and the tool it lands in (see
[harness gaps](harness-and-tooling-gaps.md)). The governing principle, from the evidence: **the LLM's job is to
inhabit a type or satisfy a property; Scala's job is to state it, check it, and translate the failure.** Move work
leftward whenever a decidable check exists.

## Tier 1 — cheap, high evidence, do first

**1. Test-protection hooks + holdout module.** *Closes:* test gaming. *Evidence:* ImpossibleBench (agents delete or
edit failing tests; test access and feedback loop change cheating rates) and SpecBench (visible-suite saturation, gap
growing "by 28 percentage points for every tenfold increase in code size"). *Build:* a dsh/Claude hook denying writes
under test paths during implementation; a `holdout` test module in the template that only the gate runs, never the
agent. R-8/R-9 already state the rule; this makes it mechanical.

> **Status: Shipped** (2026-09-20) — landed in `skreeep2` on `master`: a `sealed` access token in `.dsh/frozen-paths` (reads
> and writes denied), every module test root frozen, the harness and its live hook wiring self-frozen, a `holdout` Mill
> module run only by the turn-end gate and `prePush` with names-only reporting, and six installation rules in
> `check-harness.sh`. Spec: `docs/superpowers/specs/2026-09-19-test-protection-and-holdout-design.md` (this repo). The
> two residuals from the final review — quoted or alternate Mill selectors (`./mill 'holdout.test'`, `_.test`,
> `__.test.<task>`, brace forms) reaching the suite, and the guard's stand-down hint naming the now-refused `__.compile` —
> were closed the same day. What stays outside the text-matching layer, by design and documented: `bash -c '…'`,
> variable-built selectors, and reads scoped at an ancestor (`grep -r .`, `git grep`). Plan C carries the engine into
> the profile.
>
> **Follow-up shipped** (2026-10-01) — the wrapper bypass closed. A probe in skreeep2 showed the seal opened by one
> prefix word: `env FOO=1`, `timeout 600`, `nice -n 5`, `time -p`, `eval "…"` and `sh -c "…"` in front of
> `./mill holdout.test` were all allowed, as was `timeout 5 sbt test`, because the hook's command parser skipped a
> wrapper only as a bare word and read its first option as the command. The parser now skips each wrapper together
> with its own options and leading `NAME=value` assignments, and the code given to `eval` / `<shell> -c` is judged as
> a command of its own, up to three nested levels; a command word computed at run time inside it (`sh -c "$CMD"`,
> `eval "$(cat run.sh)"`) is refused. So `bash -c '…'` is no longer outside the layer. The frozen-path interpreter
> rule inherits the fix (`sudo -u x python3 -c …` against a frozen file is now denied). 29 new fixture cases, 22 of
> them failing before the fix; suites 475 passed, 0 failed under both sh and dash. Still outside the text layer: a
> shell alias or function, and a script file an interpreter runs.
>
> **Status: Outdated** (2026-10-01) — "the wrapper bypass closed" held only for wrappers WITHOUT option arguments. A
> live gated session in a skreeep2 worktree, plus direct payloads to the hook, found `nice -n 5 eval '…'`,
> `timeout 5 eval`, `sudo -u x eval`, `nice -n 5 sh -c '…'` and `nice -n 5 bash -c '…'` in front of
> `./mill holdout.test` still allowed (exit 0); live, only Claude Code's own `eval` check stopped the first one. The
> step that lifts the code out of `eval` / `-c` stopped at the wrapper's first option, and the 29 cases had no such
> shape. Closed in scala-llm-template b1c8ac4: all six forms exit 2, `timeout 5 sh -c 'echo hi'` still exits 0;
> 8 new cases, 7 failing before; suites run.sh 483 passed, 0 failed under both sh and dash.
>
> **Follow-up shipped** (2026-10-01) — the holdout run is time-capped. The gate ran the sealed suite with no cap of its
> own, after turnEndTests and Stainless had spent their share of `DSH_STOP_BUDGET`; holdout tests exercising agent code
> that never terminates would hold the Stop hook past its timeout. A probe with a `sleep 40` holdout and a 20 s budget
> ended with `rc=0 secs=40`. Now the run is capped at `min(DSH_HOLDOUT_CAP (default 120), what is left of
> DSH_STOP_BUDGET)` and not started with 10 s or less left; the same probe ended with `rc=0 secs=20`, the run killed and
> named. skreeep2's real holdout takes 6 s cold, 1 s warm, so the cap costs a healthy run nothing (scala-llm-template
> 7d23e59).
>
> **Follow-up shipped** (2026-10-02) — shell script files are held to the seal. A script holding `./mill holdout.test`
> reached the sealed suite: `sh`, `bash`, `.`, `source`, `./run.sh` and an absolute path all exited `rc=0` at the hook,
> while the same text inline was refused, and the seal has no post-hoc layer. tool-policy now reads the script a
> command runs (also `sh < f`, `cat f | sh`, and `echo`/`printf` text piped into a shell) and refuses a word selecting
> the sealed suite or a sealed path in it; a script the same line writes, `xargs sh`, and a pipe from another command
> are refused. Only the sealed rules read script text: applying every rule refused 3 of skreeep2's 18 real scripts.
> A first live gated run still reached the suite four ways, all closed; after the fix all 10 probe forms exit `rc=2`
> and `total=54 denied=0` over the real scripts. Still outside, shown live: a selector built at run time
> (`H=hold; H="${H}out"`) and a Python script calling Mill — a structural seal is the only fix for those
> (scala-llm-template 709d0ba).
>
> **Follow-up shipped** (2026-10-03) — the structural holdout seal. The holdout tests now live outside the repository,
> in the contract's `holdoutDir`; turn-end hands that directory to the holdout run only (`DSH_HOLDOUT_DIR`, read by
> Gate.mill's `HoldoutModule`), which writes its own `out-holdout`, while every engine Mill call runs without a daemon
> into `out-agent`. Claude Code's sandbox denies the agent `./out`, `./out-holdout`, the sealed paths and the holdout
> directory, and turn-end prints only test names that match a declared test literal exactly. A confined-gate design
> under `sandbox-exec` profiles was withdrawn first: the profiles were escapable. Live in a gated skreeep2 clone,
> "Seal held on all 11 probes": the run-time-built selector reached Mill and failed on `Operation not permitted` at
> the holdout directory, and turn-end ran `ExternalSmokeHoldout` from outside the repository. Still open: code the
> gate runs can reach the directory if it knows the path, MCP servers run outside the sandbox, and git history keeps
> the old in-repo placeholder (scala-llm-template 05c0f00, 0868142; skreeep2 6bf6c1b).
>
> **Evaluation** (2026-10-08) — does the gate pay off? A/B on skreeep2: 8 remove-and-rebuild tasks (members cut to
> `???`, spec file deleted), gated arm with the real hooks and sandbox vs an ungated arm with permissions only, sonnet,
> scored afterwards on the hidden spec and the 7 rendered-audio holdout scenarios. A weekly usage limit left 16 runs per
> arm (2 repeats). Strict report (6 runs excluded by the contamination tripwire): solve rate 0.87 [0.62, 0.96] gated
> vs 0.73 [0.43, 0.90] ungated, "Verdict: inconclusive". The 6 flags were audited as tripwire false positives; over all
> 32 runs: gated 14 of 16 solved, ungated 10 of 16; `false_done` 2 of 16 vs 6 of 16; gated never lost a task (4
> positive, 0 negative, p = 0.125). Cost: median wall 181.5 s vs 46.5 s (3.9×), median cost USD 0.308 vs 0.156. The
> pre-registered rule needs wall time at most 2×, so its verdict on the audited numbers is "does not pay off" even
> though the correctness branch holds. Every unsolved run passed all 7 holdout scenarios: the holdout decided no run,
> and the study cannot say which gate part bought the gain. Ungated failures are one missed edge case each, claimed
> done. Read: the gate buys correctness at about four times the wall time; the 2× bound, not the effect, is what fails.
>
> **Ablation** (2026-10-09) — is turn-end the part that pays? Arm `t` (the gate without its turn-end Stop hook) ran 8
> times on the 4 tasks where the A/B arms differed, against the A/B's gated and ungated runs on the same tasks. After
> the contamination audit (one more tripwire false positive, a here-doc to the agent's scratch file): gated 8 of 8,
> no turn-end 8 of 8, ungated 4 of 8; median wall 200 s, 80 s, 52 s. Pre-registered verdict: "the gain comes without
> turn-end". Without turn-end the gate costs about 1.5× the ungated wall time instead of 3.9×. But mixer-modes and
> scope-view `t` runs solved with no gate block at all, some with no test run and no MCP call — the same visible
> behaviour as the ungated failures — so no mechanism was found, and the arms ran on different days without a
> same-day ungated control.
>
> **Same-day ungated control** (2026-10-09) — the ungated arm re-run on the ablation's day, same 4 tasks, reps 1 and 2.
> After the audit (2 more tripwire false positives: a `grep -v` pattern and a `sed` replacement): 8 of 8 solved,
> `false_done` 0, median wall 53.5 s, median cost USD 0.16. Pre-registered verdict: "gap was day drift; gate gain not
> shown". Same day, the gate without turn-end and the ungated arm both solved 8 of 8 (median wall 80 s vs 53.5 s).
>
> **Status: Disputed** — the A/B (2026-10-08) and the ablation read a correctness gain for the gate on these 4 tasks
> (ungated 4 of 8 on 2026-10-03 and 2026-10-08). The same-day control (2026-10-09) solved them 8 of 8 ungated. Day
> drift or run-to-run noise, not the gate, explains the gap on current evidence; the tasks were picked for a past
> difference, which favours regression to the mean. No same-day full-gate runs exist.
>
> **Gate-visible tasks** (2026-10-09) — 4 new remove-and-rebuild tasks (ADSR envelope, Sine phase maths, ForTime
> sample clock, SinesGenerator partial sets) whose cut breaks downstream test modules and the holdout, with hidden specs
> written for the study. Ungated screen: 8 of 8 solved, every run passing the holdout with no regressions, median wall
> 38.5 s. Pre-registered verdict: "no gate-visible failure found; ungated sonnet handles these"; the gated test phase
> was not run. Read: on remove-and-rebuild tasks whose scaladoc states the behaviour, sonnet does not make the mistakes
> the gate's checks exist to catch, so the gate's correctness value is not shown on this task family; its holdout seal
> and tool policy remain safety parts, not correctness parts.
>
> **Turn-end safety mode** (2026-10-10) — acting on the three studies above, scala-llm-template master (`kit/harness`)
> adds `.dsh/policy/turn-end.tsv`, frozen and the human's: `mode safety` keeps the Stop hook's frozen-path check,
> diff-scoped verification and vericoding, and drops format, scalafix, compile, turnEndTests (owners and downstream)
> and purrism; `mode full` is the old hook. Fresh installs seed `safety`; no table means `full`, so existing installs
> (skreeep2) are unchanged until the human opts in. The saving is not measured for `safety` itself: the ablation's
> median wall was 200 s with the whole hook and 80 s without it.
> Scope: that evidence is remove-and-rebuild only. Feature-level benchmarks find regressions the dominant failure
> (FeatBench: 73.6% of analysed failures), which is what `full` mode's downstream turnEndTests target — see item 11.

**2. Shrunk-counterexample feedback loop (ScalaCheck ⇄ LLM).** *Closes:* misinterpretation, missing corner cases,
self-deceiving I/O tests. *Evidence:* Property-Generated Solver — property-oriented, structurally minimal feedback,
"over 64% fix rate" on initially failed problems. *Build:* a convention that every algebra ships properties; a hook
that, on property failure, feeds the LLM *only* the shrunk counterexample and the property text. ScalaCheck shrinking
makes this nearly free.

> **Status: Shipped** (2026-09-20) — landed in `skreeep2` on `master`: a generic normaliser (weaver/weaver-scalacheck,
> ScalaCheck, munit, infra adapters → TAB failure records → a fixed `FEEDBACK:/PROPERTY:/TEST:/INFRA:` block), the
> `feedback.sh` wrapper (raw log kept, names and counterexample only shown), a PreToolUse rewrite of the agent's own
> `mill … test` runs, `fastTests` in post-edit (noted) and `turnEndTests` at turn-end (blocking; drafts under
> `test-draft/src` noted), five installation rules. Spec: `docs/superpowers/specs/2026-09-20-property-feedback-loop-design.md`
> (this repo). Caveat found at implementation: weaver-scalacheck reports the failing input but does not shrink it, so the
> "shrunk" half depends on the runner — plain ScalaCheck's `ARG_0_ORIGINAL` is rendered when present. Open: whether Claude
> Code honours the `updatedInput` rewrite live; the post-edit and turn-end stages do not depend on it.

**3. Diagnostic→instruction table.** *Closes:* idiom drift, compile-error loops that stall. *Evidence:* FPEval's repair
gains come from static-analysis feedback paired with "hand-crafted instructions for common types of issues"; raw
diagnostics alone repaired less. *Build:* purrism exports per-rule repair instructions; the build-result hook joins
compiler/scalafix diagnostics with them before the model sees the output.

> **Status: Shipped** (2026-09-21) — two halves. *scalafix-purrism v0.9.1:* a finding catalog (31 stable codes, one per
> finding kind, `code = Rule.kind` = scalafix's lint id so Mill/scalafix print `[Rule.kind]` natively), every diagnostic
> text carries the explanation for humans, `purrism/findings.tsv` in the jar (`code rule title explanation instruction doc`,
> header `# purrism <version>`), a docs "Findings" page, `findingsCheck` in CI. *skreeep2 `master`:* `scalafix` and `scalac`
> adapters in the feedback normaliser (`kind diagnostic` records), two frozen tables (`.dsh/instructions/purrism.tsv` vendored
> by `instructions-sync.sh`; project-owned `local.tsv` for scalac/wart codes), `DIAGNOSTIC:` + `INSTRUCTION:` + `DOC:`
> rendering with a `No instruction for:` gap line and a `.dsh/.state/instructions-missing` backlog, post-edit/turn-end
> `compile`/`fix --check`/`fixStrict` routed through the wrapper (blocking and F53 attribution still read the raw log), the
> PreToolUse rewrite extended to those tasks, five installation rules. Spec:
> `docs/superpowers/specs/2026-09-20-diagnostic-instruction-table-design.md` (this repo). Found at implementation: Mill's
> compile reporter prints no Scala 3 error ids, so scalac codes are `scalac.<kind>` classified from the message headline by a
> small project table; warts are `wartremover.<Wart>`. Open for the human: the instruction prose is a first draft (codes are
> stable, prose edits are non-breaking); `Layer.mill`'s `fixStrict` still swallows scalafix config errors as success.
>
> **Follow-up shipped** (2026-10-01) — the `fixStrict` false green closed. A probe in skreeep2 confirmed it: with an
> unknown rule in `.scalafix.conf`, `score.fixStrict` printed `Unknown rule 'NoSuchRuleXyz'` and still ended `SUCCESS`
> with `exit=0`, because the task (`GateLayer` in `Gate.mill`, not `Layer.mill`) only logged scalafix's run-level error
> and treated an empty diagnostic list as clean. It now fails by name — `fixStrict: scalafix could not run: <msg>`, or
> `fixStrict: N file(s) failed:` for a file scalafix could not evaluate, as `fixRanges` already did — and the engine
> renders that as an `INFRA:` record instead of `UNREADABLE`. After: `exit=1` on the broken config, clean config and a
> real lint finding unchanged. Shipped in scala-llm-template d122d6a, re-vendored in skreeep2 d347476. The
> unevaluated-file path is covered by a parser test only, not reproduced live.

## Tier 2 — moderate effort, direct tool upgrades

**4. Typed-hole context tool in ScalaSemantic.** *Closes:* hallucinated APIs/objects, wrong types, missing context.
*Evidence:* Hazel — "contextualization with type definitions is particularly impactful"; hallucination arises for
definitions "not in the training data nor near the cursor". *Build:* `hole_context(uri, pos)` → expected type,
compatible in-scope bindings, applicable givens, definitions of involved types; a skill that makes the agent write the
`???` skeleton first, compile, then fill holes one at a time with this context.

> **Status: Shipped** (2026-09-24) — `ScalaSemanticMCP` master: a `hole_context(uri, line, character, source?)` MCP tool
> answering from the Scala 3 compiler (a dotty `InteractiveDriver` per module backend, error-tolerant, so an uncompiled
> buffer still answers): the expected type with `confidence` (`exact` when the enclosing tree prescribes it,
> `unconstrained` when nothing does — then no candidate is guessed) and a `why`; `bindings` (in-scope values that
> conform under `<:<`), `producers` (methods and companion members whose result fits, `match` `exact` or `shape`),
> `givens` (instances the compiler would pick), `types` (the expected type's definitions; project types carry a source
> excerpt), with per-list bounds and `omitted` counts. Candidates are filtered for accessibility from the hole and named
> the way the hole's own scope names them (alias, short name when unshadowed, else fully qualified) — never a name that
> would not compile there. The `typed-holes` skill makes the agent write `???` skeletons first, compile, then fill one
> hole at a time. Spec: `docs/superpowers/specs/2026-09-21-typed-hole-context-design.md` (this repo). Deferred with
> reasons in the branch's final review: scope-distance ordering, producers'-`using`-clause givens, non-parameterised
> given types. Open for the human: a live smoke needs classpath metadata wired in the target project.

**5. Diff-scoped deterministic verification.** *Closes:* hallucinated symbols, allowlist breaches, unresolvable build
coordinates. *Evidence:* Khati et al.'s AST-vs-knowledge-base check reached "100% precision" and auto-corrected
"77.0%" of found hallucinations without an LLM. *Build:* `verify_diff` in ScalaSemantic resolving every introduced
symbol against SemanticDB/classpath and the AllowedShape allowlist; a coursier resolve check for new dependencies.
Deterministic reject before any repair prompt.

> **Status: Shipped** (2026-09-25) — both halves are on master. Brainstorming split the item in two: *what* is verified is a
> Scala semantic fact, *where* it runs is harness policy, and no policy argument crosses the boundary. Shipped:
> `resolve_references(files)` — batched `{uri, ranges, source?}` input, since a turn-end sweep should be one JVM
> spin-up rather than one per file. Every name token scalameta finds inside the given line ranges is joined against the
> occurrences the presentation compiler emits for the same buffer; a token with no occurrence covering it is the one
> thing neither source can report alone — a name the compiler could not resolve. Each row carries the name as written,
> `kind`, `role`, `resolved`, the resolved `symbol` and its `origin` (`project` | `stdlib` | `classpath`), or, when
> unresolved, deterministic near-miss `suggestions` ranked by edit distance. The 200-row cap never drops an unresolved
> row, so a hallucination past the 200th name still reaches the caller. Also shipped: a one-shot
> `scalasemantic-mcp call <tool> '<json>' [root] [classpath]` mode, because a shell hook cannot reuse the agent
> session's MCP server. Spec: `docs/superpowers/specs/2026-09-24-diff-scoped-verification-design.md` (this repo), §1.
> Found at implementation: a handful of correct Scala 3 shapes carry no compiler occurrence and would read as
> hallucinations — `derives` clauses, named `export` selectors, macro splices, backticked definitions, and an annotated
> ascription used as a match scrutinee in a val's right-hand side. Each is suppressed rather than reported, and the tool
> documents the resulting blind spots; four review rounds went into making each suppression exactly as wide as the
> compiler's real hole, because one too wide silently passes a hallucinated name.
>
> The skreeep2 half (§2–§4) is `dsh verify-diff`: `git diff -U0` hunk headers become 0-based half-open line ranges, so
> the filtering rule that makes the check diff-scoped lives with git and old breakage is never re-reported; coordinates
> added to a build file go to `cs resolve`; resolved symbols are matched against the human-owned, frozen
> `.dsh/policy/allowed-types.tsv`. The turn-end gate runs it before the compile chain — the gate invokes it, not the
> agent, which is the whole difference between mandatory and optional — and `git commit` / `./mill prePush` run it again
> against the index, where the rules are stricter.
>
> What the review rounds changed, and why it is worth recording: the gate's cardinal invariant is that it never silently
> passes, and three separate violations of it survived the fixtures and were caught only by measurement. A `printf` whose
> format string began with `---` was parsed as an option by both `/bin/sh` and `dash`, so one degradation note never
> printed at all. A launcher predating the one-shot `call` mode falls through to `serve`, reads EOF on the hook's closed
> stdin and exits 0 printing nothing — which the gate read as "nothing unresolved" until a live smoke caught it. And a
> file answered with an empty `references` list — the ordinary answer for a comment-only change — produced a false
> "not checked" note. The opposite failure appeared too: at the commit boundary, degradation notes promoted to blocks, so
> on a machine without `timeout` every commit was denied with a reason no edit of the agent's could clear. The rule that
> settled it is that a *finding* promotes and a *degradation* never does — "I could not check" must not become "you may
> not commit".
>
> Verified live in skreeep2 on 2026-09-25, end to end against the installed launcher, once `./mill
> scalaSemanticWriteClasspath` wrote the classpath metadata the server discovers: a hallucinated name blocks the turn at
> its exact line, a typo of a real method blocks with a "did you mean" suggestion, and correct code passes silently.
> Found in that run, left for ScalaSemanticMCP: when a method does not resolve, names inside its lambda are reported too
> (a cascade that only ever accompanies a real error), and suggestion ranking can place a `cats` `map` above `List`'s.
> Open for the human: `.dsh/policy/allowed-types.tsv` ships with
> its header and no rule rows, because a wrong row blocks real work; and a prose name is never verified, since the
> semantic tool answers only about Scala files — a change set with no Scala file says so rather than implying otherwise.

**6. purrism as a loop stage, plus a memorisation-shape rule.** *Closes:* the "94%" imperative-pattern rate in Scala
output; SpecBench-style test-memorising code. *Build:* run the rewrite rules on the agent's diff before the agent
reviews it; add a rule flagging large literal-keyed tables and constant returns in production sources.

> **Status: Shipped** (2026-09-26) — both halves on master. purrism already ran in the loop as a *check* (items 1–3), so
> the new part is *applying* it: at turn-end the skreeep2 harness applies purrism's rewrites to the lines the session
> changed (`<module>.fixRanges`, which keeps only patches whose every edit is in range) and blocks once so the agent
> reviews the diff; a rewrite that breaks the compile is undone. The memorisation half ended up in the harness rather
> than as a purrism rule: the signal is *overlap with test literals*, not table size, because in an audio app a
> legitimate MIDI note table and a memorising table look alike by size. A new syntax-only ScalaSemanticMCP tool,
> `literal_tokens`, returns each literal with a normalised value and its enclosing construct; the harness blocks a
> construct when at least 8 of its non-trivial literals, and at least half of them, also appear in the visible tests,
> judging the whole construct once touched so a table grown a few rows a turn is still caught. Exemptions live only in a
> frozen, human-owned table. Spec: `docs/superpowers/specs/2026-09-25-purrism-loop-and-memorisation-design.md` (this repo).
>
> What measurement changed: every task review found a silent path, and a live run against real purrism found a
> spec-level defect the fixtures could not — the escape the design promised, `// scalafix:ok <Rule>`, does not suppress
> purrism's rewrites, because scalafix honours suppression only for patches a rule marks atomic and purrism marks almost
> none. The block message now tells the agent to restructure or to report to the human. The rewrite stage also turned
> out to rewrite a human's pre-session edits until it was confined to a session baseline and to files clean at
> SessionStart. Open: purrism marking its rewrites atomic, which would restore a per-line opt-out; recording a
> `git stash create` snapshot at SessionStart so a human's mid-session lines can be told from the agent's.

## Tier 3 — new surfaces, higher payoff and risk

**7. BDD step algebra as the text/type seam.** *Closes:* misinterpretation at the requirement level; gives the human a
readable spec. *Evidence:* LLM-generated Gherkin rated relevance 95%, clarity 100% by human judges, with omissions and
hallucinations that need review. *Build:* in the template, a cucumber-scala (or specs2) layout where step signatures
are typed holes over opaque domain types, Then-clauses are properties, and scenarios split into visible/holdout. Detail
in [Alternative Paradigms and the BDD Seam](alternative-paradigms-and-bdd-seam.md).

> **Status: Outdated** (2026-10-01) — investigated, not pursued. Measured evidence does not show that a BDD layer makes
> a coding agent's work easier; it shows that the executable half helps and the textual half does not. Gherkin given to
> the agent as its spec scored 6.8% lower TPR than natural language on average and won only 14 out of the 30
> model/tool/sample combinations, while the typed signature model improved average TPR (+7.82%) (Feng et al.). Generic
> TDD instructions without test context raised SWE-bench Verified regressions to 9.94%, against 6.08% for the vanilla
> agent, while a source-to-test map cut them to 1.82% and moved resolution from 24% to 32% (TDAD). Prompting agents to
> write more or fewer tests barely moved resolution (GPT-5.2 71.8% both ways; Gemini 3 Pro 74.2% to 73.2%) (Chen et
> al., agent-generated tests). Models follow test-specified rules inconsistently — mean switching rates from 11.1% to
> 65.8% — while explicit descriptions elicit the rule on every instance (Liang et al.). On the cost side, 80.2% of
> 1,113,616 public Gherkin steps are exact duplicates. A throwaway probe on a skreeep2 kernel confirmed the tool facts:
> a Scala step algebra rejects a mistyped step at compile time; cucumber-scala compiles it and reports the step
> undefined only at run time, and its step text and body sit in one call, so the signature cannot be frozen apart from
> the body. What the evidence supports already ships: frozen executable tests and the holdout (item 1), property
> feedback with minimal counterexamples (item 2), typed holes (item 4). Open follow-up: TDAD's test map — show the agent
> which tests cover the files it touched — which the engine's module map could provide.

> **Status: Shipped** (2026-10-01) — the test-map follow-up, at module level and enforced rather than advised. The
> problem is real for frontier agents: 16.1% (Claude 4.7) to 45.2% (Qwen3) of applied patches break a pre-existing test
> (TensorBench). Selecting the relevant existing tests helps independently of TDAD: a minimised regression subset gave
> an 8.0%-12.9% relative increase in issue resolution across Agentless, SWE-Agent and Trae (TestPrune). TDAD's own
> numbers carry caveats in its full text: resolution fell 31% → 29% as agents produced more empty patches (26% vs. 14%),
> the Phase 2 gain is 6/25 → 8/25 with a 0% regression rate in both arms, and only two smaller, locally-run models were
> tested. The gap in our gate was measured in skreeep2: turn-end ran tests only for the modules owning a touched file,
> and 21 of 29 modules have dependents whose tests it never ran (`core` 13). Running them costs 56.9 s for a `core`
> edit, 24.4 s without `bench`, whose real-time budget cases were red in 3 of 3 runs. *Built:* `Gate.moduleMapRows`
> writes each module's dependents with tests (Mill's `transitiveModuleCompileModuleDeps` of `<id>.test`) as a fourth
> map column, minus a project's `downstreamExcluded` (skreeep2: `bench`); the engine runs their `turnEndTests` at
> turn-end after the vericoding stage, only when nothing earlier blocked, and a red dependent blocks. Checked against
> Mill's compile classpaths in skreeep2: `rows 30 mismatches 0`, `core` lists 12 dependents. TDAD's symbol-level map is
> still open: most skreeep2 test modules emit no SemanticDB yet. *Confirmed live* (2026-10-01): a gated headless
> session that reversed `Tree.leavesOf` in `core` was blocked at turn-end with `turnEndTests: app (downstream of core)
> (failed)` — `FEEDBACK: 10 passed, 1 failed (app.test)` — the remedy line, and the 11 dependents left unrun named;
> after two blocks the third Stop released the turn as an unverified completion, as designed. The message's closing
> line had still claimed "Tests are NOT run by this gate"; since b1c8ac4 it names the modules whose turnEndTests ran.
>
> **Follow-up shipped** (2026-10-01) — delete-only turns are checked. A probe in skreeep2 found the Stop gate blind to
> deletions: a session whose only change was deleting `RelativeTime.scala` from `core`, a file dependents import, ended with
> `rc=0 secs=1` and no output, while `./mill -k app.compile` failed with `Not found: type RelativeTime`. The touched-file
> list kept only files that still exist. Now a deleted `.scala`/`.sc` file (also a staged delete, or the old name of a rename)
> brings its owning module into the check, so the turnEndTests of its dependents run too, and any error under the directory of that
> module blocks; a file already deleted at SessionStart is not counted. After scala-llm-template 834d4ea the same
> probe gave `rc=2 secs=15`, blocked by `turnEndTests: app (downstream of core)`.

**8. Code-execution surface for the semantic tools.** *Closes:* context bloat and multi-step tool fragility.
*Evidence:* Anthropic's example fell "from 150,000 tokens to 2,000 tokens"; Felendler et al. confirm the gain and the
enlarged attack surface. *Build:* expose ScalaSemantic and purrism as a Scala library a scala-cli script calls, executed
in the `workspace-write` sandbox. Side benefit: the compiler now type-checks the agent's *tool use*.

> **Status: Outdated** (2026-10-01) — investigated, not pursued as a new surface. Code-as-tool-use does win in the
> literature: CodeAct was the higher-success format for 12 out of 17 evaluated LLMs (up to 20% higher success rate),
> programmatic tool calling matched or beat JSON calls in 11 of 14 models on BFCL v4, and Anthropic's own version cut
> tokens from 43,588 to 27,297 (37%). But the gain tracks tool count and result size — Bifrost's input-token cut rose
> from 58.2% at 96 tools to 92.8% at 508 tools — and it has measured costs: programmatic calling underperforms JSON by an
> absolute 14.1% in the parallel categories (Patel et al.), and CE-MCP scored lower task fulfillment on a subset of
> three-server tasks through orchestration bugs such as a missing conditional branch (Felendler et al.). Anthropic lists
> it as less beneficial for "quick lookups with small responses" and when Claude "should see and reason about all
> intermediate results". Our case sits there. Claude Code already defers the scala-semantic tool schemas (load on
> demand), removing the definitions cost. In the ScalaSemanticMCP server log, symbol queries return little
> (find_usages median 911 characters) and the large results are reading tools whose content the agent must see
> (annotated_source median 11790). A throwaway probe on skreeep2 — subtypes of `Host` with a reference count each — took
> 7 MCP calls and 4576 characters of results, against one scala-cli run of 1063 characters of script plus 369 of output
> at 0.87 s warm: a real but small saving. The compiler did reject a wrong import before the script ran. Nothing needs
> building: the analysis library is published and the agent already runs `scala-cli` through its shell, so the new attack
> surface Felendler et al. warn about does not arise. Reopen if sessions show long chains of symbol queries.

**9. Stainless on the pure kernel.** *Closes:* invariant violations in the core. *Evidence:* vericoding succeeds where
the verifier is strong and the spec formal (82% in Dafny), and prose adds nothing. *Build:* extend the existing Mill
Stainless integration with a skill: the human writes `require`/`ensuring`, the LLM fills the body until Stainless
accepts.

> **Status: Shipped** (2026-09-28) — in three repositories. *ScalaSemanticMCP:* a syntax-only `contract_clauses` tool
> lists every def's `require`/`ensuring`/`decreases` with a shape hash of each clause's syntax tree, so reformatting
> never reads as a change and any edit to a clause does. *scala-llm-template:* a Stainless Mill recipe, a frozen
> kernel registry (`.dsh/policy/kernels.tsv`), and a turn-end vericoding stage in the vendored engine — the contracts
> of every registered kernel are snapshotted at SessionStart and are the human's: a changed, removed or added clause, a
> missing snapshot, or a new `assume`/`@extern`/`@ignore` blocks; then Stainless runs, and an invalid verification
> condition, a body Stainless cannot compile, or an unimplemented body blocks, while a solver timeout is only a note;
> plus a `vericoding` skill. *skreeep2:* the first kernels, three audio/time helpers, verified in place; an agent
> filled the bodies in a live gated session, and the existing callers delegate to them with unchanged behaviour.
> Spec: `docs/superpowers/specs/2026-09-27-stainless-vericoding-design.md` (this repo).
>
> What measurement changed: the real tool, not the design, settled three things. An implication written `a ==> r == c`
> does not type-check (`==>` binds like `==`). One human-written postcondition compared its input with NaN itself, so no
> body could satisfy it — the live session surfaced it, the agent stopped and asked instead of editing the contract, and
> the human revised the contract. And `require`/`ensuring` in production code are not erased by default: they run on
> every call unless Stainless's `StaticChecks` is imported. The live session also proved the guard's point — a contract
> edit the human asked for in chat was still blocked until the human reviewed it and started a new session. Open: an
> escape written as script-side file writes is not caught by the engine; the turn-end tests have no time cap of their
> own.
>
> **Follow-up shipped** (2026-09-29) — both open items closed in the engine (scala-llm-template `kit/harness`,
> re-vendored into skreeep2). A frozen path is now checked after the fact at turn-end, whatever route wrote it: git's
> changed paths are matched against the frozen rows (session baseline respected, so the human's in-flight work is not
> blamed on the agent), and git-ignored frozen files are hashed at SessionStart and re-checked. Before a command runs,
> an interpreter or inline shell (`python3 -c`, `sh -c`, a writing `awk`) whose text names a frozen path is denied.
> Each module's turn-end tests run under their own time cap inside the Stop hook's budget; a timeout is a note, not a
> block. One exemption, a human decision: Claude Code itself writes `.claude/settings.local.json` on "don't ask again",
> so a change there is a loud note rather than a block. Live check in skreeep2: the inline-interpreter write was denied
> before it ran; asked to compute the path in a script instead, the agent refused — the deny message had named that
> route as a bypass — so the post-hoc layer was proven with a human-side edit, which blocked every turn until the
> attempt cap released it. Documented blind spot: an agent with arbitrary code execution can still rewrite a
> git-ignored frozen file together with its hash record; only a sandbox closes that. Spec:
> `docs/superpowers/specs/2026-09-28-frozen-path-bypasses-design.md` (this repo).

## Tier 4 — long range

**10. Type-constrained decoding via a local model.** Sampler-level enforcement halved compile errors for TypeScript
(Mündler et al.), but needs logit access and a type system tractable as a prefix automaton; Scala's is not, and hosted
models expose no logits. Revisit if dsh runs an open-weight model locally and a Scala-subset grammar is worth building.

## Tier 5 — from the feature-level refresh

Items 1–9 were built and measured on remove-and-rebuild tasks, where sonnet solved everything ungated. Feature-level
benchmarks show where current frontier agents still fail (details in [the taxonomy, class 8](llm-code-defect-taxonomy.md)):
FeatBench's best resolved rate is "only 29.94%", with regressions from scope creep the main cause; FeatureBench has
Claude 4.5 Opus at "only 11.0%" against "74.4%" on SWE-bench; SWE-Chain shows regression precision for Claude-Opus-4.7
falling to 7.8% when the spec is a bare problem statement, against 88.1% once it adds expectations and constraints.
FeatureBench's leading failures (NameError, TypeError, AttributeError) are compile errors in Scala, so the open
classes are regressions and unpinned behaviour. Ranked:

**11. Measure the gate on feature tasks from a short spec.** *Closes:* the open question whether the gate pays where
agents regress. *Evidence:* FeatBench, SWE-Chain (above); this repo's three gate studies (none on feature work).
*Build:* in `eval/gate-ab`, feature tasks in skreeep2 given as a short natural-language request with no signatures or
scaladoc, scored on the new behaviour's hidden spec *and* the existing tests (regressions counted separately); arms
ungated vs gated `full` vs gated `safety`, same day. Decides whether `full` should come back as the default for
feature work. Do first: it is the evidence the next two items wait on.

> **Status: Measured** (2026-10-10) — 4 plain-language feature requests on skreeep2 (a V-Pot pan command, a tuning reference,
> mode-key LEDs, exclusive solo), each needing an edit to shared code; hidden specs and reference patches validated
> against every existing test module and the holdout. Ungated screen: 5 of 8 solved; fr-pan and fr-exclusive-solo kept.
> Same-day test phase on the kept tasks: gf 4 of 4, gs 4 of 4, u 4 of 4 solved; regressions 0 in every run of the
> study. Decision: **no gain for either mode**; gf − gs = 0, so the `safety` default stands. Cost: median wall gf
> 208 s, gs 117 s, u 48 s; median cost gf USD 0.348, gs USD 0.355, u USD 0.161.
> The regressions the feature-level benchmarks report did not appear at this size. The failure that did appear is an
> edit-tool failure: all 3 screen failures were a python `str.replace` through Bash whose search text matched nothing
> (8-space indentation where the file has 6), so nothing changed, everything still compiled and passed, and the agent
> reported done. Observed outside the decision rule: gated runs used the Edit tool in 7 of 8, ungated runs on the kept
> tasks used python on `.scala` in 8 of 8. Two harness findings: a gate false positive (the turn-end semantic check
> reported lines using `!` or `!=` as "did not resolve" in 3 of 8 gated runs), and wartremover's ban on default
> arguments forced two requests to be reworded before any run. Limits: n = 4 per arm; the kept tasks did not fail
> again (regression to the mean); sonnet only.
>
> **Follow-up shipped** (2026-10-11) — two fixes from the study. (1) The `!` false positive: ScalaSemanticMCP's
> `resolve_references` skips the operator of a prefix application, which the compiler records nowhere; `!=` and `==`
> were never affected. (2) A `tool-policy` rule (scala-llm-template `kit/harness`) denies Bash commands that edit Scala
> source through an interpreter or `sed` in place — the silent no-op's route — and points to the Edit tool, which
> refuses an edit whose text is not found. Measured alone (arm `p`, only that hook) against ungated on fr-pan and
> fr-exclusive-solo, 8 runs each: p 8 of 8, u 8 of 8 solved, false_done 0 in both — **failure not reproduced**, so its
> effect on outcomes is unmeasured. Mechanism observed: 7 of 8 `p` runs tried an interpreter edit on Scala, all 7 were
> blocked, and each then used the Edit tool and solved the task; no run routed around the rule. Cost: median wall p
> 49 s, u 46 s; median cost p USD 0.162, u USD 0.158. Correction: "gated runs used the Edit tool in 7 of 8" above
> counted Edit tool calls, including calls the semantic guard blocked (with the full gate it routes `.scala` writes to
> the MCP `annotated_source` write path).

**12. Spec template with expectations and preservation constraints.** *Closes:* regressions and unpinned behaviour
at the spec level. *Evidence:* SWE-Chain's granularity study — "expectations and constraints are essential to avoid
regressions"; FeatureBench's interface ablation. *Build:* a task-spec skill (goal, expected behaviour, interfaces
touched, what must not change) that the agent fills before editing and the human approves; cheap, and testable in
item 11 as a third arm.

> **Status: Not pursued** (2026-10-11) — item 11 found no regressions in 20 runs of feature requests, so there was
> nothing for preservation constraints to act on; the failure that did appear was an edit-tool failure, addressed by
> the tool-policy rule under item 11.

**13. Scope guard at turn-end.** *Closes:* scope creep. *Evidence:* FeatBench's call for "mechanisms that can control
the agent’s level of aggressive implementation". *Build:* a turn-end note listing modules and public symbols the turn
changed that the approved spec (item 12) does not name. Build only if item 11 shows regressions that the downstream
tests miss.

> **Status: Not pursued** (2026-10-11) — its condition was not met: item 11 showed no regressions.

## Tier 6 — from the long-horizon refresh

Item 11 found sonnet solving small feature requests ungated; the only failure seen was a silent no-op edit followed by a
false "done". Long-horizon studies show where failure does grow (details in [the taxonomy, class 9](llm-code-defect-taxonomy.md)):
on SlopCodeBench "the highest checkpoint solve rate is 17.2%" and "erosion rises in 80% of trajectories", with prompts
unable to change the slope and no linter or test feedback ever tried; false success is "75.8%" of failed AppWorld
coding-agent trajectories with status claims, and "26% of failed trajectories fabricate success" on Terminal-Bench.
The SonarSource minimal-pair study is the counterweight: cleanliness did not change pass rate, only tokens ("7 to 8%")
and file revisitations ("34%"). Ranked:

**14. Measure chained feature work.** *Closes:* the open question whether anything in this repo's gate pays once the
agent builds on its own earlier code. *Evidence:* SlopCodeBench (above); SWE-Chain (Tier 5). *Build:* in
`eval/gate-ab`, a chain of 3–4 feature requests in skreeep2, each run continuing from the previous run's result; each
step scored on its hidden spec, on every earlier step's hidden spec (regressions) and on an erosion figure for the
touched methods; arms ungated vs gated `safety`. Decides whether items 15–16 are worth building. Cost: about one
campaign the size of item 11's.

**15. Claim-versus-diff check at turn-end.** *Closes:* false success of the shape seen locally (the agent says done,
the edit never landed). *Evidence:* Advani, Zhao et al. (above); item 11's 3 silent no-ops. *Build:* the Stop hook
compares the files the turn says it changed with the working-tree diff and blocks a turn that reports completion over
an empty or partial diff; deterministic, no judge (the paper's judges stayed under "AUROC 0.65"). Cheap; overlaps
with the interpreter rule under item 11, which already removes the one route seen, so build only if item 14 or
everyday use shows false "done" through another route.

**16. Erosion signal in the loop.** *Closes:* structural erosion over checkpoints. *Evidence:* SlopCodeBench, where
prompts did not bend the slope and no deterministic feedback was tried. *Build:* a scalameta metric (cyclomatic
complexity × √SLOC per method, as SlopCodeBench's mass, plus duplicated blocks) computed on touched methods; the
turn-end note reports methods whose mass grew past a threshold, and purrism or the semantic tools suggest the extract.
Build only if item 14 shows erosion rising in skreeep2 across a chain; the minimal-pair study says the short-run
payoff is effort, not correctness.

## Reengineering Kit status

> **Status: Shipped** (2026-09-26) — the harness engine every shipped item above lives in (the gate, its hooks,
> `verify-diff`, the rewrite stage, the memorisation check) moved out of skreeep2 into `scala-llm-template`'s
> `kit/harness`, its one editable home, and is vendored into each project as `.dsh/engine/` with a stamp the
> installation check verifies; skreeep2 is its first consumer, and a scratch second project is gated end to end by
> the kit's own recipe. The Mill tasks the engine calls by name ship as a Mill recipe beside it. This replaces the kit
> design's plan to host the engine in the DSH profile (Plan C, now marked superseded). Spec:
> `docs/superpowers/specs/2026-09-26-harness-engine-vendoring-design.md` (this repo); where the gate lives is in
> [Verification and the Gate](../reengineering/verification-and-the-gate.md). Open: the engine supports Mill only —
> sbt and scala-cli projects get a named stand-down, not checks.

## What not to do

- Do not add more prose rules to CLAUDE.md for security or style: prompting did not reduce vulnerability counts
  (Kharma et al.), and moved idiom only partway (FPEval). Put the check in the gate.
- Do not let the agent's own tests be the completion signal (Tier 1, item 1).
- Do not try to make the whole module formally verified; the vericoding numbers support small pure kernels only.

## See Also

- [What Goes Wrong in LLM-Generated Code](llm-code-defect-taxonomy.md)
- [Where Types and Properties Buy the Most](where-types-and-properties-buy-most.md)
- [Harness and Tooling Gaps](harness-and-tooling-gaps.md)
- [Alternative Paradigms and the BDD Seam](alternative-paradigms-and-bdd-seam.md)
- [The Reengineering Contract](../reengineering/reengineering-contract.md)
