export { ScreenRenderer } from './ScreenRenderer';
export type { ScreenRendererProps } from './ScreenRenderer';
export {
  builtinScreenTemplates,
  getScreenTemplateById,
  listScreenTemplateCategories,
  listScreenTemplates,
} from './registry';
export type {
  ScreenAggregation,
  ScreenBarComponent,
  ScreenCartesianData,
  ScreenChartOptions,
  ScreenComponent,
  ScreenComponentData,
  ScreenComponentStyle,
  ScreenComponentType,
  ScreenDataBinding,
  ScreenDataOverrides,
  ScreenLineComponent,
  ScreenMapComponent,
  ScreenMapData,
  ScreenMapOptions,
  ScreenMapPoint,
  ScreenMapRoute,
  ScreenMetricBinding,
  ScreenMetricComponent,
  ScreenMetricData,
  ScreenPieComponent,
  ScreenPieData,
  ScreenPieItem,
  ScreenScalar,
  ScreenSeries,
  ScreenTableColumn,
  ScreenTableComponent,
  ScreenTableData,
  ScreenTemplate,
  ScreenTextComponent,
  ScreenTextData,
  ScreenTheme,
  ScreenTickerComponent,
  ScreenTickerData,
  ScreenTickerItem,
  ScreenTickerOptions,
} from './model';
export { assertValidScreenTemplate, validateScreenTemplate } from './validator';
export type { ScreenTemplateValidationResult } from './validator';
export {
  commandCenterTemplate,
  dataCenterTemplate,
  operationCenterTemplate,
  simpleDashboardTemplate,
} from './templates';
