# AGENTS.md instructions for D:\Users\34647\Desktop\myProject\eklose

## CodeGraph

This project has a CodeGraph MCP server (`codegraph_*` tools) configured. Use CodeGraph for structural code questions such as symbol lookup, callers/callees, flow tracing, impact analysis, and focused code context. Use native text search only for literal strings or after a specific file is already known.

If `.codegraph/` does not exist or CodeGraph reports that the project is not initialized, ask before initializing it.

## Local Tooling

- For workspace file reads/writes, prefer the filesystem MCP service.
- For shell commands in this environment, use the `rtk` prefix.
- Do not guess ambiguous user intent. If a change could reasonably be interpreted in multiple ways and the choice affects behavior or UI, ask first.

## Ekwing Exam Reading Hard Constraint

- In this project, the UI labels that may be described as current/history homework are actually learning-center exam tasks. Keep them mapped to the exam flows: current tasks come from `/student/Hw/getnewmainlist` or `/student/Hw/getbasicnewmainlist` filtered with `type=exam`; historical tasks come from `/student/exam/getstuexamlist` or `/student/exam/getbasicstuexamlist`.
- When changing learning-center exam question download or answer parsing, use `D:\Users\34647\Desktop\myProject\ekwing_get_answer\release.py` as the source of truth. The aligned exam detail flow is `getstuexamitem` for exam item data, `getscoreinfo`/`getbasicscoreinfo` for score info, then `getmodelscoreinfo` for model score data.
- `getscoreinfo` is POST, but `getmodelscoreinfo` must be fetched like `release.py` does through the score-page GET path with query parameters. Do not replace the exam model-score answer flow with normal homework answer APIs.
- Correct exam answers should be parsed from `model_score_infos`/`model_info` standard answer fields first. Student answer fields such as `user_ans`, `user_answer`, `hypothesis`, scores, audio, and pronunciation details are result metadata and must not be treated as the standard answer unless the Python source of truth changes.

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
