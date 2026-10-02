# Structural holdout seal — design

Date: 2026-10-02. Repositories: scala-llm-template (`kit/harness`, `kit/recipes/mill`), skreeep2 (first adopter).
Roadmap: item 1 follow-up (test protection and holdout), wiki `wiki/llm-scala-fusion/roadmap-more-scala-less-llm.md`.

## 1. Problem

The sealed holdout suite is protected only by tool-policy's text rules. Two live gated runs on 2026-10-02 showed
routes no text rule can see: a selector built at run time (`H=hold; H="${H}out"; ./mill "$H.test"`) and a Python
script calling `subprocess.call(["./mill", "hold" + "out.test"])`. Both ran the suite. Any rule that reads command text
loses to code that computes the command.

## 2. Goal and success criteria

An agent working in a gated repository cannot run the holdout suite, read its sources, read its compiled output, or
read its log — by any route its shell commands can take — while the gate keeps running the suite at turn-end and
reporting names only. The human's own terminal is unaffected.

Success, measured in skreeep2 by a live gated session after the change:

1. `./mill holdout.test`, the run-time-built selector, the Python `subprocess` route, and `cat holdout/...` all fail at
   the operating-system level (`Operation not permitted`), and no holdout test name or source line appears in the
   agent's output.
2. A Mill server started outside the sandbox — by the human's terminal (`./mill` in `out/`) or by the gate's hooks —
   is not reachable by the agent's Mill client.
3. Normal agent work runs sandboxed: `./mill <m>.compile` and `./mill <m>.test` for skreeep2 modules, with no
   unsandboxed retry available.
4. The gate's turn-end holdout run still reports `holdout: N passed, M failed` with names.
5. The added cost per turn-end and per edit is measured and stated; the target is under 10 s per turn-end on skreeep2.

## 3. Facts the design rests on (spike, 2026-10-02)

Claude Code 2.1.287, Mill 1.1.8, macOS; scratch clone of skreeep2; headless gated `claude -p` runs and `sandbox-exec`.

| Measured | Result |
|---|---|
| Sandbox on, strict, no other allowance | Mill cannot start: `SocketException: Operation not permitted` on the launcher's connect to its server |
| `network.allowLocalBinding` + `allowWrite` on `~/.cache/mill` and `~/Library/Caches/Coursier` | `version` 0.49 s, `core.compile` 0.95 s, `signals.test` 3 s |
| Mill server started outside the sandbox, agent's client sandboxed | **bypass**: `"$H.test"` ran, `+ the holdout suite runs under the gate`. The gate's own Stop hook left such a server running |
| Server started inside the sandbox, `denyRead` on `holdout/` | run-time selector and Python route fail: `…/holdout: Operation not permitted`; no test runs |
| `MILL_OUTPUT_DIR=out-agent` | honored: separate output directory and server; the server's port is in `<dir>/mill-daemon/socketPort` |
| Gate `--no-daemon` in the agent's directory while the agent's sandboxed server is alive | works, no new server: `signals.compile` 2 s; holdout first run 14 s, passes |
| `--no-daemon` cost outside the sandbox | `core.compile` 2 s (server path: 1–2 s) |
| Env switch for no-daemon | none: the launcher knows `MILL_ENABLE_STATIC_CHECKS`, `MILL_EXECUTABLE_PATH`, `MILL_SERVER_TIMEOUT_MILLIS`, `MILL_TEST_EXIT_AFTER_BSP_CHECK`, `MILL_WORKSPACE_ROOT`; `MILL_NO_SERVER` / `MILL_NO_DAEMON` still start a server |

Claude Code documentation (code.claude.com/docs/en/sandboxing): `denyRead` is enforced by the OS on shell commands
"and the processes they start"; hooks, MCP servers and the file tools run outside the sandbox; with
`allowUnsandboxedCommands: false` "Claude Code ignores the `dangerouslyDisableSandbox` parameter";
`failIfUnavailable: true` makes Claude Code exit rather than run commands unsandboxed when the sandbox cannot start;
the sandbox needs Seatbelt (macOS) or bubblewrap + socat (Linux/WSL2) and does not exist on native Windows.

