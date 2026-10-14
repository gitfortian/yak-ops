"""Real browser entry, new target names, read-only preview, drafts and pinned schema-stage detail."""
import json
from pathlib import Path
import re
import uuid
from playwright.sync_api import expect, sync_playwright


def verify_schema_migration_browser(api, client, source, target):
    frontend = "http://127.0.0.1:5173"
    directory = Path("/tmp/yak-schedule-browser")
    directory.mkdir(parents=True, exist_ok=True)
    jar = next(handler.cookiejar for handler in client.opener.handlers if hasattr(handler, "cookiejar"))
    names = [client.call("GET", f"/api/v1/data-source/{identity}")["name"] for identity in (source, target)]
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch()
        context = browser.new_context(viewport={"width": 1440, "height": 1000})
        context.add_cookies([{"name": cookie.name, "value": cookie.value, "url": frontend,
                             "httpOnly": True, "sameSite": "Lax"} for cookie in jar])
        context.add_init_script("localStorage.setItem('yak-ops.current-workspace-id'," + json.dumps(client.workspace) + ");")
        page = context.new_page()
        errors = []
        page.on("pageerror", lambda error: errors.append(str(error)))
        identity = None
        try:
            def choose(trigger, name):
                trigger.click()
                popup = page.locator('[data-base-ui-portal] [role="presentation"][data-side][data-open]').last
                listbox = popup.get_by_role("listbox")
                list_id = listbox.get_attribute("id")
                assert list_id
                listbox.get_by_role("option", name=name, exact=True).click()
                expect(page.locator(f'[id="{list_id}"]')).to_be_hidden()
            page.goto(frontend + "/data-sync?create=1")
            dialog = page.get_by_role("dialog", name="新建同步任务", exact=True)
            for index, name in enumerate(("MySQL", "PostgreSQL", "单表离线")):
                choose(dialog.get_by_role("combobox").nth(index), name)
            schema = dialog.get_by_role("checkbox", name="结构迁移", exact=True)
            expect(schema).not_to_be_checked()
            expect(schema).to_be_enabled()
            schema.check()
            expect(dialog.get_by_role("checkbox", name="全量同步", exact=True)).to_be_checked()
            expect(dialog.get_by_role("checkbox", name="全量同步", exact=True)).to_be_disabled()
            dialog.get_by_role("textbox", name="名称", exact=True).fill("browser-schema-" + uuid.uuid4().hex[:8])
            dialog.get_by_role("button", name="下一步", exact=True).click()
            for index, name in enumerate(names):
                choose(page.locator("#datasource").get_by_role("combobox").nth(index), name)
            choose(page.locator("#source").get_by_role("combobox").last, re.compile(r"(.*\.)?sm_browser_source$"))
            target_section = page.locator("#target")
            schema_select = target_section.get_by_role("combobox", name="Schema", exact=True)
            if schema_select.count():
                choose(schema_select, "public")
            target_input = target_section.get_by_placeholder("去向表名称", exact=True)
            expect(target_input).to_be_enabled()
            target_input.fill("sm_browser_unused")
            task_count = api["query_mysql"]("SELECT COUNT(*) FROM yak_ops.yak_ops_task_definition")
            for name in ("sm_browser_unused", "sm_browser_target"):
                target_input.fill(name)
                page.get_by_role("button", name="DDL 预览", exact=True).click()
                ddl = page.get_by_role("dialog", name="DDL 预览", exact=True)
                expect(ddl.locator("code")).to_contain_text("CREATE TABLE")
                expect(ddl.locator("code")).to_contain_text(name)
                expect(ddl.get_by_role("textbox")).to_have_count(0)
                assert api["query_postgres"]("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('sm_browser_unused','sm_browser_target')") == "0"
                assert api["query_mysql"]("SELECT COUNT(*) FROM yak_ops.yak_ops_task_definition") == task_count
                if name == "sm_browser_target":
                    page.screenshot(path=str(directory / "schema-preview-desktop.png"), full_page=True)
                ddl.get_by_role("button", name="关闭", exact=True).click()
            page.reload()
            expect(page.get_by_placeholder("去向表名称", exact=True)).to_have_value("sm_browser_target")
            page.get_by_role("button", name="保存", exact=True).click()
            expect(page).to_have_url(re.compile(r"/data-sync/[A-Za-z0-9_-]+/edit$"))
            task = page.url.split("/")[-2]
            params = json.loads(client.call("GET", f"/api/v1/task-definitions/{task}")["parameters"])
            assert params["steps"] == ["SCHEMA", "FULL"]
            assert params["tableMappings"][0]["target"]["table"] == "sm_browser_target"
            page.get_by_role("button", name="保存并上线", exact=True).click()
            expect(page).to_have_url(frontend + "/data-sync")
            accepted, _ = api["submit"](client, task)
            identity = accepted["instanceId"]
            api["wait_status"](client, identity, {"FINISHED"})
            page.goto(f"{frontend}/data-sync/{task}/detail?executionId={identity}")
            expect(page.get_by_role("progressbar", name="结构迁移", exact=True)).to_have_attribute("aria-valuenow", "100")
            expect(page.get_by_role("progressbar", name="全量同步", exact=True)).to_have_attribute("aria-valuenow", "100")
            assert api["query_postgres"]("SELECT name FROM public.sm_browser_target WHERE id=1") == "browser"
            assert api["claim_count"](identity) == 0
            page.screenshot(path=str(directory / "schema-execution-desktop.png"), full_page=True)
            page.emulate_media(reduced_motion="reduce")
            expect(page.get_by_role("progressbar", name="结构迁移", exact=True)).to_have_attribute("aria-valuenow", "100")
            assert not errors, errors
            print("SCHEMA BROWSER PASS: optional legal steps, new target input, two read-only previews, draft restore, save/publish/run and pinned two-stage detail", flush=True)
        except Exception:
            page.screenshot(path=str(directory / "schema-migration-failure.png"), full_page=True)
            raise
        finally:
            if identity:
                current = client.call("GET", f"/api/v1/task-instances/{identity}")
                if current["status"] in ("SUBMITTED", "LAUNCHING", "RUNNING", "CANCELLING"):
                    client.call("POST", f"/api/v1/task-instances/{identity}/cancel")
                    api["wait_status"](client, identity, {"CANCELED", "FINISHED"}, timeout=45)
            context.close()
            browser.close()
