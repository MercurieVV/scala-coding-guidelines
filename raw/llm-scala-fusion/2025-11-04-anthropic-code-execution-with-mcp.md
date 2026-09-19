# Code execution with MCP: Building more efficient agents

> Source: https://www.anthropic.com/engineering/code-execution-with-mcp
> Collected: 2026-09-19
> Published: 2025-11-04

Anthropic engineering blog.

## Section headings

- Excessive token consumption from tools makes agents less efficient
- Code execution with MCP improves context efficiency
- Benefits of code execution with MCP

## Key content (extracted)

Two main inefficiencies emerge when agents rely on direct tool calling:

1. Tool definitions overload context. All tool definitions load upfront into the context window. With thousands of tools across multiple servers, agents must process hundreds of thousands of tokens before even reading the user request.
2. Intermediate results duplicate tokens. When agents call tools sequentially, every result passes through the model's context. In the document example: "the full call transcript flows through twice. For a 2-hour sales meeting, that could mean processing an additional 50,000 tokens."

By presenting MCP servers as code APIs instead of direct tool calls, agents write a program that calls the tools; in the worked example: "This reduces the token usage from 150,000 tokens to 2,000 tokens—a time and cost saving of 98.7%."

"agents can load only the tools they need and process data in the execution environment before passing results back to the model."
