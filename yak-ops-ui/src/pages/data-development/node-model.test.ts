import {
  NODE_CATEGORY_BY_TYPE,
  canConnectNodes,
  getNodeCategory,
  type NodeType,
} from './node-model';

describe('data-development node model', () => {
  it('classifies authoring nodes as processing and output nodes as output', () => {
    expect(NODE_CATEGORY_BY_TYPE).toEqual({
      SQL: 'PROCESSING',
      SHELL: 'PROCESSING',
      HTTP: 'PROCESSING',
      PYTHON: 'PROCESSING',
      DATASET: 'OUTPUT',
      DATA_SERVICE: 'OUTPUT',
    });
    expect(getNodeCategory('SQL')).toBe('PROCESSING');
    expect(getNodeCategory('DATASET')).toBe('OUTPUT');
    expect(getNodeCategory('DATA_SERVICE')).toBe('OUTPUT');
  });

  it.each<[NodeType, NodeType]>([
    ['SQL', 'SQL'],
    ['SQL', 'DATASET'],
    ['SQL', 'DATA_SERVICE'],
    ['DATASET', 'DATA_SERVICE'],
  ])('allows %s -> %s', (source, target) => {
    expect(canConnectNodes(source, target)).toBe(true);
  });

  it.each<[NodeType, NodeType]>([
    ['DATASET', 'SQL'],
    ['DATASET', 'DATASET'],
    ['DATA_SERVICE', 'SQL'],
    ['DATA_SERVICE', 'DATASET'],
    ['DATA_SERVICE', 'DATA_SERVICE'],
    ['SHELL', 'DATASET'],
    ['HTTP', 'DATA_SERVICE'],
    ['PYTHON', 'SQL'],
  ])('rejects undeclared %s -> %s edges', (source, target) => {
    expect(canConnectNodes(source, target)).toBe(false);
  });
});
