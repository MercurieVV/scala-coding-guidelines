package harness.core

object Gate:
  private val Blocked = "BLOCKED by ([A-Za-z0-9_.-]+)".r

  def isTurnEndText(text: String): Boolean =
    text.startsWith("Stop hook feedback") || (text.contains("turn-end") && text.contains("BLOCKED"))

  /** hookTexts: system-event strings and user-message text blocks only (never assistant text or tool_result content). */
  def gateBlocks(calls: Seq[ToolCall], hookTexts: Seq[String]): Map[String, Int] =
    val counts = scala.collection.mutable.LinkedHashMap.empty[String, Int]
    def bump(k: String): Unit = counts(k) = counts.getOrElse(k, 0) + 1
    for c <- calls if c.isError do
      Blocked.findFirstMatchIn(c.resultText).map(_.group(1))
        .map(n => if n == "the" && c.resultText.contains("BLOCKED by the DSH gate") then "post-edit" else n) // the engine's post-edit check
        .filter(_ != "turn-end").foreach(bump) // turn-end is counted from the text blocks below
      if c.resultText.contains("SEALED PATH") then bump("semantic-guard-sealed")
    for t <- hookTexts if isTurnEndText(t) do bump("turn-end")
    scala.collection.immutable.ListMap.from(counts)
