package io.yak.ops.common.bean.vo.modeling;

import io.yak.ops.common.enums.modeling.dimension.DimensionScope;

/**
 * Authorized concept dimension projection.
 *
 * @param id dimension identity
 * @param scope owning layer: public binds a data domain, application binds a data mart
 * @param domainId owning data domain identity, null for application scope
 * @param domainName current data domain name for display, null outside public scope
 * @param datamartId owning data mart identity, null for public scope
 * @param datamartName current data mart name for display, null outside application scope
 * @param name business display name
 * @param enName business English name
 * @param code abbreviation unique inside its owning scope
 * @param description optional business description
 * @param revision current optimistic version
 */
public record DimensionVO(
        String id,
        DimensionScope scope,
        String domainId,
        String domainName,
        String datamartId,
        String datamartName,
        String name,
        String enName,
        String code,
        String description,
        long revision) {}
