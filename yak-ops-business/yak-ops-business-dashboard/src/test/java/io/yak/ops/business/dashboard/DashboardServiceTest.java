package io.yak.ops.business.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.analysis.AnalysisReferenceService;
import io.yak.ops.business.dashboard.domain.DashboardAsset;
import io.yak.ops.business.dashboard.domain.DashboardDetail;
import io.yak.ops.business.dashboard.domain.DashboardDraft;
import io.yak.ops.business.dashboard.domain.DashboardGlobalFilterBindingSnapshot;
import io.yak.ops.business.dashboard.domain.DashboardGlobalFilterOperator;
import io.yak.ops.business.dashboard.domain.DashboardGlobalFilterSnapshot;
import io.yak.ops.business.dashboard.domain.DashboardInteractionEvent;
import io.yak.ops.business.dashboard.domain.DashboardInteractionSnapshot;
import io.yak.ops.business.dashboard.domain.DashboardVersion;
import io.yak.ops.business.dashboard.domain.DashboardVersionSnapshot;
import io.yak.ops.business.dashboard.domain.DashboardWidgetSnapshot;
import io.yak.ops.business.dashboard.domain.FilterBindingSpec;
import io.yak.ops.business.dashboard.domain.GlobalFilterSpec;
import io.yak.ops.business.dashboard.domain.InteractionSpec;
import io.yak.ops.business.dashboard.domain.WidgetSpec;
import io.yak.ops.business.dashboard.repository.DashboardRepository;
import io.yak.ops.business.dashboard.service.DashboardService;
import io.yak.ops.business.dashboard.service.support.DashboardDraftValidator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class DashboardServiceTest {

  @Test
  void createPersistsDraftV1AndValidatesAnalysisReference() {
    FakeRepository repository = new FakeRepository();
    AnalysisReferenceService analysisReferences = mock(AnalysisReferenceService.class);
    DashboardService service = service(repository, analysisReferences);

    DashboardDetail detail = service.create(new DashboardDraft(
        "销售驾驶舱", "核心销售分析", 12L, Map.of("mode", "dark"),
        List.of(new WidgetSpec("w1", 99L, null, null, 0, 0, 10, 7, 6, 5)),
        List.of(new GlobalFilterSpec(
            "region", "区域", DashboardGlobalFilterOperator.EQ, "华南",
            List.of(new FilterBindingSpec("w1", "region-field")))),
        List.of(new InteractionSpec(
            "link-region", DashboardInteractionEvent.SELECT, "w1", "region-field", "region"))));

    assertEquals(1, detail.dashboard().currentVersionNo());
    assertEquals(0, detail.dashboard().publishedVersionNo());
    assertEquals(1, detail.versions().size());
    assertEquals(1, detail.widgets().size());
    assertEquals("dark", ((Map<?, ?>) detail.theme()).get("mode"));
    assertEquals("华南", detail.globalFilters().get(0).defaultValue());
    assertEquals(1, detail.interactions().size());
    verify(analysisReferences).requireExists(99L);
  }

  @Test
  void publishPointsToCurrentDraftWithoutCreatingAnotherVersion() {
    FakeRepository repository = new FakeRepository();
    DashboardService service = service(repository, mock(AnalysisReferenceService.class));
    DashboardDetail created = service.create(command(
        "A",
        List.of(new WidgetSpec("w1", null, "临时", Map.of("type", "bar"), 0, 0, 10, 7, 6, 5))));

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
    DashboardService service = service(repository, mock(AnalysisReferenceService.class));
    DashboardDetail created = service.create(command(
        "A",
        List.of(new WidgetSpec("w1", null, "临时", Map.of("type", "bar"), 0, 0, 10, 7, 6, 5))));
    service.publish(created.dashboard().id());

    DashboardDetail saved = service.saveVersion(created.dashboard().id(), command(
        "A2",
        List.of(new WidgetSpec("w1", null, "临时", Map.of("type", "line"), 1, 1, 10, 7, 6, 5))));

    assertEquals(2, saved.dashboard().currentVersionNo());
    assertEquals(1, saved.dashboard().publishedVersionNo());
    assertEquals(2, saved.versions().size());
    assertEquals("A", service.published(created.dashboard().id()).version().name());
  }

  @Test
  void restoreHistoricalVersionCreatesNewDraftAndKeepsPublishedVersionAndTheme() {
    FakeRepository repository = new FakeRepository();
    DashboardService service = service(repository, mock(AnalysisReferenceService.class));
    DashboardDraft first = new DashboardDraft(
        "A", null, null, Map.of("palette", "classic"),
        List.of(new WidgetSpec("w1", null, "临时", Map.of("type", "bar"), 0, 0, 10, 7, 6, 5)),
        List.of(), List.of());
    DashboardDetail created = service.create(first);
    service.publish(created.dashboard().id());
    service.saveVersion(created.dashboard().id(), command(
        "A2",
        List.of(new WidgetSpec("w1", null, "临时", Map.of("type", "line"), 1, 1, 10, 7, 6, 5))));

    DashboardDetail restored = service.restoreVersion(created.dashboard().id(), 1);

    assertEquals(3, restored.dashboard().currentVersionNo());
    assertEquals(1, restored.dashboard().publishedVersionNo());
    assertEquals("A", restored.currentVersion().name());
    assertEquals("bar", ((Map<?, ?>) restored.widgets().get(0).inlineAnalysis()).get("type"));
    assertEquals("classic", ((Map<?, ?>) restored.theme()).get("palette"));
    assertEquals(3, restored.versions().size());
  }

  @Test
  void versionDetailReadsHistoricalSnapshotWithoutChangingDraftPointer() {
    FakeRepository repository = new FakeRepository();
    DashboardService service = service(repository, mock(AnalysisReferenceService.class));
    DashboardDetail created = service.create(command(
        "A",
        List.of(new WidgetSpec("w1", null, "临时", Map.of("type", "bar"), 0, 0, 10, 7, 6, 5))));
    service.saveVersion(created.dashboard().id(), command(
        "A2",
        List.of(new WidgetSpec("w1", null, "临时", Map.of("type", "line"), 2, 3, 10, 7, 6, 5))));

    var v1 = service.version(created.dashboard().id(), 1);

    assertEquals(1, v1.version().versionNo());
    assertEquals("A", v1.version().name());
    assertEquals(0, v1.widgets().get(0).x());
    assertEquals(2, service.get(created.dashboard().id()).dashboard().currentVersionNo());
  }

  @Test
  void widgetCannotCarryLinkedAndInlineDefinitionsTogether() {
    DashboardService service = service(new FakeRepository(), mock(AnalysisReferenceService.class));
    assertThrows(IllegalArgumentException.class, () -> service.create(command(
        "bad",
        List.of(new WidgetSpec("w1", 9L, null, Map.of("type", "bar"), 0, 0, 10, 7, 6, 5)))));
  }

  @Test
  void globalFilterCannotBindUnknownWidget() {
    DashboardService service = service(new FakeRepository(), mock(AnalysisReferenceService.class));
    assertThrows(IllegalArgumentException.class, () -> service.create(new DashboardDraft(
        "bad-filter", null, null, null,
        List.of(new WidgetSpec("w1", null, "临时", Map.of("type", "bar"), 0, 0, 10, 7, 6, 5)),
        List.of(new GlobalFilterSpec(
            "region", "区域", DashboardGlobalFilterOperator.EQ, null,
            List.of(new FilterBindingSpec("missing", "region-field")))),
        List.of())));
  }

  @Test
  void interactionMustTargetExistingFilter() {
    DashboardService service = service(new FakeRepository(), mock(AnalysisReferenceService.class));
    assertThrows(IllegalArgumentException.class, () -> service.create(new DashboardDraft(
        "bad-link", null, null, null,
        List.of(new WidgetSpec("w1", null, "临时", Map.of("type", "bar"), 0, 0, 10, 7, 6, 5)),
        List.of(),
        List.of(new InteractionSpec(
            "link", DashboardInteractionEvent.SELECT, "w1", "region", "missing")))));
  }

  private DashboardService service(
      DashboardRepository repository,
      AnalysisReferenceService analysisReferences) {
    return new DashboardService(
        repository,
        new DashboardDraftValidator(analysisReferences, new ObjectMapper()),
        mock(ApplicationEventPublisher.class));
  }

  private DashboardDraft command(String name, List<WidgetSpec> widgets) {
    return new DashboardDraft(name, null, null, null, widgets, List.of(), List.of());
  }

  private static final class FakeRepository implements DashboardRepository {
    private long nextDashboardId = 1;
    private long nextVersionId = 100;
    private final Map<Long, DashboardAsset> dashboards = new LinkedHashMap<>();
    private final Map<Long, DashboardVersion> versions = new LinkedHashMap<>();
    private final Map<Long, Object> themes = new LinkedHashMap<>();
    private final Map<Long, List<DashboardWidgetSnapshot>> widgets = new LinkedHashMap<>();
    private final Map<Long, List<DashboardGlobalFilterSnapshot>> filters = new LinkedHashMap<>();
    private final Map<Long, List<DashboardInteractionSnapshot>> interactions = new LinkedHashMap<>();

    @Override
    public long insertDashboard(String name, String description) {
      long id = nextDashboardId++;
      dashboards.put(id, new DashboardAsset(
          id, name, description, null, 0, null, 0, null, Instant.now(), Instant.now()));
      return id;
    }

    @Override
    public long appendVersion(long dashboardId, int versionNo, DashboardDraft draft) {
      long id = nextVersionId++;
      versions.put(id, new DashboardVersion(
          id, dashboardId, versionNo, draft.name(), draft.description(), draft.activeDatasetId(), Instant.now()));
      themes.put(id, draft.theme());

      List<DashboardWidgetSnapshot> widgetRows = new ArrayList<>();
      for (int i = 0; i < draft.widgets().size(); i++) {
        WidgetSpec value = draft.widgets().get(i);
        widgetRows.add(new DashboardWidgetSnapshot(
            i + 1L, id, value.widgetKey(), value.analysisId(), value.title(), value.inlineAnalysis(),
            value.x(), value.y(), value.w(), value.h(), value.minW(), value.minH(), i + 1));
      }
      widgets.put(id, widgetRows);

      List<DashboardGlobalFilterSnapshot> filterRows = new ArrayList<>();
      for (int i = 0; i < draft.globalFilters().size(); i++) {
        GlobalFilterSpec value = draft.globalFilters().get(i);
        List<DashboardGlobalFilterBindingSnapshot> bindings = new ArrayList<>();
        for (int j = 0; j < value.bindings().size(); j++) {
          FilterBindingSpec binding = value.bindings().get(j);
          bindings.add(new DashboardGlobalFilterBindingSnapshot(
              binding.widgetKey(), binding.fieldId(), j + 1));
        }
        filterRows.add(new DashboardGlobalFilterSnapshot(
            value.filterKey(), value.name(), value.operator(), value.defaultValue(), bindings, i + 1));
      }
      filters.put(id, filterRows);

      List<DashboardInteractionSnapshot> interactionRows = new ArrayList<>();
      for (int i = 0; i < draft.interactions().size(); i++) {
        InteractionSpec value = draft.interactions().get(i);
        interactionRows.add(new DashboardInteractionSnapshot(
            value.interactionKey(), value.event(), value.sourceWidgetKey(), value.sourceFieldId(),
            value.targetFilterKey(), i + 1));
      }
      interactions.put(id, interactionRows);
      return id;
    }

    @Override
    public void updateCurrentVersion(long dashboardId, long versionId, int versionNo, String name, String description) {
      DashboardAsset old = dashboards.get(dashboardId);
      dashboards.put(dashboardId, new DashboardAsset(
          dashboardId, name, description, versionId, versionNo,
          old.publishedVersionId(), old.publishedVersionNo(), old.publishedTime(),
          old.createTime(), Instant.now()));
    }

    @Override
    public void updatePublishedVersion(long dashboardId, long versionId, int versionNo) {
      DashboardAsset old = dashboards.get(dashboardId);
      dashboards.put(dashboardId, new DashboardAsset(
          dashboardId, old.name(), old.description(), old.currentVersionId(), old.currentVersionNo(),
          versionId, versionNo, Instant.now(), old.createTime(), Instant.now()));
    }

    @Override
    public Optional<DashboardAsset> findDashboard(long id) {
      return Optional.ofNullable(dashboards.get(id));
    }

    @Override
    public List<DashboardAsset> listDashboards() {
      return new ArrayList<>(dashboards.values());
    }

    @Override
    public Optional<DashboardVersionSnapshot> findVersionSnapshot(long id) {
      DashboardVersion version = versions.get(id);
      if (version == null) return Optional.empty();
      return Optional.of(new DashboardVersionSnapshot(
          version,
          themes.get(id),
          widgets.getOrDefault(id, List.of()),
          filters.getOrDefault(id, List.of()),
          interactions.getOrDefault(id, List.of())));
    }

    @Override
    public Optional<DashboardVersionSnapshot> findVersionSnapshotByNo(long dashboardId, int versionNo) {
      return versions.values().stream()
          .filter(version -> version.dashboardId() == dashboardId && version.versionNo() == versionNo)
          .findFirst()
          .flatMap(version -> findVersionSnapshot(version.id()));
    }

    @Override
    public List<DashboardVersion> listVersions(long dashboardId) {
      return versions.values().stream()
          .filter(version -> version.dashboardId() == dashboardId)
          .sorted((a, b) -> Integer.compare(b.versionNo(), a.versionNo()))
          .toList();
    }

    @Override
    public int nextVersionNo(long dashboardId) {
      return listVersions(dashboardId).stream()
          .mapToInt(DashboardVersion::versionNo)
          .max()
          .orElse(0) + 1;
    }

    @Override
    public void deleteDashboard(long dashboardId) {
      dashboards.remove(dashboardId);
    }
  }
}
