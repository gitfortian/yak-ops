import { Toast as BaseToast } from "@base-ui/react/toast";
import type { ReactNode } from "react";

import { cn } from "../cn";

export type ToastTone = "success" | "error" | "warning" | "info";

type ToastData = {
  meta?: ReactNode;
};

export const toastManager = BaseToast.createToastManager<ToastData>();

export type ToastOptions = {
  description?: ReactNode;
  meta?: ReactNode;
  timeout?: number;
  id?: string;
  action?: {
    label: ReactNode;
    onClick: () => void;
  };
};

const addToast = (type: ToastTone, title: ReactNode, options?: ToastOptions) =>
  toastManager.add({
    id: options?.id,
    title,
    description: options?.description,
    timeout: options?.timeout,
    type,
    data: options?.meta ? { meta: options.meta } : undefined,
    actionProps: options?.action
      ? {
          children: options.action.label,
          onClick: options.action.onClick,
        }
      : undefined,
  });

export const toast = {
  success: (title: ReactNode, options?: ToastOptions) => addToast("success", title, options),
  error: (title: ReactNode, options?: ToastOptions) => addToast("error", title, options),
  warning: (title: ReactNode, options?: ToastOptions) => addToast("warning", title, options),
  info: (title: ReactNode, options?: ToastOptions) => addToast("info", title, options),
  dismiss: (id?: string) => toastManager.close(id),
};

const toneClass: Record<ToastTone, string> = {
  success: "text-[var(--yak-components-toast-success)]",
  error: "text-[var(--yak-components-toast-error)]",
  warning: "text-[var(--yak-components-toast-warning)]",
  info: "text-[var(--yak-components-toast-info)]",
};

const toneHaloClass: Record<ToastTone, string> = {
  success:
    "bg-[linear-gradient(90deg,var(--yak-components-toast-success-halo)_0%,transparent_72%)]",
  error: "bg-[linear-gradient(90deg,var(--yak-components-toast-error-halo)_0%,transparent_72%)]",
  warning:
    "bg-[linear-gradient(90deg,var(--yak-components-toast-warning-halo)_0%,transparent_72%)]",
  info: "bg-[linear-gradient(90deg,var(--yak-components-toast-info-halo)_0%,transparent_72%)]",
};

function ToastStatusIcon({ tone }: { tone: ToastTone }) {
  if (tone === "warning") {
    return (
      <svg
        aria-hidden="true"
        viewBox="0 0 20 20"
        className={cn("size-5 shrink-0", toneClass[tone])}
      >
        <path
          fill="currentColor"
          d="M8.57 3.05a1.65 1.65 0 0 1 2.86 0l6.16 10.68A1.65 1.65 0 0 1 16.16 16H3.84a1.65 1.65 0 0 1-1.43-2.27L8.57 3.05Z"
        />
        <path
          fill="white"
          d="M9.2 7.05a.8.8 0 1 1 1.6 0v4.05a.8.8 0 1 1-1.6 0V7.05Zm.8 6.9a.95.95 0 1 1 0-1.9.95.95 0 0 1 0 1.9Z"
        />
      </svg>
    );
  }

  return (
    <svg aria-hidden="true" viewBox="0 0 20 20" className={cn("size-5 shrink-0", toneClass[tone])}>
      <circle cx="10" cy="10" r="8" fill="currentColor" />
      {tone === "success" ? (
        <path
          d="m6.5 10.2 2.15 2.15 4.85-5"
          fill="none"
          stroke="white"
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth="1.8"
        />
      ) : tone === "error" ? (
        <path
          fill="white"
          d="M9.2 5.8a.8.8 0 1 1 1.6 0v5a.8.8 0 1 1-1.6 0v-5Zm.8 8.4a1 1 0 1 1 0-2 1 1 0 0 1 0 2Z"
        />
      ) : (
        <path
          fill="white"
          d="M9.2 8.9a.8.8 0 1 1 1.6 0v5a.8.8 0 1 1-1.6 0v-5ZM10 6.8a1 1 0 1 1 0-2 1 1 0 0 1 0 2Z"
        />
      )}
    </svg>
  );
}

function ToastCloseIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 20 20" className="size-4">
      <path
        d="m6.5 6.5 7 7m0-7-7 7"
        fill="none"
        stroke="currentColor"
        strokeLinecap="round"
        strokeWidth="1.5"
      />
    </svg>
  );
}

