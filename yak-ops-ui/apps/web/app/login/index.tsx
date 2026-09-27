import { Activity, Database } from "lucide-react";

import LoginPanel from "./LoginPanel";
import "./login.css";

interface LoginPageProps {
  onAuthenticated: () => Promise<void>;
}

function DataFlowVisual() {
  return (
    <div
      className="relative mt-12 h-[286px] w-full max-w-[620px] overflow-hidden rounded-[20px] border border-[#e2e6ec] bg-white/70"
      aria-hidden="true"
    >
      <div className="absolute inset-0 bg-[linear-gradient(rgba(37,40,50,0.035)_1px,transparent_1px),linear-gradient(90deg,rgba(37,40,50,0.035)_1px,transparent_1px)] bg-[size:28px_28px]" />

      <svg className="absolute inset-0 h-full w-full" viewBox="0 0 620 286" fill="none">
        <path d="M132 74C206 74 216 142 284 142" stroke="#D9DDE3" strokeWidth="1.5" />
        <path d="M132 214C206 214 216 154 284 154" stroke="#D9DDE3" strokeWidth="1.5" />
        <path d="M354 148C424 148 442 86 500 86" stroke="#D9DDE3" strokeWidth="1.5" />
        <path d="M354 148C424 148 442 206 500 206" stroke="#D9DDE3" strokeWidth="1.5" />

        <circle r="4" fill="var(--yak-color-primary)" className="yak-login-motion">
          <animateMotion
            dur="5.4s"
            repeatCount="indefinite"
            path="M132 74C206 74 216 142 284 142"
          />
        </circle>
        <circle r="4" fill="var(--yak-color-primary)" className="yak-login-motion">
          <animateMotion
            begin="1.8s"
            dur="5.4s"
            repeatCount="indefinite"
            path="M132 214C206 214 216 154 284 154"
          />
        </circle>
        <circle r="4" fill="var(--yak-color-primary)" className="yak-login-motion">
          <animateMotion
            begin="0.9s"
            dur="5.4s"
            repeatCount="indefinite"
            path="M354 148C424 148 442 86 500 86"
          />
        </circle>
        <circle r="4" fill="var(--yak-color-primary)" className="yak-login-motion">
          <animateMotion
            begin="2.7s"
            dur="5.4s"
            repeatCount="indefinite"
            path="M354 148C424 148 442 206 500 206"
          />
        </circle>
      </svg>

      <div className="absolute left-[36px] top-[45px] flex w-[112px] items-center gap-2 rounded-[10px] border border-[#e2e6ec] bg-white px-3 py-2.5 shadow-[0_6px_18px_rgba(31,35,41,0.04)]">
        <Database size={16} className="text-[var(--yak-color-primary)]" />
        <span className="text-[13px] font-medium text-[#343841]">MySQL</span>
      </div>

      <div className="absolute bottom-[43px] left-[36px] flex w-[112px] items-center gap-2 rounded-[10px] border border-[#e2e6ec] bg-white px-3 py-2.5 shadow-[0_6px_18px_rgba(31,35,41,0.04)]">
        <Database size={16} className="text-[var(--yak-color-primary)]" />
        <span className="text-[13px] font-medium text-[#343841]">PostgreSQL</span>
      </div>

      <div className="absolute left-1/2 top-1/2 flex w-[144px] -translate-x-1/2 -translate-y-1/2 flex-col items-center rounded-[14px] border border-[#d9e1ff] bg-white px-4 py-4 shadow-[0_10px_30px_rgba(0,51,255,0.08)]">
        <div className="yak-login-flow-core flex size-9 items-center justify-center rounded-[10px] bg-[var(--yak-color-primary)] text-white">
          <Activity size={18} />
        </div>
        <span className="mt-2 text-[13px] font-semibold text-[#252832]">Yak Ops</span>
        <span className="mt-0.5 text-[11px] text-[#8f949e]">Batch · CDC</span>
      </div>

      <div className="absolute right-[34px] top-[57px] flex w-[102px] items-center gap-2 rounded-[10px] border border-[#e2e6ec] bg-white px-3 py-2.5 shadow-[0_6px_18px_rgba(31,35,41,0.04)]">
        <Database size={16} className="text-[#667085]" />
        <span className="text-[13px] font-medium text-[#343841]">Oracle</span>
      </div>

      <div className="absolute bottom-[55px] right-[34px] flex w-[102px] items-center gap-2 rounded-[10px] border border-[#e2e6ec] bg-white px-3 py-2.5 shadow-[0_6px_18px_rgba(31,35,41,0.04)]">
        <Database size={16} className="text-[#667085]" />
        <span className="text-[13px] font-medium text-[#343841]">MySQL</span>
      </div>
    </div>
  );
}

