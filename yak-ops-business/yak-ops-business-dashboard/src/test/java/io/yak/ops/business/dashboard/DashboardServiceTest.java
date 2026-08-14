package io.yak.ops.business.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.analysis.AnalysisService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {

  @Test
  void createPersistsV1AndValidatesAnalysisReference() {
    FakeRepository repository = new FakeRepository();
    AnalysisService analysisService = mock(AnalysisService.class);
    DashboardService service = new DashboardService(repository, analysisService, new ObjectMapper());

    DashboardDetail detail = service.create(new DashboardService.SaveCommand(
        "销售驾驶舱", "核心销售分析", 12L,
        List.of(new DashboardService.WidgetSpec("w1", 99L, null, null, 0, 0, 10, 7, 6, 5))));

    assertEquals(1, detail.dashboard().currentVersionNo());
    assertEquals(1, detail.versions().size());
    assertEquals(1, detail.widgets().size());
    verify(analysisService).get(99L);
  }

  @Test
  void manualSaveCreatesImmutableNextVersion() {
    FakeRepository repository = new FakeRepository();
    DashboardService service = new DashboardService(repository, mock(AnalysisService.class), new ObjectMapper());
    DashboardDetail created = service.create(new DashboardService.SaveCommand(
        "A", null, null,
        List.of(new DashboardService.WidgetSpec("w1", null, "临时", Map.of("type", "bar"), 0, 0, 10, 7, 6, 5))));

    DashboardDetail saved = service.saveVersion(created.dashboard().id(), new DashboardService.SaveCommand(
        "A2", null, null,
        List.of(new DashboardService.WidgetSpec("w1", null, "临时", Map.of("type", "line"), 1, 1, 10, 7, 6, 5))));

    assertEquals(2, saved.dashboard().currentVersionNo());
    assertEquals(2, saved.versions().size());
    assertEquals("A", saved.versions().get(1).name());
  }

  @Test
  void widgetCannotCarryLinkedAndInlineDefinitionsTogether() {
    DashboardService service = new DashboardService(new FakeRepository(), mock(AnalysisService.class), new ObjectMapper());
    assertThrows(IllegalArgumentException.class, () -> service.create(new DashboardService.SaveCommand(
        "bad", null, null,
        List.of(new DashboardService.WidgetSpec("w1", 9L, null, Map.of("type", "bar"), 0, 0, 10, 7, 6, 5)))));
  }

  private static final class FakeRepository implements DashboardRepository {
    private long nextDashboardId = 1;
    private long nextVersionId = 100;
    private final Map<Long, DashboardAsset> dashboards = new LinkedHashMap<>();
    private final Map<Long, DashboardVersion> versions = new LinkedHashMap<>();
    private final Map<Long, List<DashboardWidgetSnapshot>> widgets = new LinkedHashMap<>();

    @Override public long insertDashboard(String name, String description) {
      long id = nextDashboardId++;
      dashboards.put(id, new DashboardAsset(id, name, description, null, 0, Instant.now(), Instant.now()));
      return id;
    }
    @Override public long insertVersion(long dashboardId, int versionNo, String name, String description, Long activeDatasetId) {
      long id = nextVersionId++;
      versions.put(id, new DashboardVersion(id, dashboardId, versionNo, name, description, activeDatasetId, Instant.now()));
      return id;
    }
    @Override public void insertWidgets(long versionId, List<DashboardService.WidgetSpec> specs, List<String> json) {
      List<DashboardWidgetSnapshot> rows = new ArrayList<>();
      for (int i = 0; i < specs.size(); i++) {
        DashboardService.WidgetSpec value = specs.get(i);
        rows.add(new DashboardWidgetSnapshot(i + 1L, versionId, value.widgetKey(), value.analysisId(), value.title(), value.inlineAnalysis(), value.x(), value.y(), value.w(), value.h(), value.minW(), value.minH(), i + 1));
      }
      widgets.put(versionId, rows);
    }
    @Override public void updateCurrentVersion(long dashboardId, long versionId, int versionNo, String name, String description) {
      DashboardAsset old = dashboards.get(dashboardId);
      dashboards.put(dashboardId, new DashboardAsset(dashboardId, name, description, versionId, versionNo, old.createTime(), Instant.now()));
    }
    @Override public Optional<DashboardAsset> findDashboard(long id) { return Optional.ofNullable(dashboards.get(id)); }
    @Override public List<DashboardAsset> listDashboards() { return new ArrayList<>(dashboards.values()); }
    @Override public Optional<DashboardVersion> findVersion(long id) { return Optional.ofNullable(versions.get(id)); }
    @Override public Optional<DashboardVersion> findVersionByNo(long dashboardId, int versionNo) { return versions.values().stream().filter(v -> v.dashboardId() == dashboardId && v.versionNo() == versionNo).findFirst(); }
    @Override public List<DashboardVersion> listVersions(long dashboardId) { return versions.values().stream().filter(v -> v.dashboardId() == dashboardId).sorted((a,b) -> Integer.compare(b.versionNo(), a.versionNo())).toList(); }
    @Override public List<DashboardWidgetSnapshot> listWidgets(long versionId) { return widgets.getOrDefault(versionId, List.of()); }
    @Override public int nextVersionNo(long dashboardId) { return listVersions(dashboardId).stream().mapToInt(DashboardVersion::versionNo).max().orElse(0) + 1; }
    @Override public void deleteDashboard(long dashboardId) { dashboards.remove(dashboardId); }
  }
}
