# The diagnostic → instruction table — design (2026-09-20)

**Status:** §1 (purrism) implemented and released as scalafix-purrism v0.9.1 (2026-09-21, master 314afcd; 31 codes, `findings.tsv` in the jar, docs Findings page, `findingsCheck` in CI). §2–§4 (harness) in progress: `skreeep2/docs/superpowers/plans/2026-09-21-diagnostic-instruction-table.md`.
**Origin:** Tier 1, item 3 of `wiki/llm-scala-fusion/roadmap-more-scala-less-llm.md`.
**Evidence:** FPEval (`wiki/llm-scala-fusion/llm-code-defect-taxonomy.md` §4): self-repair improved when static-analysis
findings were paired with "hand-crafted instructions for common types of issues"; raw diagnostics alone repaired less.
**Depends on:** the property feedback loop (`2026-09-20-property-feedback-loop-design.md`) — the normalizer, the
wrapper, the rewrite and the post-edit / turn-end stages are extended, not duplicated.

## Goal

Every diagnostic the agent sees — a purrism finding, a scalac error, a wart — arrives with a *code* it can be joined on
and, when one exists, an *instruction*: one to three imperative sentences saying what to change and what to check
afterwards. Humans keep reading clear text: the code is a suffix tag, the explanation is in the message, and the same
instructions are published on purrism's docs site. Instructions are human authorship; the agent may propose, never write
the frozen tables.

## Decisions taken during brainstorming

| Question | Decision |
|---|---|
| Where the table lives | Purrism ships a catalog and a TSV (versioned with the rules, in the jar and on the docs site); purrism rules also carry the explanation in the diagnostic text; the harness vendors purrism's TSV and adds a project-local overlay for scalac / wart / project-rule codes |
| Join key | Stable finding codes `<RuleName>.<kebab-kind>` for purrism (amended 2026-09-20 at planning: the code IS scalafix's lint id — `categoryID = kind` — so scalafix/Mill print `[Rule.kind]` natively and no suffix is hand-built; the spec's `/` and the manual `[code]` suffix are superseded) (one per finding *kind*), `E006`-style codes for scalac, wart names for wartremover — appended as `[code]` after the human text, never replacing it |
| Join point | The feedback normalizer: `scalafix` and `scalac` adapters produce `diagnostic` records; the renderer appends `INSTRUCTION:` from the tables; post-edit / turn-end / the PreToolUse rewrite route `compile`, `fix --check`, `fixStrict` through the wrapper like tests |
| Scope | Full purrism catalog now (all rule families), a small scalac/wart overlay; a later pass deepens the `Idiom*` / `Suspend*` instructions |
| Transport | A: catalog in purrism + vendored copy in the harness refreshed by a sync script; B (separate findings artifact with a Scala API) when roadmap item 8 lands; C (read the jar at hook time) rejected |

## §1 Purrism: the finding catalog and coded messages

**Catalog** — `scalafix/src/fix/FindingCatalog.scala`:

```scala
final case class Finding(code: String, rule: String, title: String, explanation: String, instruction: String, doc: String)
object FindingCatalog {
  val all: List[Finding]
  def byCode: Map[String, Finding]
  def message(code: String, text: String): String   // s"$text. $explanation [$code]"
}
```

