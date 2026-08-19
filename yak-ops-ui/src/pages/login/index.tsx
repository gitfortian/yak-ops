import loginHeroVideo from "@/assets/video/login-hero.mp4";
import { Sparkles } from "lucide-react";
import LoginPanel from "./LoginPanel";

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

        <div className="grid flex-1 grid-cols-1 gap-10 pt-6 lg:grid-cols-[minmax(0,0.92fr)_minmax(500px,1.08fr)] lg:items-center lg:gap-12 lg:pt-0 xl:gap-16">
          <section className="flex min-w-0 items-center justify-center py-7 lg:py-10">
            <div className="w-full max-w-[520px]">
              <div className="mb-8 text-center">
                <h1 className="m-0 text-[42px] font-normal leading-[1.02] tracking-[-0.045em] text-[#171717] sm:text-[54px] lg:text-[58px]">
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

          <aside className="hidden min-w-0 items-center justify-center lg:flex xl:justify-end">
            <div className="relative h-[clamp(560px,calc(100vh-132px),734px)] aspect-[4/5] flex-none overflow-hidden rounded-[22px] bg-[#ecece9]">
              <video
                className="h-full w-full object-cover"
                src={loginHeroVideo}
                autoPlay
                loop
                muted
                playsInline
                preload="metadata"
                aria-label="Yak Ops login visual"
              />
              <div className="pointer-events-none absolute inset-0 bg-gradient-to-t from-black/10 via-transparent to-white/5" />
            </div>
          </aside>
        </div>
      </div>
    </main>
  );
}
