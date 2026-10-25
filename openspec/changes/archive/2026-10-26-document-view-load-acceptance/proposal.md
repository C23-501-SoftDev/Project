## Why
The previous US4.4.1 benchmark measured one user on loopback and did not establish performance under concurrent load. The user selected 25 active users as the normal acceptance profile on 2026-10-26. Feature ID: `document-view-load-acceptance`.

Input: [US4.4.1 backlog description](https://github.com/C23-501-SoftDev/Docs/tree/main/documents/backlog-descriptions/E4%20Администрирование%20и%20Безопасность%20(Admin%20%26%20Security)/F4.4%20Производительность%20системы/US4.4.1%20Оптимизация%20времени%20отклика%20при%20просмотре), [base requirements](https://github.com/C23-501-SoftDev/Docs/blob/main/base-requirements.md).
## What Changes
- Add reproducible isolated fixtures and concurrent read workloads with real authenticated users.
- Measure document readiness in a browser with network latency, distributed documents and one large space.
- Record normal acceptance and a separate 50-user stress profile, raw evidence and environmental limits.
- Fix bottlenecks demonstrated by the acceptance run if required; preserve authorization.
## Capabilities
### New Capabilities
- `document-view-load-acceptance`: measurable document-view acceptance for the agreed concurrency and dataset.
### Modified Capabilities
Existing API and authorization contracts are preserved; the internal tree renderer is optimized after the measured large-space failure.
## Impact
Performance tooling, test data, documentation and potentially document-read paths. No production data is changed. RAG inference is outside this document-view benchmark.
## Out of scope
Production infrastructure certification, unlimited scale, RAG-generation throughput and a universal latency guarantee.
