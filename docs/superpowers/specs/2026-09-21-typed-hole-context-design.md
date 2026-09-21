# Typed-hole context tool and skeleton-first skill — design (2026-09-21)

**Status:** approved design, not yet implemented. One plan follows, in `ScalaSemanticMCP`.
**Origin:** Tier 2, item 4 of `wiki/llm-scala-fusion/roadmap-more-scala-less-llm.md`.
**Evidence:** Hazel (`wiki/llm-scala-fusion/where-types-and-properties-buy-most.md`): "contextualization with type
definitions is particularly impactful"; hallucination arises for definitions "not in the training data nor near the
cursor". The tool puts exactly that context — expected type, what fits, the definitions involved — in front of the model
at the moment it fills a hole.
**Depends on:** ScalaSemanticMCP's presentation-compiler backend (`pc/PresentationCompilerBackend`, per-module backends in
`Mcp.scala`), pinned to `scala3-presentation-compiler` 3.8.4; the harness's compile feedback (roadmap item 3) makes the
compile-after-each-fill step cheap to read.

## Goal

An agent that writes a Scala body asks one question at one `???` (or any expression position) and gets back, from the
compiler, the type the position demands and the things in reach that satisfy it — bindings, one-step producers, applicable
givens — plus the definitions of the types involved. A companion skill makes the agent write signatures first, compile,
then fill holes one at a time with that context.

## Decisions taken during brainstorming

| Question | Decision |
|---|---|
| What is a hole | Any expression position, not only `???`. The answer carries `confidence`: `exact` when the enclosing tree prescribes a type, `unconstrained` when nothing does — then no candidates are guessed |
| Candidate depth | Bindings (conforming vals/params/nullary defs) + one-step producers (in-scope methods and companion members whose result type unifies with the expected type, extension methods) + applicable givens. No two-step chains |
| Where the skill lives | `ScalaSemanticMCP/.claude/skills/typed-holes/` — travels with the tool; the dsh `scala-arhitect` profile points at it |
| Engine | A: a typed-tree query in a dotty `InteractiveDriver` built like the PC's own — real `<:<` and `ctx.implicits`, not string matching of completion labels (B) and not SemanticDB-only (C) |

## §1 Tool contract

`hole_context(uri, line, character, source?)` — conventions of `type_at_position`: `uri` is the SemanticDB-form path
relative to the project root, `line`/`character` are 0-based, `source` is the file's current full text (when absent, the
on-disk file is read). The presentation compiler does the typing, so the server must have been started with a classpath;
without one the tool returns the existing "started without classpath" error.

Result (JSON, upickle model `HoleContextResult` in `analysis`):

```json
{
  "found": true,
  "hole":      { "kind": "???" | "expression", "text": "???" },
  "expected":  { "type": "IO[Unit]", "confidence": "exact" | "unconstrained",
                 "why": "result type of def run" | "argument 2 of Resource.make" | "no type prescribed here" },
  "bindings":  [ { "name": "release", "type": "IO[Unit]", "kind": "param" | "val" | "def", "from": "enclosing def run" } ],
  "producers": [ { "name": "IO.unit", "signature": "IO[Unit]", "from": "companion IO" },
                 { "name": "IO.pure", "signature": "[A](a: A): IO[A]", "from": "companion IO" },
                 { "name": "acquire.void", "signature": "IO[Unit]", "from": "extension via cats.syntax.functor" } ],
  "givens":    [ { "name": "IO.asyncForIO", "type": "Async[IO]", "from": "companion IO" } ],
  "types":     [ { "symbol": "cats/effect/IO#", "source": "classpath" | "project",
                   "signature": "sealed abstract class IO[+A]", "excerpt": "…first lines of the definition (project only)…" } ],
  "omitted":   { "bindings": 0, "producers": 12, "givens": 0, "types": 0 },
  "pcFallback": true
}
```

- `confidence: unconstrained` ⇒ `bindings`, `producers`, `givens` are empty and `why` says what is missing (`no type
  prescribed here — declare the val's type or the def's result type first`).
- Bounds: bindings ≤ 20, producers ≤ 15, givens ≤ 10, types ≤ 5. Ordering: exact conformance before unification through
  type parameters; within that, scope distance — locals, enclosing class, imports, companion. `omitted` counts what the
  bounds cut.
- Position not inside an expression (a comment, a keyword, whitespace between definitions) → `{ "found": false, "why": … }`.
- `pcFallback` is present and `true` when the answer came from a buffer the disk index does not hold, as for
  `type_at_position`.
- Types are rendered with the PC's printer (short names, as `type_at_position` prints them).

## §2 Engine (`pc` module)

