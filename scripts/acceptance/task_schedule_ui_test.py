"""Browser interaction contracts with controlled API responses; not real database evidence."""

import asyncio, json, re, os
from urllib.parse import urlparse
from pathlib import Path
from playwright.async_api import async_playwright, expect

params = {
    "source": {
        "type": "JDBC",
        "datasourceId": "src",
        "database": "app",
        "table": "orders",
    },
    "transform": [],
    "sink": {
        "type": "JDBC",
        "datasourceId": "dst",
        "database": "ods",
        "schema": "public",
        "table": "ods_orders",
        "writeMode": "APPEND",
    },
}
task = {
    "id": "task1",
    "name": "Orders sync",
    "taskType": "DATA_SYNC",
    "status": "PUBLISHED",
    "definitionVersion": 1,
    "parameterSchemaVersion": 2,
    "parameters": json.dumps(params),
    "debug": False,
    "updateBy": "user1",
    "createTime": "2026-10-14T00:00:00",
    "updateTime": "2026-10-14T00:00:00",
}
sources = [
    {"id": "src", "name": "MySQL source", "dbType": "MYSQL", "database": "app"},
    {
        "id": "dst",
        "name": "PostgreSQL target",
        "dbType": "POSTGRE_SQL",
        "database": "ods",
        "schema": "public",
    },
]
state = {
    "schedule": None,
    "requests": [],
    "created_tasks": 0,
    "updated_tasks": 0,
    "fail_schedule": False,
    "fires": [],
    "task": task,
}


def paging(rows):
    return {
        "bizData": rows,
        "pagination": {"pageNo": 1, "pageSize": 200, "total": len(rows), "pages": 1},
    }


