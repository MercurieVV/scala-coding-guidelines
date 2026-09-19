# Recording a Migration: Target Lists, Receipts, and Rulings

> Sources: `skreeep2` campaign documents; the reengineering-kit design, 2026-09-19
> Raw: [The reengineering kit — design (2026-09-19)](../../raw/reengineering/2026-09-19-kit-design.md)
> Updated: 2026-09-19

## Overview

`R-12` and `R-13` are the two rules that say what a reshape leaves behind, and they answer from opposite ends of the
same question. `R-13` fixes the reshape's *extent*: one module at a time, on a target list declared before anything
moves rather than assembled from whatever the working tree happened to touch. `R-12` fixes its *record*: the documents
are amended as the shape moves, a stale claim is corrected rather than tolerated, and a divergence that stays open is
written down as a ruling. The record is an artifact of the rules layer in its own right — L1's artifact line names
"the two-level shape, the migration record" — so what a migration leaves behind belongs to the kit rather than being
bookkeeping around it. Both rules are stated in
[The Reengineering Contract: Rules R-1 … R-14](reengineering-contract.md), the spine of this topic; this article
carries the target list, the two documents it feeds, and the shape of a ruling.

## R-13: one module at a time, on a declared list

The rule, in the design's words:

> One module at a time, and a reshape is declared deliberate by an explicit target list rather than by whatever a
> diff happened to touch.

The unit of work is the module. Step 4 of the procedure is "Reshape one module", and the skills it names do their work
inside that unit: `shaping-a-module` decides the split before types move, while `reengineering-module` "narrows it and
names what happens to dependants". The unit matters because a reshape moves an interface — a module whose abstract
level changes is read differently by everything that depends on it — and a pass that takes two modules at once
produces a diff in which neither move can be judged on its own.

The target list is the other half of the rule, and the design makes it a first-step output rather than a summary
written afterwards. Step 1 reads the ground — "build tool, module map, source roots, which modules carry composition,
where an abstract level could sit" — and ends with "a proposed module list, one line of purpose each, plus the
contract file." The list is then approved before any reshape starts:

> A human approves the list before anything else starts.

That ordering is what makes the reshape deliberate. A list written before types move is a decision about scope, and
the work can be held against it; a scope read off the diff afterwards is a description, and every module the pass
touched is in it by construction. R-13's origin column names where the rule comes from in the working project's
contract — `C-4`/`C-5`, "the strictness dial" — the knob that says how much of a project one deliberate pass may take
on.

The procedure also states where the list ends. Step 6 repeats the pass until the exit condition:

> every composition-carrying module has the two-level split with its own allowlist, the gate is green, every module
> has its one-sentence purpose

An exit condition written that way can be checked against the list: every item is either done or not, and the list
does not grow to fit whatever the pass happened to touch.

## The two documents: a contract that holds, a dial that moves

The record travels in two documents, and the design gives them different jobs. The first is the contract file: step 1
produces it together with the module list, a human approves it, and it is what a later reshape is held against. Its
durability is the point — a rule stays citable while the document stating it stays still.

The second is the one that must move, and the procedure opens its record step with exactly that update:

> the project's dial/contract updated as the shape moves, stale claims corrected, divergences recorded as rulings

A dial is a fitting name for it: the document is expected to change every time a module crosses the boundary, so
rewriting it is not drift but part of the work. In the working project the dial is the campaign document the header's
Sources line names, while the contract file is the one that stays still so that a reader can tell what the project's
rules are without having to work out where the migration currently stands.

## R-12: the record travels with the code

The rule, in the design's words:

> The record travels with the code: docs amended as the shape moves, stale claims corrected, open divergences
> written down as rulings.

The image is movement, and it rules out the shape a record usually takes: a report written after the work, from
memory, about a state the reader cannot see. The record travels *with* the code because the amendments land in the
same pass — the document that names a member is corrected when that member's type changes, not when somebody next
notices.

**A stale claim is a defect, not a nit.** A document describing a shape that no longer exists is not merely untidy;
it is wrong about the code, and wrong in the direction that costs most, because a document is read exactly when the
reader does not yet know the shape. The design tags the case `R-12` in its worked examples:

> Docs that described a tree which no longer existed were corrected, and an unreconciled divergence was recorded as a
> ruling

The three commits it lists are `f1c4ecd`, `fa8fea2` and `3b38bfa`. Its first half is the class of defect the rule is
aimed at: documents that were accurate when written and became false when the shape moved under them. That is also
why the amendment belongs to the reshape rather than to a follow-up: nothing in a green gate reads prose, so a stale
claim passes every check a machine can run.

## An open divergence is a ruling

Not every divergence can be reconciled on the spot, and R-12's third clause says what to do with the ones that
cannot: write them down as rulings. A ruling states that the divergence stands and carries the reason why it was not
reconciled. It is not a note to revisit the question later and not a silent exception — the boundary rule puts the
underlying question on the human side of the line:

> is this divergence a fix or a design change

Reconciling a divergence means deciding which of those two it is, and that is a judgement rather than a machine
finding. A fix restores the shape the contract already describes; a design change alters the contract, and the second
is not something an agent doing a reshape may slip in under a cleanup. So the divergence is written into the record
and the reason travels with it, which is what lets the human who owns the design rule on it with the trade-off
visible instead of inferring it from a diff.

The design holds itself to the same form. Its constraints and open decisions do not bury what was left unresolved;
each item carries why it stands. The rule's own future home in a separate scalafix project is "deferred by the trigger
above, not decided" — deferred, and by a named trigger rather than by an unstated one — and that trigger is written
down where the vendoring decision is.

## What is not a record

Step 5 ends with the test that separates the record from everything that merely describes it:

> One commit per module; ledgers are convenience, never the record.

A ledger earns its keep while the work is in flight — it is a convenience the migration keeps for itself — but it is
not what the next reader is owed. The same test disposes of the other artifacts a reshape tends to leave behind: a
ledger kept outside version control, a summary of the work written in a chat, a note-to-self in the source marking
something to revisit. Each can be accurate while the work is under way, and none of them travels with the code. The
ledger that never enters the repository is the clearest case of the failure, because it cannot travel at all: the
record is a record only if it lands where the code lands.

That is what "One commit per module" buys. The reshape and its amendments to the contract and the dial land together
in the module's own commit, so the record cannot lag the code by a release and a later reader meets the change and
its record as one artifact. It is the migration's receipt, as distinct from the gate receipt that R-8 requires before
a completion claim: one says what the shape now is, the other says the checks were run.

## Worked examples

The design's worked-examples table is in `skreeep2`, 2026-09-19. One of its rows is tagged `R-12`:

| What happened | Commits | Rule it demonstrates |
|---|---|---|
| "Docs that described a tree which no longer existed were corrected, and an unreconciled divergence was recorded as a ruling" | `f1c4ecd`, `fa8fea2`, `3b38bfa` | `R-12` |

The row is the rule's clauses in the order they tend to arrive: documents corrected because the shape moved under
them, and a divergence that could not be reconciled, written down instead of resolved.

No row in that table is tagged `R-13`, which fits what the rule is about. Its evidence is the procedure rather than a
single commit — step 4's one module per pass, the target list from step 1 that the pass is held against, and step 6's
exit condition.

## See Also

- [The Reengineering Contract: Rules R-1 … R-14](reengineering-contract.md) — the spine: all fourteen rules, and what each forbids and permits
- [The Decidable Boundary: What a Machine Decides, What a Human Rules](decidable-vs-judgement.md) — `R-10`, `R-11`: the machine/human split, and why an unreconciled divergence is recorded as a ruling rather than decided by the agent that found it
