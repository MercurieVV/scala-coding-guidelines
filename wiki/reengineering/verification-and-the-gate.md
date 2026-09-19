# Verification and the Gate: Receipts, the Build Contract, and the Independent Lane

> Sources: `skreeep2` session, 2026-09-19 (gate and lane findings)
> Raw: [The reengineering kit — design (2026-09-19)](../../raw/reengineering/2026-09-19-kit-design.md)
> Updated: 2026-09-19

## Overview

`R-8` and `R-9` are the two rules that say what it takes to call a piece of work finished, and they answer the
question from opposite sides. `R-8` is the receipt: a completion claim is made against the gate's own output, and the
gate has to *run* the project's tests rather than only compile them. `R-9` is the second reader: a fresh, read-only
lane judges the diff against the `R-n` IDs, not against the author's account of it. Between the two sits the **build
contract** — the interface L4 exposes, and the reason a gate can drive a project's own tasks without knowing which
build tool declared them. Both rules are stated in
[The Reengineering Contract: Rules R-1 … R-14](reengineering-contract.md), the spine of this topic; this article
carries the receipt, the contract that makes it runnable, and the lane that audits it.

## R-8: the gate's own output, and a gate that runs tests

The rule, in the design's words:

> No completion claim without the gate's own output, and the gate must *run* tests, not only compile them.

The first half fixes whose output counts. Not a memory of a green run, not a summary written after the fact, not the
author's confidence that nothing else touched the module: the claim is made against what the gate itself printed. The
design's origin column names two sources for the rule — `C-10` of the `skreeep2` contract, and an `I4` finding, a label
the design carries without saying what it was.

The second half is the one that gets skipped, because compiling is the most visible thing a gate does and the
cheapest to mistake for verification. A gate that compiles and stops has said nothing about whether the tests ran —
and the boundary rule puts exactly that on the machine's side of the line, where no judgement is needed to settle it:

> a test that does not run

A module can sit in the build, compile cleanly, and never be executed by anything the gate invokes. The design's own
evidence for the second half is a matter of timing rather than of a red result:

> The gate ran the module's own lint and tests only after the module joined the gate lists

Nothing in that record says the gate was wrong about the module — those checks simply had not run yet, and a check
that does not run leaves nothing for a reader to notice. `R-8`'s second half is
therefore a requirement about coverage rather than about exit codes: the gate must invoke the tests, and the coverage
map below is what says whose tests those are.

## The build contract: three declarations, a coverage map, the source roots

`R-8` would be unenforceable if the gate knew one build tool. The design avoids that by giving L4 a declared interface
instead of a built-in one: the artifact is the gate

> as a runner over a build contract

and each project states that contract for itself. It has three declarations. The first is a set of task names:

> A project declares three task names

The three are `preCommit`, `prePush` and `checkFormat`. The gate runs the tasks a project declared and nothing else,
so a project whose build tool is not Mill answers the same three questions by declaring what its equivalents are
called. The second declaration is the coverage map:

> a **coverage map** (which module is formatted, linted, compiled, tested), and its source roots.

The map is what makes a green gate mean something specific. It names, module by module, which of those four
treatments the gate actually applies, so a module the map leaves out is not covered by a pass over the rest of the
build — and the gate is forbidden from implying otherwise:

> The gate runs declared tasks only and refuses to claim coverage the map does not grant.

The third declaration is the source roots, named with the other two; the design says no more about them here, and the
procedure's first step reads them as part of the ground. What the contract buys is a limit on the gate's knowledge as
much as on its claims:

> Mill gets an implementation first; sbt and scala-cli get recipes; the gate never learns their names.

That sentence is the whole point of declaring the interface. Mill gets an implementation first and sbt and scala-cli
get recipes, but the harness holds no branch for any of them: `preCommit`, `prePush` and `checkFormat` are the
project's answer, so moving to another build tool is a change to a declaration rather than to the gate. It also keeps
the coverage map honest, since the gate can never infer coverage from a tool's own defaults — only from what the
project said.

## R-9: the lane, and what it is allowed to believe

The rule, in the design's words:

> Verification is independent: a fresh read-only lane judges the diff against these IDs, not against the author's summary.

