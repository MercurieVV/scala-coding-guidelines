package harness.core

import scala.util.Try

object Json:
  val Ansi: scala.util.matching.Regex = "\u001b\\[[0-9;]*m".r

  /** Python `repr(float)` (what json.dumps writes): shortest round-trip digits, scientific below 1e-4 and from 1e16. */
  def pyFloat(d: Double): String =
    if d.isNaN then "NaN"
    else if d.isInfinite then (if d > 0 then "Infinity" else "-Infinity")
    else if d == 0 then (if 1 / d < 0 then "-0.0" else "0.0")
    else
      val bd = new java.math.BigDecimal(java.lang.Double.toString(d)).stripTrailingZeros
      val digits = bd.unscaledValue.abs.toString
      val exp = digits.length - 1 - bd.scale
      val sign = if d < 0 then "-" else ""
      if exp >= -4 && exp < 16 then
        val plain = bd.abs.toPlainString
        sign + (if plain.contains('.') then plain else plain + ".0")
      else
        val mant = digits.take(1) + (if digits.length > 1 then "." + digits.drop(1) else "")
        f"$sign$mant${if exp < 0 then "e-" else "e+"}${math.abs(exp)}%02d"

  def readJson(p: os.Path): Option[ujson.Value] =
    Try(ujson.read(os.read(p))).toOption

  def writeJsonAtomic(p: os.Path, v: ujson.Value): Unit =
    val tmp = p / os.up / (p.last + ".tmp")
    os.write.over(tmp, ujson.write(v, indent = 2), createFolders = true)
    os.move(tmp, p, replaceExisting = true, atomicMove = true)

  def readLenient(p: os.Path): String =
    Try {
      val bytes = os.read.bytes(p)
      val dec = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE)
      dec.decode(java.nio.ByteBuffer.wrap(bytes)).toString
    }.getOrElse("")
