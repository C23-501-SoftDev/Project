## 1. Export implementation
- [x] 1.1 Implement authorized bounded local attachment resolution and decoded-image budgets.
- [x] 1.2 Embed validated PNG/JPEG in HTML, PDF and DOCX while preserving structure.
- [x] 1.3 Add a complete offline ZIP archive with safe names, relative links and manifest.
- [x] 1.4 Expose ZIP export in document and shared menus with controlled error messages.
## 2. Verification
- [x] 2.1 Test authorization, foreign references, missing files, symlinks, image limits and no external requests.
- [ ] 2.2 Verify PDF/DOCX pictures and offline ZIP rendering with real uploaded fixtures.
- [ ] 2.3 Проверка критериев качества: [quality gates](../../quality-gates.md), all Java, Node and Playwright tests; PostgreSQL/pgvector tests included.
## 3. Documentation
- [x] 3.1 Обновить BACKTRACKER.md — актуализировать затронутые эндпоинты и их статусы.
- [ ] 3.2 Document supported formats, limits and fallback behavior; update registry and project overview.
- [ ] 3.3 Synchronize and archive the completed specification.

Focused verification: 28/28 Java tests passed (17 export service, 2 storage confinement, 6 representation/ACL, 3 HTML/resource safety). Rendered PDF visually checked; full regression and real offline browser fixtures remain below.
