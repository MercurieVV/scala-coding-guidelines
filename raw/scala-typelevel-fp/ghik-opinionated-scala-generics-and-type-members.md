# Generics and type members (opinionated-scala)

> Source: https://github.com/ghik/opinionated-scala/blob/master/chapters/Generics-and-type-members.md
> Collected: 2026-09-13
> Published: Unknown

From Roman Janusz's (ghik) "opinionated-scala" guide.

## Type parametrization basics

Java uses angle brackets (`<>`) for generics; Scala uses square brackets (`[]`).

```scala
def double[T](value: T): (T,T) = (value, value)
```

```scala
def listOfTwo[T](first: T, second: T): List[T] = List(first, second)
```

```scala
class Box[T](var boxedValue: T)
trait Callable[T] {
  def call(): T
}
```

## Bounds

`<:` upper bound, `>:` lower bound.

```scala
def closed[T <: Closeable](closeable: T): T = {
  closeable.close()
  closeable
}
```

### `<: AnyRef` vs `>: Null`

To express nullable types, use `>: Null` as a lower bound, NOT `<: AnyRef` — `Nothing` is within `<: AnyRef` bounds but cannot accept null values.

## Wildcards, existentials

Covariant generic lists example, and existential syntax:

```scala
type S = Set[A] forSome { type A }
```

`Map[_,_]` differs from `Map[T,T] forSome { type T }` because each wildcard represents a separate unknown type.

## Type members

### Type aliases

```scala
type IntPair = (Int, Int)
type Mapping[A] = Map[A,A]
type JList[A] = java.util.List[A]
```

May be local or members of a class/trait/object:

```scala
class Klass {
  type IntPair = (Int, Int)
}
```

### Abstract type members

```scala
trait MyStack {
  type Elem

  def push(elem: Elem): Unit
  def pop(): Elem
}
```

May have bounds:

```scala
trait NumberCollection {
  type Elem <: Number
}
```

Can be narrowed by a subtrait while staying abstract:

```scala
trait ObjectStack extends MyStack {
  type Elem <: AnyRef
}
```

Or made concrete (a type alias in that scope):

```scala
trait StringStack extends ObjectStack {
  type Elem = String
}
```

"Quite a confusing property of abstract type members is the fact that even though they're abstract, they don't necessarily need to be 'implemented'. They may remain abstract even though they are members of non-abstract classes." Example that compiles fine but produces an unusable member:

```scala
class Klass {
  type T
}
object Klass {
  new Klass
}
```

### Type members vs type parameters

Type members may be "implemented" with an inner class or trait:

```scala
trait CustomStack extends MyStack {
  class Elem // an inner class becomes a concretization of abstract type member
}
```

"You cannot do something like this with a type parameter."

Type members cannot be used in constructor parameters — `class Box[T](val value: T)` "cannot be (easily) expressed using a type member."

A type-member equivalent of `MyStack[String]` is the type refinement `MyStack { type Elem = String }`. You can translate a type member into a type parameter:

```scala
type MyStackAux[E] = MyStack { type Elem = E }
```

or via a specialized subtrait:

```scala
trait MyGenericStack[E] extends MyStack {
  type Elem = E
}
```

("in this case the 'translation' works only inside the hierarchy of that specialized trait, while type alias encompasses every possible subtype of `MyStack`.")

### Referring to type member from outside

With generics + wildcards there is no way to refer to the unknown element type; the following does NOT compile:

```scala
trait MyStack[E] {
  def push(e: E): Unit
  def pop(): E
}
object Test {
  def popAndPush(stack: MyStack[_]): Unit = {
    val elem = stack.pop()
    stack.push(elem) // error!
  }
}
```

"The compiler cannot follow the fact that the type of `elem` returned from `pop` is exactly the same type that `push` accepts. The type of `elem` is pretty much `Any`."

With type members this compiles:

```scala
trait MyStack {
  type Elem
  def push(e: Elem): Unit
  def pop(): Elem
}
object Test {
  def popAndPush(stack: MyStack): Unit = {
    val elem = stack.pop()
    stack.push(elem) // no problem!
  }
}
```

"This is because type members (even though they're abstract) can be referred to outside of its containing class/trait." `elem` has type `stack.Elem` (a path-dependent type). "if `stack1` and `stack2` are different stack instances, then `stack1.Elem` and `stack2.Elem` are types incompatible with each other (although they still have a common supertype, `Stack#Elem`)."

