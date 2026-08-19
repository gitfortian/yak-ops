import { login } from "@/services/security/account";
import { resetAuthenticationFailure } from "@/utils/request";
import { getSafeReturnTo } from "@/utils/security/redirect";
import { history, useIntl, useModel } from "@umijs/max";
import { App, Button, Form, Input } from "antd";
import { useForm } from "antd/es/form/Form";
import { useState } from "react";
import { flushSync } from "react-dom";
import GoogleLoginButton from "./components/GoogleLoginButton";

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
    } finally {
      setLoading(false);
    }
  };

  const handleGoogleSuccess = async () => {
    const userInfo = await fetchUserInfo();

    if (!userInfo) {
      setLoading(false);
      return;
    }

    redirectAfterLogin();
  };

  return (
    <div className="w-full">
      <div className="rounded-[26px] border border-[#e4e4e1] bg-white p-5 shadow-[0_12px_40px_rgba(15,23,42,0.035)] sm:p-6">
        <GoogleLoginButton
          className="!h-11 !rounded-xl !border-[#dededb] !bg-white !font-medium !text-[#1b1b1b] !shadow-none hover:!border-[#bdbdb8] hover:!bg-[#fafafa] hover:!text-[#1b1b1b]"
          loading={loading}
          onStart={() => setLoading(true)}
          onSuccess={handleGoogleSuccess}
          onError={() => setLoading(false)}
        />

        <div className="my-5 flex items-center gap-3" aria-hidden="true">
          <span className="h-px flex-1 bg-[#ececea]" />
          <span className="text-[11px] font-medium uppercase tracking-[0.12em] text-[#8a8a86]">
            or
          </span>
          <span className="h-px flex-1 bg-[#ececea]" />
        </div>

        <Form
          layout="vertical"
          form={form}
          requiredMark={false}
          onFinish={handleAccountLogin}
        >
          <Form.Item
            className="!mb-4"
            label={
              <span className="text-[13px] font-medium text-[#333]">
                Username
              </span>
            }
            name="userName"
            rules={[{ required: true, message: "请输入用户名" }]}
          >
            <Input
              className="!h-11 !rounded-xl !border-[#dededb] !bg-white !px-3.5 !text-[15px] !shadow-none placeholder:!text-[#aaa] hover:!border-[#bdbdb8] focus:!border-[#171717]"
              placeholder="Enter your username"
              autoComplete="username"
            />
          </Form.Item>

          <Form.Item
            className="!mb-2.5"
            label={
              <span className="text-[13px] font-medium text-[#333]">
                Password
              </span>
            }
            name="userPassword"
            rules={[
              {
                required: true,
                message: "Please enter your password",
              },
            ]}
          >
            <Input.Password
              className="!h-11 !rounded-xl !border-[#dededb] !bg-white !px-3.5 !shadow-none hover:!border-[#bdbdb8] focus-within:!border-[#171717] [&>input.ant-input]:!bg-white [&>input.ant-input]:!text-[15px]"
              placeholder="Enter your password"
              autoComplete="current-password"
            />
          </Form.Item>

          <div className="mb-5 flex justify-end">
            <button
              type="button"
              className="border-0 bg-transparent p-0 text-[13px] text-[#666] transition-colors hover:text-[#171717]"
            >
              Forgot password?
            </button>
          </div>

          <Button
            block
            type="primary"
            htmlType="submit"
            loading={loading}
            className="!h-11 !rounded-xl !border-[#171717] !bg-[#171717] !font-medium !text-white !shadow-none hover:!border-[#292929] hover:!bg-[#292929]"
          >
            Log in
          </Button>
        </Form>

        <p className="mb-0 mt-4 text-center text-[11px] leading-5 text-[#92928d]">
          By continuing, you agree to use Yak Ops according to your organization&apos;s access policy.
        </p>
      </div>

      <div className="mt-5 text-center text-[13px] text-[#73736f]">
        Don&apos;t have an account?{" "}
        <button
          type="button"
          className="border-0 bg-transparent p-0 font-semibold text-[#171717] hover:underline"
        >
          Sign up
        </button>
      </div>
    </div>
  );
}