## 4. Design

Four parts. The invariant they jointly hold: **every Mill server the agent's client can find runs inside the agent's
sandbox, and the sandbox cannot read the holdout.**

### 4.1 The agent's sandbox (human-owned settings)

The seed `kit/harness/seed/cc-settings.json` (copied by the human to `.claude/settings.json`) gains:

```json
"env": { "MILL_OUTPUT_DIR": "out-agent" },
"sandbox": {
  "enabled": true,
  "allowUnsandboxedCommands": false,
  "failIfUnavailable": true,
  "network": { "allowLocalBinding": true },
  "filesystem": {
    "allowWrite": ["~/.cache/mill", "~/.cache/coursier", "~/Library/Caches/Coursier"],
    "denyRead": ["./out/mill-daemon", "./out-agent/<holdout target>", "./.dsh/.state/holdout.log", "./<sealed source rows>"]
  }
}
```

- `denyRead` lists every `sealed` row of `.dsh/frozen-paths` (in skreeep2: `holdout`, `.dsh/.state/holdout.log`), the
  holdout module's compiled output in the agent's directory (`out-agent/holdout`), and the human's server directory
  `out/mill-daemon` — the agent cannot learn the port of a server it may not use.
- `env.MILL_OUTPUT_DIR=out-agent` puts the agent's builds and server in their own directory. An agent that overrides it
  (`MILL_OUTPUT_DIR=out ./mill …`) reaches `out/mill-daemon`, which it cannot read: its client fails rather than
  connecting.
- The values are a template: `install.sh` seeds them, filling `denyRead` from the sealed rows and the contract's
  holdout target. The settings file is already frozen, so the agent cannot loosen it.
- `out-agent/` is added to the seed `.gitignore` lines `install.sh` maintains.

### 4.2 The gate never leaves a Mill server behind

A new engine script, `kit/harness/mill-gate.sh`, runs the project's Mill (`resolve_mill`) with `--no-daemon` and
`MILL_OUTPUT_DIR` set to the agent's directory: the contract key `agentOutputDir` (new, optional, default `out-agent`), which check-harness requires to equal the settings file's `env.MILL_OUTPUT_DIR`. Every Mill
process the engine starts goes through it:

- direct calls (`$mill …` in post-edit, turn-end, module-map, health, feedback probes): `resolve_mill` returns the
  wrapper instead of `./mill`;
- contract commands the engine executes (`turnEndTests`, `fastTests`, `holdout`, `checkFormat`, `fix`, `coverage`):
  a leading `./mill` or `mill` word is replaced by the wrapper before `sh -c` runs the command
  (`dsh_gate_command <cmd>`); a command whose first word is anything else is run as written, and check-harness notes it
  (the engine cannot vouch for a server that command may start).

Sharing the agent's directory keeps one compile cache: the gate checks exactly what the agent built. `--no-daemon`
measured at 2 s against 1–2 s for `core.compile`.

### 4.3 The holdout run