The lane is layer L5, and the design gives its protocol as four items on one line:

> the lane protocol: a fresh read-only agent

the diff, the `R-n` list, a verdict per rule. Each item does separate work:

- **A fresh read-only agent.** Fresh, so that it has not watched the work being done and cannot inherit the author's
  framing of what the change was for: what it reads first is the diff, not the explanation of the diff. Read-only, so
  that it has nothing to defend — it "consumes L1 and L4; produces receipts", and a lane that starts fixing what it
  finds is no longer the thing that reported it.
- **The diff.** The artifact under judgement, not a description of it. This is the item that makes the freshness
  matter: a lane pointed at a summary is judging the summary, which is precisely what `R-9` forbids.
- **The `R-n` list.** The standard the diff is held to, and the reason the rules are stable IDs. As the design puts
  it elsewhere, "a rule changes by adding or superseding an ID, never by silently editing prose that machinery
  believes it implements" — so a lane's verdict on a rule stays meaningful after the rule's prose is rewritten.
- **A verdict per rule.** Not one impression of the change, but a finding under each ID the work touched, including
  the ones it satisfied. Where a verdict is that a rule was broken, it is only usable as a counterexample — the
  symbol, the call site, the line in the diff where the rule fails — because a verdict without one is the author's
  summary in a second voice, which is what `R-9` exists to exclude.

The process layer wires the receipt and the lane together in one skill, `verify-before-done`, which the design's
step 4 says

> earns "done" with a gate receipt plus a lane.

Neither half substitutes for the other: the receipt says the project's own checks were run, and the lane says whether
what they checked was what the rules required.

## Worked example: what only the lane could find

Both examples are in `skreeep2`, 2026-09-19:

| What happened | Commits | Rule it demonstrates |
|---|---|---|
| "An independent lane found the contract worded for one implementor only, a lost check direction, and a test that proved less than it claimed" | `5170561`, `6863013` | `R-9`, `R-11` |
| "The gate ran the module's own lint and tests only after the module joined the gate lists" | `6fbd8e1`, `8d09e1a` | `R-8` |

The second row is `R-8`'s evidence and needs no lane to read it: the gate's lists are what decide whether a module's
lint and tests run at all, and until the module joined them the gate could pass a change it had not tested.

The first row is `R-9`'s, and it is the sharper case. Three findings came out of one lane, and none of them is
visible from the gate's output. The first — the contract worded for one implementor only — is the boundary article's
finding, a design judgement about a contract rather than a fact about a run. The other two are the lane's own
currency: "a lost check direction", and "a test that proved less than it claimed". Read as categories rather than as
a reconstruction of that diff, neither surfaces as a failure. A direction of a check that nobody writes is a
direction nobody runs, so its absence leaves no red mark anywhere; and a test can run, pass, and establish less than
its name and its assertion claim, with no exit code distinguishing it from one that establishes what it says.

That last finding is where the two rules meet. `R-8`'s receipt says the tests ran; it does not say what they proved,
and the boundary rule's machine side asks only whether a test ran. Reading the diff against the IDs is what closes
the gap, and that reading is what the lane is for.

## Where the gate stops

`R-9` does not turn the lane into a second gate. The lane sits on the judgement side of the boundary — L5 is named
there alongside L1 and L3 — and the questions it may ask are the ones the machine may not: whether the split is the
right one, whether a component names a shape rather than spells it, and "is this contract honest". Its verdicts are
consequently receipts about compliance with the IDs, not a second exit code: a lane can report that every rule it
checked holds, and the design still refuses to read a green result as a correct design.

The kit holds its own machinery to the same standard. The design's constraints record that the `AllowedShape` rule the
enforcement layer ships has no tests today, and why that is work rather than a footnote:

> Whatever its home, fixtures (a flagged file, a passing file) are part of the work, because R-8 applies to the kit's own machinery.

## See Also

- [The Reengineering Contract: Rules R-1 … R-14](reengineering-contract.md) — the spine: all fourteen rules, and what each forbids and permits
- [The Decidable Boundary](decidable-vs-judgement.md) — `R-10`, `R-11`: the machine/human split the lane sits on the human side of
