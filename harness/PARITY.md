# Parity with gate-ab (GA)

GA = `scala-llm-template/eval/gate-ab` (read-only reference; retired on 2026-10-12, last at scala-llm-template commit
`98f5652`). To re-run a tier: `git -C <scala-llm-template> worktree add /tmp/ga 98f5652`, then pass
`--ga /tmp/ga/eval/gate-ab`. Every tier below ran on copies in a scratch dir; the run
roots `~/gate-eval*`, skreeep2, skreeep2-holdout and GA were only read. Results from 2026-10-12, branch
`agent-eval-harness`. Spec §7 numbers the tiers; the build tier (§7.2) is Task 19, live agents (§7.6) Task 20.

## Reproduce

The six roots hold 28 GB (repo trees, score trees, tarballs), so only what the static and report tiers read is copied:

```bash
S=<scratch>; mkdir -p $S/parity-roots; cd ~
rsync -a -m --exclude 'repo/' --exclude 'score-tree/' --exclude '/*/head/' --exclude '/*/base/*/tree/' \
  --include '/*/base/*/hidden/**' --exclude 'holdout/' --include '*/' --include '*.json' --include '*.jsonl' \
  --include '*.diff' --include '*.log' --include '*.txt' --include '*.md' --include '*.sha256' --include '*.out' \
  --include 'KEPT' --exclude '*' gate-eval gate-eval-control gate-eval-feature gate-eval-hard gate-eval-interp \
  gate-eval-chain $S/parity-roots/                                          # 27 MB, 131 run dirs
ln -s ../gate-eval/base $S/parity-roots/gate-eval-control/base               # -control's base is a symlink to gate-eval's
rsync -a ~/gate-eval/base/noise/tree/ $S/arm-inputs/noise-tree/             # one prepared task tree for the arm tier

R=$S/parity-roots
H="scala-cli run harness --"
$H parity-static  --experiment harness/experiments/gate-ab.conf $R/gate-eval $R/gate-eval-control $R/gate-eval-feature \
                  $R/gate-eval-hard $R/gate-eval-interp $R/gate-eval-chain
$H parity-arms    --experiment harness/experiments/gate-ab.conf --work $S/arms-work --task noise \
                  --base-tree $S/arm-inputs/noise-tree --snapshot $R/gate-eval/gated-settings.json
$H parity-reports --experiment harness/experiments/chain.conf --work $S/reports-work $R/gate-eval $R/gate-eval-control \
                  $R/gate-eval-feature $R/gate-eval-hard $R/gate-eval-interp $R/gate-eval-chain
$H parity-erosion --experiment harness/experiments/chain.conf --work $S/erosion-work \
                  $R/gate-eval-chain/chain-reference-erosion.json
```

Each subcommand exits 0 when everything is equal, 1 otherwise, and prints one line per mismatch. `--ga <dir>` overrides
GA's location (default `<template>/eval/gate-ab`); `parity-static --originals <dir>` says where the copied roots live
originally (default `$HOME`).

## Static tier (§7.1)

`parity-static` runs GA's `score.py` functions in python (`sys.path.insert(0, GA); import score`, the snippet is a string
in `parity/StaticParity.scala`, `PYTHONDONTWRITEBYTECODE=1` so GA gets no `__pycache__`) and the harness core in process
on every run dir under `runs/` and `invalid-runs/`, then compares field by field as parsed JSON: numbers numerically
(ujson `1` = python `1.0`), object key order checked once the values agree. Fields per run: `parse_transcript` minus
`init` (12), `gate_blocks`, `env_leaks(arm)`, `first_clean_compile`, `contamination` (`GA_ROOT` = the original root,
`GA_SKREEEP2`, `HOME` as at run time, `CLAUDE_CONFIG_DIR` unset; the run dir is its original path), `tampering`,
`build_edits`, `hardcoding`, `suite_fq`, and per saved `*-score.log`: `weaver_counts`, `hidden_counts`, `suite_passes`,
`build_broken`, `parse_mill_failures` (modules from `resolve-score.log` / `resolve-all.log` as GA parses them, fed to both
sides).