Unchanged in what it reports. It runs through 4.2 (no server, agent's directory), outside the sandbox (hooks are), so it
reads `holdout/` normally; its compiled output lands in `out-agent/holdout`, which the agent cannot read. The time cap
(`DSH_HOLDOUT_CAP`) is unchanged.

### 4.4 Checks and reporting

- `check-harness.sh` (new group): `.claude/settings.json` exists and has `sandbox.enabled: true`,
  `allowUnsandboxedCommands: false`, `failIfUnavailable: true`, `env.MILL_OUTPUT_DIR`, and a `denyRead` entry for every
  sealed row, `out/mill-daemon`, and `<dir>/<holdout target>`. Missing or weaker → FAIL with the exact line to add.
  A contract command that starts Mill other than through `./mill`/`mill` → note.
- `health` (SessionStart): a named degradation line when the settings file lacks the sandbox block, and on a platform
  with no sandbox (native Windows; Linux without `bwrap`) — "the holdout seal is text-only in this session".
- tool-policy's text rules stay: they refuse the plain forms earlier, with a message, before the OS does.

## 5. Out of scope, and the residuals stated

- The human's terminal server in `out/` stays reachable through `localhost` by an agent that guesses its port and
  speaks Mill's protocol; `socketPort` is unreadable to it. Documented, not solved.
- Claude Code's file tools are not sandboxed: semantic-guard's sealed-path refusal stays their layer (unchanged).
- MCP servers run outside the sandbox: an MCP tool that runs Mill (none in skreeep2's set) would reopen the bypass;
  check-harness notes MCP servers named in the settings.
- Adding a new library dependency inside the sandbox: the JVM ignores the proxy variables, so Coursier downloads may
  fail sandboxed. To be measured in the plan; if they fail, the documented remedy is the human running
  `MILL_OUTPUT_DIR=out-agent ./mill --no-daemon <m>.compile` once in a terminal.
- Native Windows: no sandbox; the seal stays text-only and health says so.
- Seen in the spike, separate: when Mill's launcher cannot start, the feedback wrapper renders it as `UNREADABLE` rather
  than an INFRA record naming the cause. Fixed alongside only if it is one line; otherwise ledgered.

## 6. Testing

- Fixture cases (sh and dash): `dsh_gate_command` rewriting (`./mill`, `mill`, other first words, leading
  assignments); `resolve_mill` returning the wrapper; the wrapper passing `--no-daemon` and `MILL_OUTPUT_DIR`; turn-end
  and post-edit Mill calls going through it (stub Mill records its arguments and environment); check-harness rows for
  each missing or weak settings key; health lines.
- Live, skreeep2 clone with the new engine and settings: success criteria 1–5, by a gated headless session and a
  `pgrep` for servers after a turn ends (expected: only the agent's sandboxed server).
- Cost: turn-end and post-edit wall time before/after on skreeep2.

## 7. Rollout

scala-llm-template first (engine, seed, recipes doc), then skreeep2: re-vendor, the human copies the new settings block
into `.claude/settings.json` (a human step — the file is frozen), first agent build populates `out-agent/`.

## 8. Amendments after the final review (2026-10-02)

The whole-branch review found the design incomplete in six places; each is now part of the design.

1. **Writes, not only reads.** The sandbox allows writes in the working directory by default, so a sandboxed interpreter
   could replace `.dsh/engine/mill-gate.sh`, the contract's `holdout =` line or `.dsh/frozen-paths`, and the next hook —
   unsandboxed — would run it. The settings add `sandbox.filesystem.denyWrite` on `./.dsh/engine`, `./.dsh/contract`,
   `./.dsh/frozen-paths`, `./.claude` and every sealed row; check-harness FAILs per missing entry.
2. **Settings that switch the seal off FAIL.** A non-empty `sandbox.excludedCommands` (those commands run unsandboxed),
   `enableWeakerNestedSandbox: true`, and a `.claude/settings.local.json` that overrides any required key with a weaker
   value. health reads the local override for `sandbox.enabled`.
3. **One Mill per contract command.** A command that runs Mill after a separator (`./mill a && ./mill holdout.test`) can be
   routed only at its first Mill; check-harness FAILs it (`dsh_gate_command` exit 2).
4. **The holdout's output path** is the selector's module segments without the task (`engine.holdout.test` →
   `out-agent/engine/holdout`), not its first segment.
5. **No holdout, no change.** Without a `holdout =` line and without `agentOutputDir`, the agent directory defaults to `out`,
   so projects that need no seal keep one cache with the human's builds.
6. **MCP servers** named in `.mcp.json` or the settings get a check-harness note (they run outside the sandbox).
   `install.sh` does NOT generate `denyRead`/`denyWrite` from the sealed rows (§4.1 said it would): the seed is static for
   the conventional `holdout` module, and check-harness names every exact entry a project with another layout must add —
   the same result without editing JSON in POSIX sh.
