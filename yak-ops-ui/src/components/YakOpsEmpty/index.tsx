import React, { useId } from 'react';

export interface YakOpsEmptyProps
  extends Omit<React.SVGProps<SVGSVGElement>, 'width' | 'height'> {
  /** 插画宽度 */
  width?: number | string;
  /** 插画高度 */
  height?: number | string;
  /** 唯一强调色 */
  primaryColor?: string;
  /** 无障碍标题 */
  title?: string;
  /** 无障碍描述 */
  description?: string;
}

/**
 * Yak Ops 空状态插画。
 *
 * 用一个更有动作感的“探身检查空服务器抽屉”场景表达暂无数据：
 * 角色整体保持黑灰线稿，只把疑问号留给品牌强调色。
 */
const YakOpsEmpty: React.FC<YakOpsEmptyProps> = ({
  width = 220,
  height = 150,
  primaryColor = 'var(--yak-brand-color, #fe2c55)',
  title = 'Yak Ops 暂无数据',
  description = '一只小牦牛探身查看打开的服务器抽屉，正在寻找缺失的数据',
  style,
  ...props
}) => {
  const titleId = useId();
  const descriptionId = useId();

  const lineColor = '#565b62';
  const secondaryLineColor = '#9ca1a8';
  const lightLineColor = '#d9dde2';
  const softFill = '#f7f8f9';

  return (
    <svg
      width={width}
      height={height}
      viewBox="0 0 220 150"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      role="img"
      aria-labelledby={`${titleId} ${descriptionId}`}
      focusable="false"
      style={style}
      {...props}
    >
      <title id={titleId}>{title}</title>
      <desc id={descriptionId}>{description}</desc>

      {/* 地面 */}
      <path
        d="M29 132H194"
        stroke={lightLineColor}
        strokeWidth="1.4"
        strokeLinecap="round"
      />

      {/* 唯一强调色：疑问气泡 */}
      <circle cx="36" cy="31" r="16" fill={softFill} stroke={lightLineColor} />
      <path
        d="M31.5 27.5C31.5 24.2 33.7 22.2 37 22.2C40.4 22.2 42.5 24 42.5 26.7C42.5 29.1 41.1 30.4 39 31.7C36.9 33 36 34.4 36 36.4"
        stroke={primaryColor}
        strokeWidth="3.5"
        strokeLinecap="round"
      />
      <circle cx="36" cy="41.4" r="2" fill={primaryColor} />

      {/* 小牦牛：身体前倾，重心压向右侧 */}
      <path
        d="M70 75C60 82 56 95 60 106C64 116 75 121 88 119L109 116C116 115 119 108 116 101L105 82C99 73 80 69 70 75Z"
        fill="#fff"
        stroke={lineColor}
        strokeWidth="1.8"
        strokeLinejoin="round"
      />

      {/* 后腿蹲姿 */}
      <path
        d="M71 112C63 116 58 123 57 131H78C79 125 82 120 87 116"
        fill="#fff"
        stroke={lineColor}
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M94 117C99 121 102 126 103 131H122C120 123 115 116 110 112"
        fill="#fff"
        stroke={lineColor}
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />

      {/* 头部稍微前倾，视线朝向抽屉 */}
      <g transform="rotate(7 82 57)">
        <path
          d="M62 51C65 43 72 39 81 39C91 39 98 44 101 52L98 68C97 77 91 82 82 82C73 82 67 77 66 68L62 51Z"
          fill="#fff"
          stroke={lineColor}
          strokeWidth="1.8"
          strokeLinejoin="round"
        />
        <path
          d="M66 49C57 48 54 42 56 35C59 40 63 42 68 41"
          stroke={lineColor}
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <path
          d="M98 49C106 47 109 41 107 34C104 39 100 41 95 40"
          stroke={lineColor}
          strokeWidth="1.8"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <path
          d="M65 55C60 52 56 54 58 59C60 62 63 62 67 61"
          fill="#fff"
          stroke={lineColor}
          strokeWidth="1.5"
          strokeLinejoin="round"
        />
        <path
          d="M99 55C104 52 108 54 106 59C104 62 101 62 98 61"
          fill="#fff"
          stroke={lineColor}
          strokeWidth="1.5"
          strokeLinejoin="round"
        />
        <path
          d="M71 43L75 48L80 42L84 48L90 43"
          stroke={lineColor}
          strokeWidth="1.7"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <path
          d="M72 61C74 60 76 60 78 61"
          stroke={lineColor}
          strokeWidth="1.5"
          strokeLinecap="round"
        />
        <path
          d="M87 61C90 60 92 60 94 61"
          stroke={lineColor}
          strokeWidth="1.5"
          strokeLinecap="round"
        />
        <path
          d="M75 69C77 66 80 65 84 65C88 65 91 67 92 70C91 74 88 76 84 76C80 76 77 74 75 69Z"
          fill={softFill}
          stroke={lineColor}
          strokeWidth="1.45"
        />
        <circle cx="81" cy="70" r="1" fill={lineColor} />
        <circle cx="87" cy="70" r="1" fill={lineColor} />
      </g>

      {/* 左手搭在额头上，像在认真寻找 */}
      <path
        d="M72 79C65 73 62 66 64 60C66 55 70 54 73 57C76 60 74 64 73 67"
        fill="#fff"
        stroke={lineColor}
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M72 57C78 55 83 55 88 57"
        stroke={lineColor}
        strokeWidth="1.6"
        strokeLinecap="round"
      />
      <path d="M76 55L77 60" stroke={secondaryLineColor} strokeWidth="1.1" strokeLinecap="round" />
      <path d="M80 55L81 60" stroke={secondaryLineColor} strokeWidth="1.1" strokeLinecap="round" />

      {/* 右手扶住拉出的服务器抽屉 */}
      <path
        d="M103 83C111 84 119 89 125 96C128 99 131 100 134 98C137 96 136 92 133 90C126 83 117 78 107 76"
        fill="#fff"
        stroke={lineColor}
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path d="M130 93L137 90" stroke={secondaryLineColor} strokeWidth="1.2" strokeLinecap="round" />

      {/* 服务器机柜：结构简洁，抽屉向人物方向拉出 */}
      <path
        d="M143 78H187V129H143Z"
        fill={softFill}
        stroke={lineColor}
        strokeWidth="1.8"
        strokeLinejoin="round"
      />
      <path d="M165 78V129" stroke={secondaryLineColor} strokeWidth="1.2" />
      <path
        d="M141 78L151 67H194L187 78H141Z"
        fill="#fff"
        stroke={lineColor}
        strokeWidth="1.8"
        strokeLinejoin="round"
      />
      <path d="M151 71H184" stroke={lightLineColor} strokeWidth="1.3" strokeLinecap="round" />

      {/* 拉出的空抽屉 */}
      <path
        d="M143 96L121 103V118L143 123V96Z"
        fill="#fff"
        stroke={lineColor}
        strokeWidth="1.8"
        strokeLinejoin="round"
      />
      <path d="M125 106H137" stroke={secondaryLineColor} strokeWidth="1.2" strokeLinecap="round" />
      <circle cx="134" cy="114" r="1.4" fill={secondaryLineColor} />

      {/* 空状态细节 */}
      <path d="M151 94H178" stroke={lightLineColor} strokeWidth="1.3" strokeLinecap="round" strokeDasharray="3 4" />
      <path d="M151 101H173" stroke={lightLineColor} strokeWidth="1.3" strokeLinecap="round" strokeDasharray="3 4" />
      <path d="M151 108H180" stroke={lightLineColor} strokeWidth="1.3" strokeLinecap="round" strokeDasharray="3 4" />

      {/* 视线 / 动作提示，只用灰色辅助线 */}
      <path d="M111 69C118 67 124 68 130 71" stroke={lightLineColor} strokeWidth="1.2" strokeLinecap="round" strokeDasharray="2 4" />
      <path d="M116 74C123 73 129 75 134 79" stroke={lightLineColor} strokeWidth="1.2" strokeLinecap="round" strokeDasharray="2 4" />

      {/* 一根松开的线缆，让场景更有“排查问题”的感觉 */}
      <path
        d="M185 103C195 105 197 115 190 119C186 121 184 125 187 129"
        stroke={secondaryLineColor}
        strokeWidth="1.4"
        strokeLinecap="round"
      />
      <circle cx="188" cy="131" r="1.5" fill={secondaryLineColor} />
    </svg>
  );
};

export default YakOpsEmpty;
