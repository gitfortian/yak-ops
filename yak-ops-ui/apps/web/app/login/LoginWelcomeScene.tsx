import type { ComponentProps } from "react";

import LoginCharacters from "./LoginCharacters";

import "./login-welcome-scene.css";

export default function LoginWelcomeScene(props: ComponentProps<typeof LoginCharacters>) {
  return (
    <div className="yak-login-welcome-scene">
      <div className="yak-login-welcome-scene__copy">
        <p className="yak-login-welcome-scene__brand">YAK OPS</p>
        <h2 className="yak-login-welcome-scene__title">复杂的是系统，不是用户</h2>
        <p className="yak-login-welcome-scene__description">连接数据，管理同步，让运维更简单。</p>
      </div>

      <div className="yak-login-welcome-scene__stage">
        <div className="yak-login-welcome-scene__ground" aria-hidden="true" />
        <LoginCharacters {...props} />
      </div>
    </div>
  );
}
