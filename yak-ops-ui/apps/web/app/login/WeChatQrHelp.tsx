import {
  Popover,
  PopoverContent,
  PopoverDescription,
  PopoverTitle,
  PopoverTrigger,
} from "@yak-ops/yak-ui";
import { useState } from "react";

const WECHAT_QR_CODE_SRC = "/wechat_qr.png";

export default function WeChatQrHelp() {
  const [qrCodeAvailable, setQrCodeAvailable] = useState(true);

  return (
    <div className="text-center text-xs leading-5 text-[var(--yak-components-button-ghost-text)]">
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
