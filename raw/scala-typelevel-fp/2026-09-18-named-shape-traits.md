# Named shape traits: one trait per shape, named at the use site (skreeep2, 2026-09-18)

> Source: skreeep2 engineering session, 2026-09-18 — the injected-type-system refactor. Committed artifacts: `docs/superpowers/specs/2026-09-18-injected-typesystem-design.md`, `docs/superpowers/plans/2026-09-18-injected-typesystem.md`, `AGENTS.md` (the shape rule), `signals/src/com/github/mercurievv/skreeep2/signals/TypeSystem.scala`
> Collected: 2026-09-18
> Published: Unknown

## The rule, as the project owner stated it

> "if we pass ts: TypeSystemEventsValuesRuntime, do we need specify its types? Like
> `type -->[X, Y] = Kleisli[Stream[F, *], X, Y]`. I think if we need more concrete type assign, we can specify it in child
> trait of TypeSystemEventsValuesRuntime. I think it works - it should become generic rule for all project - do not specify
> types in place - create trait for it. Then you will hide details from here and also could reuse in other places"

## As written into the repo's agent instructions

> ### The shape rule (do not specify types in place)
>
> A component **names** a shape; it does not spell one. When a signature needs concrete type members
> (`-->`/`==>`/`~~>`/`Global`/`Value`/`Compiled`/`Uncompiled`), put them in a named child trait and have
> consumers name that trait — a component holding `[..., TS <: ThatTrait](val ts: TS)` sees the pinned
> aliases *through the bound*, so no inline refinement is needed at the use site.
>
> Where a shape's members come from a **value** rather than a type parameter (a `backend` service, a
> `wiring` value), a trait cannot name them (`RB#Uncompiled` is not a legal path; a class's
> type-parameter bound cannot see its value parameters) — pass them as **type arguments** to the trait
> instead (`BackendWorld[F, V, backend.Uncompiled, backend.Compiled]`). Where the named form genuinely
> reads worse than one clause at one site, keep the clause and say why.
>
> Precedent: `signals/TypeSystem.scala`'s carriers plus `StreamWorld`/`BackendWorld`/`WiringWorld`/
> `IdentityWorld`, and `handoff/openclHost`'s `BatchWorld` — the injected type-system refactor of
> 2026-09-18 (`docs/superpowers/specs/2026-09-18-injected-typesystem-design.md`).

## The shipped traits (signals/TypeSystem.scala, as of commit 76f0213)

```scala
trait StreamWorld[F[_], V] extends TypeSystemEventsRuntime:
  type -->[X, Y] = Kleisli[Stream[F, *], X, Y]
  type Global = GloabalSignals[V]

trait BackendWorld[F[_], V, Unc[_, _], Comp[_, _]] extends StreamWorld[F, V]:
  type ==>[X, Y] = Unc[X, Y]
  type ~~>[X, Y] = Comp[X, Y]

trait WiringWorld[F[_], V, Unc[_, _]] extends TypeSystemEventsRuntime: ...
trait IdentityWorld[F[_], V] extends StreamWorld[F, V]: ...   // ==>: Id Kleisli, ~~>: plain function
```

A host that names one, verbatim from the refactor:

```scala
final class HostDrivenHost[F[_], Value, RB <: RenderBackend, TS <: TypeSystemEventsRuntime](
  val backend: RB,
)(
  val ts: TS & BackendWorld[F, Value, backend.Uncompiled, backend.Compiled],
)(using Sync[F]) extends Host:
  def compile(f: Global ==> M): Global ~~> M = backend.compile(f)
```

## Probe evidence (throwaway Scala 3.8.4 snippets, no repo change)

1. A pinned alias **does** reduce through a type-parameter bound:

```scala
trait CompiledUncompiledTS extends TypeSystem:
  type Compiled = String
  type Uncompiled = Int

class Backend[TS <: CompiledUncompiledTS](ts: TS):
  def useCompiled(c: ts.Compiled): String = c     // compiles
```

2. A class's type-parameter bound **cannot** name a value parameter's paths:

```
final class ByValueParam[TS <: TSE { type ==>[X, Y] = backend.Uncompiled[X, Y] }](val backend: RB, val ts: TS)
  ✗ Not found: backend
```

3. A type parameter's members **cannot** be projected:

```
class H[RB0 <: RB, TS <: TSE { type ==>[X, Y] = RB0#Uncompiled[X, Y] }](val ts: TS)
  ✗ RB0 is not a legal path, since it is not a concrete type
```

4. A **later parameter list's type** can name an earlier parameter's paths, and the seam then typechecks cast-free:

```scala
final class HostDrivenHost[F[_], Value, RB <: RenderBackend](val backend: RB)(
  val ts: TS & BackendWorld[F, Value, backend.Uncompiled, backend.Compiled],
) extends Host:
  def compile(f: Global ==> M): Global ~~> M = backend.compile(f)   // compiles
```

5. A `def` is not a valid type prefix: `def ts: TS; type -->[A, B] = ts.-->[A, B]` → `✗ (ts : => TS) is not a valid type prefix, since it is not an immutable path`.

6. The singleton tie spelling `Host { val ts: ts.type }` is a cyclic reference — the refinement's own `val ts` shadows the parameter, in the same list or an earlier one: `✗ Cyclic reference involving val ts`. It was expressed instead through `TopologyWiring.HostOf[Ts, M0] = Host { val ts: Ts; type M = M0 }`.

## The related failure: a world-specific operation does not belong in a generic interface

The same refactor tried twice to put the compile seam (`compile`/`discharge`) on the type-system value:

```
compile[G, M](f: G ==> M): G ~~> M     → the OpenCL readback needs a concrete Global: no implementation without a cast
compile[M](f: Global ==> M): Global ~~> M  → Found: Tree[(String, Value)]  Required: M    (the chunk is a member, not a parameter)
```

The seam that shipped is the **host's own**, computed through the backend service its parameters name.

## What the rule retired in that refactor

`TypeSystemEventsOf` / `TypeSystemEventsValuesOf` (shape aliases), `eventsOf`, `buildEffect`, and every inline
`{ type -->[X, Y] = … }` clause on a carrier: net 162 insertions against 347 deletions across the 16-file change
(`git show --numstat 4a381c8`).
