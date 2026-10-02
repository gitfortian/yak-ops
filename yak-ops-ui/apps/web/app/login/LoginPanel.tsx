import {
  Button,
  Field,
  FieldError,
  FieldLabel,
  Input,
  PasswordInput,
  Popover,
  PopoverContent,
  PopoverDescription,
  PopoverTitle,
  PopoverTrigger,
} from "@yak-ops/yak-ui";
import { useEffect, useRef, useState, type FormEvent } from "react";

import { notifyOnce } from "@/utils/notification";
import { login } from "../../service/auth";
import {
  LOGIN_FAILURE_MOTION_MS,
  LOGIN_SUCCESS_MOTION_MS,
  type LoginFocusState,
  type LoginResultState,
} from "./login-interaction";

const WECHAT_QR_CODE_SRC = "/wechat_qr.png";

interface LoginPanelProps {
  onAuthenticated: () => Promise<void>;
  onFocusStateChange: (state: LoginFocusState) => void;
  onLoginResultChange: (state: LoginResultState) => void;
  onPasswordVisibilityChange: (visible: boolean) => void;
}

interface LoginValues {
  userName: string;
  userPassword: string;
}

function waitForMotion(duration: number, signal: AbortSignal) {
  if (signal.aborted || window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
    return Promise.resolve();
  }

  return new Promise<void>((resolve) => {
    const finish = () => {
      window.clearTimeout(timer);
      signal.removeEventListener("abort", finish);
      resolve();
    };
    const timer = window.setTimeout(finish, duration);
    signal.addEventListener("abort", finish, { once: true });
  });
}

function WeChatQrHelp() {
  const [qrCodeAvailable, setQrCodeAvailable] = useState(true);

  return (
    <div className="mt-5 text-center text-xs leading-5 text-[var(--yak-components-button-ghost-text)]">
      需要账号？{" "}
      <Popover>
        <PopoverTrigger
          type="button"
          openOnHover
          delay={120}
          closeDelay={120}
          className="cursor-pointer rounded-sm border-0 bg-transparent px-1 py-0.5 font-medium text-[var(--yak-color-primary)] underline underline-offset-4 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--yak-color-primary)]"
        >
          扫码获取
        </PopoverTrigger>
        <PopoverContent side="top" className="w-52 max-w-[calc(100vw-2rem)] p-3">
          <PopoverTitle className="sr-only">获取账号和密码</PopoverTitle>
          <div className="flex flex-col items-center gap-2">
            {qrCodeAvailable ? (
              <img
                src={WECHAT_QR_CODE_SRC}
                alt="微信公众号二维码"
                width={160}
                height={160}
                className="h-40 w-40 max-w-full rounded-xl object-contain"
                onError={() => setQrCodeAvailable(false)}
              />
            ) : (
              <div className="flex h-40 w-40 max-w-full items-center justify-center px-3 text-center text-xs leading-5">
                二维码暂时无法加载，请联系管理员获取账号。
              </div>
            )}
            <PopoverDescription className="m-0 text-center text-xs leading-5">
              关注公众号，发送 <strong>9527</strong> 获取账号和密码。
            </PopoverDescription>
          </div>
        </PopoverContent>
      </Popover>
    </div>
  );
}

