# F-algebra

> Source: https://en.wikipedia.org/wiki/F-algebra
> Collected: 2026-09-07
> Published: Unknown

This is the full original Wikipedia article text, reproduced verbatim (converted from wikitext to markdown; image/diagram references replaced with a placeholder note since the images themselves can't be reproduced here — see the original page).

---

In mathematics, specifically in category theory, *F*-**algebras** generalize the notion of algebraic structure. Rewriting the algebraic laws in terms of morphisms eliminates all references to quantified elements from the axioms, and these algebraic laws may then be glued together in terms of a single functor *F*, the *signature*.

*F*-algebras can also be used to represent data structures used in programming, such as lists and trees.

The main related concepts are initial *F*-algebras which may serve to encapsulate the induction principle, and the dual construction *F*-coalgebras.

## Definition
If <math>C</math> is a category, and <math>F : C \rightarrow C</math> is an endofunctor of <math>C</math>, then an <math>F</math>-**algebra** is a tuple <math>(A, \alpha)</math>, where <math>A</math> is an object of <math>C</math> and <math>\alpha</math> is a <math>C</math>-morphism <math>F(A) \rightarrow A</math>.  The object <math>A</math> is called the *carrier* of the algebra.  When it is permissible from context, algebras are often referred to by their carrier only instead of the tuple.

A homomorphism from an <math>F</math>-algebra <math>(A, \alpha)</math> to an <math>F</math>-algebra <math>(B, \beta)</math> is a <math>C</math>-morphism <math>f : A \rightarrow B</math> such that <math>f \circ \alpha = \beta \circ F(f)</math>, according to the following commutative diagram:

(commutative diagram, see original page for image)

Equipped with these morphisms, <math>F</math>-algebras constitute a category.

The dual construction are <math>F</math>-coalgebras, which are objects <math>A^*</math> together with a morphism <math>\alpha^* : A^* \rightarrow F(A^*)</math>.

## Examples
### Groups
Classically, a group is a set <math>G</math> with a *group law* <math>m : G \times G \rightarrow G</math>, with <math>m(x,y)=x\cdot y</math>, satisfying three axioms: the existence of an identity element, the existence of an inverse for each element of the group, and associativity.

To put this in a categorical framework, first define the identity and inverse as functions (morphisms of the set <math>G</math>) by <math>e :1 \rightarrow G</math> with <math>e(*)=1</math>, and <math>i  : G \rightarrow G</math> with <math>i(x)=x^{-1}</math>. Here <math>1</math> denotes the set with one element <math>1=\left\{*\right\}</math>, which allows one to identify elements <math>x\in G</math> with morphisms <math>1 \rightarrow G</math>.

It is then possible to write the axioms of a group in terms of functions (note how the existential quantifier is absent):

:* <math>\forall x\in G, \forall y\in G, \forall z\in G, m(m(x, y), z) = m(x, m(y, z))</math>,
:* <math>\forall x\in G, m(e(*), x) = m(x, e(*)) = x</math>,
:* <math>\forall x\in G, m(i(x), x) = m(x, i(x)) = e(*)</math>.

Then this can be expressed with commutative diagrams:

(commutative diagram, see original page for image)

(commutative diagram, see original page for image)

(commutative diagram, see original page for image)

Now use the coproduct (the disjoint union of sets) to glue the three morphisms in one: <math>\alpha = e + i + m</math> according to

::<math>\begin{matrix}
\alpha: {1}+G+G \times G & \to & G,\\
              * & \mapsto & 1,\\
              x & \mapsto & x^{-1},\\
              (x,y) & \mapsto & x \cdot y.
\end{matrix}</math>

Thus a group is an <math>F</math>-algebra where <math>F</math> is the functor <math>F(G) = 1 + G + G \times G</math>.  However the reverse is not necessarily true. Some <math>F</math>-algebra where <math>F</math> is the functor <math>F(G) = 1 + G + G \times G</math> are not groups.

The above construction is used to define group objects over an arbitrary category with finite products and a terminal object <math>1</math>. When the category admits finite coproducts, the group objects are <math>F</math>-algebras. For example, 
finite groups are <math>F</math>-algebras in the category of finite sets and Lie groups are <math>F</math>-algebras in the category of smooth manifolds with smooth maps.

### Algebraic structures
Going one step ahead of universal algebra, most algebraic structures are *F*-algebras. For example, abelian groups are *F*-algebras for the same functor *F*(*G*) = 1 + *G* + *G*×*G* as for groups, with an additional axiom for commutativity: *m*∘*t* = *m*, where *t*(*x*,*y*) = (*y*,*x*) is the transpose on *G*x*G*.

Monoids are *F*-algebras of signature *F*(*M*) = 1 + *M*×*M*. In the same vein, semigroups are *F*-algebras of signature *F*(*S*) = *S*×*S*

Rings, domains and fields are also *F*-algebras with a signature involving two laws +,•: *R*×*R* &rarr; R, an additive identity 0: 1 &rarr; *R*, a multiplicative identity 1: 1 &rarr; *R*, and an additive inverse for each element -: *R* &rarr; *R*. As all these functions share the same codomain *R* they can be glued into a single signature function 1 + 1 + *R* + *R*×*R* + *R*×*R*  &rarr; *R*, with axioms to express associativity, distributivity, and so on. This makes rings *F*-algebras on the category of sets with signature 1 + 1 + *R* + *R*×*R* + *R*×*R*.

Alternatively, we can look at the functor *F*(*R*) = 1 + *R*×*R* in the category of abelian groups. In that context, the multiplication is a homomorphism, meaning *m*(*x*&thinsp;+&thinsp;*y*, *z*) = *m*(*x*,*z*)&thinsp;+&thinsp;*m*(*y*,*z*) and *m*(*x*,*y*&thinsp;+&thinsp;*z*) = *m*(*x*,*y*)&thinsp;+&thinsp;*m*(*x*,*z*), which are precisely the distributivity conditions. Therefore, a ring is an *F*-algebra of signature 1 + *R*×*R* over the category of abelian groups which satisfies two axioms (associativity and identity for the multiplication).

When we come to vector spaces and modules, the signature functor includes a scalar multiplication *k*×*E* &rarr; *E*, and the signature *F*(*E*) = 1 + *E* + *k*×*E* is parametrized by *k* over the category of fields, or rings.

Algebras over a field can be viewed as *F*-algebras of signature 1 + 1 + *A* + *A*×*A* + *A*×*A* + *k*×*A* over the category of sets, of signature 1 + *A*×*A* over the category of modules (a module with an internal multiplication), and of signature *k*×*A* over the category of rings (a ring with a scalar multiplication), when they are associative and unitary.

### Lattice
Not all mathematical structures are *F*-algebras. For example, a poset *P* may be defined in categorical terms with a morphism *s*:*P* × *P* &rarr; Ω, on a subobject classifier (Ω = {0,1} in the category of sets and *s*(*x*,*y*)=1 precisely when *x*≤*y*). The axioms restricting the morphism *s* to define a poset can be rewritten in terms of morphisms. However, as the codomain of *s* is Ω and not *P*, it is not an *F*-algebra.

However, lattices, which are partial orders in which every two elements have a supremum and an infimum, and in particular total orders, are *F*-algebras. This is because they can equivalently be defined in terms of the algebraic operations: *x*∨*y* = inf(*x*,*y*) and *x*∧*y* = sup(*x*,*y*), subject to certain axioms (commutativity, associativity, absorption and idempotency). Thus they are *F*-algebras of signature *P* x *P* + *P* x *P*.  It is often said that lattice theory draws on both order theory and universal algebra.

### Recurrence
Consider the functor <math>F: \mathrm{\bf{Set}} \to \mathrm{\bf{Set}}</math> that sends a set <math>X</math> to <math>1+X</math>. Here, <math>\mathrm{\bf{Set}}</math> denotes the category of sets, <math>+</math> denotes the usual coproduct given by the disjoint union, and <math>1</math> is a terminal object (i.e. any singleton set). Then, the set  <math>\mathbb{N}</math> of natural numbers together with the function <math>[\mathrm{zero},\mathrm{succ}] : 1+\mathbb{N} \to \mathbb{N}</math>—which is the coproduct of the functions <math>\mathrm{zero} : 1 \mapsto 0</math> and <math> \mathrm{succ} : n \mapsto n+1</math>—is an *F*-algebra.

## Initial F-algebra

If the category of *F*-algebras for a given endofunctor *F* has an initial object, it is called an **initial algebra**. The algebra <math>(\mathbb{N}, [\mathrm{zero},\mathrm{succ}])</math> in the above example is an initial algebra. Various finite data structures used in programming, such as lists and trees, can be obtained as initial algebras of specific endofunctors.

Types defined by using least fixed point construct with functor *F* can be regarded as an initial *F*-algebra, provided that parametricity holds for the type.

See also Universal algebra.

## Terminal F-coalgebra
In a dual way, a similar relationship exists between notions of greatest fixed point and terminal *F*-coalgebra. These can be used for allowing potentially infinite objects while maintaining strong normalization property.

## See also
* Algebras for a monad
* Algebraic data type
* Catamorphism
* Dialgebra

## References
* Pierce, Benjamin C.. "*F*-Algebras." In Basic Category Theory for Computer Scientists, 1991, MIT Press. ISBN 0-262-66071-7.
* Barr, Michael, and Wells, Charles. Category theory for computing science. Prentice Hall, New York, 1990. p. 355.

## External links
* [Categorical programming with inductive and coinductive types](https://kodu.ut.ee/~varmo/papers/thesis.pdf) by Varmo Vene
* Philip Wadler: [Recursive types for free!](http://homepages.inf.ed.ac.uk/wadler/papers/free-rectypes/free-rectypes.txt) University of Glasgow, June 1990. Draft.
* [Algebra and coalgebra](http://tunes.org/wiki/algebra_20and_20coalgebra.html) from CLiki
* B. Jacobs, J. Rutten: [A Tutorial on (Co) Algebras and (Co) Induction. Bulletin of the European Association for Theoretical Computer Science](https://www.cs.ru.nl/~bart/PAPERS/JR.pdf), vol. 62, 1997
* [Understanding F-Algebras](https://www.schoolofhaskell.com/user/bartosz/understanding-algebras) by Bartosz Milewski
