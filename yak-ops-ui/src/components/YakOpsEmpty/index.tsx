import React from 'react';

export interface YakOpsEmptyProps
  extends Omit<
    React.ImgHTMLAttributes<HTMLImageElement>,
    'src' | 'alt' | 'width' | 'height'
  > {
  /** 插画宽度 */
  width?: number | string;
  /** 插画高度 */
  height?: number | string;
  /** @deprecated PNG 插画不再支持动态强调色，仅保留用于兼容现有调用方。 */
  primaryColor?: string;
  /** 无障碍标题 */
  title?: string;
  /** 无障碍描述 */
  description?: string;
}

/**
 * Yak Ops 空状态插画。
 *
 * 统一使用 public/empty.png，减少组件内的大段 SVG 代码并复用公共空状态资源。
 */
const YakOpsEmpty: React.FC<YakOpsEmptyProps> = ({
  width = 220,
  height = 150,
  primaryColor: _primaryColor,
  title = 'Yak Ops 暂无数据',
  description = '一只小牦牛探身查看打开的服务器抽屉，正在寻找缺失的数据',
  style,
  ...props
}) => {
  const alt = description ? `${title}，${description}` : title;

  return (
    <img
      src="/empty.png"
      width={width}
      height={height}
      alt={alt}
      draggable={false}
      style={{ objectFit: 'contain', ...style }}
      {...props}
    />
  );
};

export default YakOpsEmpty;
