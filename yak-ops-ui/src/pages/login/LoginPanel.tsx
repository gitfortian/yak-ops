import { login } from "@/services/security/account";
import { resetAuthenticationFailure } from "@/utils/request";
import { getSafeReturnTo } from "@/utils/security/redirect";
import { history, useIntl, useModel } from "@umijs/max";
import { App, Button, Form, Input } from "antd";
import { useForm } from "antd/es/form/Form";
import { useState } from "react";
import { flushSync } from "react-dom";

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
    } catch (_error) {
      // Request handling already surfaces authentication errors to the user.
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full rounded-[24px] border border-[#e4e4e1] bg-white p-5 shadow-[0_12px_36px_rgba(15,23,42,0.035)] sm:p-6">
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
            className="!h-10 !rounded-full !border-[#dededb] !bg-white !px-3.5 !text-[15px] !shadow-none placeholder:!text-[#aaa] hover:!border-[#bdbdb8] focus:!border-[#171717]"
            placeholder="Enter your username"
            autoComplete="username"
          />
        </Form.Item>

        <Form.Item
          className="!mb-5"
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
            className="!h-10 !rounded-full !border-[#dededb] !bg-white !px-3.5 !shadow-none hover:!border-[#bdbdb8] focus-within:!border-[#171717] [&>input.ant-input]:!bg-white [&>input.ant-input]:!text-[15px]"
            placeholder="Enter your password"
            autoComplete="current-password"
          />
        </Form.Item>

        <Button
          block
          type="primary"
          htmlType="submit"
          loading={loading}
          className="!h-10 !rounded-full !border-[#171717] !bg-[#171717] !font-medium !text-white !shadow-none hover:!border-[#292929] hover:!bg-[#292929]"
        >
          Log in
        </Button>
      </Form>
    </div>
  );
}
