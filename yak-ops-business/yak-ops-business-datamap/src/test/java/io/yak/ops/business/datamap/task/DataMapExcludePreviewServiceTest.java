package io.yak.ops.business.datamap.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datamap.config.DataMapProperties;
import io.yak.ops.business.datamap.task.impl.DataMapExcludePreviewServiceImpl;
import io.yak.ops.common.bean.dto.datamap.DataMapCrawlExcludePreviewDTO;
import io.yak.ops.common.enums.datamap.DataMapErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceInfo;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Verifies preview refuses invalid expressions and unsupported engines before connecting. */
class DataMapExcludePreviewServiceTest {

    @Mock
    private DataSourceLookupService dataSourceLookup;

    private DataMapExcludePreviewServiceImpl service;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        service = new DataMapExcludePreviewServiceImpl(dataSourceLookup, new DataMapProperties());
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void anUncompilableExpressionIsRefusedBeforeTheDatasourceIsResolved() {
        DataMapCrawlExcludePreviewDTO input = input("^tmp_[a-");

        BusinessException failure = assertThrows(BusinessException.class, () -> service.preview("ws", input));

        assertEquals(DataMapErrorCode.CRAWL_PATTERN_INVALID, failure.getErrorCode());
        verify(dataSourceLookup, never()).dataSource("ws", "ds");
    }

    @Test
    void anEngineWithoutAServerSideRegexQueryIsRefusedBeforeConnecting() {
        when(dataSourceLookup.dataSource("ws", "ds")).thenReturn(new DataSourceInfo("DORIS", "demo", null, 1));

        BusinessException failure =
                assertThrows(BusinessException.class, () -> service.preview("ws", input("^tmp_")));

        assertEquals(DataMapErrorCode.CRAWL_PREVIEW_UNSUPPORTED, failure.getErrorCode());
        verify(dataSourceLookup, never()).connectionOptions("ws", "ds");
    }

    private static DataMapCrawlExcludePreviewDTO input(String regex) {
        DataMapCrawlExcludePreviewDTO input = new DataMapCrawlExcludePreviewDTO();
        input.setDatasourceId("ds");
        input.setDatabase("demo");
        input.setRegex(regex);
        return input;
    }
}
