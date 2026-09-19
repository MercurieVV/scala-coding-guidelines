# Reengineering Kit, Plan A — the rules in `scala-coding-guidelines`

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Compile the kit's citable rules (`R-1 … R-14`) into the `scala-coding-guidelines` wiki, so every other layer — the scalafix config, the process skills, the verification lane — can cite an ID instead of restating a rule.

**Architecture:** The design document (`docs/2026-09-19-reengineering-kit-design.md`, already written) becomes `raw/` source material for a new `reengineering` topic. Five articles are compiled from it through the `karpathy-llm-wiki` flow: one spine carrying all fourteen IDs, and four that elaborate the IDs this session actually produced. No `wiki/` file is hand-edited outside that flow.

**Tech Stack:** Markdown; the `karpathy-llm-wiki` skill (formats below are copied from its `references/`); its grounding checker `~/.claude/skills/karpathy-llm-wiki/scripts/check_evidence.py`; git.

**Spec:** `docs/2026-09-19-reengineering-kit-design.md` (same repository — the plan argues from it; read both).

## Global Constraints

- **Wiki operations go through the skill.** Never hand-edit `wiki/` outside the `karpathy-llm-wiki` flow; never edit a `raw/` file after it is written.
- **Grounding invariant:** every number, date and direct quote in a `wiki/` article must exist verbatim in the `raw/` file(s) its `Raw:` field names. Verified by `python3 ~/.claude/skills/karpathy-llm-wiki/scripts/check_evidence.py .`
- **One topic directory deep:** `raw/reengineering/<file>.md`, `wiki/reengineering/<article>.md`.
- **The frozen names** (they appear in articles and are cited by the other plans; do not rename them here): config keys `allowedTypes`, `disallowedConstructs`; rule name `AllowedShape`; gate tasks `preCommit`, `prePush`, `checkFormat`.
- **Never suppress (R-10):** if a claim cannot be grounded in `raw/`, ask; do not soften the rule to make the check pass.
- **This repository is written from outside the executing agent's workspace** (it is a sibling checkout): expect one approval per repository visit, so finish all of this plan in one visit.
- Article headers, index rows and log entries must match the templates in `~/.claude/skills/karpathy-llm-wiki/references/` exactly (the exact blocks are given per task; do not invent a variant).

## File Structure

| File | Responsibility |
|---|---|
| `docs/2026-09-19-reengineering-kit-design.md` | the design (exists; Task 1 commits it) |
| `raw/reengineering/2026-09-19-kit-design.md` | the design's rule content as immutable source material (metadata header + the `R-n` table and decisions, verbatim) |
| `wiki/reengineering/reengineering-contract.md` | the spine: `R-1 … R-14`, one line each, and where each is elaborated |
| `wiki/reengineering/absence-unrepresentable.md` | R-5, R-6 and their worked examples |
| `wiki/reengineering/decidable-vs-judgement.md` | R-10, R-11 — the boundary, stated plainly |
| `wiki/reengineering/verification-and-the-gate.md` | R-8, R-9 and the build contract |
| `wiki/reengineering/recording-a-migration.md` | R-12, R-13 — how a migration is recorded |
| `wiki/index.md` | gains the `## reengineering` topic section and one row per article |
| `wiki/log.md` | gains one `## [2026-09-19] ingest` entry per article |

Interfaces produced for the other plans: the article paths above and the ID set `R-1 … R-14` with their one-line statements.

---

### Task 1: Commit the design and open the topic's raw source

**Files:**
- Create: `raw/reengineering/2026-09-19-kit-design.md`
- Commit (existing, untracked): `docs/2026-09-19-reengineering-kit-design.md`

**Interfaces:**
- Consumes: nothing.
- Produces: `raw/reengineering/2026-09-19-kit-design.md` — the `Raw:` source every later article cites.

- [ ] **Step 1: Create the raw source file**

