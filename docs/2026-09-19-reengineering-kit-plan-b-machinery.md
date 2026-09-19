# Reengineering Kit, Plan B — the machinery in `scala-llm-template`

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the kit installable: one canonical copy of the `AllowedShape` rule with fixtures that prove it discriminates, the four process skills plus the verification-lane skill generalized to cite `R-n`, a wiring recipe per build tool, and a `Setup.scala` feature that vendors all of it into a target project.

**Architecture:** The template is the kit's distribution point (design L2/L3). The rule is vendored, not published: the canonical file lives under `kit/arhitecture-shape/`, a shell check drives the scalafix CLI over two fixtures, a new `Setup.scala` `Feature` writes the rule, the wiring, the skills and an `R-n` digest into a project, and `kit/apply.md` is the six-step procedure a human or agent follows afterwards. Nothing in this plan touches `skreeep2` or the wiki.

**Tech Stack:** Scala 3.8.4 via scala-cli (`project.scala` `//> using` directives), `Setup.scala` (an `IOApp` of `Feature`s writing files with `os.write`, plus a `--agent` protocol mode), POSIX shell for the fixture check, the scalafix CLI launched through coursier.

**Spec:** `scala-coding-guidelines/docs/2026-09-19-reengineering-kit-design.md` (this plan argues from it; §L2 and §The procedure are binding here) and the compiled rules `scala-coding-guidelines/wiki/reengineering/reengineering-contract.md` (`R-1 … R-14`).

## Global Constraints

- **Frozen interface, do not rename:** the rule name `AllowedShape`; the config keys `allowedTypes`, `disallowedConstructs`; the gate task names `preCommit`, `prePush`, `checkFormat`. The rule's canonical text is `/Users/viktorskalinins/IdeaProjects/skreeep2/scalafixRules/src/AllowedShape.scala` (171 lines) — copied **verbatim** except its `package` line, which becomes `io.github.mercurievv.arhitecture.scalafix`.
- **Never suppress (R-10):** a fixture that does not discriminate is a defect; no `@SuppressWarnings`, no allowlist widening, no threshold bump anywhere in this plan.
- **The rule's fixtures must fail first:** write the violating fixture and watch the check report its diagnostics before the clean fixture is added.
- **`Setup.scala` contract, as the file actually has it (corrected during execution):** the question is a `Feature(id, group, name, prompt, defaultValue)` in `getFeaturesList`, and the work is a `private object … extends SetupStep` with `id` and `run(ctx: SetupContext): StepResult`, registered in `setupSteps`. The kit's step sits between `RunSpecializedSetupScripts` and `FormatProject`: that ordering is what makes the digest land after `.agents/AGENTS.md` is regenerated, and before formatting and staging cover the new files. Writes go through `os.write` (a vendored file that already exists with different content is never overwritten silently — the kit's copy is written beside it), human progress goes to stderr, and a second run must leave every file the step writes byte-identical.
- **No new build tooling in the template:** the fixture check is a POSIX shell script invoking the scalafix CLI; do not add sbt/mill to this repository.
- **The wiki and `skreeep2` are read-only** for this plan.

## File Structure

| File | Responsibility |
|---|---|
| `kit/arhitecture-shape/AllowedShape.scala` | the canonical rule (vendored; copied verbatim apart from the package) |
| `kit/arhitecture-shape/META-INF/services/scalafix.v1.Rule` | rule registration (one line: the FQCN) |
| `kit/arhitecture-shape/README.md` | the config schema, and what the rule can and cannot decide (R-11) |
| `kit/arhitecture-shape/check.sh` | builds the rule and runs the scalafix CLI over `fixtures/violating/` and `fixtures/clean/`, asserting their diagnostic counts |
| `kit/arhitecture-shape/fixtures/violating/Example.scala`, `…/clean/Example.scala` | the discriminating pair |
| `kit/recipes/mill.md`, `kit/recipes/sbt.md`, `kit/recipes/scala-cli.md` | how to turn the rule on, per build tool |
| `kit/recipes/mill/ArchitectureShapeModule.mill` | the Mill helper generalized from `skreeep2`'s (parameterized: package root, allowlist, denylist) |
| `kit/skills/{shaping-a-module,reengineering-module,reshaping-module-by-linters,verify-before-done,verification-lane}/SKILL.md` | the process skills, citing `R-n` |
| `kit/digest.md` | the agent-facing block the installer plants into a project (`AGENTS.md`/`scala-rules.md`) |
| `kit/apply.md` | the six-step on-demand procedure |
| `Setup.scala` | gains the `kit` feature |
| `README.md`, `CONVENTIONS.md` | the kit's existence and its one-line install command |

