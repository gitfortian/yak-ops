package io.yak.ops.testutils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;

class DatabaseExtensionTest {
    @Test
    void rootStoreSharesContainerAndClosesItOnce() throws Throwable {
        GenericContainer<?> container = mock(GenericContainer.class);
        when(container.getLogs()).thenReturn("test database log");
        Map<Object, Object> resources = new HashMap<>();
        ExtensionContext context = context(resources);
        var first = extension(container, "reuse");
        var second = extension(container, "reuse");
        first.beforeAll(context);
        second.beforeAll(context);
        assertSame(first.metadata(), second.metadata());
        verify(container, times(1)).start();
        for (Object resource : resources.values()) ((ExtensionContext.Store.CloseableResource) resource).close();
        verify(container, times(1)).stop();
    }

    @Test
    void startupFailureClosesPartialContainerAndDoesNotEnterTheStore() {
        GenericContainer<?> container = mock(GenericContainer.class);
        when(container.getLogs()).thenReturn("startup failed");
        doThrow(new IllegalStateException("injected startup failure"))
                .when(container)
                .start();
        Map<Object, Object> resources = new HashMap<>();
        assertThrows(
                IllegalStateException.class,
                () -> extension(container, "failure").beforeAll(context(resources)));
        verify(container).stop();
        assertEquals(0, resources.size());
    }

    private DatabaseExtension extension(GenericContainer<?> container, String name) {
        return new DatabaseExtension(name, true) {
            @Override
            protected GenericContainer<?> createContainer() {
                return container;
            }

            @Override
            protected DatabaseMetadata describe(GenericContainer<?> started) {
                return new DatabaseMetadata("jdbc:h2:mem:" + name, "sa", "", "localhost", 0);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private ExtensionContext context(Map<Object, Object> resources) {
        ExtensionContext context = mock(ExtensionContext.class);
        ExtensionContext.Store store = mock(ExtensionContext.Store.class);
        when(context.getRoot()).thenReturn(context);
        when(context.getStore(any())).thenReturn(store);
        when(context.getRequiredTestClass()).thenReturn((Class) DatabaseExtensionTest.class);
        doAnswer(call -> resources.computeIfAbsent(
                        call.getArgument(0), key -> ((Function<Object, Object>) call.getArgument(1)).apply(key)))
                .when(store)
                .getOrComputeIfAbsent(any(), any(), any());
        return context;
    }
}
