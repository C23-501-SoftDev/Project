# OpenSpec quick setup (new machine)

1. Install prerequisites:
   - Git
   - Node.js 18+
2. Install OpenSpec CLI:
   - `npm install -g @fission-ai/openspec@latest`
3. Clone repos so they are side-by-side:
   - `D:\Soft-dev\Project`
   - `D:\Soft-dev\Docs`
4. Verify layout:
   - `D:\Soft-dev\Project\openspec\...`
   - `D:\Soft-dev\Docs\documents\...`

## Daily usage

1. In `Project`, choose `featureId` from `openspec/feature-registry.json`.
2. Start change with OpenSpec (`/opsx:propose` in agent).
3. Implement (`/opsx:apply`).
4. Run quality gates from `openspec/quality-gates.md`.
5. Archive (`/opsx:archive`) — this updates `openspec/feature-registry.json`.

## Supported agents

- Cursor (`.cursor/`)
- Continue (`.continue/`)
- Kilo Code (`.kilocode/`)