---

### Task 1: The canonical rule, its registration, and a check that discriminates

> **Amended after execution (2026-09-19).** Steps 3–6 below record what the executed check actually is: the
> measured CLI artifact and flags, the four per-mechanism diagnostics, the fixture config's own-package
> prefix, the SemanticDB precondition and the asserted exit statuses. The first draft's guesses at these are
> replaced rather than left beside the truth (R-12: the record travels with the code).

**Files:**
- Create: `kit/arhitecture-shape/AllowedShape.scala`, `kit/arhitecture-shape/META-INF/services/scalafix.v1.Rule`, `kit/arhitecture-shape/README.md`, `kit/arhitecture-shape/check.sh`
- Create: `kit/arhitecture-shape/fixtures/violating/Example.scala`

**Interfaces:**
- Consumes: nothing.
- Produces: `check.sh` (exit 0 when the fixtures behave), the canonical rule file every later task and every recipe copies.

- [x] **Step 1: Copy the rule verbatim**

```bash
S=/Users/viktorskalinins/IdeaProjects/skreeep2/scalafixRules/src/AllowedShape.scala
T=/Users/viktorskalinins/IdeaProjects/my/scala-llm-template
mkdir -p "$T/kit/arhitecture-shape/META-INF/services" "$T/kit/arhitecture-shape/fixtures/violating" "$T/kit/arhitecture-shape/fixtures/clean"
sed 's|^package skreeep2.scalafix$|package io.github.mercurievv.arhitecture.scalafix|' "$S" > "$T/kit/arhitecture-shape/AllowedShape.scala"
diff <(grep -v '^package' "$S") <(grep -v '^package' "$T/kit/arhitecture-shape/AllowedShape.scala")   # expect no output
```

- [x] **Step 2: Register the rule**

`kit/arhitecture-shape/META-INF/services/scalafix.v1.Rule` contains exactly one line:

```
io.github.mercurievv.arhitecture.scalafix.AllowedShape
```

- [x] **Step 3: Write the violating fixture**

`kit/arhitecture-shape/fixtures/violating/Example.scala` produces **four** diagnostics — one per mechanism the rule implements: a plain type reference, an importee, an object-valued term, and a construct:

```scala
package example

import java.time.Instant // not in the allowlist below

import scala.util.Random // a Scala object reached as a term

object Example:
  def at(i: Instant): Int = i.getNano
  def pick(): Int = Random.nextInt()
  def render(flag: Boolean): Int = if flag then at(Instant.now()) else pick()
```

