import { useIntl } from '@umijs/max';

/**
 * Frontend-only shell for quick navigation.
 *
 * Navigation items will be designed separately; this PR only reserves the
 * homepage structure and spacing.
 */
export default function HomeQuickNavigation() {
  const intl = useIntl();

  return (
    <section className="min-h-[128px] rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-5 pt-5">
      <header>
        <h2 className="m-0 text-xl font-semibold tracking-[-0.35px] text-[#252832]">
          {intl.formatMessage({ id: 'pages.home.quickNavigation.title' })}
        </h2>
      </header>
    </section>
  );
}
