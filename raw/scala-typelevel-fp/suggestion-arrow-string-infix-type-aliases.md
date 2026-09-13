# Suggestion: Represent Arrow-Shaped Types as Symbolic Infix Type Aliases (`-->`, `==>`, `~~>`)

> Source: Self-authored suggestion (Claude Code), synthesized from Scala 3's `infix` feature, cats' `~>` precedent, and `scala-purrism`'s existing recognition of `-->` as a Kleisli alias
> Collected: 2026-09-13
> Published: 2026-09-13

This is an opinion/suggestion note, not a quoted external source — treat the recommendation itself as guidance to weigh, not as a fact requiring grounding-invariant verification. The cited precedents (cats' `~>`, Scala 3's `infix type`, `scala-purrism`'s `KleisliType`) are separately grounded in their own raw files.

## The suggestion

For a Scala 3 codebase with arrow-shaped abstractions — Kleisli-style effectful functions (`A => F[B]`), `cats.arrow.Arrow`-family types, natural transformations (`F[_] => G[_]`), or a bespoke domain "process" type — consider giving the *unapplied* type constructor a short symbolic infix alias and writing it infix at use sites, instead of always spelling out `SomeArrow[A, B]`:

```scala
type -->[A, B] = Kleisli[F, A, B]     // "plain" effectful function A --> B
type ==>[A, B] = Kleisli[F, A, B]     // an alternative spelling, same idea
type ~~>[F[_], G[_]] = FunctionK[F, G] // a natural transformation F ~~> G (cats already ships `~>` for this)

def validate: User --> ValidatedUser   // reads as "User to ValidatedUser", not "Kleisli[F, User, ValidatedUser]"
def toEither: F ~~> Either[Err, *]     // reads as "an F-to-Either transformation"
```

## Why this has real precedent, not just aesthetics

- **`infix type` is a first-class Scala 3 feature**, not a hack: `infix type or[X, Y]` lets you write `A or B` for any two-parameter type ([Scala 3 Reference: Rules for Operators](https://docs.scala-lang.org/scala3/reference/changed-features/operators.html)). Symbolic type names (like `-->`) don't even need the `infix` modifier — "Infix operations involving symbolic operators are always allowed."
- **Cats already does exactly this for natural transformations.** `cats.~>` is a type alias for `FunctionK`, letting you write `List ~> Option` instead of `FunctionK[List, Option]`. The cats docs frame this explicitly as an arrow-symbol analogy: "Being able to use `~>` as an alias for `FunctionK` parallels being able to use `=>` as an alias for `Function1`." That is the exact pattern this suggestion generalizes — if `~>` is idiomatic for one arrow-shaped abstraction (natural transformations), the same move is available for others (Kleisli, `cats.arrow.Arrow` instances, bespoke pipeline stages).
- **`scala-purrism` (this project's own scalafix tooling) already treats `-->` as a recognized Kleisli-alias spelling.** Its `fix.arrow.KleisliType` discriminator "dealiases through `-->`, `Flow`, fully-qualified and inferred types via SemanticDB" and specifically distinguishes an alias named `-->` from an unrelated type *parameter* that happens to share the spelling (`ProgramArrows[-->[_, _]]`) — see `raw/scala-typelevel-fp/scala-purrism-arrow-patterns.md`. In other words, the tooling that already refactors Kleisli-shaped code in this codebase's convention has independently converged on `-->` as the canonical example spelling for "a user-defined Kleisli alias." This suggestion just proposes adopting that spelling (and its `==>`/`~~>` siblings for other arrow families) deliberately, rather than it being an incidental fixture name.
- **`~~>` is specifically called out in the Scala language-design corpus as a legal, idiomatic-looking symbolic operator.** SIP-33 ("Priority-based infix type precedence") opens by noting "Currently scala allows symbol operators (`-`, `*`, `~~>`, etc.) for both type names and definition names" — `~~>` is used there as the paradigm example of a multi-character symbolic name, not a hypothetical.
- **Other libraries independently reach for the same idea.** The reintech.io infix-types guide shows `type ->[K, V] = (K, V)` and repeats the `~>`-for-`FunctionK` example as one of the standard illustrations of what infix type aliases are *for* — arrow-suggestive symbols reading left-to-right as "goes to."

## Caveats to weigh (from the same sources)

- **Precedence surprise.** SIP-33 documents that infix *types* do not (pre-fix) share expression operators' precedence rules — `N1 + N2 / N3` parses differently as a type than as a term. This mattered for arithmetic-shaped operators (`+`, `/`); an arrow alias used purely for a single binary relationship (`A --> B`, not chained/mixed with other operators) mostly sidesteps this, but avoid combining multiple different infix type operators in one expression without parentheses.
- **Discoverability/IDE support.** The reintech.io guide flags "limited IDE support" and team-unfamiliarity as real costs of symbolic infix type aliases generally — go-to-definition and hover-doc tooling is more mature for named types (`Kleisli[F, A, B]`) than for symbolic aliases. Reserve the convention for a small, well-known set of arrow-shaped aliases (mirroring how `~>` is the *one* alias cats promotes for `FunctionK`, not a proliferation of ad hoc symbols) rather than inventing a new symbol per use case.
- **Name collisions across meanings.** `scala-purrism`'s own fixtures had to specifically guard against `-->` being confused between "the Kleisli alias" and "an unrelated type parameter with the same spelling" (`ProgramArrows[-->[_, _]]`). If adopting this convention, pick one canonical meaning per symbol per codebase/module and do not reuse the same symbol for a structurally different relationship.
- **`@targetName` recommended for symbolic type definitions**, per the Scala 3 reference, to keep bytecode/stack-trace names readable and support Java interop where relevant.

## Suggested house convention (draft)

- `-->` — a plain effectful arrow, `type -->[A, B] = Kleisli[F, A, B]` (or `F[A] => F[B]`/`A => F[B]` depending on which Kleisli-shaped abstraction is in play) — reuses `scala-purrism`'s own recognized spelling.
- `~~>` (or cats' own `~>` where `FunctionK`/natural transformations are literally what's meant) — a transformation between two first-order type constructors, `type ~~>[F[_], G[_]] = FunctionK[F, G]`.
- `==>` — reserve for a distinct third relationship only if actually needed (e.g., a total/pure mapping vs. `-->`'s effectful one) — don't introduce it just to have three symbols.
- Only introduce a symbolic alias where the corresponding named type is used often enough, in signature position, that the infix spelling measurably improves signature readability (e.g., `def validate: User --> ValidatedUser` in a pipeline-heavy module) — not as a blanket replacement for every two-parameter type in the codebase.