async def mock(route):
    r = route.request
    p = urlparse(r.url).path
    method = r.method
    b = r.post_data_json if r.post_data else {}
    state["requests"].append((method, p, b, r.headers.get("x-workspace-id")))
    status = 200
    data = None
    code = 200
    if p.endswith("/account/current"):
        data = {
            "id": "user1",
            "userName": "tester",
            "realName": "Tester",
            "status": "ACTIVE",
        }
    elif p == "/api/v1/workspaces":
        data = [
            {"id": "ws1", "name": "Workspace A", "role": "OWNER"},
            {"id": "ws2", "name": "Workspace B", "role": "OWNER"},
        ]
    elif "/user-preferences" in p:
        data = []
    elif "/users/" in p or p.endswith("/user/batch"):
        data = []
    elif p == "/api/v1/data-source/page":
        data = paging(sources)
    elif "/catalog/schemas" in p:
        data = []
    elif "/catalog/tables" in p:
        if "/src/" in p:
            data = [
                {"database": "app", "schema": None, "name": "orders", "type": "TABLE"}
            ]
        else:
            data = [
                {
                    "database": "ods",
                    "schema": "public",
                    "name": "ods_orders",
                    "type": "TABLE",
                }
            ]
    elif p.startswith("/api/v1/data-source/"):
        data = next((x for x in sources if x["id"] == p.rsplit("/", 1)[-1]), {})
    elif p == "/api/v1/task-definitions/page":
        data = paging([state["task"]])
    elif p == "/api/v1/task-definitions" and method == "POST":
        state["created_tasks"] += 1
        state["task"] = {**task, **b, "id": "new-task", "status": "UNPUBLISHED"}
        data = state["task"]
    elif p.startswith("/api/v1/task-definitions/"):
        if method == "PUT":
            state["updated_tasks"] += 1
            state["task"] = {**state["task"], **b}
        data = state["task"]
    elif p == "/api/v1/task-instances/page":
        data = paging([])
    elif p.startswith("/api/v1/task-instances/"):
        if "/logs" in p:
            data = {
                "content": "INFO JDBC query executed\nINFO JDBC sink summary\n",
                "nextCursor": "cursor1",
                "hasMore": False,
            }
        elif p.endswith("/definition"):
            data = {
                "definitionId": "task1",
                "version": 1,
                "name": task["name"],
                "taskType": "DATA_SYNC",
                "parameterSchemaVersion": 2,
                "parameters": task["parameters"],
            }
        elif p.endswith("/metrics"):
            data = {}
        else:
            data = {
                "id": "i1",
                "definitionId": "task1",
                "definitionVersion": 1,
                "taskType": "DATA_SYNC",
                "triggerType": "SCHEDULE",
                "scheduleId": "s1",
                "scheduleRevision": 2,
                "scheduledTime": "2026-10-14T00:01:00Z",
                "status": "FINISHED",
                "debug": False,
                "createTime": "2026-10-14T00:01:00",
                "startTime": "2026-10-14T00:01:00",
                "endTime": "2026-10-14T00:01:02",
            }
    elif p == "/api/v1/schedules/preview":
        if b["cronExpression"] == "invalid":
            status = 400
            code = 18001
        else:
            data = {
                "after": "2026-10-14T00:00:00Z",
                "nextFireTimes": [f"2026-10-{d:02d}T18:00:00Z" for d in range(14, 19)],
            }
    elif p == "/api/v1/schedules/page":
        data = paging(
            [state["schedule"]]
            if state["schedule"] and r.headers.get("x-workspace-id") == "ws1"
            else []
        )
    elif p == "/api/v1/schedules" and method == "POST":
        if state["fail_schedule"]:
            status = 503
            code = 500
        else:
            state["schedule"] = {
                **b,
                "id": "s1",
                "enabled": False,
                "revision": 1,
                "registrationStatus": "APPLIED",
                "exhausted": False,
                "nextFireTime": None,
                "schedulable": True,
                "eligibilityReason": None,
            }
            data = state["schedule"]
    elif p.startswith("/api/v1/schedules/"):
        if p.endswith("/fires/page"):
            data = paging(state["fires"])
        elif p.endswith("/enable") or p.endswith("/disable"):
            state["schedule"] = {
                **state["schedule"],
                "enabled": p.endswith("/enable"),
                "revision": state["schedule"]["revision"] + 1,
            }
            data = state["schedule"]
        elif method == "PUT":
            state["schedule"] = {
                **state["schedule"],
                **b,
                "revision": state["schedule"]["revision"] + 1,
            }
            data = state["schedule"]
        elif method == "DELETE":
            state["schedule"] = None
            data = True
        else:
            data = state["schedule"]
    else:
        print("UNKNOWN", method, p, flush=True)
        data = []
    await route.fulfill(
        status=status,
        content_type="application/json",
        body=json.dumps({"code": code, "data": data, "msg": "test"}),
    )