Its content is the design document's content, verbatim, under the skill's raw header (no other edits — `raw/` is immutable once written):

```markdown
# The reengineering kit — design (2026-09-19)

> Source: session in `skreeep2`, 2026-09-17…19 (commits `4a3242c`…`3b38bfa`); design record at `docs/2026-09-19-reengineering-kit-design.md`
> Collected: 2026-09-19
> Published: 2026-09-19

<the rest of docs/2026-09-19-reengineering-kit-design.md, from "# The reengineering kit — design" onward, copied without edits>
```

- [ ] **Step 2: Verify the copy is faithful**

Run: `diff <(tail -n +7 raw/reengineering/2026-09-19-kit-design.md) docs/2026-09-19-reengineering-kit-design.md`
Expected: no output (identical from line 5 on).

- [ ] **Step 3: Commit both files**

```bash
git add docs/2026-09-19-reengineering-kit-design.md raw/reengineering/2026-09-19-kit-design.md
git commit -m "docs: the reengineering kit design, and its raw source for the wiki"
```

---

### Task 2: The spine article — `reengineering-contract.md`

**Files:**
- Create: `wiki/reengineering/reengineering-contract.md`
- Modify: `wiki/index.md` (new topic section), `wiki/log.md` (append)

**Interfaces:**
- Consumes: `raw/reengineering/2026-09-19-kit-design.md`.
- Produces: the article other plans cite as the canonical `R-n` list.

- [ ] **Step 1: Write the article**

Header (exact template shape) plus body. The body must contain, in order: an Overview paragraph; a table with one row per ID (`R-1 … R-14`) whose second column is the one-line statement copied verbatim from the design's rules table; a short subsection per ID family that says what it forbids and what it permits in a sentence or two; and a `## See Also` section linking `../scala-typelevel-fp/named-shape-traits.md` (R-4), `../scala-typelevel-fp/kleisli-to-arrow-refactoring.md` (R-1), `../scala-typelevel-fp/project-structure-and-testing.md` (R-7, module layout), `absence-unrepresentable.md` (R-5, R-6), `decidable-vs-judgement.md` (R-10, R-11), `verification-and-the-gate.md` (R-8, R-9) and `recording-a-migration.md` (R-12, R-13).

```markdown
# The Reengineering Contract: Rules `R-1 … R-14`

> Sources: `skreeep2` contract and campaign documents; the reengineering-kit design, 2026-09-19
> Raw: [The reengineering kit — design (2026-09-19)](../../raw/reengineering/2026-09-19-kit-design.md)
> Updated: 2026-09-19

## Overview

<one paragraph: the rules are cited by ID from the scalafix config, the process skills and the verification lane,
so a rule changes by adding or superseding an ID rather than by editing prose that machinery believes it implements.>

## The rules

| ID | Rule |
|---|---|
| R-1 | … (verbatim from the design) |
…

## What each rule forbids, and what it permits

<R-1 … R-14, one short paragraph each, in ID order.>

## See Also
```

- [ ] **Step 2: Add the index section and row**

Append to `wiki/index.md` (after the last topic section):

```markdown
## reengineering

Reengineering a Scala project toward an abstract level: the citable rules, the machine/human boundary, and how a
migration is verified and recorded.

| Article | Summary | Updated |
|---------|---------|---------|
| [The Reengineering Contract: Rules R-1 … R-14](reengineering/reengineering-contract.md) | The citable spine: fourteen rules, one line each, with what each forbids and permits | 2026-09-19 |
```

- [ ] **Step 3: Append the log entry**

```markdown
## [2026-09-19] ingest | The Reengineering Contract: Rules R-1 … R-14
- Disposition: New
- Raw: raw/reengineering/2026-09-19-kit-design.md
```

- [ ] **Step 4: Run the grounding check**

Run: `python3 ~/.claude/skills/karpathy-llm-wiki/scripts/check_evidence.py .`
Expected: no ungrounded number, date or quote attributed to this article (fix by citing the raw file or removing the claim — never by editing `raw/`).

