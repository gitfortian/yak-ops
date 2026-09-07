import { useIntl } from '@umijs/max';

/**
 * Frontend-only shell for the homepage resource center.
 *
 * Resource statistics and recent-file content will be added in a dedicated
 * follow-up once the presentation is finalized.
 */
export default function HomeResourceCenter() {
  const intl = useIntl();

  return (
    <section className="flex min-h-[176px] flex-1 flex-col rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-5 pt-5">
      <header>
        <h2 className="m-0 text-xl font-semibold tracking-[-0.35px] text-[#252832]">
          {intl.formatMessage({ id: 'pages.home.resourceCenter.title' })}
        </h2>
      </header>
    </section>
  );
}
