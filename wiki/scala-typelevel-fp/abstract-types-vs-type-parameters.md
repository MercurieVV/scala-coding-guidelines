# Abstracting Over Abstract Types: Type Members vs. Type Parameters

> Sources: Martin Odersky & Matthias Zenger, OOPSLA'05, 2005-10; Bill Venners (Artima), 2009-10-07; Typelevel Blog, 2015-07-13; Roman Janusz (ghik, "opinionated-scala"), Unknown; Matthew Orford, 2024-04-08; MercurieVV/scala-purrism docs, Unknown
> Raw: [Scalable Component Abstractions (OOPSLA'05)](../../raw/scala-typelevel-fp/odersky-zenger-scalable-component-abstractions-oopsla2005.md); [Abstract Type Members versus Generic Type Parameters](../../raw/scala-typelevel-fp/2009-10-07-venners-abstract-type-members-vs-generics.md); [Type members are almost type parameters](../../raw/scala-typelevel-fp/2015-07-13-typelevel-type-members-almost-type-parameters.md); [opinionated-scala: Generics and type members](../../raw/scala-typelevel-fp/ghik-opinionated-scala-generics-and-type-members.md); [DI and the Cake Pattern](../../raw/scala-typelevel-fp/2024-04-08-orford-di-and-the-cake-pattern.md); [scala-purrism: Engineering Rules](../../raw/scala-typelevel-fp/scala-purrism-engineering-rules.md); [scala-purrism: README](../../raw/scala-typelevel-fp/scala-purrism-readme.md)
> Updated: 2026-09-13

## Overview

Scala has two orthogonal ways to abstract over "some type I don't know yet": a **generic type parameter** (`trait Repo[A]`) and an **abstract type member** (`trait Repo { type A }`). They can express the same designs — Odersky and Zenger's OOPSLA'05 paper even gives a mechanical encoding of generics as abstract type members — but they diverge on inference, composability, binary/source compatibility, Java interop, and how many places in a codebase have to change when the abstracted type multiplies. The choice is a modularization decision, not just a syntax preference: type members are what make Scala's "cake pattern" DI style possible at all, and are the mechanism behind *family polymorphism* (a type that varies per-instance and is overridden covariantly in subclasses). A `scala-purrism` scalafix family (`PreferTypeParameters` and friends) mechanizes a related but distinct axis — widening a *concrete* type to a *type parameter* — not the type-member-vs-parameter choice itself.

## The core distinction

```scala
// Type parameter version
trait Collection[T] { def get: T }

// Type member version
trait Collection { type T; def get: T }
```

Both can be made concrete in a subtype (`extends Collection[String]` vs. `extends Collection { type T = String }`). Odersky frames the duality as deliberate orthogonality: Scala gives every kind of member (value, method/function, type) both a parameterized and an abstract-member form, whereas Java mixes them arbitrarily (abstract methods but no abstract fields; parameterized values but, pre-generics, no parameterized types). See [Scala 3 Type-Level Features](scala3-type-level-features.md) for opaque types and other Scala 3 abstract-type mechanics that build on this.

## When type parameters win

- **Constructor parameters.** A type member cannot appear as a constructor parameter's type in the way a type parameter can (`class Box[T](val value: T)` has no clean type-member equivalent).
- **Java interop.** Type members are Scala-only; Java sees generics, not type members. Anything meant to be called from Java needs plain generics.
- **Free renaming.** A type parameter's name is not part of the public API and can be renamed without breaking callers. A type member's name *is* part of the API — renaming `type Elem` to something else silently leaves the old member abstract in any subclass that wrote `type Elem = Int` (that declaration becomes an unrelated type alias, not an override), a real backward-compatibility trap.
- **Multiple independent instantiations at the call site.** If a caller needs to combine several different concretizations of the same abstraction in one place (e.g. `List[Int]` and `List[String]` side by side), a type parameter is the natural fit; a type member ties the type to one enclosing instance via a path-dependent type, which resists that kind of ad hoc combination.
- **Existentials you want to be explicit about.** The Typelevel rule of thumb: "a type parameter is usually more convenient and harder to screw up, but if you intend to use it existentially in most cases, changing it to a member is probably better." With a type parameter, writing an existential (`PList[_]`) is something the caller has to *ask for*; with a type member, forgetting to refine it (`{ type T = Int }`) leaves you with the existential *silently* — you don't get an error, you get an accidentally underspecified type.

## When abstract type members win

- **Avoiding parameter/bound explosion.** Odersky's argument (attributed to a 1998 ECOOP paper with Kim Bruce and Phil Wadler): "as you increase the number of things you don't know, the typical program will grow quadratically" when threading multiple type parameters (and their bounds) through a hierarchy, whereas abstract members don't produce this blow-up. Concretely: a component needing 3 abstracted types as parameters (`FixtureSuite3[A, B, C]`) forces every mixin and every subclass to keep repeating all three; as abstract members (`FixtureSuite3` with `type A`/`type B`/`type C`), the mixins that concretize them don't need repeating at every use site.
- **Mixing in a concretizing trait without repetition.** This is the concrete case above, from Bill Venners' design of ScalaTest's `FixtureSuite`: with a type parameter, `class MySuite extends FixtureSuite[StringBuilder] with StringBuilderFixture` still has to spell out `StringBuilder` even though `StringBuilderFixture` already fixes it via a self-type; with a type member, `class MySuite extends FixtureSuite with StringBuilderFixture` needs no repetition, because the mixed-in trait's `type F = StringBuilder` already does the work.
- **Referring to the abstract type from outside without re-parameterizing every call site.** With `MyStack[E]` and a wildcard `MyStack[_]`, the compiler forgets the element type is the same one going in and out (`stack.pop()` followed by `stack.push(elem)` fails to typecheck against a wildcard-typed `stack`). With a type member, `stack.Elem` is a path-dependent type the compiler *does* track, so the same code compiles without generifying the surrounding method. The "fix" with generics is to make every intermediate function generic too — which is exactly the boilerplate-multiplication type members are for.
- **Backward-compatible growth.** Adding a new abstract type member to an existing trait doesn't force existing implementers to change (until they need it); adding a new type parameter to an existing trait/class is a breaking signature change for every caller.
- **Encapsulation / hiding an implementation-fixed type.** Odersky and Zenger: type members let a component "hide the exact definition of a type member from client code, to obtain a kind of encapsulation known from SML-style module systems" — useful for an opaque per-instance `Session`/`Handle` that a caller should treat abstractly.
- **Concretizing via an inner class/trait.** A type member can be "implemented" by nesting a class/trait inside the concretizing subtype (`class Elem` inside a `trait CustomStack extends MyStack`); a type parameter has no equivalent.
- **Family polymorphism.** Overriding a type member covariantly in a subclass — so each subclass's family of related types moves together — is a pattern with no clean type-parameter equivalent; Odersky and Zenger cite this alongside encapsulation as the two situations where "one needs to refer to the abstract type from client code."

## Converting between the two

You can go from parameter-style to member-style representation with a type alias or a subtrait:

```scala
type MyStackAux[E] = MyStack { type Elem = E }     // alias: works for every subtype of MyStack

trait MyGenericStack[E] extends MyStack {          // subtrait: only inside this hierarchy
  type Elem = E
}
```

Going the *other* direction (encoding an existing abstract-type-member design as generics) is harder, per Odersky and Zenger: it "requires at least a global rewriting of the program" and, in a system with bounded polymorphism, "might entail a quadratic expansion of type bounds." Their type-theoretic gloss: plain generics (without F-bounds) live in System F<:, while abstract types need systems based on dependent types, which are strictly more expressive — Scala's path-dependent types can encode F<: but not vice versa. Practically this means: default to type parameters when you're unsure, because migrating a type-parameter design to type members later is comparatively local, while migrating type members to type parameters later is not.

## The "my-type" (self-type-returning) problem: a concrete member-vs-parameter case study

A recurring instance of this choice is a method that must return "the same type as whichever subclass calls it" (`withName` returning `Person`, not `HasName`). Three solutions, in increasing generality:

1. **`this.type`** — only correct when returning literally the same instance (fluent setters); can't express "a copy of the same type."
2. **F-bounded polymorphism** (type-parameter style) — `trait HasName[Self <: HasName[Self]] { def withName(...): Self }`. Requires every user of `HasName` as a standalone type to write `HasName[_]`, and the bound alone doesn't stop a mistaken `class Item extends HasName[Person]`; enforcing "`this` really is a `Self`" needs an additional self-type annotation (`trait SelfTyped[Self <: SelfTyped[Self]] { this: Self => ... }`).
3. **Type-member style** — `trait HasName { type Self >: this.type <: HasName; def withName(...): Self }`, concretized as `type Self = Person`. The `>: this.type` lower bound plays the role the self-type annotation played in option 2, without forcing every reference to `HasName` to become `HasName[_]`.

## Relation to modularization: the cake pattern and hand-wired DI

Abstract type members (plus self-types and mixin composition) are the mechanism behind Scala's **cake pattern** for dependency injection — Odersky and Zenger's original three "component abstractions" (abstract type members, explicit self-types, symmetric mixin composition) are precisely the cake pattern's ingredients. A component declares its dependency as a self-type (`trait UserServiceComponent { self: UserRepositoryComponent => ... }`) and its own service as a member; wiring happens by mixing traits together (`object UserModule extends UserRepositoryComponent with UserServiceComponent`), with no explicit constructor plumbing.

The tradeoff Matthew Orford documents from practice: the cake pattern gives "explicit dependencies, compile-time wiring, modular design," but "traits start depending on other traits, self-types accumulate and the wiring graph becomes a second program next to the program you meant to write" — new engineers end up learning the dependency-wiring system before the business logic. His recommendation: default to plain **constructor injection** (which already gives compile-time safety — the cake pattern's actual differentiator is *enforcing a component structure*, not safety per se); reach for the cake pattern only for framework code, library extension points, or a codebase where a component hierarchy is already the organizing principle; and prefer a DI library (MacWire, Airframe) over hand-rolled cake once manual wiring gets tedious. This lines up with [Tagless-Final Architecture & Hand-Wired DI](tagless-final-and-di-architecture.md)'s hand-wired-`Resource` default for this wiki's own house style — constructor/`Resource`-based DI, not cake, is the default; abstract type members earn their place for the specific encapsulation/family-polymorphism needs above, not as a general module-wiring tool.

## The scalafix angle: `scala-purrism`'s type-parameter-widening rules

`scala-purrism`'s `PreferTypeParameters` umbrella rule (and its three components — `PreferPolymorphicTypeclasses`, `PreferPolymorphicCollections`, `PreferPolymorphicCollectionOps`) mechanizes a *different* axis from the one in this article: it widens a signature that names a **concrete** type or type constructor (`List`, `IO`, `Eval`) into a **type parameter** (`F[_]`, `S[_]: Traverse`), in service of parametricity/least-power (see [Provider/Consumer Design: Least Power, Algebras, and Modular Composition](provider-consumer-least-power.md)). It does not choose between type-parameter and type-member *encodings* of an already-abstract type, and there is no `scala-purrism` rule (as of this writing) that automates the type-member-vs-type-parameter decision itself — that choice, per the sources above, remains a design judgment about encapsulation, mixin ergonomics, and API-compatibility needs, not something a linter can settle mechanically.

## See Also

- [Scala 3 Type-Level Features](scala3-type-level-features.md)
- [Tagless-Final Architecture & Hand-Wired DI](tagless-final-and-di-architecture.md)
- [Provider/Consumer Design: Least Power, Algebras, and Modular Composition](provider-consumer-least-power.md)
- [Refactoring Kleisli to Arrow: When and How](kleisli-to-arrow-refactoring.md)
