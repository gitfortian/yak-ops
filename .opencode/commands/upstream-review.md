---
description: Compare Yak Ops runtime behavior with a specified upstream source
agent: plan
---

Compare $ARGUMENTS against the **specified version** of Apache Flink, Flink CDC, or SeaTunnel. Do not edit files or open a PR.

1. Identify the exact Yak Ops and upstream versions, packages, classes, and dependencies.
2. Trace both execution paths, including lifecycle, state transitions, algorithms, error handling, and recovery.
3. Classify each difference: aligned, partially aligned, real behavioral gap, or intentional adaptation.
4. Check YakFlow Runtime and Debezium version constraints before recommending direct reuse.
5. Prefer upstream contracts and algorithms when semantics match; never substitute mechanical renames.
6. Cite source files and line numbers for both sides. State what could not be verified.

Conclude with a prioritized, evidence-based shortlist; no speculative redesign.
