package harness.agent

import cats.effect.IO
import java.util.Optional
import org.snakeyaml.engine.v2.api.{ConstructNode, DumpSettings, LoadSettings, RepresentToNode}
import org.snakeyaml.engine.v2.api.lowlevel.Compose
import org.snakeyaml.engine.v2.common.FlowStyle
import org.snakeyaml.engine.v2.constructor.StandardConstructor
import org.snakeyaml.engine.v2.nodes.{Node, ScalarNode, Tag}
import org.snakeyaml.engine.v2.representer.StandardRepresenter
import scala.jdk.CollectionConverters.*

/** dsh profile YAML (loader patch lists, `--dump-config` output): java collections, `!!js` kept as [[Yaml.Tagged]]. */
object Yaml:
  /** A scalar with a tag YAML core does not know (dsh's `!!js <expr>`), written back as it was read. */
  final case class Tagged(tag: String, value: String)

  /** The fields of a loader row the harness looks at. */
  final case class Row(id: Option[String], name: Option[String], serverName: Option[String])

  private final class KeepTags(s: LoadSettings) extends StandardConstructor(s):
    private val keep: ConstructNode = (node: Node) => Tagged(node.getTag.getValue, node.asInstanceOf[ScalarNode].getValue)
    override protected def findConstructorFor(node: Node): Optional[ConstructNode] =
      val std = super.findConstructorFor(node)
      if std.isPresent || !node.isInstanceOf[ScalarNode] then std else Optional.of(keep)

  private final class WriteTags(s: DumpSettings) extends StandardRepresenter(s):
    private val tagged: RepresentToNode = (data: Object) =>
      val t = data.asInstanceOf[Tagged]
      representScalar(new Tag(t.tag), t.value)
    representers.put(classOf[Tagged], tagged)

  def load(text: String): Object =
    val s = LoadSettings.builder().build()
    val composed = Compose(s).composeString(text)
    if composed.isPresent then KeepTags(s).constructSingleDocument(composed) else null

  def dump(doc: Object): String =
    val s = DumpSettings.builder().setDefaultFlowStyle(FlowStyle.BLOCK).setWidth(4096).build()
    org.snakeyaml.engine.v2.api.Dump(s, WriteTags(s)).dumpToString(doc)

  def list(doc: Object): List[Object] = doc match
    case l: java.util.List[?] => l.asScala.toList.map(_.asInstanceOf[Object])
    case _                    => Nil

  def map(o: Object): Option[java.util.Map[String, Object]] = o match
    case m: java.util.Map[?, ?] => Some(m.asInstanceOf[java.util.Map[String, Object]])
    case _                      => None

  def str(m: java.util.Map[String, Object], k: String): Option[String] = Option(m.get(k)).collect { case s: String => s }

  def row(m: java.util.Map[String, Object]): Row =
    Row(str(m, "id"), str(m, "name"), Option(m.get("config")).flatMap(map).flatMap(str(_, "serverName")))

  /** an ordered java map, for writing */
  def jmap(kvs: (String, Object)*): java.util.Map[String, Object] =
    val m = java.util.LinkedHashMap[String, Object]()
    kvs.foreach((k, v) => m.put(k, v))
    m

/** `harness dsh-profile --from <p>`: derives `<p>-headless` from a TUI profile (spec §4, dsh adapter). The only harness
  * code that writes under the dsh home, and only when the user runs that command.
  */
