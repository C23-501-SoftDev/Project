# E2E Failure Analysis (Current State)

## Run Context

- Command: `npm test`
- Workspace: `Project/tests/E2E`
- Total: **51**
- Passed: **38**
- Failed: **13**
- Scope included:
  - baseline (`auth/admin/content/assets`),
  - strict acceptance,
  - full user,
  - depth/gaps (`user-depth-negative`, `coverage-gaps`).

## Failure Summary by Category

1. **Known missing frontend implementation (placeholders/WIP)** — 9 failures
2. **API robustness/validation behavior mismatch** — 3 failures
3. **Static assets missing in main layout** — 1 failure

## Detailed Failed Cases

## 1) Missing main layout assets

- **Case:** `@strict layout assets must be available (no 5xx)`
- **Observed:** `/css/main.css` and `/js/main.js` are not OK (5xx)
- **Impact:** User pages using `layout.html` can break style/behavior
- **Severity:** High

## 2) Placeholder user pages (strict + userfull)

- **Cases:**
  - `@strict home page must show real document list`
  - `@userfull U11` (home page)
  - `@userfull U12` (search results)
  - `@userfull U13` (document view)
  - `@userfull U14` (document history)
  - `@userfull U15` (space page)
- **Observed:** Pages still show placeholder texts like:
  - "Список доступных документов будет отображаться здесь."
  - "Результаты поиска будут отображены здесь."
  - "Содержимое документа будет отображаться здесь."
  - "История изменений будет отображаться здесь."
  - "Документы пространства будут отображаться здесь."
- **Impact:** Core user content flows are not functionally implemented
- **Severity:** Critical for user acceptance

## 3) Admin settings still WIP

- **Case:** `@strict admin settings page must be implemented (no WIP message)`
- **Observed:** "Функция в разработке" is displayed
- **Impact:** Admin settings not production-ready
- **Severity:** Medium

## 4) Create page form not implemented as business form

- **Case:** `@strict document create page must contain working form fields`
- **Observed:** only navbar forms are present (search/logout); no dedicated create-document form
- **Impact:** Document creation flow not available in SSR UI
- **Severity:** High

## 5) API robustness mismatches (negative/depth)

- **Case:** `@userfull D5 users sort by unsupported field should fail safely`
  - **Expected by test:** fail with 400/500
  - **Observed:** request succeeds (`200`)
  - **Interpretation:** backend silently tolerates invalid sort field; behavior is permissive rather than strict
  - **Severity:** Low/Medium (depends on API contract policy)

- **Case:** `@userfull D6 rapid duplicate create requests - exactly one succeeds`
  - **Expected:** `201 + 409`
  - **Observed:** `201 + 500`
  - **Interpretation:** race-condition path leaks as internal error instead of clean conflict
  - **Severity:** High

- **Case:** `@userfull D8 assign invalid permission value is rejected`
  - **Test currently expects:** 400/422
  - **Observed in run:** 500
  - **Interpretation:** invalid enum handling may propagate as server error
  - **Severity:** Medium/High

## 6) Pass-through behavior worth clarifying (contract question)

- **Case:** `@userfull D5 users sort by unsupported field should fail safely`
  - Backend currently returns `200` instead of failing.
  - This may be acceptable if contract allows fallback sorting.
  - Decide and document expected behavior:
    - strict validation (4xx), or
    - permissive fallback (200 + default sorting).

## Recommended Fix Order

1. **Fix 5xx for `main.css/main.js`** (fast unblock for user layout stability)
2. **Implement real content pages** (`/`, `/search`, `/documents/{id}`, `/documents/{id}/history`, `/spaces/{id}`)
3. **Harden API error handling**
   - map invalid inputs to 4xx
   - convert race duplicate create to deterministic 409
4. **Replace WIP on admin settings** or explicitly remove from acceptance scope for current release

## Notes

- Playwright traces/screenshots are available in `Project/tests/E2E/test-results`.
- Open HTML report via:

```bash
npm run report
```

- Open any failing trace:

```bash
npx playwright show-trace <path-to-trace.zip>
```

