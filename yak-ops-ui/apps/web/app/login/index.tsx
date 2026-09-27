import { useState } from "react";

import LoginCharacters from "./LoginCharacters";
import LoginPanel from "./LoginPanel";
import type { LoginFocusState } from "./login-interaction";

interface LoginPageProps {
  onAuthenticated: () => Promise<void>;
}

export default function LoginPage({ onAuthenticated }: LoginPageProps) {
  const [focusState, setFocusState] = useState<LoginFocusState>("idle");

  return (
    <main className="min-h-screen bg-white md:grid md:grid-cols-[7fr_5fr]">
      <section className="hidden min-h-screen overflow-hidden md:block">
        <LoginCharacters focusState={focusState} />
      </section>

      <section className="flex min-h-screen items-center justify-center bg-white px-6 py-12 sm:px-10 lg:px-14">
        <div className="w-full max-w-[380px]">
          <div className="mb-8 text-center">
            <h1 className="m-0 text-[32px] font-bold leading-[1.15] tracking-[-0.025em] text-[#0f172a]">
              Welcome back!
            </h1>
            <p className="mt-2.5 text-[14px] text-[#64748b]">Please enter your details</p>
          </div>

          <LoginPanel onAuthenticated={onAuthenticated} onFocusStateChange={setFocusState} />
        </div>
      </section>
    </main>
  );
}