`pc/HoleContext.scala`, next to `PresentationCompilerBackend`, owns an `InteractiveDriver` built from the same classpath and
options (the PC's driver is private; mtags constructs one identically). One `HoleContext` per module backend, sharing
lifecycle and `close()` with it.

1. `driver.run(uri, code)` → error-tolerant typed tree; the innermost expression tree spanning the offset is the hole.
2. **Expected type** — the enclosing-tree cases of dotty's `InferExpectedType`: `ValDef`/`DefDef` with a declared `tpt` → that
   type (`exact`, `why = result type of def <name>`); `Apply` argument → the parameter's type after instantiating the method's
   type parameters from the other arguments (`exact`, `why = argument <n> of <method>`); `If`/`Match` branch, `Block` last
   expression, `Typed` → the propagated expected type; `ValDef` without `tpt`, a bare statement, a `Block` non-last
   statement → `unconstrained`.
3. **Bindings** — walk `ctx.outersIterator`: local scopes (`ctx.scope`), enclosing class members (`owner.info.decls` and
   inherited), the `ctx.importInfo` chain. Keep vals, params and nullary defs whose `info.finalResultType <:< expected`.
4. **Producers** — the same pool plus the companion of `expected.classSymbol` and the companions of its type arguments'
   class symbols, plus extension methods reachable through `ctx.implicits`. Keep methods whose `finalResultType` unifies
   with `expected` (type parameters `constrained`, then `<:<`); render the full signature.
5. **Givens** — `ctx.implicits.eligible(expected)` when `expected` is an applied type whose class is a trait/class taking
   the hole's type as a parameter (typeclass shape), plus for each listed producer's `using` clause the eligible instances,
   deduplicated.
6. **Types** — class symbols of `expected` and its arguments (≤ 5). Project-owned (the SemanticDB index holds the
   document) → `source: project` with an excerpt of the definition's first lines from the existing source helpers;
   classpath → `source: classpath` with the signature only.

Everything dotty-internal stays in `pc`; `analysis` holds the upickle model; `mcp` holds the tool wrapper over the existing
`ModulePresentationCompilerBackends` and the `source`/disk/`pcFallback` selection exactly as `type_at_position` does.

## §3 The skill

`ScalaSemanticMCP/.claude/skills/typed-holes/SKILL.md` — trigger: writing a new `def`/`val` body, or any function longer than
a one-liner, in a project served by ScalaSemantic.

1. **Skeleton first** — write the signature(s) with `???` bodies; nested steps as local `def step: T = ???`. Compile
   (`<module>.compile`): signature errors surface before any body exists.
2. **One hole at a time** — `hole_context(uri, line, character, source)` on the first `???`. Read `expected`; if
   `unconstrained`, declare the type first and re-ask. Choose from `bindings` / `producers` / `givens`. A body longer than
   about three expressions is split into new `???` sub-holes rather than written in one go.
3. **Fill, compile, repeat** — every fill is followed by `compile` before the next hole; a red compile is fixed at that
   hole, never by rewriting other holes.
4. **Red flags** — "I know this API" → still ask for a definition that is not in the file; "I'll fill all holes then
   compile" → one at a time; never `asInstanceOf` or `null` to satisfy an expected type; a candidate list that is empty with
   `confidence: exact` means the value has to be built — ask `hole_context` on a smaller sub-hole, or add a binding.

Docs: a tool page under `docs/reference` with an mdoc-run example like the other tools; the README tool list;
`chat-tool-usage.md`. The dsh `scala-arhitect` profile gets one line pointing at the skill (a separate small commit in dsh).

## §4 Testing

*`pc` — `HoleContextSuite`* (dogfood plus fixtures under `pc/test/resources`): `???` as a `def` result → `exact`, a
conforming parameter listed as a binding; `???` as a method argument → the parameter type after type-argument
instantiation; `val x = ???` → `unconstrained` with empty lists and the declare-first `why`; `IO[Unit]`-style generic
expected type → a generic producer (`pure`-shaped) listed with its signature; a typeclass expected type (`Show[Int]`) → the
given listed; a subtype binding accepted (`List[Int]` for `Seq[Int]`); a non-conforming binding excluded; bounds honoured
with `omitted` counts; a buffer with a type error elsewhere still answers; a position in a comment → `found: false`.

*`mcp`*: JSON shape against the contract above; `source` vs disk path; `pcFallback`; the no-classpath error.

*Compat golden* (`compatGoldenAll`): one entry for the new tool. *Docs*: the mdoc example compiles and runs.

*Skill*: no automated test; the plan's live smoke runs the procedure once on a scratch `def` in skreeep2 and records the
transcript in the task report.

## Out of scope

Two-step chains (`f(g(x))`, `a.toB.toC`); ranking by usage frequency; a harness gate that enforces skeleton-first (a
possible follow-up in skreeep2's post-edit, like roadmap item 3's wiring); Scala 2 projects; positions in pattern or type
trees.

## Files touched

*ScalaSemanticMCP:* `pc/src/main/scala/…/pc/HoleContext.scala` (new), `pc/src/test/…/HoleContextSuite.scala` (new),
`analysis/src/main/scala/…/model/HoleContextResult.scala` (new), `mcp/src/main/scala/…/mcp/McpTools.scala` (tool),
`mcp/src/main/scala/…/mcp/Mcp.scala` (backend wiring), `mcp` tests, compat golden, `docs/reference/hole-context.md`,
`mdoc-docs` example, `README.md`, `chat-tool-usage.md`, `.claude/skills/typed-holes/SKILL.md` (new).
*dsh:* `scala-arhitect` profile — one pointer line.
