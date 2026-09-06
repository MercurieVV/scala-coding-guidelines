# How to Derive Type Class Instances With Shapeless 3

> Source: https://xebia.com/blog/how-to-derive-type-class-instances-with-shapeless-3/
> Collected: 2026-09-06
> Published: 2025-04-30 (updated)

By Javier Martínez. Shapeless 3 version referenced: 3.3.0.

## Key components explained

**K0.ProductInstances:** provides primitives like `project`, `construct`, and `foldLeft` for deriving type classes on product types (case classes, tuples). The `project` method selects a field by index and transforms it using a polymorphic function.

**K0.CoproductInstances:** the counterpart for sum types (sealed traits, enums). Its `fold` method applies the corresponding type class instance to each member of the coproduct.

**K0.Generic:** a high-level abstraction that determines whether a type is a product or sum, routing to the appropriate derivation logic automatically.

## Code example: deriving a Show typeclass

The article demonstrates deriving a `Show` type class that "transforms a type into a string." Basic instances are defined for `Int`, `Boolean`, and `String`.

For products, the derivation iterates through field labels and uses `project` to stringify each value. For sums, `fold` handles each variant. The `derived` method coordinates both strategies, enabling automatic derivation via the `derives` clause.

The implementation produces output like `"Foo(x = 1, y = s, z = true)"` for case classes and `"Blue()"` for enum variants.
