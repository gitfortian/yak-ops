package io.yak.ops.business.task.definition;

import io.yak.ops.dao.entity.task.DefinitionEntity;

/** Publishes a previously validated Task Definition version in one short transaction. */
public interface PublicationService {

    /**
     * Atomically publishes the expected Definition version.
     *
     * @param id stable Definition identifier
     * @param expectedVersion version validated before entering the transaction
     * @param operatorId publishing operator
     * @return published Definition identity and status
     */
    DefinitionEntity publish(String id, int expectedVersion, String operatorId);
}
