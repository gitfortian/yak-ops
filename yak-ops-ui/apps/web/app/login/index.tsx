import { SplitSparkIcon } from "@yak-ops/yak-ui";
import { useState } from "react";

import LoginCharacters from "./LoginCharacters";
import LoginPanel from "./LoginPanel";
import WeChatQrHelp from "./WeChatQrHelp";
import type { LoginFocusState, LoginResultState } from "./login-interaction";

import "./login-form.css";

interface LoginPageProps {
  onAuthenticated: () => Promise<void>;
}

export default function LoginPage({ onAuthenticated }: LoginPageProps) {
  const [focusState, setFocusState] = useState<LoginFocusState>("idle");
  const [resultState, setResultState] = useState<LoginResultState>("idle");
  const [passwordVisible, setPasswordVisible] = useState(false);

  return (
    <main className="h-full min-h-screen overflow-y-auto bg-white md:grid md:grid-cols-[7fr_5fr]">
      <section className="hidden min-h-screen overflow-hidden md:block">
        <LoginCharacters
          focusState={focusState}
          resultState={resultState}
          passwordVisible={passwordVisible}
        />
      </section>

      <section
        aria-labelledby="login-title"
        className="flex min-h-screen min-w-0 flex-col items-center bg-white px-6 py-8 sm:px-10 sm:py-10 lg:px-14"
      >
        <div className="flex w-full max-w-[380px] flex-1 flex-col">
          <div className="pb-8 pt-4 sm:pt-[clamp(2rem,7vh,4rem)]">
            <div className="mb-8 text-center">
              <SplitSparkIcon
                size={40}
                className="mx-auto mb-8 text-[var(--yak-components-page-header-title)]"
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
          <div className="mt-auto pt-8">
            <WeChatQrHelp />
          </div>
        </div>
      </section>
    </main>
  );
}
