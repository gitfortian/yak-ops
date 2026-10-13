package io.yak.ops.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

/** Verifies immutable, exact-match SPI registration without a shared global registry. */
class SpiRegistryTest {

    private record Provider(String type, List<String> aliases) {}

    @Test
    void registersCanonicalTypesAndAliases() {
        Provider mysql = new Provider("MYSQL", List.of());
        Provider postgres = new Provider("POSTGRE_SQL", List.of("POSTGRES", "POSTGRESQL"));
        SpiRegistry<Provider> registry = SpiRegistry.of(List.of(mysql, postgres), SpiRegistryTest::identifiers);

        assertSame(mysql, registry.get("MYSQL"));
        assertSame(postgres, registry.get("POSTGRE_SQL"));
        assertSame(postgres, registry.get("POSTGRES"));
        assertEquals(List.of("MYSQL", "POSTGRE_SQL", "POSTGRES", "POSTGRESQL"), List.copyOf(registry.types()));
        assertThrows(NoSuchElementException.class, () -> registry.get("postgres"));
        assertThrows(UnsupportedOperationException.class, () -> registry.types().add("ORACLE"));
    }

    @Test
    void rejectsDuplicateNamesAndAliases() {
        Provider mysql = new Provider("MYSQL", List.of("DATABASE"));
        Provider oracle = new Provider("ORACLE", List.of("DATABASE"));
        assertThrows(
                IllegalStateException.class,
                () -> SpiRegistry.of(List.of(mysql, oracle), SpiRegistryTest::identifiers));
        assertThrows(
                IllegalStateException.class,
                () -> SpiRegistry.of(List.of(mysql, mysql), SpiRegistryTest::identifiers));
    }

    @Test
    void rejectsMissingProvidersAndIdentifiers() {
        assertThrows(
                IllegalStateException.class,
                () -> SpiRegistry.of(java.util.Collections.singletonList(null), SpiRegistryTest::identifiers));
        assertThrows(
                IllegalStateException.class,
                () -> SpiRegistry.of(List.of(new Provider("", List.of())), SpiRegistryTest::identifiers));
        assertThrows(
                IllegalStateException.class,
                () -> SpiRegistry.of(List.of(new Provider("MYSQL", List.of())), provider -> List.of()));
    }

    @Test
    void createsIndependentRegistriesFromExplicitProviders() {
        Provider mysql = new Provider("MYSQL", List.of());
        Provider oracle = new Provider("ORACLE", List.of());

        SpiRegistry<Provider> first = SpiRegistry.of(List.of(mysql), SpiRegistryTest::identifiers);
        SpiRegistry<Provider> second = SpiRegistry.of(List.of(oracle), SpiRegistryTest::identifiers);

        assertSame(mysql, first.get("MYSQL"));
        assertSame(oracle, second.get("ORACLE"));
        assertThrows(NoSuchElementException.class, () -> first.get("ORACLE"));
    }

    private static Collection<String> identifiers(Provider provider) {
        List<String> values = new ArrayList<>();
        values.add(provider.type());
        values.addAll(provider.aliases());
        return values;
    }
}
