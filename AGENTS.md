# Agent guidelines

## Documentation

- Do **not** create overview, summary, architecture, design or "notes" documents (e.g. `ARCHITECTURE.md`,
  `SUMMARY.md`, `IMPLEMENTATION.md`, `CHANGES.md`). Explain your work in the conversation or PR description instead.
- Allowed documentation:
  - short code comments where the code is not self-explanatory (one or two lines, no essays),
  - KDoc on public API,
  - user-facing docs: `readme.md` and the Writerside topics in `docs/Writerside`.
- When behaviour visible to library users changes, update the matching Writerside topic.

## Project

- Kotlin Multiplatform library, modules live in `packages/*` (`core`, `smtp`, `jmap`, `html`).
- `core` must stay dependency-light and compile for every target; protocol code belongs in `smtp` / `jmap`.
- Public API uses explicit API mode. After changing it run `./gradlew apiDump` and commit the `api/` files.

## Before finishing

- `./gradlew check` must pass (tests, detekt, `apiCheck`).
- New behaviour needs tests, preferably in `commonTest`.
