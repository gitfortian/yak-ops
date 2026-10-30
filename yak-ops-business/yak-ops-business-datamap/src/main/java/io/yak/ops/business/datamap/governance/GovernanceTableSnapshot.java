package io.yak.ops.business.datamap.governance;

/**
 * Immutable view of one ACTIVE catalog table for the governance rules.
 *
 * <p>Carries no credentials and no data values: only the curated owner and description, and the
 * observed table comment.
 *
 * @param id asset identity
 * @param displayName schema-qualified name
 * @param description curated business description
 * @param remarks engine-reported table comment
 * @param ownerId curated accountable member, blank when unassigned
 */
public record GovernanceTableSnapshot(
        String id, String displayName, String description, String remarks, String ownerId) {}
