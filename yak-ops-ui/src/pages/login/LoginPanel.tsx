import { login } from "@/services/security/account";
import { resetAuthenticationFailure } from "@/utils/request";
import { getSafeReturnTo } from "@/utils/security/redirect";
import { history, useIntl, useModel } from "@umijs/max";
import { App, Button, Form, Input, type InputProps } from "antd";
import { useForm } from "antd/es/form/Form";
import { useState } from "react";
import { flushSync } from "react-dom";

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
  const floating = focused || String(value ?? "").length > 0;

  const handleFocus: InputProps["onFocus"] = (event) => {
    setFocused(true);
    onFocus?.(event);
  };

  const handleBlur: InputProps["onBlur"] = (event) => {
    setFocused(false);
    onBlur?.(event);
  };

  const className = password
    ? "!h-11 !rounded-full !border-[#dededb] !bg-white !px-4 !shadow-none hover:!border-[#bdbdb8] focus-within:!border-[#171717] [&>input.ant-input]:!bg-white [&>input.ant-input]:!text-[15px]"
    : "!h-11 !rounded-full !border-[#dededb] !bg-white !px-4 !text-[15px] !shadow-none hover:!border-[#bdbdb8] focus:!border-[#171717]";

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
          className="!mb-5"
          name="userName"
          rules={[{ required: true, message: "请输入用户名" }]}
        >
          <FloatingInput label="Username" autoComplete="username" />
        </Form.Item>

        <Form.Item
          className="!mb-6"
          name="userPassword"
          rules={[
            {
              required: true,
              message: "Please enter your password",
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
      </Form>
    </div>
  );
}
