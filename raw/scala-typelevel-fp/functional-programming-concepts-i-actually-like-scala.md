# Functional Programming Concepts I Actually Like: A Bit of Praise for Scala (For Once)

> Source: https://chollinger.com/blog/2022/06/functional-programming-concepts-i-actually-like-a-bit-of-praise-for-scala-for-once/
> Collected: 2026-09-07
> Published: Unknown

By Christian Hollinger. ~5,799 words, ~23 minutes reading time.

## Concepts the author appreciates

1. **Type-Driven Programming** — thinking about types first, implementation second; forces deeper consideration of code paths and structure; enables composition through type matching.
2. **Domain Models as Types** — creating custom types instead of relying on generic `String`; using Algebraic Data Types (ADTs) and sum types. Example: modeling `RomanNumeral` as a distinct type rather than `String`.
3. **Type Signatures for Code Understanding** — reading library code becomes possible through clear type signatures alone. Example: understanding `fs2-kafka` streaming without documentation; the compiler enforces correctness through type composition.
4. **Implicits and Type Classes** — implicit parameters reduce boilerplate while maintaining clarity; type classes enable behavior specification without mutation. Example: `circe` library's JSON encoding without custom serialization code.
5. **The IO Monad and cats-effect** — explicitly declaring side effects in types; distinguishing between pure and impure computations; lazy evaluation prevents unintended side effects; similarity to JavaScript `Promise` pattern; resource management through types.

## Author's criticisms

1. **Unnecessary complexity in OOP approaches** — traditional Java patterns like stateful objects returning `void` obscure intent. Example: `RomanNumeral.toInteger()` with side effects vs. a pure function signature.
2. **Standard Kafka Java library** — relies heavily on `Map<String, String>` without type safety; unclear effect model compared to typed alternatives; comments note ambiguity around failure conditions and blocking behavior.
3. **Jargon and academic feel** — acknowledges FP can feel "bleak and overly academic"; category theory references exist but remain accessible through practical application.

## Key code examples

Type-first design:
```scala
def romanNumeralToInteger(roman: RomanNumeral): Int = ???
```

Domain model with ADT:
```scala
trait RomanCharacter
case object I extends RomanCharacter
case object V extends RomanCharacter
// ... etc
```

IO monad usage:
```scala
def getBeer(): IO[Beer] = // ...
getBeer().flatMap(b => Logger[IO].info(b) >> IO.unit)
```

Type class with implicits:
```scala
implicit class MapOps[K, V](private val value: Map[K, V]) {
  def filterValues(f: V => Boolean): Map[K, V] = 
    value.filter(kv => f(kv._2))
}
```

## Conclusion

The author argues that functional programming concepts — particularly type-driven design, explicit effect modeling, and type classes — improve code clarity across paradigms. While acknowledging remaining criticisms, they contend these patterns transfer value to object-oriented and procedural development. The piece emphasizes pragmatic application over academic purity: "Code the happy path and watch the frameworks do the right thing."
