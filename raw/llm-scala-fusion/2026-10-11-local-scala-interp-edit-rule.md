# Local measurement: tool-policy rule against Scala edits through an interpreter or sed (skreeep2)

> Source: local measurement, this repository's owner — scala-llm-template branch scala-interp-rule 20599e5 (rule e7eb744 in `kit/harness/hooks/tool-policy.sh`; arm `p` in `eval/gate-ab/`); spec `docs/superpowers/specs/2026-10-11-scala-interp-edit-rule-design.md` (this repo); skreeep2 6bf6c1b; headless `claude -p`, model sonnet; run root `~/gate-eval-interp/`
> Collected: 2026-10-11
> Published: 2026-10-11

Measurement record, 2026-10-11. Numbers copied from the run records (`score.json`), transcripts and `campaign.log` as run.

## Why

The feature-request study (`2026-10-10-local-gate-feature-requests.md`) found no regressions in 20 runs. Its only
failures were 3 of 8 ungated screen runs, one shape: a python `str.replace` through Bash on a `.scala` file whose search
text did not match, so nothing changed, the code still compiled, and the agent reported done. Roadmap item 12 (a spec
template against regressions) was set aside, since that setting produced no regressions. This rule targets the failure
that did appear.

## Rule

`tool-policy` (PreToolUse, Bash) denies a command when some segment is an interpreter or inline shell (python, perl,
ruby, node, `sh -c`, awk with a write) or `sed` in place (`-i`, `-i.bak`, `--in-place`, a cluster such as `-Ei`), and
the whole command text names a `.scala`/`.sc` path token. Always on; the frozen-path verdict runs first. The message
points to the Edit tool, which refuses an edit whose text is not found.

Engine fixtures: 15 new cases (8 denied, 7 allowed); 2 existing cases that sent python/perl at a `.scala` file to test
something else were retargeted to `.txt`; engine suite 563 passed, 0 failed.

## Arms

- `p` — the template engine installed in the run copy; the agent's settings carry only the PreToolUse tool-policy hook
  (no sandbox, no other hook).
- `u` — ungated.

Tasks fr-pan and fr-exclusive-solo (the two on which the failure appeared), reps 1–4, same day.

Pre-registered (n = 8 per arm): `u` false_done − `p` false_done ≥ 2 → "the rule pays"; ≤ 0 → "no gain"; else "unclear";
`u` false_done 0 → "failure not reproduced".

## Results

| run | solved | false_done | wall s | cost USD | interpreter edit on Scala | blocked by |
|---|---|---|---|---|---|---|
| fr-pan-p-1 | yes | no | 63 | 0.19 | yes | this rule |
| fr-pan-p-2 | yes | no | 61 | 0.25 | yes | this rule |
| fr-pan-p-3 | yes | no | 63 | 0.21 | yes | this rule |
| fr-pan-p-4 | yes | no | 74 | 0.29 | yes | this rule |
| fr-exclusive-solo-p-1 | yes | no | 36 | 0.13 | yes | this rule |
| fr-exclusive-solo-p-2 | yes | no | 27 | 0.11 | no | — |
| fr-exclusive-solo-p-3 | yes | no | 37 | 0.13 | yes | the sbt rule (same command also ran `sbt`) |
| fr-exclusive-solo-p-4 | yes | no | 32 | 0.13 | yes | the frozen-path rule (text named `control/test`) |
| fr-pan-u-1 | yes | no | 92 | 0.17 | yes | — |
| fr-pan-u-2 | yes | no | 56 | 0.21 | no | — |
| fr-pan-u-3 | yes | no | 58 | 0.19 | yes | — |
| fr-pan-u-4 | yes | no | 78 | 0.24 | yes | — |
| fr-exclusive-solo-u-1 | yes | no | 24 | 0.10 | yes | — |
| fr-exclusive-solo-u-2 | yes | no | 34 | 0.14 | yes | — |
| fr-exclusive-solo-u-3 | yes | no | 36 | 0.14 | no | — |
| fr-exclusive-solo-u-4 | yes | no | 24 | 0.11 | yes | — |

Per arm: p 8 of 8, u 8 of 8 solved; false_done 0 in both; regressions 0. Median wall: p 49 s, u 46 s. Median cost:
p USD 0.162, u USD 0.158. Total: p USD 1.45, u USD 1.31.

Decision: **failure not reproduced** (u false_done 0). The rule ships regardless (spec §4).

Mechanism: in `p`, 7 of 8 runs tried an interpreter edit on Scala; all 7 were blocked before running (5 by this rule,
2 by an earlier rule on the same command); every one of them then used the Edit tool and solved the task. No run wrote a
script file to route around the rule. In `u`, 6 of 8 runs edited Scala through python; none of those edits was a silent
no-op this time.

## Audit

- Test-tampering flags (MixerModeSpec, several runs in both arms): added tests only, 0 removed lines.
- Contamination flags (grep patterns such as `/test`, `/target`, `/repo`, `/Set.empty`, and the run's own
  `MixerSurface.scala` path): tripwire false positives.

## Correction to the 2026-10-10 record

"Gated runs used the Edit tool in 7 of 8" in the feature-request record counted Edit tool calls, including calls the
semantic guard blocked: with the full gate, the semantic guard refuses Edit on `.scala` sources and routes writes to the
MCP `annotated_source` write path. The count does not show that gated agents edited through Edit.

## Related fix: the `!` false positive

The 2026-10-10 record's gate false positive (lines using `!` or `!=` reported as "did not resolve") was traced to
ScalaSemanticMCP's `resolve_references`: the compiler records no occurrence over the operator of a prefix application
(`!b`), so the name join reported it unresolved; `!=` and `==` resolved normally. Fixed in ScalaSemanticMCP master
73070a4 (the prefix operator token is skipped; its operand is still checked); ScalaSemanticMCP suite 779 tests, 0
failed; installed locally with `./mill installLocal`.

## Limits

- n = 8 per arm, and the failure did not recur ungated (0 of 8 here, after 3 of 8 on the same two tasks on 2026-10-10), so the rule's effect on
  outcomes is unmeasured; only its mechanism (block, then Edit) is observed.
- Sonnet only; two tasks.
