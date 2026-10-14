"""Exercise task scheduling in Chromium against the real authenticated product API."""

import http.cookiejar
import json
from pathlib import Path
import re
from playwright.sync_api import expect, sync_playwright

FRONTEND = "http://127.0.0.1:5173"
SCREENSHOTS = Path("/tmp/yak-schedule-browser")


def verify_schedule_browser(client, definition):
    SCREENSHOTS.mkdir(parents=True, exist_ok=True)
    jar = next(
        handler.cookiejar
        for handler in client.opener.handlers
        if hasattr(handler, "cookiejar")
    )
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch()
        context = browser.new_context(viewport={"width": 1440, "height": 1000})
        # Reuse the real test login; cookies never enter diagnostics or artifacts.
        context.add_cookies(
            [
                {
                    "name": cookie.name,
                    "value": cookie.value,
                    "url": FRONTEND,
                    "httpOnly": True,
                    "sameSite": "Lax",
                }
                for cookie in jar
            ]
        )
        context.add_init_script(
            "localStorage.setItem('yak-ops.current-workspace-id', "
            + json.dumps(client.workspace)
            + ");"
        )
        page = context.new_page()
        page_errors = []
        page.on("pageerror", lambda error: page_errors.append(str(error)))
        writes = []
        page.on(
            "request",
            lambda request: (
                writes.append((request.method, request.url))
                if request.method != "GET" and "/schedules" in request.url
                else None
            ),
        )
        try:
            page.goto(f"{FRONTEND}/offline-sync/{definition}/detail")
            page.get_by_role("button", name="调度", exact=True).click()
            dialog = page.get_by_role("dialog", name="调度", exact=True)
            page.get_by_role("button", name="打开 Cron 调度配置", exact=True).click()
            page.get_by_role("tab", name="高级 Cron", exact=True).click()
            cron = page.get_by_role("textbox", name="Cron 表达式", exact=True)
            advanced = "0 0 2 ? * MON#5 2030"
            cron.fill(advanced)
            page.get_by_role("tab", name="常用配置", exact=True).click()
            expect(cron).to_have_value(advanced)
            expect(page.get_by_text("该表达式无法转换为常用配置")).to_be_visible()
            cron.fill("0/20 * * * * ?")
            expect(page.get_by_text("未来 5 次触发时间", exact=True)).to_be_visible()
            page.get_by_role("button", name="确认", exact=True).click()
            dialog.get_by_role("button", name="保存", exact=True).click()
            toggle = dialog.get_by_role("switch", name="启用调度", exact=True)
            expect(toggle).not_to_be_checked()
            schedules = client.call(
                "POST",
                "/api/v1/schedules/page",
                {
                    "targetType": "TASK",
                    "targetIds": [definition],
                    "pageNo": 1,
                    "pageSize": 200,
                },
            )
            schedule = schedules["bizData"][0]
            identity = schedule["id"]
            assert not schedule["enabled"]
            assert not any(url.endswith("/enable") for _, url in writes)
            toggle.check()
            expect(toggle).to_be_checked()
            expect(
                dialog.get_by_role("button", name="打开 Cron 调度配置")
            ).to_be_disabled()
            dialog.get_by_role("tab", name="触发记录", exact=True).click()
            view = dialog.get_by_role("button", name="查看实例", exact=True)
            expect(view.first).to_be_visible(timeout=65000)
            dialog.get_by_role("tab", name="配置", exact=True).click()
            toggle.uncheck()
            expect(toggle).not_to_be_checked()
            assert not client.call("GET", f"/api/v1/schedules/{identity}")["enabled"]
            assert (
                client.call("GET", f"/api/v1/task-definitions/{definition}")[
                    "definitionVersion"
                ]
                == 1
            )
            dialog.get_by_role("tab", name="触发记录", exact=True).click()
            expect(view.first).to_be_visible()
            page.screenshot(
                path=str(SCREENSHOTS / "real-fire-history.png"), full_page=True
            )
            view.first.click()
            expect(page).to_have_url(re.compile("executionId="))
            expect(page.get_by_text("调度版本：2", exact=True)).to_be_visible()
            expect(page.get_by_role("progressbar", name="离线同步")).to_have_attribute(
                "aria-valuenow", "100", timeout=60000
            )
            page.get_by_role("tab", name="运行日志", exact=True).click()
            expect(page.get_by_text(re.compile("JDBC query executed"))).to_be_visible(
                timeout=15000
            )
            expect(page.get_by_text(re.compile("JDBC sink summary"))).to_be_visible(
                timeout=15000
            )
            page.get_by_role("tab", name="执行状态", exact=True).click()
            # Delete current configuration, then read the old ID through the selected Instance.
            page.get_by_role("button", name="调度", exact=True).click()
            dialog.get_by_role("button", name="删除调度", exact=True).click()
            page.get_by_role("dialog", name="删除调度", exact=True).get_by_role(
                "button", name="确认", exact=True
            ).click()
            expect(
                dialog.get_by_role("button", name="删除调度", exact=True)
            ).to_have_count(0)
            dialog.get_by_role("button", name="关闭", exact=True).click()
            page.get_by_role("button", name="触发记录", exact=True).click()
            historical = page.get_by_role("dialog", name="触发记录", exact=True)
            expect(
                historical.get_by_role("button", name="查看实例", exact=True).first
            ).to_be_visible()
            page.set_viewport_size({"width": 390, "height": 844})
            page.emulate_media(reduced_motion="reduce")
            page.screenshot(
                path=str(SCREENSHOTS / "real-history-mobile.png"), full_page=True
            )
            page.keyboard.press("Escape")
            expect(page.get_by_role("dialog")).to_have_count(0)
            assert not page_errors, page_errors
            print(
                "BROWSER PRODUCT PASS: save without enable, advanced Cron, real Quartz JDBC admission, stop, Instance version/provenance, text logs and deleted schedule history",
                flush=True,
            )
        except Exception:
            page.screenshot(path=str(SCREENSHOTS / "real-failure.png"), full_page=True)
            raise
        finally:
            context.close()
            browser.close()