The generics fix requires making `popAndPush` itself generic:

```scala
trait MyStack[E] {
  def push(e: E): Unit
  def pop(): E
}
object Test {
  def popAndPush[E](stack: MyStack[E]): Unit = {
    val elem = stack.pop()
    stack.push(elem)
  }
}
```

"However, adding that generic is somewhat a boilerplate. It isn't very painful in our simple example, but it may become much more annoying when there is e.g. more than just one type parameter, some of the params have type bounds that need to be repeated and you have many places in your code that needs to be made generic just because of that. That's why type members are often much more flexible in such situations."

### Naming conventions

"You are probably used to giving one-letter names to type parameters in Java. This convention generally persists also for generics in Scala, but you should **NOT** carry it over to abstract type members."

Names of type members are part of the public API. Consequence — changing a type member's name is a breaking change:

```scala
trait Stack {
  type Elem
}
class IntStack extends Stack {
  type Elem = Int
}
```

"If we now change the `Elem` to something else, the implementor's code would be broken. The `type Elem = Int` will still compile but will just become a type alias unrelated to base trait and the type member from `Stack` will remain abstract."

By contrast, renaming a type parameter (`trait Stack[E]` → any other name) breaks nothing for callers.

### The my-type problem

How to declare a method returning "self" type:

```scala
trait HasName {
  def name: String
  def withName(newName: String): <???>
}
final class Person(val name: String, val surname: String) extends HasName {
  def withName(newName: String): Person = new Person(newName)
}
```

Goal: `def renameToJohn[T <: HasName](hasName: T): T = hasName.withName("John")` should compile.

**`this.type` solution** — works only when returning exactly the same instance (e.g. fluent setters):

```scala
trait MutableHasName {
  var name: String
  def fluentSetName(newName: String): this.type = {
    name = newName
    this
  }
}
```

**F-bounded polymorphism solution:**

```scala
trait HasName[Self <: HasName[Self]] {
  def name: String
  def withName(newName: String): Self
}
final class Person(val name: String, val surname: String) extends HasName[Person] {
  def withName(newName: String): Person = new Person(newName)
}
```

Problem: nothing stops `final class Item(val name: String) extends HasName[Person]` — the compiler can't ensure `Self` is always the implementing class, so `trait SelfTyped[Self <: SelfTyped[Self]] { def returnSelf: Self = this }` is rejected (ERROR: compiler can't be sure `this` is an instance of `Self`). Fix requires an additional self-type annotation:

```scala
trait SelfTyped[Self <: SelfTyped[Self]] { this: Self =>
  def returnSelf: Self = this
}
```

"In general, the solution with type parameter (generic) is nice, but introduces some syntactic crust. We can no longer simply use `HasName` as a standalone type, now we have to always write `HasName[_]` which may be annoying."

**Type member solution:**

```scala
trait HasName {
  type Self >: this.type <: HasName
  def name: String
  def withName(newName: String): Self
}
final class Person(val name: String, val surname: String) extends HasName {
  type Self = Person
  def withName(newName: String): Person = new Person(newName)
}
```

"The `>: this.type` lower bound serves the same purpose as `this: Self =>` self-type annotation from type parameter based solution... This solves the problem in pretty much the same way the type parameter did, but relieves us from polluting definition of `HasName` with unnecessary generic crust. We don't have to rewrite all usages of `HasName` to introduce a generic or wildcard."

### Java interoperability

"Type members are purely Scala feature - Java does not see them. If you want your types to be fully visible from Java code, you must use plain generics instead of type members."

### Type members — summary

Advantages of type members over generics:
- type members don't pollute type declarations of your trait, like generics do
- adding a type member to class or trait is by itself a fully backwards compatible change, while adding a type parameter will require you to rewrite a lot of code that uses your trait or class
- type member may be referred to outside of its declaring class or trait using path-dependent types
- you can concretize an abstract type member with inner class or trait

Advantages of generics over type members:
- generics may be referred to in constructor parameters (and self-type annotation)
- type parameters are recognized by Java
- names of type parameters may be much more freely changed than names of type members (without breaking backwards compatibility)

"In general, a type member seems to be a good replacement for type parameter in situations where the generic was only there to be made concrete by various subclasses."

"As we have already noted, type members and type parameters are fairly orthogonal concepts that often serve similar purposes. In the possible future version of Scala called dotty, type members and type parameters have been merged into a single being."
