package io.yak.ops.connector.base.factory;

import io.yak.ops.configuration.rule.OptionRule;

/** SPI contract declaring an independent Connector and its configuration requirements. */
public interface Factory {

    /** Persistent connector identifier used to select this provider. */
    String factoryIdentifier();

    /** Declares options accepted by this Connector. */
    OptionRule optionRule();
}
