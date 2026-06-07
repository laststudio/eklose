# AGENTS.md instructions for D:\Users\34647\Desktop\myProject\eklose

## CodeGraph

This project has a CodeGraph MCP server (`codegraph_*` tools) configured. Use CodeGraph for structural code questions such as symbol lookup, callers/callees, flow tracing, impact analysis, and focused code context. Use native text search only for literal strings or after a specific file is already known.

If `.codegraph/` does not exist or CodeGraph reports that the project is not initialized, ask before initializing it.

## Local Tooling

- For workspace file reads/writes, prefer the filesystem MCP service.
- For shell commands in this environment, use the `rtk` prefix.
- Do not guess ambiguous user intent. If a change could reasonably be interpreted in multiple ways and the choice affects behavior or UI, ask first.

## UI Hard Constraint

When building or modifying the Android UI, visible UI and interaction controls must use miuix components whenever miuix provides an equivalent.

This includes, but is not limited to:

- Theme and colors: `MiuixTheme` and miuix color tokens.
- Page structure: miuix `Scaffold`, `Surface`, `TopAppBar` / `SmallTopAppBar`.
- Navigation: miuix navigation components.
- Rows and settings items: miuix `BasicComponent` or the closest miuix extension component.
- Inputs, buttons, switches, sliders, dropdowns, popups, dividers, text, icons, progress indicators, and similar visible controls: miuix components.

Do not use Material3 or hand-built Compose replacements for visible controls when miuix has a suitable component.

Allowed exceptions:

- Compose foundation/layout primitives such as `Row`, `Column`, `Box`, `LazyColumn`, `Spacer`, `PaddingValues`, and `Modifier` may be used for layout because miuix itself is built on these primitives.
- If miuix does not provide a required component or behavior, use Compose foundation/custom code only for that missing part, keep it visually wrapped in miuix `Surface`/theme tokens when possible, and keep the custom code narrowly scoped.
