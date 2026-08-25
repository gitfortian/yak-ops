import { Button, type ButtonProps } from 'antd';
import './index.less';

export type YakButtonProps = ButtonProps & {
  /**
   * Keep icon-only actions aligned to the same square rhythm as normal buttons
   * without forcing Ant Design's circular shape.
   */
  iconOnly?: boolean;
};

/**
 * Yak Ops unified button.
 *
 * The component keeps the complete Ant Design Button API while applying the
 * compact neutral treatment used by Yak Ops. Default buttons follow the soft
 * grey button in the interaction reference; primary, text and danger states
 * share the same spacing, radius and feedback rules.
 */
export default function YakButton({
  className,
  iconOnly = false,
  ...props
}: YakButtonProps) {
  return (
    <Button
      {...props}
      className={[
        'yak-button',
        iconOnly ? 'yak-button--icon-only' : '',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    />
  );
}
