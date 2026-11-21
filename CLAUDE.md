# Working rules for Claude Code

## Source of truth
- `DESIGN.md` defines what to build. Follow it exactly.
- If something in DESIGN.md looks wrong, unclear, or missing, **stop and ask**. Do not
  silently change the design.
- Build only what the current step asks for. No extra features, frameworks, or dependencies.

## Tech
- Java 17 language level (`maven.compiler.release` = 17), even though the local JDK is newer.
  Do not use APIs newer than Java 17 (e.g. no `SequencedCollection.removeFirst`).
- Maven with the Maven Wrapper. JUnit 5 only. No Mockito, no Spring, no Lombok.
- Package root: `com.courseseat`. Layout as in DESIGN.md section 12.
- OS is Windows: run tests with `mvnw.cmd test`.

## Code style
- Records for model and result types; enums for statuses.
- Business outcomes are returned as results, never thrown.
- Getters return immutable copies (`List.copyOf`).
- Small, readable methods. Short comments only where the reason is not obvious
  (e.g. why promotion happens inside `drop`).

## Workflow for every step
1. Restate in 2–3 lines what you will change, and which files.
2. Make the change, with its tests.
3. Run `mvnw.cmd test` and show the result.
4. Show the diff (`git status` + `git diff`) and **stop**.
5. **Never commit or push** unless I say "commit". Use the commit message I give you.

## Tests
- Every test asserts both the returned result and the resulting state
  (`getEnrolled()`, `getWaitlist()`).
- Use the test names listed in DESIGN.md section 10.
- Concurrency tests use one shared service instance, an `ExecutorService`, and a
  `CountDownLatch` start gate.

## Never
- Never put credentials, tokens, or personal data in code, tests, or commit messages.
- Never force-push or rewrite git history.