- `code` = `<RuleName>/<kebab-kind>`, e.g. `PreferEffectIdioms/manual-resource`, `SuspendSideEffects/eager-pure`. One code per
  finding *kind*; a rule with a single kind still gets one. Codes are append-only; renaming is a breaking change (documented
  in purrism's `MAINTENANCE.md`).
- `title` ≤ 60 chars. `explanation`: one human-facing sentence, appended to every message. `instruction`: 1–3 imperative
  sentences for the agent — what to change, what to check after (it may say "run `<module>.fix`", the harness never runs it).
  `doc`: an anchor into `docs/index.md` (`#effect-boundaries`).
- Every rule emits `Patch.lint(Diagnostic(pos, FindingCatalog.message(code, text)))`; ad-hoc messages are gone. The
  human text comes first, the tag last — the way scalac prints `[E006]`.

**Coverage tests** (purrism `scalafix/test`):
1. Every lint message in the golden fixtures ends with `[<code>]` and the code is catalogued — else fail naming file:line.
2. Every catalog code appears in at least one fixture output — a dead catalog row fails.
3. Codes unique; `rule` equals the emitting rule's name; all fields non-empty and TAB-free; `message` renders exactly.
4. **Kinds are globally unique** (amended 2026-09-20): umbrella rules (`TypelevelPurrism`, `PreferTypeParameters`) run
   child rules inside their own `SemanticRule`, so scalafix prints `[TypelevelPurrism.readability-budget]` for a
   `PreferArrow` finding. The harness therefore joins on the full code first and falls back to the kind alone; unique
   kinds make that fallback exact. `doc` anchors must exist as headings in `docs/index.md`.

**Export.** A generator (`findingsgen`, beside `indexgen`, or a Mill task) writes `docs/findings.tsv` — header line
`# purrism <version>`, then `code<TAB>rule<TAB>title<TAB>explanation<TAB>instruction<TAB>doc`, sorted by code — and copies it
into `scalafix/resources/purrism/findings.tsv` (in the jar). `mill findings.check` fails CI when the committed TSV is stale
against the catalog. The docs site renders a "Findings" page from the same TSV (mdoc): one text for humans and agents.

**Migration.** Existing messages become `text`; the initial catalog is written from the rule sections of `docs/index.md`
(human authorship; an agent may draft, a human reviews). Roughly 40 rule families → an estimated 60–90 finding kinds.

## §2 Harness: adapters, records, rendering, tables

> **Amended 2026-09-21 at harness planning, from real Mill 1.1.7 captures in skreeep2:**
> 1. Mill's compile reporter prints no Scala 3 error ids (`-- [E006] …` never appears; the shape is
>    `[error] <path>:<line>:<col>` / source line / caret line / message lines / blank). scalac codes are therefore
>    **`scalac.<kind>`**, the kind classified from the message headline by a project-owned `.dsh/instructions/scalac-kinds.tsv`
>    (`kind TAB ERE`); unclassified → `scalac.error` / `scalac.warning`. Warts print `[wartremover:Var] …` on the message
>    line → **`wartremover.Var`**. Every code is `<rule>.<kind>`; `local.tsv` keeps purrism's six columns with `rule` = `scalac` /
>    `wartremover`. The `E006` / bare `Var` spellings below are superseded.
> 2. skreeep2's `fixStrict` printed `[WARNING][<Rule>] <abs path>:<l>:<c> <text>` (rule name only); it now prints the full lint
>    id `[<Rule>.<kind>]`. `fix --check` already prints scalafix's native `<abs path>:<l>:<c>: warning: [<Rule>.<kind>] <text>`.
> 3. Deprecated alias rules (`PreferHKTTypeclasses` …) and the `TypelevelPurrism` umbrella print their own name as prefix; the
>    harness joins by kind when the exact code has no row (kinds are unique across the catalog).
> 4. A `fix --check` pending rewrite yields one `scalafix.pending-rewrite` record per `--- <path>` diff header.
> 5. The wrapper honours `DSH_FEEDBACK_LOG_OUT=<file>` (writes the raw log's path) so post-edit/turn-end attribute on raw text.

**Record kind** — `dsh_feedback_parse` gains `kind<TAB>diagnostic` records:

```
kind	diagnostic
source	scalafix|scalac|wart
code	<PreferEffectIdioms/manual-resource | E006 | Var>
file	<path>:<line>[:<col>]
message	<one line, tag stripped>
```

- `scalafix` adapter: `<path>:<line>:<col>: <warning|error>: [<Rule>] <text> [<code>]`; the trailing bracket is the code;
  when absent, `code` = the rule name from the leading bracket (a rule-level instruction row may still match).
- `scalac` adapter: Scala 3 blocks `-- [E006] Not Found Error: <path>:<line>:<col>` plus the indented lines that follow →
  one record, `code E006`, message = the headline; `-- Warning:` blocks → `code warning`; `[wartremover:Var] …` →
  `source wart`, `code Var`. The `counts` header gains two optional trailing fields, errors and warnings.
- Auto-detect: `-- [E`, `warning: [`, `[wartremover:` select these adapters; the test adapters are unchanged.

**Tables.** `.dsh/instructions/purrism.tsv` (the vendored purrism file, byte-exact) and `.dsh/instructions/local.tsv`
(project-owned: scalac codes, warts, project rules), the same six columns; `local.tsv` wins on a duplicate code.
`dsh_instruction_for <code>` → the instruction and doc, or empty. Both files are frozen (human-owned).
`.dsh/instructions-sync.sh` is the one sanctioned writer of `purrism.tsv`: it extracts `purrism/findings.tsv` from the
coursier-cached purrism jar for the version `build.mill` declares.

**Rendering** — per diagnostic record:

```
DIAGNOSTIC: engine/src/Foo.scala:41:7 [PreferEffectIdioms/manual-resource] use Resource for acquire/release
  INSTRUCTION: Wrap the acquire in Resource.make(acquire)(release) and thread it with `use`; re-run <module>.fix to let the rewrite apply, then compile.
  DOC: https://mercurievv.github.io/scala-purrism/#effect-boundaries
```

The first line reads `FEEDBACK: N errors, M warnings (<selector>)` for a check run. A record without an instruction renders
the `DIAGNOSTIC:` line only; one trailing `No instruction for: <code>, <code>` line makes gaps visible, and the wrapper
appends those codes (deduped) to `.dsh/.state/instructions-missing`. The raw log is kept exactly as for tests.

**Where it runs.** post-edit and turn-end route `<module>.fix --check`, `<module>.fixStrict` and `<module>.compile` output
through the wrapper; the blocking decision and the F53 attribution keep working on the raw log (the hook hands the log
path, not the rendered text, to the attribution step). The PreToolUse rewrite also wraps bare `mill … compile | fix |
fixStrict` calls, with the same exclusions as for tests.

## §3 Installation rules, versioning, and the human side

**`dsh_instruction_checks`** (rows `ok|FAIL|note`, single lines, wired into `check-harness.sh`):
1. `purrism.tsv` present and parseable (six TAB fields, unique codes) → else FAIL.
2. The TSV's `# purrism <version>` header equals the `scala-purrism-scalafix` version `build.mill` declares → else FAIL
   naming both, remedy `sh .dsh/instructions-sync.sh`.
3. `local.tsv` parseable; every code is a scalac `E\d+`, a wart name, or a project rule → malformed row FAIL.
4. `note` listing the codes in `.dsh/.state/instructions-missing` — the backlog of instructions to write.
5. `purrism.tsv`, `local.tsv` and `instructions-sync.sh` are frozen (the self-freeze set grows by three) → else FAIL.

**Purrism CI:** `mill findings.check`, the three coverage tests, the docs page build.

**Versioning contract.** The generator writes the header from the build version; the sync script writes the same header
from the jar. A purrism release that adds codes → sync → a reviewed diff of `purrism.tsv`.

**Humans.** One text, two audiences: `explanation` is what a developer sees in the IDE or CLI; `instruction` is the agent's
repair guidance and is on the docs "Findings" page. Writing instructions is the human's (they encode design judgement,
R-11); the agent proposes rows in chat.

