import loginHeroVideo from "@/assets/video/login-hero.mp4";
import LoginPanel from "./LoginPanel";

export default function LoginPage() {
  return (
    <main className="h-screen overflow-y-auto bg-[#fbfbfa] text-[#171717]">
      <div className="mx-auto flex min-h-screen w-full max-w-[1540px] flex-col px-6 py-4 sm:px-10 lg:px-12 lg:pb-6 lg:pt-5 xl:px-16">
        <header className="flex h-11 shrink-0 items-center">
          <img
            src="/logo1.png"
            alt="Yak Ops 一体化"
            className="h-9 w-auto select-none object-contain sm:h-9"
            draggable={false}
          />
        </header>

        <div className="grid flex-1 grid-cols-1 gap-10 pt-6 lg:grid-cols-[minmax(0,0.92fr)_minmax(520px,1fr)] lg:items-center lg:gap-12 lg:pt-0 xl:gap-16">
          <section className="flex min-w-0 items-center justify-center py-7 lg:py-10">
            <div className="w-full max-w-[520px]">
              <div className="mb-8 text-center">
                <h1 className="m-0 text-[42px] font-normal leading-[1.02] tracking-[-0.045em] text-[#171717] sm:text-[54px] lg:text-[58px]">
                  Data ops, simplified.
                </h1>

                <p className="mt-4 text-[15px] leading-6 text-[#555] sm:text-base">
                  One workspace for your data.
                </p>
              </div>

              <div className="mx-auto w-full max-w-[390px]">
                <LoginPanel />
              </div>
            </div>
          </section>

          <aside className="hidden min-w-0 justify-end lg:flex">
            <div className="relative h-[min(734px,calc(100vh-112px))] w-[min(587px,calc((100vh-112px)*0.8))] overflow-hidden rounded-[24px] bg-[#ecece9] shadow-[0_12px_36px_rgba(15,23,42,0.06)]">
              <video
                className="h-full w-full object-cover"
                autoPlay
                loop
                muted
                playsInline
                preload="metadata"
                aria-label="Yak Ops login visual"
              >
                <source src={loginHeroVideo} type="video/mp4" />
              </video>

              <div className="pointer-events-none absolute inset-0 bg-gradient-to-t from-black/10 via-transparent to-white/5" />
            </div>
          </aside>
        </div>

        <footer className="flex h-8 shrink-0 items-center">
          <span className="text-[12px] tracking-[0.01em] text-black/45">
            Built by 魏福万
          </span>
        </footer>
      </div>
    </main>
  );
}