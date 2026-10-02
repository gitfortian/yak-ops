import { useState } from "react";

import LoginCharacters from "./LoginCharacters";
import LoginPanel from "./LoginPanel";
import type { LoginFocusState, LoginResultState } from "./login-interaction";

interface LoginPageProps {
  onAuthenticated: () => Promise<void>;
}

export default function LoginPage({ onAuthenticated }: LoginPageProps) {
  const [focusState, setFocusState] = useState<LoginFocusState>("idle");
  const [resultState, setResultState] = useState<LoginResultState>("idle");
  const [passwordVisible, setPasswordVisible] = useState(false);

  return (
    <main className="min-h-screen bg-white md:grid md:grid-cols-[7fr_5fr]">
      <section className="hidden min-h-screen overflow-hidden md:block">
        <LoginCharacters
          focusState={focusState}
          resultState={resultState}
          passwordVisible={passwordVisible}
        />
      </section>

      <section
        aria-labelledby="login-title"
        className="flex min-h-screen min-w-0 items-center justify-center bg-white px-6 py-10 sm:px-10 lg:px-14"
      >
        <div className="w-full max-w-[380px]">
          <div className="mb-8 text-center">
            <img
              src="/logo.png"
              alt="Yak Ops"
              draggable={false}
              className="mx-auto mb-6 h-8 w-auto max-w-full select-none object-contain"
            />
            <h1
              id="login-title"
              className="m-0 text-balance text-[28px] font-bold leading-tight tracking-tight text-[var(--yak-components-page-header-title)]"
            >
              欢迎回到 Yak Ops
            </h1>
            <p className="mt-3 text-sm leading-6 text-[var(--yak-components-button-ghost-text)]">
              登录后，开始管理你的数据任务。
            </p>
          </div>

          <LoginPanel
            onAuthenticated={onAuthenticated}
            onFocusStateChange={setFocusState}
            onLoginResultChange={setResultState}
            onPasswordVisibilityChange={setPasswordVisible}
          />
        </div>
      </section>
    </main>
  );
}
