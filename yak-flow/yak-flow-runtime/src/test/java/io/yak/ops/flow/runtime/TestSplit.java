package io.yak.ops.flow.runtime;

import io.yak.ops.flow.api.source.SourceSplit;

/**
 * Runtime 测试使用的单分片。
 *
 * @param splitId 分片标识
 * @author weifuwan
 * @since 2026-09-27
 */
record TestSplit(String splitId) implements SourceSplit {}
