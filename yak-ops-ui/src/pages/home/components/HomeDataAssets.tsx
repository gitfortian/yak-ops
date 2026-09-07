import { useIntl } from '@umijs/max';

/**
 * Frontend-only shell for the homepage data-assets area.
 *
 * The body intentionally stays empty in this PR. Dataset, data-service and
 * dashboard content will be designed and wired separately.
 */
export default function HomeDataAssets() {
  const intl = useIntl();

  return (
    <section className="h-full min-h-[520px] rounded-[22px] border border-[#f0f1f3] bg-white px-6 pb-5 pt-5">
      <header>
        <h2 className="m-0 text-xl font-semibold tracking-[-0.35px] text-[#252832]">
          {intl.formatMessage({ id: 'pages.home.dataAssets.title' })}
        </h2>
      </header>
    </section>
  );
}
