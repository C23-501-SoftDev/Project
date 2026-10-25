## 1. Safe HTML pipeline
- [x] 1.1 Sanitize server and browser rendering, style standalone exports and block external PDF resources.
- [x] 1.2 Add meaningful malicious-markup and formatting coverage; update BACKTRACKER.md with export behavior.
- [x] 1.3 Проверка критериев качества: [quality gates](../../../quality-gates.md); full Java, JavaScript and browser suites.

## Validation evidence
2026-10-25: `mvn clean verify` — **117 passed, 0 failures/errors/skips**, JAR built; JavaScript test passed; full Playwright — **144 passed, 0 failed, 0 skipped, 0 retries**. Regression test confirms zero outbound image requests from PDF conversion; browser test confirms no script execution in view, preview or downloaded HTML.
