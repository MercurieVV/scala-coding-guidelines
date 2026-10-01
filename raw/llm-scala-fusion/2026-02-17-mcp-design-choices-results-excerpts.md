# From Tool Orchestration to Code Execution: A Study of MCP Design Choices — results excerpts

> Source: https://arxiv.org/html/2602.15945
> Collected: 2026-10-01
> Published: 2026-02-17

Full-text excerpts (Section 6.2) of Felendler, Gandhi, Habler, Elovici, Shabtai. The abstract is in
`2026-02-17-mcp-design-choices-tool-orchestration-vs-code-execution.md`.

"The token savings achieved with the CE-MCP are substantial and increase with task complexity, particularly for two- and three-server tasks."

"The turn counts show the clearest differentiation between the two architectures: The MCP often requires dozens of turns due to its reasoning–tool–reasoning loop, whereas the CE-MCP aggregates most tasks into a single execution turn, reflecting a substantial reduction in execution fragmentation."

"However, the CE-MCP presents a higher number of latency outliers."

"However, for a subset of three-server tasks, the CE-MCP obtains a lower task fulfillment score."

"Examination of these cases shows that failures typically arise from incorrect global orchestration decisions made during code synthesis (e.g., missing a conditional branch), which the MCP may recover through incremental reasoning and retries."
