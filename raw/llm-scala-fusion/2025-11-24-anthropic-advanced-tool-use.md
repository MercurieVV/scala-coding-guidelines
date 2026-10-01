# Introducing advanced tool use on the Claude Developer Platform

> Source: https://www.anthropic.com/engineering/advanced-tool-use
> Collected: 2026-10-01
> Published: 2025-11-24

Anthropic engineering blog. Three beta features: Tool Search Tool, Programmatic Tool Calling, Tool Use Examples.

## Excerpts

Tool Search Tool: "Traditional approach: All tool definitions loaded upfront (~72K tokens for 50+ MCP tools) ... With the Tool Search Tool: Only the Tool Search Tool loaded upfront (~500 tokens) Tools discovered on-demand as needed (3-5 relevant tools, ~3K tokens) Total context consumption: ~8.7K tokens, preserving 95% of context window This represents an 85% reduction in token usage while maintaining access to your full tool library."

"Opus 4 improved from 49% to 74%, and Opus 4.5 improved from 79.5% to 88.1% with Tool Search Tool enabled."

Tool Search Tool — "Use it when: Tool definitions consuming >10K tokens Experiencing tool selection accuracy issues Building MCP-powered systems with multiple servers 10+ tools available Less beneficial when: Small tool library (<10 tools) All tools used frequently in every session Tool definitions are compact"

Programmatic Tool Calling: "Average usage dropped from 43,588 to 27,297 tokens, a 37% reduction on complex research tasks."

"Internal knowledge retrieval improved from 25.6% to 28.5%; GIA benchmarks from 46.5% to 51.2%."

Programmatic Tool Calling — "Most beneficial when: Processing large datasets where you only need aggregates or summaries Running multi-step workflows with three or more dependent tool calls Filtering, sorting, or transforming tool results before Claude sees them Handling tasks where intermediate data shouldn't influence Claude's reasoning Running parallel operations across many items (checking 50 endpoints, for example) Less beneficial when: Making simple single-tool invocations Working on tasks where Claude should see and reason about all intermediate results Running quick lookups with small responses"