- [ ] **Step 5: Commit**

```bash
git add wiki/reengineering/reengineering-contract.md wiki/index.md wiki/log.md
git commit -m "wiki(reengineering): the contract — R-1 … R-14 as a citable spine"
```

---

### Task 3: `absence-unrepresentable.md` — R-5 and R-6

**Files:**
- Create: `wiki/reengineering/absence-unrepresentable.md`
- Modify: `wiki/index.md`, `wiki/log.md`

**Interfaces:**
- Consumes: the raw design; the two worked examples it records.
- Produces: the article the vendored rule (plan B) and the skills cite for "make absence unrepresentable" and "state the contract on the abstract member".

- [ ] **Step 1: Write the article**

Body must carry: the rule in one sentence each; the *observable* failure mode that motivates them (a member built from a possibly-empty input throws at the first element; a contract written for one implementor is unsatisfiable for another); the two moves that fix them (`Tree`/`NonEmptyList` in the type instead of a runtime guard; the contract stated on the abstract member with total implementations, and where it cannot be satisfiable, what an implementation must do instead); the worked examples from the raw file with their commits; and a `## See Also` to `reengineering-contract.md` and `../scala-typelevel-fp/named-shape-traits.md`.

Header:

```markdown
# Absence Unrepresentable: Guarantees in the Type, Contracts on the Member

> Sources: `skreeep2` session, 2026-09-19 (commits `4a3242c`, `6863013`, `5aad2f9`)
> Raw: [The reengineering kit — design (2026-09-19)](../../raw/reengineering/2026-09-19-kit-design.md)
> Updated: 2026-09-19
```

- [ ] **Step 2: Index row** (same table, new row):

```markdown
| [Absence Unrepresentable](reengineering/absence-unrepresentable.md) | R-5, R-6: a non-empty structure in the type instead of a runtime guard; the contract stated on the abstract member, implementations total | 2026-09-19 |
```

- [ ] **Step 3: Log entry**

```markdown
## [2026-09-19] ingest | Absence Unrepresentable: Guarantees in the Type, Contracts on the Member
- Disposition: New
- Raw: raw/reengineering/2026-09-19-kit-design.md
- Updated: The Reengineering Contract (See Also link)
```

- [ ] **Step 4: Grounding check** — `python3 ~/.claude/skills/karpathy-llm-wiki/scripts/check_evidence.py .` → clean.

- [ ] **Step 5: Commit**

```bash
git add wiki/reengineering/absence-unrepresentable.md wiki/index.md wiki/log.md
git commit -m "wiki(reengineering): absence unrepresentable, and contracts on the abstract member"
```

---

### Task 4: `decidable-vs-judgement.md` — R-10 and R-11

**Files:**
- Create: `wiki/reengineering/decidable-vs-judgement.md`
- Modify: `wiki/index.md`, `wiki/log.md`

**Interfaces:**
- Produces: the article that says which findings a machine may decide, which a human must, and what to do when a rule cannot be satisfied honestly.

- [ ] **Step 1: Write the article**

Body must carry: the two lists verbatim from the design (machine-decidable: a concrete type outside the allowlist; an imperative construct; unformatted code; a red compile; a lint finding; a test that does not run — judgement: is this the right split; does this component name a shape; is this contract honest; is this divergence a fix or a design change); why a green gate is not a correct design; the escalation rule (no `@SuppressWarnings`, no allowlist widening, no threshold bump — ask instead, and what a good question looks like: name the finding, the rule, the cost of each option); and a `## See Also` to `reengineering-contract.md` and `verification-and-the-gate.md`.

Header:

```markdown
# The Decidable Boundary: What a Machine Decides, What a Human Rules

> Sources: `skreeep2` session, 2026-09-19 (the linter and review findings)
> Raw: [The reengineering kit — design (2026-09-19)](../../raw/reengineering/2026-09-19-kit-design.md)
> Updated: 2026-09-19
```

