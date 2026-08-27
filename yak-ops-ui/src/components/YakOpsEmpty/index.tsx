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
 * 只保留黑灰线稿与一个强调色问号，避免空状态本身抢占页面视觉焦点。
 */
const YakOpsEmpty: React.FC<YakOpsEmptyProps> = ({
  width = 220,
  height = 160,
  primaryColor = 'var(--yak-brand-color, #fe2c55)',
  title = 'Yak Ops 暂无数据',
  description = '一只小牦牛蹲在打开的服务器抽屉旁，正在查看为什么没有数据',
  style,
  ...props
}) => {
  const titleId = useId();
  const descriptionId = useId();

  const lineColor = '#55585f';
  const secondaryLineColor = '#9a9da3';
  const lightLineColor = '#d9dadd';
  const softFill = '#f7f7f8';

  return (
    <svg
      width={width}
      height={height}
      viewBox="0 0 220 160"
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

      <path
        d="M33 139H189"
        stroke={lightLineColor}
        strokeWidth="1.5"
        strokeLinecap="round"
      />

      {/* 小牦牛：蹲下查看抽屉 */}
      <path
        d="M79 76C65 79 55 88 53 101C51 113 57 121 70 123L96 125C105 125 111 120 112 111C113 100 109 88 101 81C94 76 87 74 79 76Z"
        fill="#fff"
        stroke={lineColor}
        strokeWidth="1.8"
        strokeLinejoin="round"
      />
      <path
        d="M68 118C61 124 57 131 55 138H77C78 133 79 128 82 124"
        fill="#fff"
        stroke={lineColor}
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M91 123C95 128 99 133 100 138H121C118 129 113 121 107 115"
        fill="#fff"
        stroke={lineColor}
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />

      <path
        d="M68 54C71 46 78 42 87 43C96 43 103 48 105 56L102 73C101 81 96 85 87 85C78 85 73 81 71 73L68 54Z"
        fill="#fff"
        stroke={lineColor}
        strokeWidth="1.8"
        strokeLinejoin="round"
      />
      <path
        d="M72 51C63 50 60 44 62 37C65 42 69 44 74 43"
        stroke={lineColor}
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M101 51C109 49 112 43 110 36C107 41 103 43 98 42"
        stroke={lineColor}
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M70 57C65 54 61 56 63 60C65 63 68 63 72 62"
        fill="#fff"
        stroke={lineColor}
        strokeWidth="1.6"
        strokeLinejoin="round"
      />
      <path
        d="M102 57C107 54 111 56 109 60C107 63 104 63 101 62"
        fill="#fff"
        stroke={lineColor}
        strokeWidth="1.6"
        strokeLinejoin="round"
      />
      <path
        d="M77 46L80 51L85 46L89 51L95 46"
        stroke={lineColor}
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path d="M78 63C80 64 82 64 84 63" stroke={lineColor} strokeWidth="1.7" strokeLinecap="round" />
      <path d="M91 64C93 65 95 65 97 64" stroke={lineColor} strokeWidth="1.7" strokeLinecap="round" />
      <path
        d="M79 70C80 67 83 66 87 66C91 66 94 68 95 71C94 75 91 77 87 77C83 77 80 75 79 70Z"
        fill={softFill}
        stroke={lineColor}
        strokeWidth="1.5"
      />
      <circle cx="84" cy="71" r="1" fill={lineColor} />
      <circle cx="90" cy="71" r="1" fill={lineColor} />

      <path
        d="M64 89C61 97 61 105 66 111C69 114 73 113 75 110L80 98"
        fill="#fff"
        stroke={lineColor}
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M100 89C107 92 112 97 117 102C121 106 125 106 128 103C130 100 128 97 125 94C119 88 112 84 104 82"
        fill="#fff"
        stroke={lineColor}
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path d="M124 96L130 92" stroke={secondaryLineColor} strokeWidth="1.2" strokeLinecap="round" />

      {/* 打开的空服务器抽屉 */}
      <path
        d="M135 94H180V135H135Z"
        fill={softFill}
        stroke={lineColor}
        strokeWidth="1.8"
        strokeLinejoin="round"
      />
      <path d="M158 94V135" stroke={secondaryLineColor} strokeWidth="1.3" />
      <path
        d="M134 94L145 82H189L180 94H134Z"
        fill="#fff"
        stroke={lineColor}
        strokeWidth="1.8"
        strokeLinejoin="round"
      />
      <path d="M145 86H179" stroke={lightLineColor} strokeWidth="1.4" strokeLinecap="round" />
      <path
        d="M134 110L118 116V132L134 135V110Z"
        fill="#fff"
        stroke={lineColor}
        strokeWidth="1.8"
        strokeLinejoin="round"
      />
      <path d="M121 119H131" stroke={secondaryLineColor} strokeWidth="1.3" strokeLinecap="round" />
      <circle cx="127" cy="126" r="1.5" fill={secondaryLineColor} />
      <path d="M145 108H172" stroke={lightLineColor} strokeWidth="1.4" strokeLinecap="round" strokeDasharray="3 4" />
      <path d="M145 115H166" stroke={lightLineColor} strokeWidth="1.4" strokeLinecap="round" strokeDasharray="3 4" />

      {/* 唯一强调色：疑问号 */}
      <circle cx="40" cy="40" r="19" fill={softFill} stroke={lightLineColor} strokeWidth="1" />
      <path
        d="M35.5 35.5C35.5 31.7 38 29.5 41.6 29.5C45.2 29.5 47.5 31.5 47.5 34.4C47.5 37 46 38.4 43.7 39.8C41.4 41.2 40.5 42.8 40.5 45"
        stroke={primaryColor}
        strokeWidth="4"
        strokeLinecap="round"
      />
      <circle cx="40.5" cy="51" r="2.3" fill={primaryColor} />
      <path
        d="M49 55C54 59 59 60 64 60"
        stroke={lightLineColor}
        strokeWidth="1.3"
        strokeLinecap="round"
        strokeDasharray="2.5 4"
      />
    </svg>
  );
};

export default YakOpsEmpty;
