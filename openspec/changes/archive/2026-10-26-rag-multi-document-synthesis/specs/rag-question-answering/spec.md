## MODIFIED Requirements

### Requirement: Measurable acceptance
Before enabling RAG, the implementation SHALL pass an ACL/leakage suite and a labelled retrieval/answer evaluation set with documented thresholds and model versions. The local release evaluation SHALL retain at least 90% expected-source recall@5 on the original single-source questions. At least 90% substantive answers with sources and at least 90% expected-source citations SHALL be required both for the original single-source subset and for all answerable questions, so new cases cannot conceal regression on the original set. All entirely unsupported questions SHALL refuse, with zero invalid citations or detected injection markers. Multi-article questions SHALL additionally satisfy the separate cross-article acceptance requirement.
#### Scenario: Release evaluation
- **WHEN** a candidate RAG version is evaluated
- **THEN** source retrieval quality, citation validity, denied-access cases, unanswerable cases, latency and cost measurements are recorded

## ADDED Requirements

### Requirement: Bounded cross-article evidence selection
Retrieval SHALL support a configurable per-document chunk cap applied before the global candidate cutoff. Defaults SHALL select at most eight chunks with at most two chunks per document, subject to the existing relevance threshold, character budget, authorization and version checks.
#### Scenario: One article has many highly ranked chunks
- **WHEN** one eligible article has six highly ranked chunks and another eligible article has a relevant chunk
- **THEN** the first article cannot consume more than the configured per-document cap and the second article remains eligible for the remaining slots
#### Scenario: Broader retrieval contains a revoked source
- **WHEN** any selected article loses authorization during generation
- **THEN** the whole generated answer is discarded under the existing source-change contract

### Requirement: Multi-article synthesis and explicit evidence gaps
The assistant SHALL be instructed to combine and compare facts from multiple retrieved articles and cite every source used. It MUST NOT require the complete answer to occur in one article. A useful supported portion SHALL be returned with explicit gaps; an entirely unsupported question SHALL still refuse. Unsupported causal relationships or transfers of rules between unrelated entities MUST NOT be invented.
#### Scenario: Answer requires two articles
- **WHEN** separate retrieved articles support different requested parts of an answer
- **THEN** the answer combines those parts and cites both articles
#### Scenario: Part of a question is unsupported
- **WHEN** retrieved sources support a useful part but do not establish another requested detail
- **THEN** the answer gives the supported information, identifies the unknown detail and cites only sources actually used
#### Scenario: Sources disagree
- **WHEN** relevant retrieved sources give conflicting facts without a supported resolution
- **THEN** the answer identifies the conflict and cites both sources rather than guessing which is correct

### Requirement: Separate cross-article acceptance
The release evaluation SHALL retain the original single-source and unsupported-question regression set and include at least eight labelled questions requiring two or three articles. At least 90% of the multi-article cases SHALL retrieve all expected articles and return all required citations and factual groups. Every HTTP response and citation provenance check SHALL pass; every entirely unsupported case SHALL refuse, and no injection marker SHALL appear. Reports MUST record retrieval settings, real model versions, answers and latency, with manual inspection of synthesis and evidence gaps.
#### Scenario: Partial citation set cannot pass a complete-answer check
- **WHEN** a question requires two articles but the answer cites only one or omits a required fact
- **THEN** that case fails the multi-article completeness gate even if the response is otherwise valid
