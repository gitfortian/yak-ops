import { defaultDagPosition, wouldCreateCycle } from './dag-model';

describe('data-development DAG helpers', () => {
  it('detects a cycle before an edge is appended', () => {
    const edges = [
      { source: 'sql-1', target: 'sql-2' },
      { source: 'sql-2', target: 'dataset-1' },
    ];

    expect(wouldCreateCycle(edges, 'dataset-1', 'sql-1')).toBe(true);
    expect(wouldCreateCycle(edges, 'dataset-1', 'service-1')).toBe(false);
  });

  it('lays new resources out on a stable three-column grid', () => {
    expect(defaultDagPosition(0)).toEqual({ x: 80, y: 80 });
    expect(defaultDagPosition(3)).toEqual({ x: 80, y: 230 });
  });
});