| root | runs | fields | mismatches |
|---|---|---|---|
| gate-eval | 63 | 2520 | 0 |
| gate-eval-control | 8 | 320 | 0 |
| gate-eval-feature | 20 | 800 | 0 |
| gate-eval-hard | 8 | 320 | 0 |
| gate-eval-interp | 16 | 640 | 0 |
| gate-eval-chain | 16 | 640 | 0 |
| **total** | **131** | **5240** | **0** |

The first pass found 6491 mismatches, all in `transcript.texts` / `transcript.hook_texts` of 120 runs: a port bug, fixed
(see below).

## Arm setup (§7.4)

`parity-arms` sets up the same run dir (`<work>/root/runs/noise-<arm>-1`) twice per arm: once with GA's own run.sh
lines from the argument check through `git tag base`, sourced with `lib.sh` (`sh -c`, `GA_ROOT=<work>/root`, so the
engine install, `ga_policy_only_settings`, the arm-t python and the sed lines are GA's), once with `Run.setupOnly`. It
compares `.claude/settings.json`, `.mcp.json`, the run dir's `settings.json` and every file under `.dsh/` except
`.dsh/.state`, byte for byte.

| arm | files | result |
|---|---|---|
| u | 4552 | equal |
| g | 4552 | equal |
| t | 4552 | equal |
| gf | 4683 | equal |
| gs | 4683 | equal |
| p | 4683 | equal |

6 arms, 27705 files, 0 mismatches. Inputs: the prepared `noise` tree of `~/gate-eval` and its frozen
`gated-settings.json` (the real snapshot; Task 9's tests use a synthetic one).

## Reports (§7.3)

`parity-reports` copies each root twice, runs GA `report.py <runs>` on one copy and `Report.main` on the other, compares
`results.csv`, `summary.md` and the outcome (both succeed, or both fail with the same message); on a chain root
(`chain-reference-erosion.json` or any `chain.json`) also `chain_report.py <root> <chain>` against `ChainReport.main`:
stdout and `chain-report.md`.

21 outputs, 0 differ. On -feature, -interp and -chain GA's `report.py` refuses the extra arms (`report.py summarises arms
g, u, t only; found gf, gs` / `p` / `gs`); the harness fails with the same text, writes the same `results.csv` and, after
the fix below, the same empty `summary.md`. The summary text itself is therefore compared on gate-eval, -control and
-hard; the chain report on -chain.

## Chain reference erosion

`parity-erosion`: HEAD (skreeep2 `6bf6c1b`, `git archive` + one commit, as warm-cache.sh builds it), the four reference
patches (fr-pan, fr-exclusive-solo, fr-mode-leds, fr-tuning, `harness/tasks/*/reference.patch`) applied with
`git apply -3` in order, `Erosion.measure` after each step against GA's recorded `chain-reference-erosion.json`:

| step | task | erosion | methods | total_mass |
|---|---|---|---|---|
| 1 | fr-pan | 0.0 | 15 | 93.98 |
| 2 | fr-exclusive-solo | 0.5425 | 16 | 107.296 |
| 3 | fr-mode-leds | 0.4275 | 21 | 136.17 |
| 4 | fr-tuning | 0.2509 | 49 | 231.966 |

All four rows equal GA's in every field. Not a unit test: it needs skreeep2's sources.

## Build tier (§7.2)

12 run dirs, each copied twice from the original root (`<id>-A`, `<id>-B`, `rsync -a --exclude /score-tree/`, the old
`score.json` removed) into a scratch root that also holds the original's `head/`, `head-baseline.json` and
`base/<task>/` for the tasks used. Per run, back to back on 2026-10-12, Mill builds included:

```bash
GA_ROOT=$R GA_SKREEEP2=$S/skreeep2-stub PYTHONDONTWRITEBYTECODE=1 python3 GA/score.py $R/<runs>/<id>-A
GA_ROOT=$R scala-cli run harness -- rescore --experiment harness/experiments/gate-ab.conf \
  --set root=$R --set target.repo=$S/skreeep2-stub $R/<runs>/<id>-B
```

`$R` is the copied root, `$S/skreeep2-stub` an empty dir (both scorers use skreeep2 only as a contamination path; neither
points at the original). The holdout is exported by `git archive` from skreeep2-holdout on both sides. The two
`score.json` are compared as parsed JSON (numbers numerically), ignoring `wall_seconds` (the only duration/timestamp
field in score.json; it came out equal anyway, both read it from run.json), then key order (nested too).

| run | arm | outcome (both sides) | GA s | harness s | result |
|---|---|---|---|---|---|
| gate-eval/runs/mcu-g-1 | g | solved, contamination 13 | 78 | 78 | equal |
| gate-eval/runs/scope-view-t-1 | t | solved | 78 | 79 | equal |
| gate-eval/runs/noise-u-1 | u | false_done | 81 | 80 | equal |
| gate-eval/runs/mixer-modes-t-2 (*) | t | timed_out (not solved, not false_done) | 82 | 81 | equal |
| gate-eval/invalid-runs/mcu-g-3 | g | unsolved, regression `control.midi.test` (count 1), api error | 82 | 80 | equal |
| gate-eval/invalid-runs/noise-u-3 | u | unsolved, hidden + regression score timeouts (`<timeout>`, count −1) | 1825 | 1826 | equal |
| gate-eval/invalid-runs/backpressure-u-3 | u | unsolved (agent exit 1), api error | 77 | 84 | equal |
| gate-eval-feature/runs/fr-pan-gf-3 | gf | solved | 81 | 81 | equal |
| gate-eval-interp/runs/fr-pan-p-1 | p | solved, contamination 19 | 81 | 81 | equal |
| gate-eval-chain/runs/fr-mode-leds-gs-1 | gs | solved, seeded chain step 3 (`seed_sha256` set) | 80 | 80 | equal |
| gate-eval-chain/runs/fr-pan-u-1 | u | false_done, chain step 1 (unseeded) | 81 | 76 | equal |
| gate-eval-hard/runs/adsr-u-1 | u | solved | 75 | 74 | equal |

12/12 equal, key order identical in all 12. Bytes: 2 of 12 identical; the other 10 differ only as difference 10 below
(`1.0` vs `1`, `·` vs `·`). Total ≈ 90 min, two thirds of it noise-u-3 (the hidden suite hits the 600 s cap and the
regression run the 1200 s cap on both sides).

(*) No run in the six roots timed out, so `mixer-modes-t-2` is a synthetic variant: both copies' `run.json` got
`"timed_out": true` (everything else original). Unsolved coverage is thin: the only unsolved runs in this tier are the
three invalid-runs `mcu-g-3`, `noise-u-3` and `backpressure-u-3`, whose agent died on an API 429 within seconds; no run in
which an agent worked and failed was rescored here.

## Live agents (§7.6)

`experiments/live-smoke.conf` (all six arms; `cap-seconds = 1200`), fresh root `~/agent-eval-live`, fr-pan, rep 1,
2026-10-12. `warm-head`, then `baseline` (prepare of an FR task reads `head-baseline.json`, as GA's prepare.sh), then
`campaign --set 'harness.tasks=[fr-pan]' --set 'harness.reps=[1]' --set 'harness.arms=[claude-u]'`, then the same with
`[codex-u,gemini-u]`, later `[agy-u]`. The dsh arms (profile `scala-headless`) were run by the user with `[dsh-u]`, then `[dsh-gs]`.

| arm | agent / model | score error | solved | wall s | turns | tokens in / out / cache read | cost USD | notes |
|---|---|---|---|---|---|---|---|---|
| claude-u | Claude Code 2.1.296 / sonnet | none | yes | 65 | 17 | 32 / 4996 / 485601 | 0.2257 (reported) | MCP `scala-semantic` connected, used; contamination `Bash: /test` (a `grep -v "/test/"` argument read as a path, GA's heuristic) |
| codex-u | codex-cli 0.148.0 / gpt-5.5 | none | yes | 307 | — | 99814 / 6506 / 2513152 | none reported; ≈0.50 at GPT-5 list prices (gpt-5.5's not known here); ChatGPT login, not billed per token | MCP used (26 calls); global `~/.codex/AGENTS.md` and `~/.codex/skills` loaded, contamination flags them; known limitation (the user declined a separate `CODEX_HOME`, agents/NOTES.md) |
| gemini-u | gemini-cli 0.38.1 / gemini-2.5-pro | `no result event` | no | 5 | — | — | 0 | unverified live (IneligibleTierError): the account login is refused before any model call; nothing of the adapter verified past argument parsing. The user has subscriptions only; next: upgrade gemini-cli, log in again |
| agy-u | Gemini via agy (generic, diff-only): agy 1.3.3 / gemini-3.1-pro-high | none | no (false_done) | 358 | — | — | unknown (subscription) | the user's global superpowers plugin made it stop for design approval: empty diff, exit 0; `regressions` `control.test` = the hidden spec does not compile on the untouched tree. Global MCP and plugins load, state written under `~/.gemini/antigravity-cli` (agents/NOTES.md) |
| dsh-u | dsh 0.1.5-rc.1 / deepseek-flash | none | yes | 241 | 30 | 51868 / 26349 / 1378432 | null: dsh reports no cost and `prices` is empty | gate blocks none (arm u, no hook ran); MCP not loaded (profile's `scalasemantic-mcp.sh` missing in the tree); contamination `bash: /out` ×5 (`grep -v "/out/"`, GA's heuristic) |
| dsh-gs | dsh 0.1.5-rc.1 / deepseek-flash | none | no (timed out at the 1200 s cap, not false_done) | 1200 | 107 | 179266 / 133476 / 14859776 (incl. subagent sessions; rescored 2026-10-13, top-level alone 128993 / 100906 / 13899648) | null (as dsh-u) | hidden 100 %, no regressions, holdout passed: apparently the agent had finished and the cap hit during its final Stop (turn-end) hook (inferred from the transcript tail). Gate blocks semantic-guard 7, tool-policy 3 (rescored after the fix below; first score also had turn-end 1). MCP not loaded; contamination: the agent read `~/.dsh` (settings.yaml, presets) and the run's `holdout/` path (blocked by tool-policy) |

Reruns, rep 2 (2026-10-12/13, after the follow-ups: scala-semantic launcher for dsh, `prices.deepseek-flash` at
DeepSeek's peak rates, agy with `--disable-slash-commands`; dsh-gs with `--set harness.cap-seconds=2700`). Rep 1 dsh
costs from rescoring with the same prices: dsh-u 0.0554, dsh-gs 0.3031 (incl. subagent sessions; 0.2432 without).

| arm | solved | wall s | turns | tokens in / out / cache read | cost USD (peak) | notes |
|---|---|---|---|---|---|---|
| dsh-u | yes | 526 | 48 | 109981 / 61862 / 4123648 (incl. subagent sessions) | 0.1320 | MCP tools listed, none called; contamination `subagent: /` |
| dsh-gs | yes | 1060 | 97 | 208481 / 132954 / 15138816 (incl. subagent sessions) | 0.3129 (incl. subagent sessions; 0.2531 without) | hidden 100 %, no regressions, holdout passed; gate blocks semantic-guard 9, semantic-guard-sealed 2, turn-end 1 (a live Stop block); 48 scala-semantic MCP calls; contamination `subagent: /` |
| agy-u | yes | 754 | — | — | unknown (subscription) | `--disable-slash-commands`: no plugin stop; contamination, gate blocks, env leaks unchecked (no transcript) |

dsh-gs rep 2 finished inside the old 1200 s cap, so rep 1's timeout says little about the arm.

`first_clean_compile_turn` above `turns` (claude-u rep 1: 28 vs 17) is GA's own semantics, not a port bug. `turns` is the
`result` event's `num_turns` (17); a tool call's turn counts `assistant` stream events, and Claude Code writes one such
event per content block (29 events, 16 message ids in this transcript), so the compile call sits at event 28. GA
`score.py` at `98f5652` (`parse_transcript` + `first_clean_compile`) gives the same 17 and 28 on this transcript.

Adapter defect found live (fixed, failing test first): Codex `input_tokens` counted the cached tokens too (OpenAI usage
includes `cached_input_tokens` in `input_tokens`), so it was not comparable with Claude Code's uncached figure; the adapter
now subtracts them. Test: OtherAgentsTest "codex parse: live exec --json capture" (fixture captured from the run). The
codex run was rescored (`rescore`), not rerun.

Second live defect (fixed, failing test first): the dsh adapter turned every blocking hook's text into a hook text, so the
dsh-gs tool-policy block "holdout.test runs the SEALED holdout suite — the gate runs it at turn-end" counted as a turn-end
block. A PreToolUse block reaches the agent as the tool result (as in Claude Code) and is counted from there; only Stop
blocks are hook texts now. Test: DshTest "parse: live PreToolUse blocks count once per blocked call, never as turn-end"
(fixture captured from the run). dsh-gs was rescored (`score`); dsh-u has no hook events and is unaffected.

## Port bugs found by parity (fixed, each with a failing test first)

| fix | test |
|---|---|
| System event leaves were collected in hash order (the event map was filtered before the walk); GA walks the dict in key order. Changed `texts` / `hook_texts` of 120 old runs. | TranscriptTest "system event leaves in the event's key order" |
| An unknown arm made `Report.main` fail before touching `summary.md`; GA opens it for writing first, so it is left empty. | ReportTest "unknown arm: csv written, summary.md emptied, GA's error" |
| A truncated score.json in chain check was recorded as `IncompleteParseException: …`; GA: `JSONDecodeError: …`. | ChainCheckTest "an unparsable score.json is recorded as JSONDecodeError with ujson's message" |
| A corrupt run.json made score.json's error `ParseException(…)` / `IncompleteParseException(…)`; GA: `JSONDecodeError(…)`. | ScoreTest "pyRepr of exceptions (GA repr(e))" |

## Intended differences

Each was ruled during the build (progress ledger) or is inherent to the port; the test pins the harness behaviour.

| # | difference | reason | test |
|---|---|---|---|
| 1 | A failing `arm <name>` commit or `git tag base` stops the run (`arm <a>: commit failed: …`, `arm <a>: tag base failed: …`). GA ignores both and goes on with no base tag, so `final.diff` comes out empty. | A run without a base tag scores as unsolved for a harness fault; an error is honest. Task 9 ruling. | RunTest "arm commit and base tag failures are fatal (GA continues)" |
| 2 | `settings still mention <holdout src>` is checked in the Claude Code adapter's setup, right after the snapshot rewrite, before the arm's setup steps. GA checks after the arm edits. | The adapter owns the settings file; outcome identical unless a setup step writes the holdout source into the settings. Task 9 ruling. | ClaudeCodeTest "setup fails when settings still mention the holdout repo" |
| 3 | The want-list in `arm must be one of …` (run) and `GA_ARMS: invalid arm 'x' (want …)` (campaign) is in sorted arm order (`g, gf, gs, p, t, u`); GA hard-codes `g, u, t, gf, gs or p`. | Arms are experiment-defined; the conf order is not recoverable after HOCON rendering. Task 12 ruling. | MainTest "run with an unknown arm exits 1 with gate-ab: arm must be one of …, never reaching the runner"; CampaignTest "GA_ARMS invalid arm dies, no runs; GA_ARMS empty dies; campaign rejects arm x beside gf; campaign rejects arm x beside p"; RunTest "unprepared task and bad run id die before anything is written" |
| 4 | When prepare-chain succeeds but `chain-validation.txt` is still missing, the campaign dies `chain validation failed (see …)`. GA's grep on the missing file exits 2 and the chain runs on. | Stricter; reachable only through a broken prepare. Task 12 ruling. | CampaignTest "prepare-chain succeeding without chain-validation.txt dies chain validation failed, runs nothing" |
| 5 | Erosion runs scalameta in process; a parse failure reads `<file>: <scalameta message>`. GA ran `erosion.scala` through scala-cli and reported `erosion.scala exit N`. | No scala-cli process per measurement; the file and message are more useful. Figures identical (chain reference line above). Task 13. | ErosionTest "measure failure text is `<file>: <scalameta message>`" |
| 6 | Chain check's catch-all records `<JVM class>: <message>` for failures with no Python counterpart, and JSON errors as `JSONDecodeError: <ujson message>`; score.json's catch-all likewise `JSONDecodeError('<ujson message>')`. GA: python's exception names and json's messages. | The exception type names are mapped where a Python one exists; messages come from the JVM libraries. Task 13. | ChainCheckTest "an unparsable score.json is recorded as JSONDecodeError with ujson's message", "any other failure is recorded as `<class>: <message>`"; ScoreTest "pyRepr of exceptions (GA repr(e))" |
| 7 | Report's `Unreadable score.json skipped:` line uses GA's wording for a blank file and Errno 2/13/21, ujson's message for a syntax error. ujson rejects `NaN` / `Infinity`, which python's json loads, so such a score.json is unreadable here. | The JSON parser is ujson; GA never writes NaN (score.json values are counts, ratios, costs from the agent). Task 14. | ReportTest "unreadable score.json: ujson wording for syntax errors, NaN rejected" |
| 8 | The agent and Mill processes (and, since 2026-10-13, setup `run` steps and every captured git command) start in their own process group through `/usr/bin/perl -e 'setpgrp(0,0); exec { $ARGV[0] } @ARGV or …exit 127'` and are killed with `kill -TERM -<pgid>`, 5 s grace, `kill -KILL -<pgid>`, plus a sweep of the JVM-visible descendants. GA's timeout.py starts the child in a new session (`start_new_session=True`) and `killpg`s it. | The JVM cannot setsid/setpgid a child; without a group, reparented daemons (Mill server, `cmd &`) survive. Needs perl on the host. Task 2 ruling. | ProcTest "timeout kills a reparented background grandchild (process group)" |
| 9 | For the dsh agent the run dir's `settings.json` artefact is the profile overlay (`dsh-overlay.yml`), not a hooks file. GA has no dsh agent; for Claude Code the artefact is `.claude/settings.json` as in GA (arm tier above). | The overlay is what selects the gate for a dsh run. Task 16. | DshTest "setup + afterSteps, gated arm: overlay names the profile's rows, hooks file is the run copy's map" |
| 10 | `score.json` / `run.json` bytes: ujson writes `1` where python writes `1.0` and raw UTF-8 where python escapes; field names, values and order equal. `chain.json` keeps python's `json.dump(indent=2)` form. | Old readers parse JSON; parity is parsed-JSON equality (Task 10 ruling). | ScoreTest "score.json: GA fields in GA order"; ChainCheckTest "chain.json is written like python json.dump(indent=2) with float repr" |

Minor differences from GA still deferred (progress ledger; not ruled, not pinned): a tool_use `input` that is not an
object becomes `{}` (GA keeps a truthy non-dict); `pyInt` of `null` has a different error text; chain check's
`own_passed` truthiness of `[]` / `{}` / `true`; `runBuild` sets `MILL_OUTPUT_DIR` over a caller's (GA: the caller's extra
env wins); `modulePasses` iterates an unordered map (more than four modules); the `{home}` variable is `user.home`, not
`$HOME`; arm t's stderr line is not printed; a missing holdout dir reuses the `holdout export` text; the contamination
ancestor check also flags paths inside the holdout when the run dir is outside `<root>/runs` (wider than GA).
Fixed 2026-10-13: `run`'s rep takes `[1-9]` only (GA's text for `05`, `+3`, ` 3`); `experiments/chain.conf` honours
`${?GA_ROOT}`.

Accepted, not to be fixed (2026-10-13)[^dropped].

[^dropped]: Byte-level python parity nits (number formatting, #10; `str.strip` vs `trim` unicode whitespace; UTF-16 vs
code-point sort order; ASCII vs unicode `\s` / `\b`; NaN / Infinity, #7), test temp-dir cleanup, dead asserts, test names,
the quadratic section parse, the `wilson` Int overflow above ~23k runs, the baseline line format duplicated in Main and
Score, and the double killTree when a cancel meets a timeout (harmless).

New agents (dsh, Codex CLI, Gemini CLI, generic) and the generic agent's `resultFromExit` have no GA counterpart.