## §4 Testing and verification

*Purrism:* `FindingCatalogSpec` (uniqueness, rule names, non-empty TAB-free fields, exact `message` rendering); the golden
sweep (every message coded and catalogued; every code emitted somewhere); generator round-trip and `findings.check` failing
on a stale copy; one docs-page snapshot row.

*Harness `lib-test.sh`:* `scalafix` adapter on a real captured `fixStrict` transcript (two coded findings → two records
with exact code/file/message; an uncoded finding → rule-name code; ANSI/Mill prefixes); `scalac` adapter on a real compile
failure (`-- [E006] …` → one record; `-- Warning:` → `warning`; `[wartremover:Var]` → wart); counts with errors/warnings;
`dsh_instruction_for` (found, override, unknown, malformed); renderer golden block; missing-instruction summary line;
`dsh_instruction_checks` one negative per rule.

*Fixtures (`run.sh`):* post-edit `fixStrict` stub with a coded finding → the section holds `DIAGNOSTIC:` + `INSTRUCTION:`
and F53 attribution still distinguishes edited vs untouched files; turn-end `<module>.compile` failing with `E006` →
blocking message carries the `local.tsv` instruction; tool-policy rewrites `./mill app.compile` / `app.fixStrict`, leaves
`./mill app.compile | tail` alone, still denies holdout; `instructions-sync.sh` against a fake jar (zip with
`purrism/findings.tsv`, header `# purrism 0.9.1`) and a stub version → writes the file; a version mismatch → check-harness
FAIL, then sync → ok.

*Live smoke (recorded):* a `var` and a manual `try/finally` resource in a scratch `test-draft/src` file; `sh
.dsh/hooks/feedback.sh -- ./mill … <module>.fixStrict` → `DIAGNOSTIC:` lines with purrism codes and `INSTRUCTION:` text;
remove the draft.

*Order across repos:* purrism first (catalog, coded messages, TSV, a release or local publish), then the harness — its
fixtures run on captured transcripts, so it can be built against a locally published purrism.

## Out of scope

The separate findings artifact / Scala API (roadmap item 8); instructions for third-party scalafix rules; applying fixes
automatically; deepening `Idiom*`/`Suspend*` instructions beyond a first sentence (a later pass).

## Files touched

*scalafix-purrism:* `scalafix/src/fix/FindingCatalog.scala` (new); every rule's `Patch.lint` site; `findingsgen/` (new) or a
Mill task; `docs/findings.tsv`, `scalafix/resources/purrism/findings.tsv`; docs "Findings" page; `MAINTENANCE.md`; tests.

*skreeep2:* `.dsh/lib.sh` (adapters, `dsh_instruction_for`, `dsh_instruction_checks`), `.dsh/hooks/feedback.sh`
(missing-code file), `.dsh/hooks/tool-policy.sh` (rewrite selectors), `.dsh/hooks/post-edit.sh`, `.dsh/hooks/turn-end.sh`,
`.dsh/check-harness.sh`, `.dsh/instructions/{purrism,local}.tsv` (new), `.dsh/instructions-sync.sh` (new),
`.dsh/frozen-paths`, `.dsh/README.md`, tests.
