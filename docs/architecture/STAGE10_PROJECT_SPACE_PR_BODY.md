# Stage 10 Project Space Architecture Audit

This branch closes the remaining Project Space migration corridors and establishes the terminal isolation contract.

- authenticated business handlers default to Project-required
- intentional public/platform endpoints use an explicit allowlist
- Task Catalog, Lineage, Resource, and Dataset fail closed through trusted CurrentProject
- nullable Project ownership and runtime legacy claim/backfill paths are removed
- terminal database migrations enforce `project_id NOT NULL`
- frontend unknown business routes default to Project-required

Historical environments must complete the earlier Expand/Backfill release and verify zero nullable Project rows before applying the Contract migrations.