function ToastHost() {
  const { toasts } = BaseToast.useToastManager<ToastData>();

  return (
    <BaseToast.Portal>
      <BaseToast.Viewport
        aria-label="Notifications"
        className="group/toast-viewport pointer-events-none fixed right-5 top-5 z-[100] w-[360px] max-w-[calc(100vw-2rem)] overflow-visible"
      >
        {toasts.map((item) => {
          const tone = (item.type as ToastTone | undefined) ?? "info";
          return (
            <BaseToast.Root
              key={item.id}
              toast={item}
              swipeDirection={["up", "right"]}
              className={cn(
                "group/toast pointer-events-auto absolute right-0 top-0 w-full origin-top cursor-default select-none rounded-xl outline-none",
                "[--toast-current-height:var(--toast-frontmost-height,var(--toast-height))] [--toast-expanded-offset-y:calc(var(--toast-offset-y)+var(--toast-swipe-movement-y)+(var(--toast-index)*var(--toast-gap)))] [--toast-gap:8px] [--toast-peek:5px] [--toast-scale:calc(1-(var(--toast-index)*0.0225))] [--toast-shrink:calc(1-var(--toast-scale))]",
                "z-[calc(100-var(--toast-index))] h-(--toast-current-height)",
                "[transition:transform_500ms_cubic-bezier(0.22,1,0.36,1),opacity_300ms,height_150ms] motion-reduce:transition-none",
                "transform-[translateX(var(--toast-swipe-movement-x))_translateY(calc(var(--toast-swipe-movement-y)+(var(--toast-index)*var(--toast-peek))+(var(--toast-shrink)*var(--toast-current-height))))_scale(var(--toast-scale))]",
                "data-expanded:h-(--toast-height) data-expanded:transform-[translateX(var(--toast-swipe-movement-x))_translateY(var(--toast-expanded-offset-y))_scale(1)]",
                "data-ending-style:pointer-events-none data-ending-style:transform-[translateY(-150%)] data-ending-style:opacity-0 data-ending-style:after:pointer-events-none",
                "data-ending-style:data-[swipe-direction=up]:transform-[translateY(calc(var(--toast-swipe-movement-y)-150%))]",
                "data-ending-style:data-[swipe-direction=right]:transform-[translateX(calc(var(--toast-swipe-movement-x)+150%))_translateY(var(--toast-expanded-offset-y))]",
                "data-limited:pointer-events-none data-limited:opacity-0 data-starting-style:transform-[translateY(-150%)] data-starting-style:opacity-0",
                "after:pointer-events-auto after:absolute after:bottom-full after:left-0 after:h-[calc(var(--toast-gap)+1px)] after:w-full after:content-['']",
                "focus-visible:ring-2 focus-visible:ring-[var(--yak-components-button-focus-ring)]",
              )}
            >
              <div className="relative h-full overflow-hidden rounded-xl border border-[var(--yak-components-toast-border)] bg-[var(--yak-components-toast-bg)] shadow-[var(--yak-components-toast-shadow)] backdrop-blur-[5px]">
                <div
                  aria-hidden="true"
                  className={cn(
                    "pointer-events-none absolute -inset-px opacity-100",
                    toneHaloClass[tone],
                  )}
                />
                <BaseToast.Content className="relative flex items-start gap-2.5 overflow-hidden p-3 transition-opacity duration-200 data-behind:opacity-0 data-expanded:opacity-100 motion-reduce:transition-none">
                  <div className="flex shrink-0 items-center justify-center pt-0.5">
                    <ToastStatusIcon tone={tone} />
                  </div>
                  <div className="min-w-0 flex-1 pt-0.5">
                    <BaseToast.Title className="min-w-0 text-[13px] font-semibold leading-5 text-[var(--yak-components-toast-title)] [overflow-wrap:anywhere]" />
                    {item.description ? (
                      <BaseToast.Description className="mt-1 min-w-0 text-xs leading-[18px] text-[var(--yak-components-toast-description)] [overflow-wrap:anywhere]" />
                    ) : null}
                    {item.data?.meta ? (
                      <div className="mt-1.5 min-w-0 text-[11px] leading-4 text-[var(--yak-components-toast-meta)] [overflow-wrap:anywhere]">
                        {item.data.meta}
                      </div>
                    ) : null}
                    {item.actionProps ? (
                      <div className="flex items-start pt-2">
                        <BaseToast.Action
                          {...item.actionProps}
                          className="inline-flex cursor-pointer items-center justify-center rounded-md border border-[var(--yak-components-toast-border)] bg-[var(--yak-components-button-secondary-bg)] px-2.5 py-1.5 text-xs font-medium text-[var(--yak-components-button-secondary-text)] outline-none hover:bg-[var(--yak-components-button-secondary-bg-hover)] focus-visible:ring-2 focus-visible:ring-[var(--yak-components-button-focus-ring)]"
                        />
                      </div>
                    ) : null}
                  </div>
                  <div className="flex shrink-0 items-center justify-center">
                    <BaseToast.Close
                      aria-label="Close notification"
                      className="flex size-7 cursor-pointer items-center justify-center rounded-md text-[var(--yak-components-toast-close)] outline-none transition-colors hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-components-toast-title)] focus-visible:bg-[var(--yak-color-hover)] focus-visible:ring-2 focus-visible:ring-[var(--yak-components-button-focus-ring)]"
                    >
                      <ToastCloseIcon />
                    </BaseToast.Close>
                  </div>
                </BaseToast.Content>
              </div>
            </BaseToast.Root>
          );
        })}
      </BaseToast.Viewport>
    </BaseToast.Portal>
  );
}

export interface ToastProviderProps extends Omit<BaseToast.Provider.Props, "toastManager"> {
  children: ReactNode;
}

export function ToastProvider({
  children,
  timeout = 5000,
  limit = 3,
  ...props
}: ToastProviderProps) {
  return (
    <BaseToast.Provider {...props} toastManager={toastManager} timeout={timeout} limit={limit}>
      {children}
      <ToastHost />
    </BaseToast.Provider>
  );
}
