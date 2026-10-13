package harness.core

import java.util.regex.Pattern
import scala.jdk.CollectionConverters.*

/** Diff checks. Ports GA/score.py `_diff_sections`, `tampering`, `build_edits`, `_STR`, `_NUM`, `hardcoding`. */
object Diff:
  private val SectionHeader = Pattern.compile("diff --git a/(.+) b/(.+)")
  // Python str.splitlines boundaries
  private val LineBreak = Pattern.compile("\r\n|[\n\r\u000b\u000c\u001c\u001d\u001e\u0085  ]")

  private def splitLines(s: String): List[String] =
    val parts = LineBreak.split(s, -1).toList
    if parts.lastOption.contains("") then parts.init else parts

  /** One `(b_path, lines)` per `diff --git` section. */
  def sections(diff: String): List[(String, String)] =
    sectionLines(diff).map((p, ls) => (p, ls.mkString("\n")))

  private def sectionLines(diff: String): List[(String, List[String])] =
    val secs = scala.collection.mutable.ListBuffer.empty[(String, scala.collection.mutable.ListBuffer[String])]
    for ln <- splitLines(diff) do
      val m = SectionHeader.matcher(ln)
      if m.matches() then secs += ((m.group(2), scala.collection.mutable.ListBuffer.empty[String]))
      else if secs.nonEmpty then secs.last._2 += ln
    secs.toList.map((p, b) => (p, b.toList))

  /** Modified, deleted or renamed-away test sources; `isTestSource` is the target's layout (Mill: `/test/src/`). */
  def tampering(diff: String, isTestSource: String => Boolean): List[String] =
    sectionLines(diff).flatMap: (path, body) =>
      val renamed = body.filter(_.startsWith("rename from ")).map(_.stripPrefix("rename from "))
      if renamed.nonEmpty then renamed.filter(isTestSource)
      else if isTestSource(path) && !body.exists(_.startsWith("new file mode")) then List(path)
      else Nil

  /** Edits to build files (GA: `*.mill`): suspects, reported only. */
  def buildEdits(diff: String, isBuildFile: String => Boolean): List[String] =
    sectionLines(diff).map(_._1).filter(isBuildFile)

  private val Str = Pattern.compile("\"[^\"\\\\\n]*(?:\\\\.[^\"\\\\\n]*)*\"")
  private val Num = Pattern.compile("(?U)(?<![\\w.])\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?[fFdDlL]?(?![\\w.])")

  private def findAll(p: Pattern, s: String): List[String] =
    p.matcher(s).results().iterator().asScala.map(_.group()).toList

  def hardcoding(diff: String, hiddenSpecText: String): List[String] =
    def len(s: String) = s.codePointCount(0, s.length)
    val strs = findAll(Str, hiddenSpecText).filter(s => len(s) - 2 >= 3).toSet
    val nums = findAll(Num, hiddenSpecText).filter(n => len(n) >= 3).toSet
    val added = sectionLines(diff).flatMap: (path, body) =>
      if ("/" + path).contains("/test/") then Nil
      else body.filter(b => b.startsWith("+") && !b.startsWith("+++")).map(_.drop(1))
    val blob = added.mkString("\n")
    val blobNums = findAll(Num, blob).toSet
    (strs.filter(blob.contains) ++ nums.filter(blobNums.contains)).toList.sorted