(As executed the file is close to this. What matters is the property, not the text: **every mechanism has a fixture line that can fail.** A mechanism whose expected count is zero cannot detect its own disappearance — measured by deleting the rule's `case t: Type.Name` block on a scratch copy: the earlier, zero-floored check stayed green.)

Two configuration facts the fixtures depend on, both found by executing the check:

- **The default allowlist includes the fixture's own package prefix** (`example/`), because that is what every real consumer does — `skreeep2`'s helper appends `ownPackagePrefix` to its list (`buildKit/ArchitectureShapeModule.mill:56`). Without it the rule reports a module's own `object` *definition* name as a disallowed reference, so the check would be proving a configuration nobody uses.
- **`Instant.now()` does not exercise the object-valued-term mechanism**: a Java static resolves to a class (`#`) symbol, and that mechanism accepts only `.`-terminated object symbols. The fixture reaches it with a genuine Scala object instead.

- [x] **Step 4: Write `check.sh`, and watch it fail on the fixture**

The script (POSIX `sh`, `set -eu`) must: resolve its own directory; take `ALLOWED_TYPES` and `DISALLOWED_CONSTRUCTS` from its arguments (defaulting to a minimal set — `scala/Predef.String`, `scala/Int`, `scala/Boolean`, `scala/Unit`, `scala/Any`, `example/` — plus `TermIf`); compile the rule against the scalafix rules artifact; run the scalafix CLI and count `disallowedType`/`disallowedConstruct` lines **per mechanism**. The invocation, as measured:

```sh
cs launch ch.epfl.scala:scalafix-cli_3.8.4:0.14.7 --main-class scalafix.cli.Cli -- \
  --tool-classpath <rule classes + META-INF> --rules AllowedShape \
  --config <generated conf> --check <fixture file>
```

- The CLI is the **Scala 3.8.4** artifact, matching `scalafix-rules_3.8.4` (the rule compiles under Scala 3); `cs launch` alone fails with "Cannot find default main class".
- Flags that exist: `--check`, `--config`. (`--test` is undocumented and behaves as `--check`; `--scalafix-conf` does not exist.)
- **The fixtures must be compiled with SemanticDB**, and the result must reach the CLI's classpath: `AllowedShape` is a `SemanticRule` and every type diagnostic is guarded by `t.symbol.value.nonEmpty`, so an uncompiled fixture yields construct diagnostics only. Under scala-cli that is `--semanticdb --semanticdb-sourceroot <dir>`; a bare `-Xsemanticdb` writes the file beside the source and the CLI answers `SemanticDB not found` (exit 8).
- **The CLI's status is asserted per fixture**, not merely printed: 8 means no SemanticDB was found, 4 means the rule did not load, 64 is the lint-findings status. Counting diagnostics from a failed run is how a check prints `clean: 0 diagnostics … OK` while proving nothing — measured on a scratch copy whose clean fixture was compiled without SemanticDB.
- Exit codes: **0** pass, **1** an artifact defect (count drift, missing fixture, load failure), **2** a setup failure (no `scala-cli`, no `cs`, no usable work directory, the rule will not compile).

Run it once with only the violating fixture present:

Run: `sh kit/arhitecture-shape/check.sh`
Expected: FAIL — the violating fixture's four diagnostics are printed (1 type reference, 1 importee, 1 object-valued term, 1 construct) and the script exits non-zero because the clean fixture is missing. The diagnostics must appear before the missing-fixture message, or the run has not shown that the check discriminates.

- [x] **Step 5: Add the clean fixture and watch the check pass**

`kit/arhitecture-shape/fixtures/clean/Example.scala` uses only allowlisted types and no disallowed construct; as executed it declares an `object`, so the pair also proves the own-package prefix keeps a module's own definitions out of the report.

Run: `sh kit/arhitecture-shape/check.sh`
Expected: PASS — `violating: 4 diagnostics [type references 1, importees 1, object-valued terms 1, constructs 1]`, `clean: 0 diagnostics [0, 0, 0, 0]`, exit 0.

- [x] **Step 6: Write the README** (schema, the decidable/judgement boundary, and the measured blind spots)

`kit/arhitecture-shape/README.md` states: what the rule checks, mechanism by mechanism; the two config keys and their exact spelling; that the policy lives in **each project's build**, never in the rule file; the own-package-prefix convention with its citation; the SemanticDB precondition with the failure modes actually measured (the CLI refuses with exit 8, or under `--syntactic` runs nothing and exits 0) under the sentence that carries the point — *a silent pass is not a pass*; and the R-11 boundary: the rule decides types and constructs, never whether a module split is right. It also names the **measured blind spot**: a type reached as a fully-qualified static term (`java.time.Instant.now()` with no import) escapes both the importee and the object-term checks, because a Java static resolves to a class (`#`) symbol while the term mechanism accepts only `.`-terminated object symbols. Closing that is a kit-level behaviour change, not an edit to a vendored copy.

- [x] **Step 7: Commit**

```bash
git add kit/arhitecture-shape
git commit -m "kit(shape): the canonical AllowedShape rule, its registration and a discriminating fixture check"
```

---

### Task 2: The recipes — Mill, sbt, scala-cli

**Files:**
- Create: `kit/recipes/mill.md`, `kit/recipes/sbt.md`, `kit/recipes/scala-cli.md`, `kit/recipes/mill/ArchitectureShapeModule.mill`

**Interfaces:**
- Consumes: Task 1's canonical rule path and the frozen keys.
- Produces: the wiring each install target uses; the Mill helper the installer writes into a Mill project.

- [x] **Step 1: Generalize the Mill helper**

`kit/recipes/mill/ArchitectureShapeModule.mill` is `skreeep2`'s `buildKit/ArchitectureShapeModule.mill` (61 lines) with its three repo-specific strings turned into parameters: the package root (`com/github/mercurievv/skreeep2/` → a required `def packageRoot`), the base allowlist (`def baseAllowedTypes`), and the construct denylist (`def disallowedConstructs`). Keep the own-package-prefix derivation from Mill's `moduleSegments`.

- [x] **Step 2: Write the three recipes**

Each recipe is a complete, copy-pasteable snippet: where the rule file goes, how it reaches the tool classpath (`scalafixToolClasspath` in Mill; `scalafixDependencies`/`--tool-classpath` for sbt; the CLI form for scala-cli), the config block with both keys, and the command that runs it (`./mill <module>.fix --check`, `sbt "scalafixAll --check"`, `scala-cli --power scalafix .`). Each recipe ends with the same warning: the policy is per module, and a module that needs an exception **removes the entry from its own list** (R-10) rather than widening a shared one.

- [x] **Step 3: Verify each recipe against its tool's real CLI**

At minimum, run the scala-cli snippet in a scratch directory holding a copy of the canonical rule and confirm it reports the violating fixture's two diagnostics. Record the exact command and output in the commit message body.

- [x] **Step 4: Commit**

```bash
git add kit/recipes
git commit -m "kit(recipes): mill, sbt and scala-cli wiring for the shape rule, with the Mill helper parameterized"
```

---

### Task 3: The skills, generalized to cite `R-n`

**Files:**
- Create: `kit/skills/{shaping-a-module,reengineering-module,reshaping-module-by-linters,verify-before-done,verification-lane}/SKILL.md`

**Interfaces:**
- Consumes: the wiki's `R-1 … R-14` (read-only) and `/Users/viktorskalinins/IdeaProjects/skreeep2/.dsh/skills/*/SKILL.md` as the base text.
- Produces: five skills the installer copies into a project; `verification-lane` is the L5 protocol (R-9).

- [x] **Step 1: Adapt the four existing skills**

Copy each `skreeep2/.dsh/skills/<name>/SKILL.md` (47–63 lines) and replace every reference to that repository's documents with the corresponding rule ID: `shaping-a-module` → R-2, R-4; `reengineering-module` → R-13; `reshaping-module-by-linters` → R-10, R-11; `verify-before-done` → R-8, R-9. Keep each skill's own structure (the rule, the red flags, the refusals); do not shorten them into summaries.

- [x] **Step 2: Write `verification-lane/SKILL.md`** (new, the L5 protocol)

Its content is the protocol as the wiki's `verification-and-the-gate.md` states it: a fresh read-only agent; the diff plus the `R-n` list; a verdict per ID; counterexamples with symbols found through the semantic tooling; and the rule that its report begins with the first verdict, not a summary.

- [x] **Step 3: Check every citation resolves**

Run: `grep -ho 'R-[0-9]*' kit/skills/*/SKILL.md | sort -u`
Expected: a subset of `R-1 … R-14`, each of which exists in the wiki's spine.

- [x] **Step 4: Commit**

```bash
git add kit/skills
git commit -m "kit(skills): the four process skills cite R-n, plus the verification-lane protocol"
```

---

### Task 4: The installer — a `kit` feature in `Setup.scala`

**Files:**
- Modify: `Setup.scala` (the `Feature` list, and a new `run` implementation)
- Create: `kit/digest.md`

**Interfaces:**
- Consumes: Tasks 1–3 (`kit/arhitecture-shape/`, `kit/recipes/`, `kit/skills/`), the answers schema in `.agents/answers.json`.
- Produces: `Setup.scala --agent --answers '{"kit":"yes","build-tool":"mill"}' .` writes the rule, the registration, the recipe's wiring, the five skills under `.dsh/skills/`, and the digest block.

- [x] **Step 1: Write the digest**

`kit/digest.md` is the block the installer plants: three sentences naming the kit, the rule name, the two config keys, the five skills and the path of the wiki's `R-1 … R-14` list — restating no rule (R-12: the wiki is the canonical text).

- [x] **Step 2: Add the feature**

A `Feature` with `id = "kit"`, labelled "Arhitecture-shape rule, skills and lint wiring", whose `run` copies the files according to the answers (`build-tool` chooses the recipe; an existing `AGENTS.md`/`scala-rules.md` gets the digest appended between stable markers; a second run replaces the marked block rather than appending again). A missing answer never reaches a step: `--agent` answers every question before the pipeline starts, and reports `{"status":"need-input",…}` with exit `2` when one is absent (measured, not assumed — see the execution record).

- [x] **Step 3: Verify against a scratch project, twice**

```bash
mkdir -p /tmp/kit-install-check && cd /tmp/kit-install-check && git init -q .
scala-cli run /path/to/scala-llm-template/Setup.scala -- --agent --answers '{"kit":"yes","build-tool":"mill"}' .
find . -type f | sort                     # expect: the rule, the registration, the Mill helper, the 5 skills, the digest block in AGENTS.md
scala-cli run /path/to/scala-llm-template/Setup.scala -- --agent --answers '{"kit":"yes","build-tool":"mill"}' .
git status --porcelain                    # expect: empty (idempotent)
```

- [x] **Step 4: Commit**

```bash
git add Setup.scala kit/digest.md
git commit -m "kit(setup): a kit feature that vendors the rule, the wiring, the skills and the R-n digest"
```

---

### Task 5: `kit/apply.md` — the on-demand procedure

**Files:**
- Create: `kit/apply.md`
- Modify: `README.md`, `CONVENTIONS.md` (one section each)

**Interfaces:**
- Consumes: the design's §The procedure; the installer's CLI; the recipes.
- Produces: the document a human or agent follows when applying the kit to a project nobody prepared.

- [x] **Step 1: Write the procedure**

Six steps, each with its output and its stop condition, exactly as the design states them (read the ground and get the module list approved; install; baseline the gate red or green; reshape one module through the four skills; record; repeat to the exit condition), plus the boundary checkpoint (R-11: a green gate is not a correct split) and the escalation rule (R-10).

- [x] **Step 2: Wire it into the template's own docs**

`README.md` gains a "Reengineering kit" section with the one-line install command; `CONVENTIONS.md` gains the same one-liner in its key-commands table.

- [x] **Step 3: Commit**

```bash
git add kit/apply.md README.md CONVENTIONS.md
git commit -m "kit(docs): the six-step on-demand procedure, and the template's pointer to it"
```

---

### Task 6: End-to-end acceptance on a scratch project

**Files:** none (verification only); the report is the deliverable.

**Interfaces:**
- Consumes: everything above.
- Produces: the evidence that a project nobody prepared can be brought under the kit.

- [x] **Step 1: Install into the scratch project from Task 4** (or a fresh one) and run the project's own lint on a violating file: the vendored rule must report the same two diagnostics the canonical check reports.

- [x] **Step 2: Confirm the frozen interface survived the vendoring** — both config keys are present in the project's config, and the rule name is `AllowedShape`.

- [x] **Step 3: Confirm the digest points at the wiki and restates no rule** — the planted block names the rule, the two keys, the five skills and the wiki path, and contains no `R-n` statement text.

- [x] **Step 4: Report** — commands and outputs to the report file; no commit (verification only).

## Self-review notes

- **Spec coverage:** design L2 (canonical copy vendored per project, policy in the build, frozen keys/name, extract-later trigger) → Tasks 1–2 and the README; L3 (four skills + the lane) → Task 3; the installer half of the design's §procedure → Task 4; the six-step procedure → Task 5; the design's "the rule has no tests today" → Task 1's fixtures. Deliberately not here: the wiki articles (Plan A, landed) and the harness (Plan C).
- **The rule stays vendored**, not published: the plan copies a file and ships fixtures; it creates no new repository, matching the design's deferred-extraction trigger.
- **Placeholder scan:** every path, key, rule name and command is exact; the rule itself is copied by a command rather than transcribed, and the copy is diff-checked.
- **The flagged risk was real, and its answer is now measured, not guessed:** the scalafix CLI's tool-classpath invocation for a locally built Scala 3 rule needed a round of adjustment. What it cost: the Scala 3 CLI artifact plus an explicit `--main-class`, `--check`/`--config` rather than the names first written down, SemanticDB compiled into the classpath, and a per-fixture status assertion. Everything the first draft got wrong was in `check.sh`, none of it in the rule — which is the plan's own prediction, restated here because it held.

## Execution record (2026-09-19)

Executed in a workspace-local clone of `scala-llm-template` on branch `kit/machinery`, landed on `master` as a fast-forward from `ae00b9e` to `cecf9f9` — 18 commits, 20 files, +3174 — with the branch deleted and the working tree clean.

| Task | Outcome |
|---|---|
| 1 — rule, registration, discriminating check | two review rounds: the first found a check that could print `OK` after a CLI failure, and a rule mechanism whose expected count was zero so its own disappearance could not be detected. Both fixed; the second is now mutation-proven (deleting the rule's `case t: Type.Name` block fails the check) |
| 2 — recipes | two rounds. All three recipes were **executed** against their real tools, and the Mill recipe was then re-verified by assembling a project *only* from the committed text — which caught two further installability defects the first run had masked by hand-adding lines |
| 3 — skills | approved on the first pass; all eight in-body rule quotes are byte-identical to the wiki's spine |
| 4 — installer | one round. Findings included a version rewrite that made the generator's own default answer unresolvable, and a scala-cli branch that wrote the rule into the tree the project compiles |
| 5 — procedure and template docs | batched with Task 6 |
| 6 — acceptance | an installed project's vendored rule reproduces the canonical four diagnostics (one per mechanism) and none on a clean file; the digest is pointer-only; the acceptance found the space-separated HOCON list examples that silently disabled the denylist in text a user copies |
| final | whole-branch review: "With fixes", six Important; one fix wave; its scoped re-review found every finding addressed, and independently reproduced the one measurement that refuted a review premise |

**Known limits, deliberately left in place.** The template's `scripts/setup-build.scala` writes `build.sc`, which Mill 1.x ignores (so a Mill target needs the `build.mill` this kit writes) and re-fetches "latest Mill" on every run, so two identical generator runs can differ. The pre-existing pipeline prints "Setup Completed Successfully" with exit 0 even when a step reported `failed`, so a caller must read the per-step JSON. One prose gloss in `kit/arhitecture-shape/README.md:162-163` is parked with a ruling.
