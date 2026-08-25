import { ExclamationCircleOutlined } from "@ant-design/icons";
import { login } from "@/services/security/account";
import { resetAuthenticationFailure } from "@/utils/request";
import { getSafeReturnTo } from "@/utils/security/redirect";
import { history, useIntl, useModel } from "@umijs/max";
import { App, Button, Form, Input, Popover, type InputProps } from "antd";
import { useForm } from "antd/es/form/Form";
import { useState } from "react";
import { flushSync } from "react-dom";

const WECHAT_QR_CODE_SRC = "/wechat-official-account-qr.png";
const FORM_ITEM_CLASS_NAME =
  "!mb-5 [&_.ant-form-item-explain]:!pt-1.5 [&_.ant-form-item-explain-error]:!text-[12px] [&_.ant-form-item-explain-error]:!leading-[18px] [&_.ant-form-item-explain-error]:!text-[#b42318]";

type FloatingInputProps = InputProps & {
  label: string;
  password?: boolean;
};

function FloatingInput({
  label,
  password = false,
  onBlur,
  onFocus,
  value,
  ...inputProps
}: FloatingInputProps) {
  const [focused, setFocused] = useState(false);
  const { status } = Form.Item.useStatus();
  const floating = focused || String(value ?? "").length > 0;
  const hasError = status === "error";

  const handleFocus: InputProps["onFocus"] = (event) => {
    setFocused(true);
    onFocus?.(event);
  };

  const handleBlur: InputProps["onBlur"] = (event) => {
    setFocused(false);
    onBlur?.(event);
  };

  const className = password
    ? `!h-11 !rounded-full !bg-white !px-4 !shadow-none [&>input.ant-input]:!bg-white [&>input.ant-input]:!text-[15px] ${
        hasError
          ? "!border-[#d92d20] hover:!border-[#d92d20] focus-within:!border-[#d92d20]"
          : "!border-[#dededb] hover:!border-[#bdbdb8] focus-within:!border-[#171717]"
      }`
    : `!h-11 !rounded-full !bg-white !px-4 !text-[15px] !shadow-none ${
        hasError
          ? "!border-[#d92d20] hover:!border-[#d92d20] focus:!border-[#d92d20]"
          : "!border-[#dededb] hover:!border-[#bdbdb8] focus:!border-[#171717]"
      }`;

  const controlProps: InputProps = {
    ...inputProps,
    value,
    className,
    placeholder: "",
    onFocus: handleFocus,
    onBlur: handleBlur,
  };

  return (
    <div className="relative">
      {password ? (
        <Input.Password {...controlProps} />
      ) : (
        <Input {...controlProps} />
      )}
      <label
        htmlFor={inputProps.id}
        className={`pointer-events-none absolute left-4 z-10 bg-white px-1 transition-all duration-200 ease-out ${
          floating
            ? "top-0 -translate-y-1/2 text-[12px] font-medium text-[#333]"
            : "top-1/2 -translate-y-1/2 text-[15px] text-[#aaa]"
        }`}
      >
        {label}
      </label>
    </div>
  );
}

function ValidationMessage({ children }: { children: string }) {
  return (
    <span className="inline-flex items-start gap-1.5">
      <ExclamationCircleOutlined className="mt-[2px] shrink-0 text-[12px]" />
      <span>{children}</span>
    </span>
  );
}

function WeChatQrHelp() {
  const [qrCodeAvailable, setQrCodeAvailable] = useState(true);

  const qrCodeContent = (
    <div className="flex w-[176px] flex-col items-center gap-2 p-1">
      {qrCodeAvailable ? (
        <img
          src={WECHAT_QR_CODE_SRC}
          alt="微信公众号二维码"
          className="h-40 w-40 rounded-xl object-cover"
          onError={() => setQrCodeAvailable(false)}
        />
      ) : (
        <div className="flex h-40 w-40 items-center justify-center rounded-xl border border-dashed border-[#dededb] bg-[#fafafa] px-5 text-center text-[12px] leading-5 text-[#999]">
          微信公众号二维码待上传
        </div>
      )}
      <span className="text-center text-[11px] leading-5 text-[#888]">
        微信扫码获取账号 / 密码
      </span>
    </div>
  );

  return (
    <div className="mt-3 text-center text-[11px] leading-5 text-[#8c8c88]">
      获取账号 / 密码，请扫描{" "}
      <Popover placement="right" trigger="hover" content={qrCodeContent}>
        <span className="cursor-help font-medium text-[#555] underline decoration-[#d6d6d1] underline-offset-2 transition-colors hover:text-[#171717]">
          微信公众号二维码
        </span>
      </Popover>
    </div>
  );
}

export default function LoginPanel() {
  const [loading, setLoading] = useState(false);
  const [form] = useForm();

  const { initialState, setInitialState } = useModel("@@initialState");
  const { message } = App.useApp();
  const intl = useIntl();

  const fetchUserInfo = async () => {
    const userInfo = await initialState?.fetchUserInfo?.();

    if (userInfo) {
      flushSync(() => {
        setInitialState((state: any) => ({
          ...state,
          currentUser: userInfo,
          currentUserLoadError: false,
        }));
      });
    }

    return userInfo;
  };

  const redirectAfterLogin = () => {
    const requested = new URLSearchParams(window.location.search).get(
      "returnTo",
    );
    history.replace(getSafeReturnTo(requested));
  };

  const handleAccountLogin = async (values: {
    userName: string;
    userPassword: string;
  }) => {
    try {
      await form.validateFields();
      setLoading(true);

      await login({
        userName: values.userName,
        pw: values.userPassword,
      });
      resetAuthenticationFailure();
      message.success(
        intl.formatMessage({
          id: "pages.login.success",
          defaultMessage: "登录成功！",
        }),
      );

      const userInfo = await fetchUserInfo();
      if (!userInfo) throw new Error("登录后无法加载当前用户");
      redirectAfterLogin();
    } catch (_error) {
      // Request handling already surfaces authentication errors to the user.
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full rounded-[26px] border border-[#e4e4e1] bg-white p-6 shadow-[0_14px_40px_rgba(15,23,42,0.04)] sm:p-7">
      <Form
        layout="vertical"
        form={form}
        requiredMark={false}
        onFinish={handleAccountLogin}
      >
        <Form.Item
          className={FORM_ITEM_CLASS_NAME}
          name="userName"
          rules={[
            {
              required: true,
              message: <ValidationMessage>请输入用户名</ValidationMessage>,
            },
          ]}
        >
          <FloatingInput label="Username" autoComplete="username" />
        </Form.Item>

        <Form.Item
          className={FORM_ITEM_CLASS_NAME}
          name="userPassword"
          rules={[
            {
              required: true,
              message: <ValidationMessage>请输入密码</ValidationMessage>,
            },
          ]}
        >
          <FloatingInput
            label="Password"
            password
            autoComplete="current-password"
          />
        </Form.Item>

        <Button
          block
          type="primary"
          htmlType="submit"
          loading={loading}
          className="!h-11 !rounded-full !border-[#171717] !bg-[#171717] !font-medium !text-white !shadow-none hover:!border-[#292929] hover:!bg-[#292929]"
        >
          Log in
        </Button>

        <WeChatQrHelp />
      </Form>
    </div>
  );
}
