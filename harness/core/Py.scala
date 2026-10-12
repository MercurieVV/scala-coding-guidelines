package harness.core

import scala.collection.mutable

/** A JSON value as Python's `json.load` sees it: int and float literals stay distinct (`%s` of `10` is `10`, of
  * `10.0` is `10.0`), objects keep their key order. The reports print score.json / chain.json values with Python's
  * `str()`, so they read through this instead of ujson.Value (which keeps only a Double).
  */
sealed trait Py
case object PNone extends Py
final case class PBool(b: Boolean) extends Py
final case class PInt(v: BigInt) extends Py
final case class PFloat(v: Double) extends Py
final case class PStr(s: String) extends Py
final case class PList(xs: Vector[Py]) extends Py
final case class PDict(kv: Vector[(String, Py)]) extends Py:
  /** `d.get(k)`: None when missing. */
  def get(k: String): Py = kv.collectFirst { case (`k`, v) => v }.getOrElse(PNone)
  def contains(k: String): Boolean = kv.exists(_._1 == k)
  /** `d[k] = v`: an existing key keeps its position. */
  def updated(k: String, v: Py): PDict =
    if contains(k) then PDict(kv.map { case (`k`, _) => k -> v; case o => o }) else PDict(kv :+ (k -> v))

object Py:
  /** Python's TypeError / AttributeError / IndexError where GA would crash on an unexpected shape. */
  final class PyError(msg: String) extends RuntimeException(msg)

  def parse(s: String): Py = ujson.transform(ujson.Readable.fromString(s), Builder)

  /** GA `json.load(open(path))`: Left(str(e)) for the errors GA catches (OSError, ValueError). */
  def load(p: os.Path): Either[String, Py] =
    val path = p.toString
    val text =
      try Right(strictUtf8(os.read.bytes(p)))
      catch
        case _: java.nio.file.NoSuchFileException     => Left(s"[Errno 2] No such file or directory: ${repr(PStr(path))}")
        case _: java.nio.file.AccessDeniedException   => Left(s"[Errno 13] Permission denied: ${repr(PStr(path))}")
        case _: java.nio.charset.CharacterCodingException => Left("'utf-8' codec can't decode bytes")
        case _: java.io.IOException if os.isDir(p)    => Left(s"[Errno 21] Is a directory: ${repr(PStr(path))}")
        case e: java.io.IOException                   => Left(Option(e.getMessage).getOrElse(e.toString))
    text.flatMap { s =>
      if s.forall(c => c == ' ' || c == '\t' || c == '\n' || c == '\r') then
        // json.JSONDecodeError for an empty / blank document, positioned at its end
        val pos = s.length
        val nl = s.lastIndexOf('\n', pos - 1)
        val line = s.count(_ == '\n') + 1
        val col = if nl >= 0 then pos - nl else pos + 1
        Left(s"Expecting value: line $line column $col (char $pos)")
      else
        try Right(parse(s))
        catch case e: Exception => Left(Option(e.getMessage).getOrElse(e.toString))
    }

  private def strictUtf8(bytes: Array[Byte]): String =
    java.nio.charset.StandardCharsets.UTF_8.newDecoder()
      .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
      .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
      .decode(java.nio.ByteBuffer.wrap(bytes)).toString

  /** Python truthiness. */
  def truthy(v: Py): Boolean = v match
    case PNone     => false
    case PBool(b)  => b
    case PInt(i)   => i != 0
    case PFloat(d) => d != 0.0
    case PStr(s)   => s.nonEmpty
    case PList(xs) => xs.nonEmpty
    case PDict(kv) => kv.nonEmpty

  /** `x or default`. */
  def or(v: Py, default: Py): Py = if truthy(v) then v else default

  /** `d.get(k)` on a value that must be a dict. */
  def get(v: Py, k: String): Py = v match
    case d: PDict => d.get(k)
    case o        => throw PyError(s"'${typeName(o)}' object has no attribute 'get'")

  def typeName(v: Py): String = v match
    case PNone => "NoneType"; case _: PBool => "bool"; case _: PInt => "int"; case _: PFloat => "float"
    case _: PStr => "str"; case _: PList => "list"; case _: PDict => "dict"

  /** The value as a Python number (bool is an int); None for anything else. */
  def num(v: Py): Option[Double] = v match
    case PBool(b)  => Some(if b then 1.0 else 0.0)
    case PInt(i)   => Some(i.toDouble)
    case PFloat(d) => Some(d)
    case _         => None

  def numOrFail(v: Py, op: String): Double =
    num(v).getOrElse(throw PyError(s"'$op' not supported for '${typeName(v)}'"))

  /** Python `int(x)` of a number (floats truncate), as `%d` formats it. */
  def toLong(v: Py): Long = v match
    case PBool(b)  => if b then 1L else 0L
    case PInt(i)   => i.toLong
    case PFloat(d) => d.toLong
    case o         => throw PyError(s"%d format: a real number is required, not ${typeName(o)}")

  /** Python `==` between two JSON values (numbers compare by value, 9 == 9.0). */
  def eq(a: Py, b: Py): Boolean = (num(a), num(b)) match
    case (Some(x), Some(y)) => (a, b) match
        case (PInt(i), PInt(j)) => i == j
        case _                  => x == y
    case _ => a == b

  /** `for x in v`: list items, str characters, dict keys. */
  def iter(v: Py): Vector[Py] = v match
    case PList(xs) => xs
    case PStr(s)   => s.codePoints.toArray.toVector.map(cp => PStr(new String(Character.toChars(cp))))
    case PDict(kv) => kv.map(kv => PStr(kv._1))
    case o         => throw PyError(s"'${typeName(o)}' object is not iterable")

  /** `len(v)`. */
  def len(v: Py): Int = v match
    case PList(xs) => xs.length
    case PStr(s)   => s.codePointCount(0, s.length)
    case PDict(kv) => kv.length
    case o         => throw PyError(s"object of type '${typeName(o)}' has no len()")

  /** `sum(values)`: an int while every term is an int (or bool), a float otherwise. */
  def sum(vs: Iterable[Py]): Py =
    vs.foldLeft(PInt(0): Py) { (acc, v) =>
      (acc, v) match
        case (PInt(a), PInt(b))  => PInt(a + b)
        case (PInt(a), PBool(b)) => PInt(a + (if b then 1 else 0))
        case (a, b)              => PFloat(numOrFail(a, "+") + numOrFail(b, "+"))
    }

  /** `sorted(set(values))`: strings by code point, numbers by value; mixed types raise as in Python. */
  def sortedSet(vs: Iterable[Py]): Vector[Py] =
    val uniq = mutable.ArrayBuffer.empty[Py]
    vs.foreach(v => if !uniq.exists(eq(_, v)) then uniq += v)
    if uniq.length <= 1 then uniq.toVector
    else if uniq.forall(_.isInstanceOf[PStr]) then uniq.toVector.sortWith((a, b) => codePointLess(a.asInstanceOf[PStr].s, b.asInstanceOf[PStr].s))
    else if uniq.forall(num(_).isDefined) then uniq.toVector.sortBy(num(_).get)
    else throw PyError("'<' not supported between instances of " + uniq.map(typeName).distinct.mkString(" and "))

  /** Python str ordering (code points, not UTF-16 units). */
  def codePointLess(a: String, b: String): Boolean =
    val x = a.codePoints.toArray
    val y = b.codePoints.toArray
    val n = math.min(x.length, y.length)
    var i = 0
    while i < n && x(i) == y(i) do i += 1
    if i < n then x(i) < y(i) else x.length < y.length

  /** Python `str(v)`. */
  def str(v: Py): String = v match
    case PStr(s) => s
    case o       => repr(o)

  /** Python `repr(v)`. */
  def repr(v: Py): String = v match
    case PNone     => "None"
    case PBool(b)  => if b then "True" else "False"
    case PInt(i)   => i.toString
    case PFloat(d) => pyFloatRepr(d)
    case PStr(s)   => strRepr(s)
    case PList(xs) => xs.map(repr).mkString("[", ", ", "]")
    case PDict(kv) => kv.map((k, x) => strRepr(k) + ": " + repr(x)).mkString("{", ", ", "}")

  private def pyFloatRepr(d: Double): String =
    if d.isNaN then "nan" else if d.isInfinite then (if d > 0 then "inf" else "-inf") else Json.pyFloat(d)

  private def strRepr(s: String): String =
    val q = if s.contains('\'') && !s.contains('"') then '"' else '\''
    val b = new StringBuilder().append(q)
    s.foreach {
      case '\\'                          => b.append("\\\\")
      case c if c == q                   => b.append('\\').append(c)
      case '\n'                          => b.append("\\n")
      case '\r'                          => b.append("\\r")
      case '\t'                          => b.append("\\t")
      case c if c < ' ' || c == '\u007f' => b.append(f"\\x${c.toInt}%02x")
      case c                             => b.append(c)
    }
    b.append(q).toString

  /** Python `json.dumps(v)` (default separators, ensure_ascii). */
  def dumps(v: Py): String = v match
    case PNone     => "null"
    case PBool(b)  => if b then "true" else "false"
    case PInt(i)   => i.toString
    case PFloat(d) => if d.isNaN then "NaN" else if d.isInfinite then (if d > 0 then "Infinity" else "-Infinity") else Json.pyFloat(d)
    case PStr(s)   => ujson.write(ujson.Str(s), escapeUnicode = true)
    case PList(xs) => xs.map(dumps).mkString("[", ", ", "]")
    case PDict(kv) => kv.map((k, x) => ujson.write(ujson.Str(k), escapeUnicode = true) + ": " + dumps(x)).mkString("{", ", ", "}")

  private object Builder extends ujson.JsVisitor[Py, Py]:
    def visitArray(length: Int, index: Int): upickle.core.ArrVisitor[Py, Py] = new upickle.core.ArrVisitor[Py, Py]:
      private val b = Vector.newBuilder[Py]
      def subVisitor: upickle.core.Visitor[?, ?] = Builder
      def visitValue(v: Py, index: Int): Unit = b += v
      def visitEnd(index: Int): Py = PList(b.result())

    def visitJsonableObject(length: Int, index: Int): upickle.core.ObjVisitor[Py, Py] = new upickle.core.ObjVisitor[Py, Py]:
      private val m = mutable.LinkedHashMap.empty[String, Py]
      private var key = ""
      def visitKey(index: Int): upickle.core.Visitor[?, ?] = upickle.core.StringVisitor
      def visitKeyValue(s: Any): Unit = key = s.toString
      def subVisitor: upickle.core.Visitor[?, ?] = Builder
      def visitValue(v: Py, index: Int): Unit = m(key) = v
      def visitEnd(index: Int): Py = PDict(m.toVector)

    def visitNull(index: Int): Py = PNone
    def visitFalse(index: Int): Py = PBool(false)
    def visitTrue(index: Int): Py = PBool(true)
    def visitString(s: CharSequence, index: Int): Py = PStr(s.toString)
    def visitFloat64StringParts(s: CharSequence, decIndex: Int, expIndex: Int, index: Int): Py =
      if decIndex == -1 && expIndex == -1 then PInt(BigInt(s.toString)) else PFloat(java.lang.Double.parseDouble(s.toString))
