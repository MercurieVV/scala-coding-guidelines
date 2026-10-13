# Agent CLI contracts

Read from `--help` and the installed sources; no model call was made. Versions: codex-cli 0.148.0, gemini 0.38.1.

## All adapters (final review fixes, 2026-10-12)

- Load-time check: `Agent.of` runs the adapter's `validate`, and every subcommand resolves all configured agents right
  after loading (see README "Config layout"). A missing knob fails as `agents.<name>.<key>: missing`.
- Contamination dispatches on `ToolKind`, not Claude tool names: every Shell call's `command` goes through the Bash
  tokenizer (file-bound here-document bodies dropped, a bare `/` kept only as a walker's argument) and the chmod/chown
  check; a Search call's `pattern` is a regex and skipped, except a glob tool's (`Glob`, dsh / Gemini `glob`), whose
  pattern is a path. Claude calls take the same branches as before.
- `settings.json` in the run dir: copied only when the adapter wrote its config. Codex / Gemini without `mcp-servers`
  write none, so their runs have no `settings.json`.

## codex-cli (`agents/codex.conf`) - verified live (Task 20, 2026-10-12)

- Headless: `codex exec [OPTIONS] [PROMPT]`. Flags used: `--json` (events as JSONL on stdout, the transcript),
  `--ephemeral` (no session files: nothing to wipe or collect), `--skip-git-repo-check`, `--ignore-user-config`
  (skips `$CODEX_HOME/config.toml`: user MCP servers, notify; auth still uses `CODEX_HOME`), `--ignore-rules`,
  `--dangerously-bypass-approvals-and-sandbox` (unattended full access), `-C <repo>`, `-m <model>`.
- MCP: project-level `<repo>/.codex/config.toml` (`[mcp_servers.<name>]`, `command`, `args`) is written by `setup`; because a
  project config is only honoured for trusted projects, the same servers are also passed as `-c mcp_servers.<name>.command=...`
  and `-c mcp_servers.<name>.args=[...]`.
- State: sessions are `~/.codex/sessions/YYYY/MM/DD/rollout-*.jsonl` (rollout format, not used). The sessions dir is the
  contamination fence; the run's own dir set is empty because runs are ephemeral.
- Event schema (`exec --json`) implemented from the documented format, not from a captured run:
  `thread.started`, `turn.started`, `item.started|completed` with `item.type` in `agent_message`, `reasoning`,
  `command_execution` (`command`, `aggregated_output`, `exit_code`, `status`), `file_change` (`changes[{path,kind}]`),
  `mcp_tool_call` (`server`, `tool`, `arguments`, `result`, `error`, `status`), `web_search`; `turn.completed`
  (`usage.input_tokens`, `cached_input_tokens`, `output_tokens`); `turn.failed` (`error.message`).
  Fixtures `exec-json-sample.jsonl` / `exec-json-failed.jsonl` are hand-written from that schema; `exec-json-live.jsonl` is
  captured from the live run `fr-pan-codex-u-1` (gpt-5.5, texts shortened). Live run confirmed the event names and fields
  above (`thread.started`, `turn.started`, `item.started|completed` for `command_execution`, `mcp_tool_call`, `file_change`,
  `agent_message`, one `turn.completed`); `file_change` paths are absolute; `mcp_tool_call.result` is an object
  (`content`, `structured_content`). `turn.completed.usage` also has `cache_write_input_tokens`, `reasoning_output_tokens`.
  Shell commands run as `/bin/zsh -lc '…'` (the user's login shell profile loads). The parser drops that wrapper
  (`CodexCli.unwrapShell`): a `command_execution` call's `command` is the script, so contamination and
  `first_clean_compile` read what the agent wrote (the raw line stays in `transcript.jsonl`).
- Tokens: OpenAI's `input_tokens` includes `cached_input_tokens`; the adapter records `input_tokens - cached_input_tokens`
  as `input_tokens` (uncached, as Claude Code's), cached as `cache_read_tokens`.
- MCP: loads live (26 `scala-semantic` calls in the run).
- Residual exposure (live): `--ignore-user-config` does not stop `$CODEX_HOME/AGENTS.md` (here `@~/.codex/RTK.md`: the
  agent prefixed commands with `rtk`) nor the user's global skills (`~/.codex/skills/scala-feature`, which sent it to read
  this repo's `wiki/index.md`). The scorer flags it (`contamination`). Full isolation needs a separate `CODEX_HOME` holding a
  copy of `auth.json`; not tried, because a ChatGPT-login refresh in the copy may rotate the user's refresh token.
  Known limitation: the user declined a separate `CODEX_HOME` (2026-10-12), so Codex runs keep this exposure; compare
  Codex numbers with that in mind and read `contamination` per run.
- Prompt: passed last, after `--` (2026-10-13). clap otherwise reads a prompt starting with `-` as an option (`codex exec -x`:
  "unexpected argument '-x' found ... use '-- -x'"); checked read-only with `codex exec -- -x extra`, which rejects only `extra`.
- Cost: Codex reports none, `costUsd = None`. Turns: not reported, `turns = None`; `ToolCall.turn` is the call's sequence number.

## gemini-cli (`agents/gemini.conf`) - parser verified against source, unverified live

- Headless: `gemini -p <prompt> -m <model> --output-format stream-json --approval-mode yolo`
  (`--allowed-mcp-server-names <n>...` restricts MCP servers). cwd is the process cwd.
- MCP: project-level `<repo>/.gemini/settings.json` (`mcpServers`), written by `setup`. Isolation flags: `-e none` (no
  extensions; `-e/--extensions` takes a list and `none` is Gemini's documented value for "none") and
  `--allowed-mcp-server-names` always, with the conf's `allowed-mcp` or the sentinel `none` (matches no server) when empty.
  Residual exposure: the user's global `~/.gemini/settings.json` (its hooks and other non-MCP settings) and the global `~/.gemini/GEMINI.md` still load. Full isolation (e.g. a copied
  `GEMINI_CLI_HOME` with only the login files) awaits a live auth check in Task 20.
- Task 20 (2026-10-12): no live run possible. The account's `oauth-personal` login fails at startup with
  `IneligibleTierError: This client is no longer supported for Gemini Code Assist for individuals` (with and without a
  `GEMINI_CLI_HOME` copy, so isolation could not be judged). `-e none` and `--allowed-mcp-server-names none` /
  `scala-semantic` were accepted (no argument error; the CLI reached auth). The harness run `fr-pan-gemini-u-1` scored
  `error: no result event`. Tool names and MCP loading stay unverified until a run with an API key or an eligible account.
  `GEMINI_CLI_HOME=<d>` means `<d>/.gemini` (gemini-cli `homedir()`).
  The user has subscriptions only (no API key). Next try: upgrade gemini-cli and log in again (`gemini`, then `/auth`); until
  then the adapter stays unverified live. Gemini models run through `agy` instead (generic adapter, below).
- State: `<gemini-home>/tmp/<id>/chats/session-*.json`, `<id>` = `projects.json` entry of the project's real path
  (`GEMINI_CLI_HOME` overrides the home). `wipeState` removes only the run copy's `<id>` dir, nothing when unregistered.
  Older versions keyed the dir by a hash of the path; not handled.
- stream-json events (checked in `gemini.js` / `StreamJsonFormatter` of 0.38.1): `init` (`session_id`, `model`),
  `message` (`role`, `content`, `delta`), `tool_use` (`tool_name`, `tool_id`, `parameters`), `tool_result` (`tool_id`,
  `status` success|error, `output`, `error{type,message}`), `error` (`severity`, `message`), `result` (`status`,
  `error`, `stats{input_tokens, output_tokens, cached, ...}`). Fixtures are hand-written from that schema, not captured
  (no earlier session had tool calls), so tool names (`run_shell_command`, `replace`, `write_file`, `read_file`, `grep_search`,
  `mcp_<server>_<tool>`) are unverified live.
- envLeaks: MCP tool names are `mcp_<server>_<tool>`; the server is the longest configured (`mcp-servers`) or allowed
  name that prefixes the tool name, so `my_srv` is not cut to `my`; an unknown server is read up to its first `_`.
- Cost: none; turns: none.

## dsh (`agents/dsh.conf`) - verified live (Task 20, 2026-10-12)

dsh 0.1.5-rc.1, profile `scala-headless` (derived by `harness dsh-profile --from scala`, installed by the user), model
`deepseek-flash`, runs `fr-pan-dsh-u-1` and `fr-pan-dsh-gs-1` (run by the user; checked by hand, read-only).

- Transcript: `collect` picks the top-level session; both `transcript.jsonl` files are byte-identical to the decompressed
  `~/.dsh/sessions/<slug>/session-*/session.v3.jsonl.zstd`. Every `tool/call` has its `tool/result` (50/50, 128/128).
  Usage: `inputTokens` excludes `cacheReadTokens` (Claude Code's meaning), summed over `assistant/message`.
- Subagents: a `subagent` call starts a child session (`parentSession`, `delegationDepth 1`, own dir under the slug).
  `collect` skips it, so its steps, tool calls and tokens are not in the run's figures (dsh-gs: child 18 steps, 50273 in /
  32570 out / 960128 cache read not counted).
- Gate blocks: a blocking PreToolUse hook reaches the agent as the call's `Error: BLOCKED by …` tool result, so it is counted
  there (one per blocked call; dsh-gs had 11 PreToolUse block events on 10 calls, two hooks blocked the same call). Only a
  Stop block is a hook text (`Stop hook feedback`, turn-end). Fixed live: PreToolUse block texts were also hook texts,
  so a tool-policy text mentioning "turn-end" counted as a turn-end block (DshTest "parse: live PreToolUse blocks …").
- Hooks: in arm u no `hook/invoked` at all; in gs only the run copy's map (`dsh-hooks.json`: tool-policy, semantic-guard,
  post-edit, turn-end). None of the user's global `~/.claude/settings.json` hooks (rtk, scala-semantic-guard, cc-status) ran;
  the `request/header` has no hook config. The map's SessionStart hooks logged no `hook/invoked`.
- MCP: did not load in either run. The profile's MCP client starts `<cwd>/scalasemantic-mcp.sh`, which the skreeep2 tree
  does not have; the header lists no `mcp__` tool, and the gs agent logged "no scala-semantic MCP tool exposed in this
  session". So dsh arms ran without ScalaSemantic, unlike the Claude Code and Codex arms. `env_leaks` is empty (no extra
  servers). Fixed 2026-10-12 (`scalasemantic-command` in `agents/dsh.conf`: setup writes the launcher into the run copy,
  git-excluded): in the rep-2 reruns the header lists the `mcp__scalasemantic__*` tools; dsh-gs rep 2 called them 48 times
  (annotated_source 25, search_text 15, resolve_references 4, find_usages 2, find_symbol 1, document_outline 1), dsh-u
  rep 2 none.
- Stop (turn-end) block observed live in dsh-gs rep 2: one `hook/result` at point `Stop`, decision block, counted as
  `turn-end` 1.
- Request header config: `provider deepseek-official, model deepseek-flash, maxTokens 131072, reasoningEffort high`
  (`adapterDefaults` mark the last two as defaults; `maxTokens` matches the user's `~/.dsh/settings.yaml`).
- Run-dir files: `dsh-overlay.yml` (the `--patch`) and `dsh-hooks.json` (the hook map) sit in the run dir, which the
  fence otherwise exempts. `privateStateDirs` lists both as forbidden, so a tool call that edits or reads either is
  `contamination` (an agent rewriting its own gate hook map is caught).
- No fence on the dsh home: the gs agent's bash read `~/.dsh` (listing), `~/.dsh/settings.yaml`, tried
  `~/.dsh/sessions/$SID`, and read `~/.dsh/.agent-presets/liangshen/*.mjs`. Contamination flags them, but nothing stops
  them (`privateStateDirs` fences only `sessions/`, and only for the scorer). `settings.yaml` is global dsh config
  (`llm-deepseek.maxTokens`, UI notices) that dsh itself loads into every run, so it reaches the run either way; reading
  it exposes no other project's data, but other files there (sessions of other projects, presets) could.
- State outside the run copy: besides `sessions/<slug>/`, dsh wrote `~/.dsh/storages/session_projcache/sessions/<session
  id>.json` per session (3 new files: two top-level sessions and the child). `wipeState` does not remove them and the
  contamination fence does not cover `storages/`. `storages/workspace.json` unchanged.
- Cost: dsh reports none and `prices {}` is empty, so `cost_usd` is null; fill `prices.deepseek-flash` to get one.

## generic (`agents/generic.conf`)

- `command` is an argv template (`{prompt}`, `{model}`, `{repo}`); any other `{name}` is rejected at load (it used to
  reach argv literally). The template owns argv: `{prompt}` becomes one element as written, and the harness adds no `--`.
  A prompt starting with `-` therefore reaches the agent's option parser when `{prompt}` sits where options are parsed;
  put it after a flag that takes a value (`-p {prompt}`, as `agy` does) or after a `--` in the template. `transcript = none` is the only mode. The transcript is empty
  (`resultFromExit`: Score takes the result from run.json `exit_code == 0`, so a crashed run reports `no result event`), so the
  diff alone is scored: turns, tokens and cost are null.
- Unchecked, not clean: with no tool calls nothing can be checked, so `score.json` has `contaminated`, `contamination`,
  `gate_blocks` and `env_leaks` (only these runs carry it) as null. `results.csv` writes `unchecked` in those columns and
  in `gate_blocks_total`; `summary.md` adds a `contamination unchecked (no transcript)` row and a gate-blocks note when
  such a run exists (GA-shaped roots: no change). The runs still count in the headline, scored on the diff. `target.repo`
  (HEAD, the solution) is not locked during a run (GA parity), so a generic agent that reads it goes undetected.

## Gemini via agy (generic adapter, `experiments/live-smoke.conf` `agents.agy`) - run live 2026-10-12

- `agy` = Google Antigravity CLI 1.3.3 (`~/.local/bin/agy`), the user's subscription login. Headless:
  `agy -p <prompt> --model <m> --dangerously-skip-permissions --print-timeout 20m` (default timeout 5m), cwd = run copy.
  Models from `agy models` (e.g. `gemini-3.1-pro-high`, `gemini-3.8-flash-*`). stdout is plain text (the final answer); the
  generic adapter keeps it as `transcript.jsonl` but scores the diff only: turns, tokens and cost are null; cost is billed to
  the subscription and is not knowable from the run.
- State written outside the run copy (find -newer, read-only, one run): only under `~/.gemini/antigravity-cli/`
  (`brain/`, `conversations/`, `conversation_summaries.db`, `mcp/<server>/` tool listings, `builtin/`, `cache/`, `log/`,
  `presence/`, `implicit/`, `annotations/`, `updater/`). Nothing under `~/.gemini/config/`, `~/.gemini/settings.json` or
  `~/Library/Application Support/Antigravity`. The harness neither wipes nor fences this state (generic: no
  `privateStateDirs`), so a later run could read an earlier run's conversations there.
- Global config loads: MCP servers from `~/.gemini/config/mcp_config.json` (it refreshed `mcp/scala-semantic` and
  `mcp/playwright`), and the user's plugins under `~/.gemini/config/plugins` (superpowers): in the live run the agent
  answered "This task looks **bounded** ... I'll present a short design here for your approval before writing any code",
  ended with "Does this approach look good to proceed?" and changed nothing (superpowers brainstorming), exit 0.
  `--disable-slash-commands` ("disable slash command and skill expansion in print mode") fixes it: `live-smoke.conf` passes
  it since 2026-10-12, and agy-u rep 2 ran with it and solved fr-pan (754 s).

