# Local probe: ScalaSemantic as tool calls vs as a scala-cli program

> Source: local measurement, this repository's owner — ScalaSemanticMCP server log (`ScalaSemanticMCP/scala-semantic-mcp.log`, 2026-06-22 to 2026-09-11), a throwaway scala-cli probe and direct MCP calls against skreeep2
> Collected: 2026-10-01
> Published: 2026-10-01

Measurement record for roadmap item 8 (code-execution surface). Outputs copied from the commands as run.

## 1. Tool definitions in Claude Code

In the Claude Code session that ran this probe, the `mcp__scala-semantic__*` tools were listed as deferred tools:
their schemas load only on demand through ToolSearch, not upfront. The last `tools/list` response in the server log
is 14039 characters.

## 2. Server log: real calls and result sizes

```
calls: 812  sessions: 239
 187 find_usages
 183 annotated_source
 162 method_signature
 151 call_path
 106 document_outline
  17 find_symbol
   3 structure
   2 members
   1 class_hierarchy
```

151 sessions are the identical automated smoke-test triple `('method_signature', 'find_usages', 'call_path')`.

```
non-smoke sessions 30 calls 359
non-smoke multi-symbol-query sessions (>=2 of find_usages/method_signature/class_hierarchy/members/call_path/find_symbol): 17
```

Result text size in characters, per tool (results paired with calls where the log recorded them):

```
annotated_source   n= 183 p50= 11790 p90= 36508 max= 58411
document_outline   n= 106 p50=  3456 p90= 14640 max= 29955
find_usages        n=  22 p50=   911 p90=  1446 max=  1887
find_symbol        n=  17 p50=   300 p90=  5522 max=  5522
structure          n=   3 p50=  8002 p90=  8070 max=  8070
method_signature   n=   3 p50=   344 p90=   481 max=   481
members            n=   2 p50=   206 p90=   206 max=   206
class_hierarchy    n=   1 p50=   145 p90=   145 max=   145
```

## 3. Probe: one multi-step query both ways

Query on skreeep2: the known subtypes of `com/github/mercurievv/skreeep2/handoff/Host#`, and the reference count of each.

**As a program.** A scala-cli script (`//> using dep io.github.mercurievv::scalasemantic-analysis:0.7.1`, the
published library) loads the SemanticDB index and runs find_symbol → class_hierarchy → find_usages per subtype.
Script size 1063 characters; output 369 characters:

```
com/github/mercurievv/skreeep2/handoff/Host# subtypes=6
com/github/mercurievv/skreeep2/handoff/SelfRendering#	9
com/github/mercurievv/skreeep2/handoff/SelfRenderingHost#	0
com/github/mercurievv/skreeep2/hostdriven/HostDrivenHost#	11
com/github/mercurievv/skreeep2/jdkaudio/JdkAudioHost#	1
com/github/mercurievv/skreeep2/jdkaudio/JdkAudioHost.JdkAudioHost#	2
local45	29
```

Timings (three runs; the first includes compilation):

```
index load 724 ms, query 29 ms   real 1.76
index load 513 ms, query 27 ms   real 0.87
index load 514 ms, query 27 ms   real 0.87
```

The first attempt failed to compile — `Not found: TypeSymbol` / `Not found: SemanticDbSymbol` (the input types live in
`InputTypes`) — so the wrong tool use was rejected before anything ran.

**As tool calls.** 7 MCP calls: class_hierarchy (returns display names only, so a second call with `detailed: true`
was needed for the symbols), then find_usages for each of the 5 subtypes it reports (it omits the local subtype).
Result sizes in characters: `172 638 1115 710 1229 297 415`, total 4576.

## 4. Execution surface

The agent in these harnesses already runs `scala-cli` and `./mill` through its shell tool; a scala-cli script calling
the library needs no new runtime or permission.
