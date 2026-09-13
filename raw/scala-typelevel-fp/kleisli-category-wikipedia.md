# Kleisli Category (Wikipedia)

> Source: https://en.wikipedia.org/wiki/Kleisli_category
> Collected: 2026-09-13
> Published: Unknown

A **Kleisli category** is a category naturally associated to any monad _T_. It is equivalent to the category of free _T_-algebras.

**Objects:** Obj(_C__T_) = Obj(_C_) — the objects remain unchanged from the base category.

**Morphisms:** Hom_C_T(_X_, _Y_) = Hom_C(_X_, _TY_) — morphisms in the Kleisli category correspond to morphisms with codomain wrapped in the monad _T_.

**Composition rule:** g ∘_T_ f = μ_Z_ ∘ Tg ∘ f: X → TY → T²Z → TZ

Where _f: X → TY_ and _g: Y → TZ_, the composition uses the monad's multiplication operator μ to flatten nested applications of _T_.

**Identity morphisms:** The identity morphism is given by the monad unit η: id_X_ = η_X_

**Relationship to monads:** the Kleisli category construction addresses the question of whether every monad arises from an adjunction, demonstrating that monads can be recovered through adjoint functor pairs constructed via this categorical framework.
