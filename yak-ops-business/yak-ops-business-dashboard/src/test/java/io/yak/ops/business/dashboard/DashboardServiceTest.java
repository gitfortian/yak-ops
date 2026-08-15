package io.yak.ops.business.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.analysis.AnalysisReferenceService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {

  @Test
  void createPersistsDraftV1AndValidatesAnalysisReference() {
    FakeRepository repository = new FakeRepository();
    AnalysisReferenceService analysisReferences = mock(AnalysisReferenceService.class);
    DashboardService service = new DashboardService(repository, analysisReferences, new ObjectMapper());

    DashboardDetail detail = service.create(new DashboardService.SaveCommand(
        "销售驾驶舱", "核心销售分析", 12L,
        List.of(new DashboardService.WidgetSpec("w1", 99L, null, null, 0, 0, 10, 7, 6, 5)),
        List.of(new DashboardService.GlobalFilterSpec(
            "region", "区域", DashboardGlobalFilterOperator.EQ, "华南",
            List.of(new DashboardService.FilterBindingSpec("w1", "region-field")))),
        List.of(new DashboardService.InteractionSpec(
            "link-region", DashboardInteractionEvent.SELECT, "w1", "region-field", "region"))));

    assertEquals(1, detail.dashboard().currentVersionNo());
    assertEquals(0, detail.dashboard().publishedVersionNo());
    assertEquals(1, detail.versions().size());
    assertEquals(1, detail.widgets().size());
    assertEquals(1, detail.globalFilters().size());
    assertEquals("华南", detail.globalFilters().get(0).defaultValue());
    assertEquals(1, detail.interactions().size());
    verify(analysisReferences).requireExists(99L);
  }

  @Test
  void publishPointsToCurrentDraftWithoutCreatingAnotherVersion() {
    FakeRepository repository = new FakeRepository();
    DashboardService service = new DashboardService(repository, mock(AnalysisReferenceService.class), new ObjectMapper());
    DashboardDetail created = service.create(command(
        "A",
        List.of(new DashboardService.WidgetSpec("w1", null, "临时", Map.of("type", "bar"), 0, 0, 10, 7, 6, 5))));

    DashboardDetail published = service.publish(created.dashboard().id());

    assertEquals(1, published.dashboard().currentVersionNo());
    assertEquals(1, published.dashboard().publishedVersionNo());
    assertEquals(published.dashboard().currentVersionId(), published.dashboard().publishedVersionId());
    assertEquals(1, published.versions().size());
    assertEquals(1, service.published(created.dashboard().id()).version().versionNo());
  }

  @Test
  void savingDraftAfterPublishDoesNotMovePublishedPointer() {
    FakeRepository repository = new FakeRepository();
    DashboardService service = new DashboardService(repository, mock(AnalysisReferenceService.class), new ObjectMapper());
    DashboardDetail created = service.create(command(
        "A",
        List.of(new DashboardService.WidgetSpec("w1", null, "临时", Map.of("type", "bar"), 0, 0, 10, 7, 6, 5))));
    service.publish(created.dashboard().id());

    DashboardDetail saved = service.saveVersion(created.dashboard().id(), command(
        "A2",
        List.of(new DashboardService.WidgetSpec("w1", null, "临时", Map.of("type", "line"), 1, 1, 10, 7, 6, 5))));

    assertEquals(2, saved.dashboard().currentVersionNo());
    assertEquals(1, saved.dashboard().publishedVersionNo());
    assertEquals(2, saved.versions().size());
    assertEquals("A", service.published(created.dashboard().id()).version().name());
  }

  @Test
  void restoreHistoricalVersionCreatesNewDraftAndKeepsPublishedVersion() {
    FakeRepository repository = new FakeRepository();
    DashboardService service = new DashboardService(repository, mock(AnalysisReferenceService.class), new ObjectMapper());
    DashboardDetail created = service.create(command(
        "A",
        List.of(new DashboardService.WidgetSpec("w1", null, "临时", Map.of("type", "bar"), 0, 0, 10, 7, 6, 5))));
    service.publish(created.dashboard().id());
    service.saveVersion(created.dashboard().id(), command(
        "A2",
        List.of(new DashboardService.WidgetSpec("w1", null, "临时", Map.of("type", "line"), 1, 1, 10, 7, 6, 5))));

    DashboardDetail restored = service.restoreVersion(created.dashboard().id(), 1);

    assertEquals(3, restored.dashboard().currentVersionNo());
    assertEquals(1, restored.dashboard().publishedVersionNo());
    assertEquals("A", restored.currentVersion().name());
    assertEquals("bar", ((Map<?, ?>) restored.widgets().get(0).inlineAnalysis()).get("type"));
    assertEquals(3, restored.versions().size());
  }

  @Test
  void versionDetailReadsHistoricalSnapshotWithoutChangingDraftPointer() {
    FakeRepository repository = new FakeRepository();
    DashboardService service = new DashboardService(repository, mock(AnalysisReferenceService.class), new ObjectMapper());
    DashboardDetail created = service.create(command(
        "A",
        List.of(new DashboardService.WidgetSpec("w1", null, "临时", Map.of("type", "bar"), 0, 0, 10, 7, 6, 5))));
    service.saveVersion(created.dashboard().id(), command(
        "A2",
        List.of(new DashboardService.WidgetSpec("w1", null, "临时", Map.of("type", "line"), 2, 3, 10, 7, 6, 5))));

    DashboardVersionDetail v1 = service.version(created.dashboard().id(), 1);

    assertEquals(1, v1.version().versionNo());
    assertEquals("A", v1.version().name());
    assertEquals(0, v1.widgets().get(0).x());
    assertEquals(2, service.get(created.dashboard().id()).dashboard().currentVersionNo());
  }

  @Test
  void widgetCannotCarryLinkedAndInlineDefinitionsTogether() {
    DashboardService service = new DashboardService(
        new FakeRepository(), mock(AnalysisReferenceService.class), new ObjectMapper());
    assertThrows(IllegalArgumentException.class, () -> service.create(command(
        "bad",
        List.of(new DashboardService.WidgetSpec("w1", 9L, null, Map.of("type", "bar"), 0, 0, 10, 7, 6, 5)))));
  }

  @Test
  void globalFilterCannotBindUnknownWidget() {
    DashboardService service = new DashboardService(
        new FakeRepository(), mock(AnalysisReferenceService.class), new ObjectMapper());
    assertThrows(IllegalArgumentException.class, () -> service.create(new DashboardService.SaveCommand(
        "bad-filter", null, null,
        List.of(new DashboardService.WidgetSpec("w1", null, "临时", Map.of("type", "bar"), 0, 0, 10, 7, 6, 5)),
        List.of(new DashboardService.GlobalFilterSpec(
            "region", "区域", DashboardGlobalFilterOperator.EQ, null,
            List.of(new DashboardService.FilterBindingSpec("missing", "region-field")))),
        List.of())));
  }

  @Test
  void interactionMustTargetExistingFilter() {
    DashboardService service = new DashboardService(
        new FakeRepository(), mock(AnalysisReferenceService.class), new ObjectMapper());
    assertThrows(IllegalArgumentException.class, () -> service.create(new DashboardService.SaveCommand(
        "bad-link", null, null,
        List.of(new DashboardService.WidgetSpec("w1", null, "临时", Map.of("type", "bar"), 0, 0, 10, 7, 6, 5)),
        List.of(),
        List.of(new DashboardService.InteractionSpec(
            "link", DashboardInteractionEvent.SELECT, "w1", "region", "missing")))));
  }

  private DashboardService.SaveCommand command(String name, List<DashboardService.WidgetSpec> widgets) {
    return new DashboardService.SaveCommand(name, null, null, widgets, List.of(), List.of());
  }

  private static final class FakeRepository implements DashboardRepository {
    private long nextDashboardId = 1;
    private long nextVersionId = 100;
    private final Map<Long, DashboardAsset> dashboards = new LinkedHashMap<>();
    private final Map<Long, DashboardVersion> versions = new LinkedHashMap<>();
    private final Map<Long, List<DashboardWidgetSnapshot>> widgets = new LinkedHashMap<>();
    private final Map<Long, List<DashboardGlobalFilterSnapshot>> filters = new LinkedHashMap<>();
    private final Map<Long, List<DashboardInteractionSnapshot>> interactions = new LinkedHashMap<>();

    @Override public long insertDashboard(String name, String description) {
      long id = nextDashboardId++;
      dashboards.put(id, new DashboardAsset(
          id, name, description, null, 0, null, 0, null, Instant.now(), Instant.now()));
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
        rows.add(new DashboardWidgetSnapshot(
            i + 1L, versionId, value.widgetKey(), value.analysisId(), value.title(), value.inlineAnalysis(),
            value.x(), value.y(), value.w(), value.h(), value.minW(), value.minH(), i + 1));
      }
      widgets.put(versionId, rows);
    }

    @Override public void insertGlobalFilters(long versionId, List<DashboardService.GlobalFilterSpec> specs, List<String> json) {
      List<DashboardGlobalFilterSnapshot> rows = new ArrayList<>();
      for (int i = 0; i < specs.size(); i++) {
        DashboardService.GlobalFilterSpec value = specs.get(i);
        List<DashboardGlobalFilterBindingSnapshot> bindings = new ArrayList<>();
        for (int j = 0; j < value.bindings().size(); j++) {
          DashboardService.FilterBindingSpec binding = value.bindings().get(j);
          bindings.add(new DashboardGlobalFilterBindingSnapshot(binding.widgetKey(), binding.fieldId(), j + 1));
        }
        rows.add(new DashboardGlobalFilterSnapshot(
            value.filterKey(), value.name(), value.operator(), value.defaultValue(), bindings, i + 1));
      }
      filters.put(versionId, rows);
    }

    @Override public void insertInteractions(long versionId, List<DashboardService.InteractionSpec> specs) {
      List<DashboardInteractionSnapshot> rows = new ArrayList<>();
      for (int i = 0; i < specs.size(); i++) {
        DashboardService.InteractionSpec value = specs.get(i);
        rows.add(new DashboardInteractionSnapshot(
            value.interactionKey(), value.event(), value.sourceWidgetKey(), value.sourceFieldId(),
            value.targetFilterKey(), i + 1));
      }
      interactions.put(versionId, rows);
    }

    @Override public void updateCurrentVersion(long dashboardId, long versionId, int versionNo, String name, String description) {
      DashboardAsset old = dashboards.get(dashboardId);
      dashboards.put(dashboardId, new DashboardAsset(
          dashboardId,
          name,
          description,
          versionId,
          versionNo,
          old.publishedVersionId(),
          old.publishedVersionNo(),
          old.publishedTime(),
          old.createTime(),
          Instant.now()));
    }

    @Override public void updatePublishedVersion(long dashboardId, long versionId, int versionNo) {
      DashboardAsset old = dashboards.get(dashboardId);
      dashboards.put(dashboardId, new DashboardAsset(
          dashboardId,
          old.name(),
          old.description(),
          old.currentVersionId(),
          old.currentVersionNo(),
          versionId,
          versionNo,
          Instant.now(),
          old.createTime(),
          Instant.now()));
    }

    @Override public Optional<DashboardAsset> findDashboard(long id) { return Optional.ofNullable(dashboards.get(id)); }
    @Override public List<DashboardAsset> listDashboards() { return new ArrayList<>(dashboards.values()); }
    @Override public Optional<DashboardVersion> findVersion(long id) { return Optional.ofNullable(versions.get(id)); }
    @Override public Optional<DashboardVersion> findVersionByNo(long dashboardId, int versionNo) { return versions.values().stream().filter(v -> v.dashboardId() == dashboardId && v.versionNo() == versionNo).findFirst(); }
    @Override public List<DashboardVersion> listVersions(long dashboardId) { return versions.values().stream().filter(v -> v.dashboardId() == dashboardId).sorted((a,b) -> Integer.compare(b.versionNo(), a.versionNo())).toList(); }
    @Override public List<DashboardWidgetSnapshot> listWidgets(long versionId) { return widgets.getOrDefault(versionId, List.of()); }
    @Override public List<DashboardGlobalFilterSnapshot> listGlobalFilters(long versionId) { return filters.getOrDefault(versionId, List.of()); }
    @Override public List<DashboardInteractionSnapshot> listInteractions(long versionId) { return interactions.getOrDefault(versionId, List.of()); }
    @Override public int nextVersionNo(long dashboardId) { return listVersions(dashboardId).stream().mapToInt(DashboardVersion::versionNo).max().orElse(0) + 1; }
    @Override public void deleteDashboard(long dashboardId) { dashboards.remove(dashboardId); }
  }
}
