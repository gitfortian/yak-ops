package io.yak.ops.business.quality.repository;

/**
 * Transitional aggregate repository contract.
 *
 * <p>Business roles should migrate to the narrow repository ports. This aggregate remains during
 * the stage-1 cutover so each role can be moved independently without changing persistence behavior.
 */
public interface QualityRepository extends
    QualityTemplateRepository,
    QualityTableAssetRepository,
    QualityMonitorRepository,
    QualityExecutionRepository,
    QualityAlertRepository {}