export default function LoginPanel({
  onAuthenticated,
  onFocusStateChange,
  onLoginResultChange,
  onPasswordVisibilityChange,
}: LoginPanelProps) {
  const [values, setValues] = useState<LoginValues>({
    userName: "",
    userPassword: "",
  });
  const [errors, setErrors] = useState<Partial<Record<keyof LoginValues, string>>>({});
  const [loading, setLoading] = useState(false);
  const submissionRef = useRef<AbortController | null>(null);
  const usernameRef = useRef<HTMLInputElement | null>(null);
  const passwordRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    return () => {
      // Invalidate UI continuations; this does not cancel the authentication request.
      submissionRef.current?.abort();
      submissionRef.current = null;
    };
  }, []);

  const handleAccountLogin = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (submissionRef.current) return;

    const nextErrors: Partial<Record<keyof LoginValues, string>> = {};
    if (!values.userName.trim()) nextErrors.userName = "请输入用户名";
    if (!values.userPassword) nextErrors.userPassword = "请输入密码";
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) {
      const firstInvalidInput = nextErrors.userName ? usernameRef.current : passwordRef.current;
      firstInvalidInput?.focus();
      return;
    }

    const submission = new AbortController();
    submissionRef.current = submission;
    let loginSucceeded = false;

    try {
      setLoading(true);
      onLoginResultChange("submitting");

      await login({
        userName: values.userName.trim(),
        pw: values.userPassword,
      });

      if (submission.signal.aborted) return;
      loginSucceeded = true;
      onLoginResultChange("success");
      await waitForMotion(LOGIN_SUCCESS_MOTION_MS, submission.signal);
      if (submission.signal.aborted) return;
      await onAuthenticated();

      notifyOnce("login-success", {
        type: "success",
        title: "登录成功！",
        description: "正在进入 Yak Ops",
        meta: "身份验证完成",
        duration: 2,
      });
    } catch {
      if (submission.signal.aborted) return;
      if (!loginSucceeded) {
        onLoginResultChange("failure");
        await waitForMotion(LOGIN_FAILURE_MOTION_MS, submission.signal);
        if (submission.signal.aborted) return;
        onLoginResultChange("idle");
      } else {
        onLoginResultChange("idle");
      }
      // Global request handling surfaces HTTP, business and network failures once.
    } finally {
      if (submissionRef.current === submission) {
        submissionRef.current = null;
        setLoading(false);
      }
    }
  };

  return (
    <>
      <form
        noValidate
        aria-label="账号登录"
        aria-busy={loading}
        onSubmit={(event) => void handleAccountLogin(event)}
      >
        <Field name="userName" invalid={Boolean(errors.userName)} className="!gap-0">
          <FieldLabel htmlFor="login-username" className="mb-2">
            用户名
          </FieldLabel>
          <Input
            ref={usernameRef}
            id="login-username"
            name="userName"
            size="large"
            variant="outlined"
            autoComplete="username"
            autoCapitalize="none"
            spellCheck={false}
            placeholder="请输入用户名"
            readOnly={loading}
            value={values.userName}
            aria-required="true"
            aria-invalid={Boolean(errors.userName) || undefined}
            aria-describedby={errors.userName ? "login-username-error" : undefined}
            onFocus={() => onFocusStateChange("userName")}
            onBlur={() => onFocusStateChange("idle")}
            onChange={(event) => {
              setValues((current) => ({ ...current, userName: event.target.value }));
              if (errors.userName) {
                setErrors((current) => ({ ...current, userName: undefined }));
              }
            }}
          />
          <div className="grid min-h-6 items-start pt-1" aria-live="polite" aria-atomic="true">
            <FieldError id="login-username-error" match={Boolean(errors.userName)}>
              {errors.userName}
            </FieldError>
          </div>
        </Field>

        <Field
          name="userPassword"
          invalid={Boolean(errors.userPassword)}
          className="mt-2 !gap-0"
          onFocus={() => onFocusStateChange("userPassword")}
          onBlur={(event) => {
            if (!event.currentTarget.contains(event.relatedTarget)) onFocusStateChange("idle");
          }}
        >
          <FieldLabel htmlFor="login-password" className="mb-2">
            密码
          </FieldLabel>
          <PasswordInput
            ref={passwordRef}
            id="login-password"
            name="userPassword"
            size="large"
            variant="outlined"
            autoComplete="current-password"
            placeholder="请输入密码"
            showPasswordLabel="显示密码"
            hidePasswordLabel="隐藏密码"
            onVisibilityChange={onPasswordVisibilityChange}
            readOnly={loading}
            value={values.userPassword}
            aria-required="true"
            aria-invalid={Boolean(errors.userPassword) || undefined}
            aria-describedby={errors.userPassword ? "login-password-error" : undefined}
            onChange={(event) => {
              setValues((current) => ({ ...current, userPassword: event.target.value }));
              if (errors.userPassword) {
                setErrors((current) => ({
                  ...current,
                  userPassword: undefined,
                }));
              }
            }}
          />
          <div className="grid min-h-6 items-start pt-1" aria-live="polite" aria-atomic="true">
            <FieldError id="login-password-error" match={Boolean(errors.userPassword)}>
              {errors.userPassword}
            </FieldError>
          </div>
        </Field>

        <Button
          variant="primary"
          size="large"
          type="submit"
          loading={loading}
          className="mt-2 w-full"
        >
          登录
        </Button>

        <WeChatQrHelp />
      </form>
      {/* Outside the busy form so the status can be announced while it is submitting. */}
      <p className="sr-only" role="status" aria-live="polite" aria-atomic="true">
        {loading ? "正在登录，请稍候。" : ""}
      </p>
    </>
  );
}
