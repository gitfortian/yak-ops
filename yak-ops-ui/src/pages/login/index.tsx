import { Sparkles } from "lucide-react";
import LoginPanel from "./LoginPanel";

// Temporary demo media from the reference page. Replace with a Yak Ops-owned asset before production.
const LOGIN_VIDEO_URL =
  "https://claude.ai/images/homepage/claude-code-promo-loop@2x.mp4";

export default function LoginPage() {
  return (
    <main className="h-screen overflow-y-auto bg-[#fbfbfa] text-[#171717]">
      <div className="mx-auto flex min-h-screen w-full max-w-[1540px] flex-col px-6 py-4 sm:px-10 lg:px-12 lg:pb-6 lg:pt-5 xl:px-16">
        <header className="flex h-10 shrink-0 items-center">
          <div className="inline-flex items-center gap-2.5">
            <span className="flex h-8 w-8 items-center justify-center rounded-xl bg-[#fff0f3]">
              <Sparkles
                className="h-[18px] w-[18px] text-[#fe2c55]"
                strokeWidth={2.2}
              />
            </span>
            <span className="text-[21px] font-semibold tracking-[-0.03em] text-[#171717]">
              Yak Ops
            </span>
          </div>
        </header>

        <div className="grid flex-1 grid-cols-1 gap-10 pt-6 lg:grid-cols-[minmax(0,0.88fr)_minmax(560px,1.12fr)] lg:items-center lg:gap-14 lg:pt-0 xl:gap-20">
          <section className="flex min-w-0 items-center justify-center py-7 lg:py-10">
            <div className="w-full max-w-[520px]">
              <div className="mb-8 text-center">
                <h1 className="m-0 text-[42px] font-normal leading-[1.02] tracking-[-0.045em] text-[#171717] sm:text-[54px] lg:text-[58px] [font-family:Georgia,'Times_New_Roman',serif]">
                  Operate what&apos;s next
                </h1>
                <p className="mt-4 text-[15px] leading-6 text-[#555] sm:text-base">
                  Your workspace for modern data operations
                </p>
              </div>

              <div className="mx-auto w-full max-w-[390px]">
                <LoginPanel />
              </div>
            </div>
          </section>

          <aside className="hidden min-w-0 justify-end lg:flex">
            <div className="relative h-[min(760px,calc(100vh-96px))] min-h-[600px] w-full max-w-[690px] overflow-hidden rounded-[24px] bg-[#ecece9]">
              <video
                className="h-full w-full object-cover"
                autoPlay
                loop
                muted
                playsInline
                preload="metadata"
                aria-label="Yak Ops login visual"
              >
                <source src={LOGIN_VIDEO_URL} type="video/mp4" />
              </video>
              <div className="pointer-events-none absolute inset-0 bg-gradient-to-t from-black/10 via-transparent to-white/5" />
            </div>
          </aside>
        </div>
      </div>
    </main>
  );
}
