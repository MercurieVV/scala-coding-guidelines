# Rules for Operators

> Source: https://docs.scala-lang.org/scala3/reference/changed-features/operators.html
> Collected: 2026-09-13
> Published: Unknown (Scala 3 Reference, version 3.9.0 at time of collection)

Scala 3 Reference — Other Changed Features — Rules for Operators.

## Summary of changes

"The rules for infix operators have changed in some parts:

First, an alphanumeric method can be used as an infix operator only if its definition carries an infix modifier.

Second, it is recommended (but not enforced) to augment definitions of symbolic operators with `@targetName` annotations.

Finally, a syntax change allows infix operators to be written on the left in a multi-line expression."

## The `infix` Modifier

"An infix modifier on a method definition allows using the method as an infix operation." Example:

```scala
import scala.annotation.targetName

trait MultiSet[T]:
  infix def union(other: MultiSet[T]): MultiSet[T]
  def difference(other: MultiSet[T]): MultiSet[T]

  @targetName("intersection")
  def *(other: MultiSet[T]): MultiSet[T]

end MultiSet

val s1, s2: MultiSet[Int]

s1 union s2       // OK
s1 `union` s2     // also OK but unusual
s1.union(s2)      // also OK

s1.difference(s2) // OK
s1 `difference` s2 // OK
s1 difference s2  // gives a deprecation warning

s1 * s2           // OK
s1 `*` s2         // also OK, but unusual
s1.*(s2)          // also OK, but unusual
```

"Infix operations involving alphanumeric operators are deprecated, unless one of the following conditions holds:
- the operator definition carries an infix modifier, or
- the operator was compiled with Scala 2, or
- the operator is followed by an opening brace.

An alphanumeric operator is an operator consisting entirely of letters, digits, the `$` and `_` characters, or any Unicode character `c` for which `java.lang.Character.isIdentifierPart(c)` returns true.

**Infix operations involving symbolic operators are always allowed, so `infix` is redundant for methods with symbolic names.**"

## Infix Types

"The `infix` modifier can also be given to a type:

```scala
infix type or[X, Y]
val x: String or Int = ...
```

### Motivation

The purpose of the `infix` modifier is to achieve consistency across a code base in how a method or type is applied. The idea is that the author of a method decides whether that method should be applied as an infix operator or in a regular application. Use sites then implement that decision consistently."

### Details

"`infix` is a soft modifier. It is treated as a normal identifier except when in modifier position.

If a method overrides another, their `infix` annotations must agree. Either both are annotated with `infix`, or none of them are.

`infix` modifiers can be given to method definitions. The first non-receiver parameter list of an infix method must define exactly one parameter." Examples:

```scala
infix def op1(x: S): R                    // ok
infix def op2[T](x: T)(y: S): R           // ok
infix def op3[T](x: T, y: S): R           // error: two parameters

extension (x: A)
  infix def op4(y: B): R                  // ok
  infix def op5(y1: B, y2: B): R          // error: two parameters
```

"`infix` modifiers can also be given to type, trait or class definitions that have exactly two type parameters. An infix type like

```scala
infix type op[X, Y]
```

can be applied using infix syntax, i.e. `A op B`.

To smooth migration to Scala 3.0, alphanumeric operators will only be deprecated from Scala 3.1 onwards, or if the `-source future` option is given in Dotty/Scala 3."

## The `@targetName` Annotation

"It is recommended that definitions of symbolic operators carry a `@targetName` annotation that provides an encoding of the operator with an alphanumeric name. This has several benefits" (improves compatibility, readability of stack traces/bytecode, serves as documentation).