export default function LoginPage({ onAuthenticated }: LoginPageProps) {
  return (
    <main className="min-h-screen bg-[#f7f8fa] text-[#252832] lg:grid lg:grid-cols-[minmax(0,1.08fr)_minmax(440px,0.92fr)]">
      <section className="relative hidden min-h-screen overflow-hidden border-r border-[#e7e9ed] bg-[#f7f8fa] lg:flex lg:flex-col">
        <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_24%_18%,rgba(0,51,255,0.08),transparent_30%)]" />

        <header className="relative z-10 flex h-20 shrink-0 items-center px-10 xl:px-14">
          <img
            src="/logo1.png"
            alt="Yak Ops"
            className="h-8 w-auto select-none object-contain"
            draggable={false}
          />
        </header>

        <div className="relative z-10 flex flex-1 items-center px-10 py-10 xl:px-14">
          <div className="yak-login-enter w-full max-w-[680px]">
            <p className="mb-4 text-[12px] font-semibold uppercase tracking-[0.16em] text-[var(--yak-color-primary)]">
              Data operations workspace
            </p>
            <h1 className="max-w-[650px] text-[46px] font-semibold leading-[1.08] tracking-[-0.035em] text-[#252832] xl:text-[52px]">
              Build, move, and operate data with confidence.
            </h1>
            <p className="mt-5 max-w-[560px] text-[15px] leading-7 text-[#667085]">
              从数据源连接、离线同步到 CDC，让数据流转和日常运维保持在一个清晰的工作空间里。
            </p>

            <DataFlowVisual />
          </div>
        </div>

        <footer className="relative z-10 flex h-16 shrink-0 items-center px-10 text-[12px] text-[#98a2b3] xl:px-14">
          Yak Ops · One workspace for your data
        </footer>
      </section>

      <section className="relative flex min-h-screen items-center justify-center bg-white px-6 py-12 sm:px-10 lg:px-12 xl:px-16">
        <header className="absolute left-6 top-6 sm:left-10 lg:hidden">
          <img
            src="/logo1.png"
            alt="Yak Ops"
            className="h-8 w-auto select-none object-contain"
            draggable={false}
          />
        </header>

        <div className="yak-login-enter yak-login-enter-delayed w-full max-w-[400px]">
          <div className="mb-8">
            <p className="mb-2 text-[12px] font-semibold uppercase tracking-[0.14em] text-[var(--yak-color-primary)]">
              Yak Ops
            </p>
            <h2 className="text-[30px] font-semibold leading-tight tracking-[-0.025em] text-[#252832]">
              Welcome to Yak Ops
            </h2>
            <p className="mt-2 text-[14px] leading-6 text-[#667085]">
              登录你的工作空间，继续管理数据连接与数据任务。
            </p>
          </div>

          <LoginPanel onAuthenticated={onAuthenticated} />

          <p className="mt-10 text-center text-[11px] text-[#b0b4bc] lg:hidden">
            Yak Ops · Data operations workspace
          </p>
        </div>
      </section>
    </main>
  );
}