object DshProfile:
  val Headless = "@deepseek-ai/dsh-headless"
  val HooksBridge = "@deepseek-ai/dsh-hooks-claude-code"
  val HooksRowId = "claude-code-hooks"
  private val McpClient = "@deepseek-ai/dsh-mcp-client"
  private val DropRows = Set("tui-beep", "deepseek-budget", "dsh-tui-beep", "dsh-deepseek-budget")
  /** TUI-bound packages: dropped from both bundles and dependencies */
  private def dropDep(d: String): Boolean =
    d.startsWith("@deepseek-harness-tui/") || d == "dsh-tui-beep" || d == "dsh-deepseek-budget"

  def defaultHome: os.Path = sys.env.get("DSH_HOME").filter(_.nonEmpty).map(os.Path(_, os.pwd)).getOrElse(os.home / ".dsh")

  def headlessName(from: String): String = s"$from-headless"
  def installCommand(from: String): String = s"dsh plugin --profile ${headlessName(from)} install"

  /** Every loader row of a patch list: plain entries and the rows of `insert` lists. */
  def rows(doc: Object): List[Yaml.Row] =
    Yaml.list(doc).flatMap(Yaml.map).flatMap { m =>
      Option(m.get("insert")) match
        case Some(ins) => Yaml.list(ins).flatMap(Yaml.map).map(Yaml.row)
        case None      => List(Yaml.row(m))
    }

  private def dropped(r: Yaml.Row): Boolean =
    r.id.exists(DropRows) || r.name.exists(DropRows) || (r.name.contains(McpClient) && r.serverName.contains("browser"))

  /** the hook bridge row gets an id, so a per-run `--patch` overlay can retarget its configPath */
  private def withHooksId(m: java.util.Map[String, Object]): java.util.Map[String, Object] =
    if Yaml.str(m, "name").contains(HooksBridge) && !m.containsKey("id") then
      Yaml.jmap((("id" -> HooksRowId) :: m.asScala.toList)*)
    else m

  private def patchList(doc: Object): java.util.List[Object] =
    val out = java.util.ArrayList[Object]()
    Yaml.list(doc).foreach { e =>
      Yaml.map(e) match
        case Some(m) if m.containsKey("insert") =>
          val kept = Yaml.list(m.get("insert")).filterNot(r => Yaml.map(r).exists(x => dropped(Yaml.row(x))))
            .map(r => Yaml.map(r).map(withHooksId).getOrElse(r))
          if kept.nonEmpty then
            out.add(Yaml.jmap(m.asScala.toList.map((k, v) => k -> (if k == "insert" then java.util.ArrayList[Object](kept.asJava) else v))*))
        case Some(m) => if !dropped(Yaml.row(m)) then out.add(withHooksId(m))
        case None    => out.add(e)
    }
    out

  def derive(home: os.Path, from: String): IO[os.Path] = IO.blocking {
    def die(msg: String): Nothing = throw new RuntimeException(s"dsh-profile: $msg")
    val src = home / "profiles" / from
    val dest = home / "profiles" / headlessName(from)
    if os.exists(dest) then die(s"$dest exists (remove it first)")
    List("package.json", "cordis.patch.yml").map(src / _).find(!os.isFile(_)).foreach(p => die(s"$p missing"))

    val pkg = ujson.read(os.read(src / "package.json")).obj
    val deps = pkg.get("dependencies").collect { case o: ujson.Obj => o.value.filterNot((k, _) => dropDep(k)) }
    pkg("name") = s"dsh-profile-${headlessName(from)}"
    pkg("dependencies") = ujson.Obj.from(deps.getOrElse(Nil))
    val dsh = pkg.get("dsh").collect { case o: ujson.Obj => o }.getOrElse(ujson.Obj())
    val profile = dsh.value.get("profile").collect { case o: ujson.Obj => o }.getOrElse(ujson.Obj())
    // the source's agent stack, minus its TUI front door, run by the one-shot runner
    val srcBundles = profile.value.get("bundles").collect { case ujson.Arr(a) => a.toList.flatMap(_.strOpt) }.getOrElse(Nil)
    val bundles = srcBundles.filterNot(dropDep).filterNot(_ == Headless) :+ Headless
    profile("bundles") = ujson.Arr.from(bundles.map(ujson.Str(_)))
    profile("patchReload") = "startup"
    dsh("profile") = profile
    pkg("dsh") = dsh

    val srcPatch = Yaml.load(os.read(src / "cordis.patch.yml"))
    val bridges = rows(srcPatch).count(_.name.contains(HooksBridge))
    if bridges > 1 then die(s"${src / "cordis.patch.yml"} has $bridges $HooksBridge rows; a run's overlay retargets only one")
    val patch = patchList(srcPatch)
    os.makeDir.all(dest)
    os.write(dest / "package.json", ujson.write(pkg, indent = 2) + "\n")
    os.write(dest / "cordis.yml", "[]\n") // the root: an empty entry list, the tree is the bundles plus the patch layer
    os.write(dest / "cordis.patch.yml",
      s"# derived by `harness dsh-profile --from $from` from $src/cordis.patch.yml:\n" +
        "# TUI rows (tui-beep, deepseek-budget) and the browser MCP client dropped; the hook bridge row has an id.\n" +
        Yaml.dump(patch))
    // pnpm's own files: the workspace (patchedDependencies) and the patches it names
    if os.isFile(src / "pnpm-workspace.yaml") then os.copy(src / "pnpm-workspace.yaml", dest / "pnpm-workspace.yaml")
    if os.isDir(src / "patches") then os.copy(src / "patches", dest / "patches")
    dest
  }