- [ ] **Step 2: Index row**

```markdown
| [The Decidable Boundary](reengineering/decidable-vs-judgement.md) | R-10, R-11: what a linter may decide, what only a human may, and why an unsatisfiable rule is escalated rather than suppressed | 2026-09-19 |
```

- [ ] **Step 3: Log entry** — same shape as Task 3, with this title and `- Updated: The Reengineering Contract (See Also link)`.

- [ ] **Step 4: Grounding check** → clean.

- [ ] **Step 5: Commit**

```bash
git add wiki/reengineering/decidable-vs-judgement.md wiki/index.md wiki/log.md
git commit -m "wiki(reengineering): the decidable boundary, and escalation over suppression"
```

---

### Task 5: `verification-and-the-gate.md` — R-8, R-9 and the build contract

**Files:**
- Create: `wiki/reengineering/verification-and-the-gate.md`
- Modify: `wiki/index.md`, `wiki/log.md`

**Interfaces:**
- Consumes: the design's build-contract section.
- Produces: the interface plan C implements — three task names, a coverage map, source roots — and the lane protocol plan B ships.

- [ ] **Step 1: Write the article**

Body must carry: R-8's two halves (no completion claim without the gate's own output; a gate that only compiles is not a gate — tests must run); the **build contract** as the three declarations (`preCommit`, `prePush`, `checkFormat`; the coverage map of formatted/linted/compiled/tested per module; the source roots), with the sentence that the gate never learns a build tool's name; R-9's lane protocol (a fresh read-only agent, the diff, the `R-n` list, a verdict per rule, counterexamples with symbols); and the worked example of a lane that found a lost check direction and an over-claimed test. `## See Also` to `reengineering-contract.md`, `decidable-vs-judgement.md`.

Header:

```markdown
# Verification and the Gate: Receipts, the Build Contract, and the Independent Lane

> Sources: `skreeep2` session, 2026-09-19 (gate and lane findings)
> Raw: [The reengineering kit — design (2026-09-19)](../../raw/reengineering/2026-09-19-kit-design.md)
> Updated: 2026-09-19
```

- [ ] **Step 2: Index row**

```markdown
| [Verification and the Gate](reengineering/verification-and-the-gate.md) | R-8, R-9 and the build contract: the three declared tasks, the coverage map, and the independent lane's verdict-per-rule protocol | 2026-09-19 |
```

- [ ] **Step 3: Log entry** — as in Task 3.

- [ ] **Step 4: Grounding check** → clean.

- [ ] **Step 5: Commit**

```bash
git add wiki/reengineering/verification-and-the-gate.md wiki/index.md wiki/log.md
git commit -m "wiki(reengineering): verification receipts, the build contract, and the lane"
```

---

### Task 6: `recording-a-migration.md` — R-12 and R-13

**Files:**
- Create: `wiki/reengineering/recording-a-migration.md`
- Modify: `wiki/index.md`, `wiki/log.md`

**Interfaces:**
- Produces: how a migration states its target list, its exit condition, and its record — the shape plan B's skills and the on-demand procedure assume.

- [ ] **Step 1: Write the article**

Body must carry: R-13 (a reshape is declared deliberate by an explicit target list; a module is not reshaped because a diff touched it) with the two-document shape (a durable contract and a rewritten campaign/dial); R-12 (docs amended as the shape moves; a stale claim is a defect, not a nit — the raw file records three that were corrected; an open divergence is recorded as a *ruling*, with the reason it was not reconciled); what does **not** count as a record (a gitignored ledger, a chat summary, a TODO); and one commit per module. `## See Also` to `reengineering-contract.md` and `decidable-vs-judgement.md`.

Header:

