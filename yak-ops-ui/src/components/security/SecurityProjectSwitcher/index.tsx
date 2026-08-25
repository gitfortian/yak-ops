import { useSecurityProject } from "@/contexts/SecurityProjectContext";
import { getLocale, setLocale } from "@umijs/max";
import { Dropdown } from "antd";
import { ChevronDown, Languages } from "lucide-react";

type SupportedLocale = "zh-CN" | "en-US";

const getSupportedLocale = (): SupportedLocale =>
  getLocale().toLowerCase().startsWith("zh") ? "zh-CN" : "en-US";

function LanguageSwitcher() {
  const currentLocale = getSupportedLocale();

  const switchLocale = (locale: SupportedLocale) => {
    if (locale === getLocale()) return;

    document.documentElement.dataset.yakLocale = locale;
    document.documentElement.lang = locale;
    setLocale(locale);
  };

  return (
    <Dropdown
      placement="bottomRight"
      trigger={["click"]}
      menu={{
        selectable: true,
        selectedKeys: [currentLocale],
        items: [
          {
            key: "zh-CN",
            label: "中文",
            onClick: () => switchLocale("zh-CN"),
          },
          {
            key: "en-US",
            label: "English",
            onClick: () => switchLocale("en-US"),
          },
        ],
      }}
    >
      <button
        type="button"
        aria-label="切换语言 / Switch language"
        title="切换语言 / Switch language"
        className="order-[-1] flex h-12 min-w-11 flex-col items-center justify-center border-0 bg-transparent px-2 text-[12px] text-[rgba(35,35,35,0.6)] transition-colors duration-150 hover:text-[rgba(35,35,35,0.9)]"
      >
        <span className="flex h-6 w-6 items-center justify-center text-[17px]">
          <Languages className="h-[17px] w-[17px]" strokeWidth={1.8} />
        </span>
        <span className="mt-0.5 whitespace-nowrap text-[10px] leading-3">
          {currentLocale === "zh-CN" ? "中文" : "EN"}
        </span>
      </button>
    </Dropdown>
  );
}

export default function SecurityProjectSwitcher() {
  const { projects, currentProject, selectProject } = useSecurityProject();

  return (
    <div className="contents">
      <LanguageSwitcher />

      {!projects.length ? (
        <span className="whitespace-nowrap px-2 text-sm">暂无可用项目</span>
      ) : (
        <Dropdown
          menu={{
            selectable: true,
            selectedKeys: currentProject ? [String(currentProject.id)] : [],
            items: projects.map((project) => ({
              key: String(project.id),
              label: project.projectName,
              onClick: () => selectProject(project),
            })),
          }}
        >
          <button
            type="button"
            className="flex items-center gap-1 border-0 bg-transparent px-2 text-sm"
          >
            <span>{currentProject?.projectName}</span>
            <ChevronDown className="h-3 w-3" />
          </button>
        </Dropdown>
      )}
    </div>
  );
}
