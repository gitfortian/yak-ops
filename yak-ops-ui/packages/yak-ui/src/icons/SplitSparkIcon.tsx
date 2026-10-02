import type { SVGProps } from "react";

export interface SplitSparkIconProps extends SVGProps<SVGSVGElement> {
  /** 图标尺寸。默认 24，可被 width / height 或 CSS 覆盖。 */
  size?: number | string;
  /** 可选的无障碍名称；不传且没有 aria-label 时，默认作为装饰图标。 */
  title?: string;
}

/**
 * 分离式星芒图标。
 * 两个独立的实心路径，透明背景；颜色继承 currentColor。
 */
export function SplitSparkIcon({ size = 24, title, ...props }: SplitSparkIconProps) {
  const hasAccessibleName = Boolean(title || props["aria-label"] || props["aria-labelledby"]);

  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="currentColor"
      role={hasAccessibleName ? "img" : undefined}
      aria-hidden={hasAccessibleName ? undefined : true}
      focusable="false"
      {...props}
    >
      {title ? <title>{title}</title> : null}
      <path
        d="M 8.05 9.53
          C 5.75 9.61 2.92 10.80 2.94 11.96
          C 2.92 13.16 5.71 14.33 8.05 14.48
          Z"
      />
      <path
        d="M 12.04 2.85
          C 10.53 2.85 10.12 4.58 9.43 7.91
          C 9.01 9.97 9.01 12.91 9.27 15.26
          C 9.53 17.94 10.49 21.08 11.65 21.02
          C 12.86 20.94 13.61 19.61 14.11 17.89
          C 14.37 16.96 14.50 15.65 15.30 15.04
          C 15.98 14.48 17.10 14.43 18.36 14.05
          C 20.03 13.63 21.01 12.89 21.00 11.96
          C 21.01 10.88 19.27 10.03 17.96 9.80
          C 16.87 9.59 15.56 9.48 15.22 9.04
          C 14.90 8.60 14.63 7.76 14.41 6.77
          C 13.88 4.50 13.48 2.85 12.04 2.85
          Z"
      />
    </svg>
  );
}

export default SplitSparkIcon;
