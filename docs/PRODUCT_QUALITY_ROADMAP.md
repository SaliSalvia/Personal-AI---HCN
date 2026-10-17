# Product Quality Roadmap: SALi-HCNSEC Project Intelligence

## Product goal

Turn SALi-HCNSEC into a trustworthy Android workspace for comparing multiple ZIP snapshots of the same software project, understanding each snapshot, and preparing a reviewed best-of merge. The product must distinguish deterministic facts from AI-generated hypotheses and must never silently modify an uploaded source archive.

## Product principles

1. **Evidence before conclusions.** Every important finding should cite a version, file path, and line/range or a clearly identified extracted document section. If evidence was not inspected, the app must say so.
2. **Compare before synthesizing.** Build a deterministic inventory and version diff first; use AI to explain implications and propose changes, not to invent repository state.
3. **Reviewable merges.** Produce a proposed patch/change set with source provenance, conflicts, and test recommendations. Do not overwrite a source version or claim a merge is buildable until the merged workspace has been validated.
4. **Privacy and explicit consent.** ZIPs remain local by default. Before remote analysis, disclose which selected provider receives which extracted context, and let the user inspect/cancel the request.
5. **Fail visibly and safely.** Corrupt, encrypted, unsupported, truncated, or limit-exceeding archives must be reported as partial/failed analyses—not silently presented as complete.
6. **Recoverable workflows.** Import, indexing, analysis, and merge jobs should expose progress, cancellation, and useful errors. A crash must not leave a database record pointing to missing files.

## Product workflow

1. Import several ZIP versions; assign a display name, optional version label, and notes.
2. Validate each archive, calculate SHA-256, and create a manifest containing normalized path, type, byte size, content hash, and analysis status.
3. Detect project type and entry points from repository files and manifests, not file-extension counts alone.
4. Compare versions: added/removed/renamed/changed files, duplicate content, dependency/build configuration changes, public API changes where supported, and suspicious or risky changes.
5. Analyze each version independently, then compare findings across versions. Separate confirmed facts, likely implications, and uncertain hypotheses.
6. Build a feature matrix with evidence: capability, source version, relevant files, trade-offs, dependencies, test coverage, security impact, and compatibility.
7. Generate a merge proposal with a file-by-file plan, conflict explanations, source provenance, and a patch preview.
8. Apply the proposal only to a new derived workspace after explicit user approval. Preserve every original ZIP and version.
9. Run available checks (tests/build/static checks) in an isolated environment where feasible. Report exactly what ran, what passed, what failed, and what could not be tested.

## Engineering workstreams

### P0 — Trust, safety, and correctness
- Harden ZIP ingestion against path traversal, duplicate/colliding paths, malformed entries, oversized entries/archives, excessive entry counts, extreme compression ratios, and nested-archive recursion.
- Enforce limits while streaming actual decompressed bytes; do not rely only on ZIP metadata.
- Keep extraction and archive persistence atomic: remove partial files on failure, close caller-owned streams consistently, and avoid orphaned workspace files/database rows.
- Verify hashes and detect duplicate uploads without confusing identical content with identical project identity.
- Add adversarial archive tests and lifecycle tests.

### P1 — Accurate project understanding
- Build a persistent file manifest with SHA-256 per file and deterministic version-to-version diffs.
- Identify ignored/generated/vendor files using configurable rules and explain exclusions.
- Improve source extraction with size/encoding/binary detection and format-aware parsing for supported formats.
- Rank context by structural importance, diff status, query relevance, and dependency relationships rather than taking the first matching chunks.
- Track context coverage and token/character budgets; disclose omitted files and reasons.
- Add citations/provenance to findings and preserve version-specific paths.

### P2 — Version comparison and feature synthesis
- Add a version comparison screen and file-level diff viewer.
- Create an evidence-backed feature matrix and explain trade-offs rather than declaring a winner without criteria.
- Generate a merge plan, conflict list, and patch preview.
- Apply selected changes to a derived workspace only; preserve originals and record provenance.
- Add a clear distinction between suggested, applied, and validated changes.

### P3 — Product quality and release readiness
- Add meaningful loading, empty, error, cancellation, retry, and partial-result states.
- Add accessibility labels, large-text checks, RTL checks, and UI regression screenshots.
- Add unit tests for archive limits, diff logic, ranking, merge planning, and failure recovery.
- Add CI gates for tests, lint/static analysis, dependency/security review, APK verification, and artifact provenance.
- Review signing/release identity: production updates must use a stable protected signing key; never publish a newly generated ephemeral key as if it were a stable production release.
- Provide privacy disclosures, data-retention controls, export/delete controls, license notices, and a reproducible release checklist.

## Acceptance criteria

- Each finding links to evidence in a specific imported version, or is explicitly labelled as an inference.
- Identical files across versions are detected by content hash; line-level diffs are deterministic.
- Failed imports leave neither a visible workspace nor partial archive/extraction files.
- Archive safety tests cover traversal paths, duplicate names, corrupt ZIPs, oversized entries, total-size limits, entry-count limits, and cancellation.
- Analysis explains its coverage: versions and files inspected, excluded files, limits reached, provider used, and whether any build/tests actually ran.
- A merge always targets a new derived workspace, shows the proposed changes before applying them, and never deletes or rewrites original versions.
- Release claims are backed by CI output; no “zero-bug” or “fully tested” claims without evidence.

## Initial repository observations

The current code already has a ZIP workspace manager, SHA-256 archive fingerprinting, multiple workspace IDs, basic archive limits, project-type heuristics, and a multi-version context path. The next implementation pass should inspect and strengthen the existing manager and retrieval logic rather than replace the architecture wholesale. In particular, the current workflow needs explicit compression-ratio/duplicate-entry handling, durable per-file manifests and deterministic diffs, evidence-aware analysis output, and a reviewable merge pipeline. Actual build/test status must be verified by CI before release readiness is claimed.


## Project Lab implementation update (2026-10-17)

The next increment adds a multi-select ZIP workflow capped at 20 project candidates, imports them sequentially to avoid saturating mobile CPU/storage, and preserves successful ZIP candidates in the same chat after each answer so the user can run staged analysis without re-uploading. The aggregate archive-plus-extracted workspace budget is capped at 1 GiB; per-archive safety limits still apply.

The chat menu exposes six editable, user-triggered stages: (1) inventory and evidence, (2) version scorecard, (3) feature/compatibility matrix, (4) golden architecture, (5) reviewable merge plan, and (6) validation/release gate. These prompts are analysis/planning steps, not claims that files were modified. The multi-version context builder now includes a compact manifest for every candidate first and then budgets source excerpts fairly per candidate, preventing early archives from consuming all available context.

Remaining work after this increment: durable project-lab sessions across app restarts, a deterministic all-version file/feature matrix, reviewable patch generation and provenance, applying approved changes into a separate derived workspace, and validating the full workflow with CI and real-device tests.
