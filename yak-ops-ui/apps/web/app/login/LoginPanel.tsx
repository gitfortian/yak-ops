import {
  Button,
  Input,
  PasswordInput,
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@yak-ops/yak-ui";
import { AlertCircle } from "lucide-react";
import { useRef, useState, type FormEvent } from "react";

import { notifyOnce } from "@/utils/notification";
import { login } from "../../service/auth";
import type { LoginFocusState } from "./login-interaction";

const WECHAT_QR_CODE_SRC = "/wechat_qr.png";

interface LoginPanelProps {
  onAuthenticated: () => Promise<void>;
  onFocusStateChange: (state: LoginFocusState) => void;
  onPasswordVisibilityChange: (visible: boolean) => void;
}

interface LoginValues {
  userName: string;
  userPassword: string;
}

function ValidationMessage({ id, children }: { id: string; children: string }) {
  return (
    <span
      id={id}
      className="mt-1.5 inline-flex min-h-[18px] items-center gap-1.5 text-[12px] leading-[18px] text-[#b42318]"
    >
      <AlertCircle size={12} className="shrink-0" />
      <span>{children}</span>
    </span>
  );
}

function WeChatQrHelp() {
  const [qrCodeAvailable, setQrCodeAvailable] = useState(true);
  const [open, setOpen] = useState(false);
  const closeTimerRef = useRef<number>();

  const openPopover = () => {
    if (closeTimerRef.current) window.clearTimeout(closeTimerRef.current);
    setOpen(true);
  };

  const scheduleClose = () => {
    closeTimerRef.current = window.setTimeout(() => setOpen(false), 120);
  };

  return (
    <div className="mt-4 text-center text-[11px] leading-5 text-[#8f949e]">
      获取账号 / 密码，请扫描{" "}
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger
          type="button"
          className="cursor-help border-0 bg-transparent p-0 font-medium text-[#667085] underline decoration-[#d9dde3] underline-offset-2 transition-colors hover:text-[#252832]"
          onMouseEnter={openPopover}
          onMouseLeave={scheduleClose}
        >
          微信公众号二维码
        </PopoverTrigger>
        <PopoverContent
          side="top"
          className="w-[188px] p-2"
          onMouseEnter={openPopover}
          onMouseLeave={scheduleClose}
        >
          <div className="flex flex-col items-center gap-2">
            {qrCodeAvailable ? (
              <img
                src={WECHAT_QR_CODE_SRC}
                alt="微信公众号二维码"
                className="h-40 w-40 rounded-xl object-cover"
                onError={() => setQrCodeAvailable(false)}
              />
            ) : (
              <div className="flex h-40 w-40 items-center justify-center rounded-xl border border-dashed border-[#d9dde3] bg-[#f7f8fa] px-5 text-center text-[12px] leading-5 text-[#98a2b3]">
                微信公众号二维码待上传
              </div>
            )}
            <span className="text-center text-[11px] leading-5 text-[#8f949e]">
              输入 <span className="rounded bg-black/[0.03] px-1">9527</span> 获取账号 / 密码
            </span>
          </div>
        </PopoverContent>
      </Popover>
    </div>
  );
}

export default function LoginPanel({
  onAuthenticated,
  onFocusStateChange,
  onPasswordVisibilityChange,
}: LoginPanelProps) {
  const [values, setValues] = useState<LoginValues>({
    userName: "",
    userPassword: "",
  });
  const [errors, setErrors] = useState<Partial<Record<keyof LoginValues, string>>>({});
  const [loading, setLoading] = useState(false);

  const handleAccountLogin = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const nextErrors: Partial<Record<keyof LoginValues, string>> = {};
    if (!values.userName.trim()) nextErrors.userName = "请输入用户名";
    if (!values.userPassword) nextErrors.userPassword = "请输入密码";
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    try {
      setLoading(true);
      await login({
        userName: values.userName.trim(),
        pw: values.userPassword,
      });
      await onAuthenticated();

      notifyOnce("login-success", {
        type: "success",
        title: "登录成功！",
        description: "正在进入 Yak Ops",
        meta: "身份验证完成",
        duration: 2,
      });
    } catch {
      // Global request handling surfaces HTTP, business and network failures once.
    } finally {
      setLoading(false);
    }
  };

  return (
    <form className="space-y-5" noValidate onSubmit={(event) => void handleAccountLogin(event)}>
      <div>
        <label
          htmlFor="login-username"
          className="mb-2 block text-[13px] font-medium leading-5 text-[#344054]"
        >
          用户名
        </label>
        <Input
          id="login-username"
          size="large"
          variant="outlined"
          autoComplete="username"
          placeholder="请输入用户名"
          value={values.userName}
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
        {errors.userName ? (
          <ValidationMessage id="login-username-error">{errors.userName}</ValidationMessage>
        ) : null}
      </div>

      <div>
        <label
          htmlFor="login-password"
          className="mb-2 block text-[13px] font-medium leading-5 text-[#344054]"
        >
          密码
        </label>
        <PasswordInput
          id="login-password"
          size="large"
          variant="outlined"
          autoComplete="current-password"
          placeholder="请输入密码"
          showPasswordLabel="显示密码"
          hidePasswordLabel="隐藏密码"
          onVisibilityChange={onPasswordVisibilityChange}
          value={values.userPassword}
          aria-invalid={Boolean(errors.userPassword) || undefined}
          aria-describedby={errors.userPassword ? "login-password-error" : undefined}
          onFocus={() => onFocusStateChange("userPassword")}
          onBlur={() => onFocusStateChange("idle")}
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
        {errors.userPassword ? (
          <ValidationMessage id="login-password-error">{errors.userPassword}</ValidationMessage>
        ) : null}
      </div>

      <Button variant="primary" size="large" type="submit" loading={loading} className="w-full">
        登录
      </Button>

      <WeChatQrHelp />
    </form>
  );
}
