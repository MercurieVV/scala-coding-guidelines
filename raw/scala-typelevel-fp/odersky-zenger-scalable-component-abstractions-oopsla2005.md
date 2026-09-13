# Scalable Component Abstractions

> Source: https://lampwww.epfl.ch/~odersky/papers/ScalableComponent.pdf (redirects to https://chara.epfl.ch/~odersky/papers/ScalableComponent.pdf)
> Collected: 2026-09-13
> Published: 2005-10 (OOPSLA'05, San Diego, California, USA)

By Martin Odersky (EPFL) and Matthias Zenger (Google Switzerland GmbH). OOPSLA'05. Copyright 2005 ACM 1-59593-031-0/05/0010.

## Abstract

We identify three programming language abstractions for the construction of reusable components: abstract type members, explicit selftypes, and modular mixin composition. Together, these abstractions enable us to transform an arbitrary assembly of static program parts with hard references between them into a system of reusable components. The transformation maintains the structure of the original system. We demonstrate this approach in two case studies, a subject/observer framework and a compiler front-end.

Categories and Subject Descriptors: D.3.3 [Programming Languages]: Language constructs and features -- Classes and objects; inheritance; modules; packages; polymorphism; recursion.

Keywords: Components, classes, abstract types, mixins, Scala.

## Introduction (key passage on the three abstractions)

"We have identified three programming language abstractions that enable such liftings [of a system of components with static data and hard references into first-class components]:

_Abstract type members_ provide a flexible way to abstract over concrete types of components. Abstract types can hide information about internals of a component, similar to their use in ML signatures. In an object-oriented framework where classes can be extended by inheritance, they may also be used as a flexible means of parameterization (often called _family polymorphism_ [11]).

_Selftype annotations_ allow one to attach a programmer-defined type to `this`. This turns out to be a convenient way to express required services of a component at the level where it connects with other components.

_Modular mixin composition_ provides a flexible way to compose components and component types. Unlike functor applications, mixin compositions can establish recursive references between cooperating components. No explicit wiring between provided and required services is needed. Services are modeled as component members. Provided and required services are matched by name and therefore do not have to be associated explicitly by hand."

All three abstractions have their theoretical foundation in the νObj calculus [35]. They have been defined and implemented in the programming language Scala.

## Section 2.1 — Abstract Type Members (framing)

"An important issue in component systems is how to abstract from required services. There are two principal forms of abstraction in programming languages: parameterization and abstract members. The first form is typical for functional languages, whereas the second form is typically used in object-oriented languages. Traditionally, Java supports parameterization for values, and member abstraction for operations. The more recent Java 5.0 with generics supports parameterization also for types.

Scala supports both styles of abstraction uniformly for types as well as values. Both types and values can be parameters, and both can be abstract members. ... We defer a discussion of functional type abstraction (aka generics) to the appendix, because this aspect of the language is more conventional and not as fundamental for composition in the large."

## Related work (abstract types vs. virtual classes/family polymorphism)

"Abstract types in Scala have close resemblances to abstract types of signatures in the module systems of ML and OCaml, generalizing them to a context of first-class components. Abstract types are also very similar to the virtual types [29] of the Beta and gbeta languages. In fact, virtual types in Beta can be modelled precisely in Scala by a combination of abstract types and selftype annotations. Virtual types as found in gbeta are more powerful than either Scala's or Beta's constructions, since they can be inherited as superclasses. This opens up possibilities for advanced forms of class hierarchy reuse [12], but it makes it very hard to check for accidental and incompatible overrides. Closely related are also the delegation layers of Caesar [38, 31] and FamilyJ's [48] work on nested inheritance for Java [32]."

## Appendix A — Generics in Scala: modeling generics with abstract types

"The presence of two type abstraction facilities in one language raises the question of language complexity -- could we have done with just one formalism? In this section we show that functional type abstraction can indeed be modeled by object-oriented type abstraction."

The encoding (parameterized class `C` with type parameter `t`, generalizes to multiple type parameters), four parts:

1. Class definition of `C` is re-written: parameters of the original class are modeled as abstract members in the encoded class (`class C { type t; /* rest of class */ }`). Lower/upper bounds on `t` carry over to the abstract type definition. Variance of the type parameter does not carry over; variance instead influences the formation of types.
2. Every instance creation `new C[T]` with type argument `T` is rewritten to `new C { type t = T }`.
3. If `C[T]` appears as a superclass constructor, the inheriting class is augmented with the definition `type t = T`.
4. Every type `C[T]` is rewritten to one of the following types which each augment class `C` with a refinement: `C { type t = T }` if `t` is declared non-variant, `C { type t <: T }` if `t` is declared co-variant, `C { type t >: T }` if `t` is declared contra-variant.

"This encoding works except for possible name-conflicts. Since the parameter name becomes a class member in the encoding, it might clash with other members, including inherited members generated from parameter names in base classes. These name conflicts can be avoided by renaming, for instance by tagging every name with a unique number.

The presence of an encoding from one style of abstraction to another is nice, since it reduces the conceptual complexity of a language. In the case of Scala, generics become simply syntactic sugar which can be eliminated by an encoding into abstract types. However, one could ask whether the syntactic sugar is warranted, or whether one could have done with just abstract types, arriving at a syntactically smaller language.

The arguments for including generics in Scala are two-fold. First, the encoding into abstract types is not that straightforward to do by hand. Besides the loss in conciseness, there is also the problem of accidental name conflicts between abstract type names that emulate type parameters. Second, generics and abstract types usually serve distinct roles in Scala programs. Generics are typically used when one needs just type instantiation, whereas abstract types are typically used when one needs to refer to the abstract type from client code. The latter arises in particular in two situations: One might want to hide the exact definition of a type member from client code, to obtain a kind of encapsulation known from SML-style module systems. Or one might want to override the type covariantly in subclasses to obtain family polymorphism.

Could one also go the other way, encoding abstract types with generics? It turns out that this is much harder, and that it requires at least a global rewriting of the program. This was shown by studies in the domain of module systems where both kinds of abstraction are also available [20]. Furthermore in a system with bounded polymorphism, this rewriting might entail a quadratic expansion of type bounds [4]. In fact, these difficulties are not surprising if one considers the type-theoretic foundations of both systems. Generics (without F-bounds) are expressible in System F<: [7] whereas abstract types require systems based on dependent types. The latter are generally more expressive than the former; for instance νObj with its path-dependent types can encode F<:."
