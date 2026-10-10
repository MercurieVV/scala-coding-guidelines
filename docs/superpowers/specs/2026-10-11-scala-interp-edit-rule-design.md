# Tool-policy rule: no Scala source edits through an interpreter or sed (design)

Date: 2026-10-11. Status: approved in conversation; written for review. Budget: at most 16 agent runs.

## 1. Problem

The feature-request study (raw `2026-10-10-local-gate-feature-requests.md`) found no regressions in 20 runs. Its only
failures were 3 of 8 ungated screen runs, all one shape: the agent edited a `.scala` file with a python `str.replace`
through Bash; the search text's indentation did not match the file, so nothing changed; the code still compiled and the
existing tests still passed; the agent reported done. Scala's strictness cannot see unchanged code. Claude Code's Edit
tool refuses an edit whose search text is not found, so the same mistake through Edit fails loudly and is retried.

Roadmap item 12 (spec template against regressions) is not pursued: the setting it targets produced no regressions.

## 2. Rule

In `kit/harness/hooks/tool-policy.sh` (scala-llm-template), a Bash command is denied (exit 2) when both hold:

- some segment is an interpreter or inline-shell shape the hook already recognises (python/perl/ruby/node/pypy/php,
  `sh|bash|zsh|dash` with `-c`, awk with a write), or `sed` with an in-place flag (`-i`, `-i.bak`, `-i ''`,
  `--in-place`, a short-option cluster containing `i` such as `-Ei`);
- the whole command text (heredoc bodies included) names a path token ending `.scala` or `.sc`, with a path-character
  boundary after it (`.scalafmt.conf`, `.scala-build/` do not count).

Message, after `BLOCKED by tool-policy:`: editing Scala source through an interpreter or sed can fail silently — a
replace that matches nothing changes nothing and raises no error. Use the Edit tool (it refuses a non-matching edit);
use Read or Grep to read.

Always on; no policy row. The frozen-path verdict keeps precedence (it runs first and names the freeze).

Not covered (documented, as for the frozen-path interpreter rule): a file name the program computes at run time, and an
interpreter run from a script file.

## 3. Tests

Engine test cases in the existing `tests/cases/<name>/` style (payload, subcommand, expect.code, expect.grep):

- denied: the screen-failure shape (`python3 - <<'EOF'` opening `.../MixerMode.scala` and calling `str.replace`);
  `python3 -c` naming `A.scala`; `sed -i 's/a/b/' Foo.scala`; `sed -Ei …` and `sed --in-place …`; `perl -pi -e … A.sc`;
  `bash -c "…Foo.scala"`.
- allowed: `python3 -c 'print(1)'`; `sed -n 1,5p Foo.scala`; `cat Foo.scala`; `python3 tool.py .scalafmt.conf`;
  `grep -n foo Bar.scala`.

## 4. Measurement

Harness `eval/gate-ab`, run root `~/gate-eval-interp/` (own HEAD copy and settings).

- New arm `p`: the run copy gets the template engine (as `gf`/`gs`), and the agent's settings carry only the
  PreToolUse Bash tool-policy hook — no SessionStart, no other PreToolUse/PostToolUse hooks, no Stop hook, no sandbox.
  The frozen-path row the harness adds for the task's test directory stays, as in `gf`/`gs`.
- `score.py`: arm `p` may show hook events (environment-leak check).
- Tasks fr-pan and fr-exclusive-solo; arms `p` and fresh `u`; reps 1–4 each: 16 runs, same day, no screen.

Decision rule (fixed before any run), n = 8 per arm:

- `u` false_done − `p` false_done ≥ 2: "the rule pays".
- difference ≤ 0: "no gain".
- otherwise: "unclear".
- `u` with false_done 0: "failure not reproduced".

Reported beside it: solved per arm, tool-policy blocks in `p` naming this rule, runs in either arm whose final diff
lacks the requested behaviour while the agent reported done (silent no-op), median wall and cost.

The rule ships whatever the verdict: it removes a silent failure mode at no measured cost; a "no gain" verdict is
recorded, not acted on.

## 5. Write-up

A raw measurement file and a roadmap update in this repository's wiki: item 12 marked not pursued with item 11's
evidence; the rule recorded with its result.

## 6. Risks

- n = 8 per arm; the failure appeared 3 times in 12 ungated runs on these tasks and 0 times in the last 4 — the result
  is a pointer.
- The agent may route around the rule (write a script file, then run it). Counted from transcripts and reported.
- Over-blocking reads (`python3` reading a `.scala` file): Read/Grep cover reading; blocks are counted.