```markdown
# Recording a Migration: Target Lists, Receipts, and Rulings

> Sources: `skreeep2` campaign documents; the reengineering-kit design, 2026-09-19
> Raw: [The reengineering kit — design (2026-09-19)](../../raw/reengineering/2026-09-19-kit-design.md)
> Updated: 2026-09-19
```

- [ ] **Step 2: Index row**

```markdown
| [Recording a Migration](reengineering/recording-a-migration.md) | R-12, R-13: the target list and exit condition, why a gitignored ledger is not a record, and how an open divergence becomes a ruling | 2026-09-19 |
```

- [ ] **Step 3: Log entry** — as in Task 3.

- [ ] **Step 4: Grounding check** → clean.

- [ ] **Step 5: Commit**

```bash
git add wiki/reengineering/recording-a-migration.md wiki/index.md wiki/log.md
git commit -m "wiki(reengineering): recording a migration — target lists, receipts, rulings"
```

---

### Task 7: Cross-links, index hygiene, and the closing check

**Files:**
- Modify: `wiki/reengineering/*.md` (See Also sections only), `wiki/index.md`, `wiki/log.md`

**Interfaces:**
- Produces: the finished topic — every ID reachable, every link resolving.

- [ ] **Step 1: Verify every `R-n` is reachable and every link resolves**

Run: `for n in $(seq 1 14); do grep -lw "R-$n" wiki/reengineering/*.md; done`
Expected: at least one file per ID (`R-1 … R-14`), and the spine listing all fourteen.

Run: `grep -ho '](\.\.\?/[^)]*)' wiki/reengineering/*.md | sort -u | while read -r l; do f="wiki/reengineering/${l#](}"; f="${f%)}"; [ -e "$f" ] || echo "BROKEN: $l"; done`
Expected: no output.

- [ ] **Step 2: Verify the index lists every article and the log every ingest**

Run: `ls wiki/reengineering/*.md | wc -l` → 5; `grep -c 'reengineering/' wiki/index.md` → 5; `grep -c '^## \[2026-09-19\] ingest' wiki/log.md` → 5.

- [ ] **Step 3: Run the grounding check one last time**

Run: `python3 ~/.claude/skills/karpathy-llm-wiki/scripts/check_evidence.py .`
Expected: clean.

- [ ] **Step 4: Commit**

```bash
git add wiki/
git commit -m "wiki(reengineering): cross-links, index rows and the closing grounding check"
```

---

## Self-review notes

- **Spec coverage:** the design's L1 row ("prose rules with stable `R-n` IDs … citable") → Tasks 2–6; the rules this session produced (R-5, R-6, R-9, R-10, R-11, R-12) → Tasks 3–6; R-13's target-list discipline → Task 6; the build contract (plan C's interface, documented in L1) → Task 5; the design's "`R-n` table is a mapping, 5–6 articles, IDs as the citable spine" → exactly five articles; the design's L1 note that the design document becomes `raw/` material → Task 1. Deliberately not here: the vendored rule (plan B), the skills (plan B), the harness (plan C), the on-demand procedure (plan B).
- **R-1 … R-4, R-7, R-14** have their canonical text in the spine article plus cross-links to the existing articles that elaborate them; no new article is created for them (YAGNI), and the spine says where each is elaborated.
- **Placeholder scan:** every article's header, index row and log entry is given verbatim; article *bodies* are specified as the claims they must carry (a docs deliverable has no compile step, so the test is Task 7's grounding check plus the link/ID checks).
- **Type consistency:** the names used across tasks — the `reengineering` topic, the five article filenames, `R-1 … R-14`, `preCommit`/`prePush`/`checkFormat`, `AllowedShape`, `allowedTypes`, `disallowedConstructs` — are the frozen names from the design's interfaces table.
- **Known risk, stated rather than hidden:** Task 7's link check assumes relative `See Also` links resolve from `wiki/reengineering/`; if the check reports a broken link, fix the *link*, not the check.
