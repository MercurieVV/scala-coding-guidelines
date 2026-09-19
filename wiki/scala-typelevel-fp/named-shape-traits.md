# Named Shape Traits: Do Not Spell a Type Member's Shape at the Use Site

> Sources: MercurieVV (skreeep2 engineering session), 2026-09-18
> Raw: [Named shape traits: one trait per shape, named at the use site](../../raw/scala-typelevel-fp/2026-09-18-named-shape-traits.md)
> Updated: 2026-09-18

## Overview

When a signature needs concrete values for a bundle of abstract type members (`-->`/`==>`/`~~>`/`Global`/`Value`, or a
`Compiled`/`Uncompiled` pair), Scala offers two spellings: write the refinement *in place*, at that parameter, or put the
refinement in a **named child trait** and have the component name the trait. The house rule for this project family is the
second: **a component names a shape; it does not spell one.** It is cheaper to read, it makes the shape reusable, and — the
non-obvious part — a *pinned* alias in a trait stays transparent through a type-parameter bound, so the consumer still sees
the concrete arrows without restating them. The rule has exactly one hard limit: it works for members determined by **type
parameters**, and never for members reached through a **value**. The bridge for the value case is a type-argument list, not a
refinement.

## The rule, in the project owner's words

A fragment of that review, the clause the rule is named for: "types in place - create trait for it. Then you will hide details from here and also could reuse in other places"

Stated in review of a refactor that had just spent three rounds moving a compile seam around; it became the repo's agent-facing
rule and produced the traits the same session shipped (`StreamWorld`, `BackendWorld`, `WiringWorld`, `IdentityWorld`, plus one
module-local `BatchWorld`).

## Why it works: a pinned alias is transparent through the bound

The mechanism that makes the named form sufficient — and the reason it is not merely cosmetic — is that a **concrete** type
alias declared in a trait is visible through a type parameter bounded by that trait:

```scala
trait CompiledUncompiledTS extends TypeSystem:
  type Compiled = String
  type Uncompiled = Int

class Backend[TS <: CompiledUncompiledTS](ts: TS):
  def useCompiled(c: ts.Compiled): String = c     // compiles: the alias reduces through the bound
```

Two consequences follow. First, the consumer needs no refinement at its own parameter — naming the trait in the bound is
enough. Second, the shape is written **once**, in the trait, so every consumer that names it is guaranteed to agree with every
other one.

## The limit: values, not types

The rule stops at members that come from a *value* — a service, a wiring bundle, a backend. A trait takes type parameters, not
value parameters, so it cannot name `backend.Uncompiled`, and neither of the tempting escapes works:

```scala
// a class's type-parameter bounds are elaborated before its value parameters exist
final class ByValueParam[TS <: TSE { type ==>[X, Y] = backend.Uncompiled[X, Y] }](val backend: RB, val ts: TS)
//   ✗ Not found: backend

// a type parameter's member cannot be projected
class H[RB0 <: RB, TS <: TSE { type ==>[X, Y] = RB0#Uncompiled[X, Y] }](val ts: TS)
//   ✗ RB0 is not a legal path, since it is not a concrete type
```

A `def` cannot serve as the third way out either: `def ts: TS; type -->[A, B] = ts.-->[A, B]` fails with
*"(ts : => TS) is not a valid type prefix, since it is not an immutable path"* — only a `val` is a stable path.

## The bridge for the value case: pass the members as type arguments

Where the shape's members live on a value, name the trait but hand it those members as **type arguments**. The value's own
paths are legal in a *later parameter list's type*, even though they are illegal in a bound:

```scala
final class HostDrivenHost[F[_], Value, RB <: RenderBackend](
  val backend: RB,
)(
  val ts: TS & BackendWorld[F, Value, backend.Uncompiled, backend.Compiled],
)(using Sync[F]) extends Host:
  def compile(f: Global ==> M): Global ~~> M = backend.compile(f)     // cast-free: the parameter's type says ==> is backend.Uncompiled
```

So the ordinary shape (`-->`, `Global`, `Value`) is named outright, and only the backend-determined pair travels as
arguments. The `TS & <world>` intersection is what keeps the caller's own type visible *and* the pinned arrows reachable: the
member's type is a subtype of the caller's `TS`, which is what lets a wiring parameter tie two components together through
`Host { val ts: Ts }` without a singleton.

## The adjacent trap: an operation's home is not decided by naming

The same session established a second, related rule: **a world-specific operation does not belong in a generic interface.** A
compile seam (`compile`/`discharge`) was twice placed on the shared type-system value, and twice the compiler refused an
implementation by any real world:

```
compile[G, M](f: G ==> M): G ~~> M            → the OpenCL readback needs a concrete Global: no implementation without a cast
compile[M](f: Global ==> M): Global ~~> M     → Found: Tree[(String, Value)]  Required: M
```

The second line is the informative one: the *output* is the chunk type, and the chunk is a **member** of the host, not a
method type parameter — so no quantification over it can admit the readback world's implementation. The seam that shipped is
the host's own, computed through the service its parameters name. The generalisation: name types in traits by all means, but
an operation whose implementation needs a specific world belongs to that world, not to a shared abstraction.

## When to keep an inline clause

The named form is the default, not an absolute. Keep a single clause at a single site when the shape is genuinely local: a
test fixture's world, a one-off tie against a value (`t: Topology { … w's paths … }`), or a place where the type-argument
list would read worse than the clause it replaces. The rule's purpose is hiding details and enabling reuse; when inlining
serves both better, inline and say why.

## See Also

- [Abstracting Over Abstract Types: Type Members vs. Type Parameters](abstract-types-vs-type-parameters.md)
- [Scala 3 Type-Level Features](scala3-type-level-features.md)
- [Refactoring Kleisli to Arrow: When and How](kleisli-to-arrow-refactoring.md)