async def main():
    Path("/tmp/yak-schedule-browser").mkdir(parents=True, exist_ok=True)
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        ctx = await browser.new_context(viewport={"width": 1440, "height": 1000})
        await ctx.add_init_script(
            "localStorage.setItem('yak-ops.current-workspace-id','ws1');"
        )
        page = await ctx.new_page()
        errors = []
        page.on("pageerror", lambda e: errors.append(str(e)))
        await page.route("**/api/**", mock)
        try:
            await page.goto("http://127.0.0.1:5173/offline-sync")
            await expect(
                page.get_by_role("button", name="手动", exact=True)
            ).to_be_visible()
            reads = [
                x for x in state["requests"] if x[1].startswith("/api/v1/schedules/")
            ]
            assert all(x[1].endswith("/page") for x in reads), reads
            await page.get_by_role("button", name="手动", exact=True).click()
            dialog = page.get_by_role("dialog", name="调度", exact=True)
            await expect(dialog).to_be_visible()
            await page.get_by_role(
                "button", name="打开 Cron 调度配置", exact=True
            ).click()
            await page.get_by_role("tab", name="高级 Cron", exact=True).click()
            cron = page.get_by_role("textbox", name="Cron 表达式", exact=True)
            expression = "0 0 2 ? * MON#5 2030"
            await cron.fill(expression)
            await page.get_by_role("tab", name="常用配置", exact=True).click()
            await expect(cron).to_have_value(expression)
            await expect(page.get_by_text("该表达式无法转换为常用配置")).to_be_visible()
            await page.get_by_role("button", name="确认", exact=True).click()
            await dialog.get_by_role("button", name="保存", exact=True).click()
            await expect(
                dialog.get_by_role("switch", name="启用调度", exact=True)
            ).not_to_be_checked()
            assert state["schedule"]["enabled"] is False
            assert state["schedule"]["cronExpression"] == expression
            assert not any(x[1].endswith("/enable") for x in state["requests"])
            await dialog.get_by_role("switch", name="启用调度", exact=True).check()
            await expect(
                dialog.get_by_role("switch", name="启用调度", exact=True)
            ).to_be_checked()
            await expect(
                dialog.get_by_role("button", name="打开 Cron 调度配置")
            ).to_be_disabled()
            await dialog.get_by_role("switch", name="启用调度", exact=True).uncheck()
            await expect(
                dialog.get_by_role("switch", name="启用调度", exact=True)
            ).not_to_be_checked()
            state["fires"] = [
                {
                    "id": "f" + str(i),
                    "scheduleId": "s1",
                    "scheduleRevision": 2,
                    "scheduledTime": "2026-10-14T00:01:00Z",
                    "fireTime": "2026-10-14T00:01:01Z",
                    "resolvedTime": "2026-10-14T00:01:01Z",
                    "outcome": outcome,
                    "reason": reason,
                    "instanceId": instance,
                }
                for i, (outcome, reason, instance) in enumerate(
                    [
                        ("ADMITTED", None, "i1"),
                        ("SKIPPED", "RESOURCE_BUSY", None),
                        ("REJECTED", "STALE_SCHEDULE", None),
                        ("SUBMISSION_FAILED", "PREPARATION_FAILED", None),
                    ]
                )
            ]
            await dialog.get_by_role("tab", name="触发记录", exact=True).click()
            await expect(dialog.get_by_text("已跳过", exact=True)).to_be_visible()
            assert await dialog.get_by_role("button", name="查看实例").count() == 1
            await page.screenshot(
                path="/tmp/yak-schedule-browser/fixture-history-desktop.png",
                full_page=True,
            )
            await dialog.get_by_role("button", name="查看实例").click()
            await expect(page).to_have_url(re.compile("executionId=i1"))
            await expect(page.get_by_text("调度版本：2")).to_be_visible()
            await page.get_by_role("tab", name="运行日志", exact=True).click()
            await expect(
                page.get_by_text(re.compile("INFO JDBC query executed"))
            ).to_be_visible()
            await page.get_by_role("tab", name="执行状态", exact=True).click()
            await page.get_by_role("button", name="触发记录", exact=True).click()
            await expect(
                page.get_by_role("dialog", name="触发记录", exact=True)
            ).to_be_visible()
            await page.set_viewport_size({"width": 390, "height": 844})
            await page.emulate_media(reduced_motion="reduce")
            await page.screenshot(
                path="/tmp/yak-schedule-browser/fixture-history-mobile.png",
                full_page=True,
            )
            await page.keyboard.press("Escape")
            await expect(page.get_by_role("dialog")).to_have_count(0)
            assert not errors, errors
            print(
                "PASS browser: batched list; advanced Cron preservation; save-without-enable; enable/disable; four terminal outcomes; historical link; instance/log tabs; narrow viewport; Escape; reduced motion",
                flush=True,
            )
        except:
            print((await page.locator("body").inner_text())[:16000])
            print("ERRORS", errors)
            await page.screenshot(
                path="/tmp/yak-schedule-browser/fixture-failure.png", full_page=True
            )
            raise
        finally:
            await browser.close()


asyncio.run(main())
