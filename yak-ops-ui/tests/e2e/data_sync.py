"""UI contracts for sync solution selection and schedule-save recovery (mocked APIs)."""
import os
import re
from pathlib import Path
from playwright.async_api import expect
from mock_api import state

def site(path):
    return os.environ["YAK_UI_BASE_URL"].rstrip("/") + path

async def choose_solution(page, kind, name):
    dialog = page.get_by_role("dialog", name="新建同步任务", exact=True)
    await expect(dialog).to_be_visible()
    await dialog.get_by_role("combobox").nth(0).click()
    await page.get_by_role("option", name="MySQL", exact=True).click()
    await dialog.get_by_role("combobox").nth(1).click()
    await page.get_by_role("option", name="PostgreSQL", exact=True).click()
    await dialog.get_by_role("combobox").nth(2).click()
    await page.get_by_role("option", name=kind, exact=True).click()
    await dialog.get_by_role("textbox", name="名称", exact=True).fill(name)
    for checkbox in await dialog.get_by_role("checkbox").all():
        await expect(checkbox).to_be_checked()
        await expect(checkbox).to_be_disabled()
    await dialog.get_by_role("button", name="下一步", exact=True).click()


async def verify_solution_creation(page):
    await page.goto(site("/data-sync"))
    await page.get_by_role("button", name="新建同步任务", exact=True).click()
    dialog = page.get_by_role("dialog", name="新建同步任务", exact=True)
    await dialog.get_by_role("combobox").nth(0).click()
    await page.get_by_role("option", name="MySQL", exact=True).click()
    await dialog.get_by_role("combobox").nth(1).click()
    await page.get_by_role("option", name="PostgreSQL", exact=True).click()
    await dialog.get_by_role("combobox").nth(2).click()
    await expect(page.get_by_role("option", name="单表实时", exact=True)).to_be_visible()
    await page.get_by_role("option", name="单表实时", exact=True).click()
    await dialog.get_by_role("textbox", name="名称", exact=True).fill("User chosen name")
    await dialog.get_by_role("combobox").nth(0).click()
    await page.get_by_role("option", name="PostgreSQL", exact=True).click()
    await expect(dialog.get_by_role("button", name="下一步", exact=True)).to_be_disabled()
    await dialog.get_by_role("combobox").nth(2).click()
    await expect(page.get_by_role("option", name="单表实时", exact=True)).to_have_count(0)
    await page.get_by_role("option", name="单表离线", exact=True).click()
    await expect(dialog.get_by_role("textbox", name="名称", exact=True)).to_have_value("User chosen name")
    state["fail_solutions"] = True
    await dialog.get_by_role("combobox").nth(1).click()
    await page.get_by_role("option", name="Oracle", exact=True).click()
    await expect(dialog.get_by_text("同步方案加载失败，请重试", exact=True)).to_be_visible()
    await expect(dialog.get_by_role("button", name="下一步", exact=True)).to_be_disabled()
    state["fail_solutions"] = False
    await dialog.get_by_role("button", name="重试", exact=True).click()
    await expect(dialog.get_by_role("combobox").nth(2)).to_be_enabled()
    await dialog.get_by_role("button", name="取消", exact=True).click()
    assert state["created_tasks"] == 0
    await page.goto(site("/data-sync?create=1"))
    await choose_solution(page, "单表实时", "CDC draft")
    await expect(page).to_have_url(re.compile("/data-sync/new$"))
    await expect(page.get_by_role("textbox", name="任务名称", exact=True)).to_have_value("CDC draft")
    await expect(page.locator("#schedule")).to_have_count(0)
    await page.get_by_role("textbox", name="任务名称", exact=True).fill("Restored draft")
    await page.reload()
    await expect(page.get_by_role("textbox", name="任务名称", exact=True)).to_have_value("Restored draft")
    await expect(page.locator("#target").get_by_role("combobox").last).to_be_disabled()
    await page.screenshot(path=str(Path(os.environ["YAK_UI_ARTIFACT_DIR"]) / "solution-editor-desktop.png"), full_page=True)
    await page.get_by_role("button", name="取消", exact=True).click()
    await page.get_by_role("button", name="新建同步任务", exact=True).click()
    dialog = page.get_by_role("dialog", name="新建同步任务", exact=True)
    await dialog.get_by_role("combobox").nth(2).click()
    await page.get_by_role("option", name="单表离线", exact=True).click()
    await dialog.get_by_role("button", name="下一步", exact=True).click()
    confirm = page.get_by_role("dialog", name="更改同步方案", exact=True)
    await expect(confirm).to_be_visible()
    await confirm.get_by_role("button", name="取消", exact=True).click()
    assert await page.evaluate("Object.values(sessionStorage).some(v => v.includes('Restored draft'))")
    await dialog.get_by_role("button", name="取消", exact=True).click()
    # Discard the test draft explicitly so the following schedule case starts with its own new draft.
    await page.evaluate("Object.keys(sessionStorage).filter(k => k.startsWith('yak.data-sync.draft:')).forEach(k => sessionStorage.removeItem(k))")


async def configure_cron(page, expression):
    await page.get_by_role("button", name="打开 Cron 调度配置", exact=True).click()
    await page.get_by_role("tab", name="高级 Cron", exact=True).click()
    await page.get_by_role("textbox", name="Cron 表达式", exact=True).fill(expression)
    await page.get_by_role("button", name="确认", exact=True).click()


async def verify_revision_conflict(page):
    await page.get_by_role("button", name="调度", exact=True).click()
    dialog = page.get_by_role("dialog", name="调度", exact=True)
    await configure_cron(page, "0 0 5 * * ?")
    state["conflict"] = True
    await dialog.get_by_role("button", name="保存", exact=True).click()
    await expect(dialog.get_by_text("调度已变更，请重新加载", exact=True)).to_be_visible()
    await expect(dialog.get_by_role("button", name="保存", exact=True)).to_be_disabled()
    await expect(dialog.get_by_role("button", name="打开 Cron 调度配置")).to_contain_text("0 0 5 * * ?")
    writes = [x for x in state["requests"] if x[0] == "PUT" and x[1] == "/api/v1/schedules/s1"]
    assert len(writes) == 1, writes
    state["conflict"] = False
    await dialog.get_by_role("button", name="关闭", exact=True).click()
    await page.get_by_role("dialog", name="放弃修改", exact=True).get_by_role("button", name="确认", exact=True).click()
    await expect(page.get_by_role("dialog")).to_have_count(0)


async def verify_partial_task_save(page):
    state["schedule"] = None
    state["fail_schedule"] = True
    await page.goto(site("/offline-sync/new"))
    await choose_solution(page, "单表离线", "Partial save")
    await expect(page).to_have_url(re.compile("/data-sync/new$"))
    assert state["created_tasks"] == 0
    await page.reload()
    await expect(page.get_by_role("textbox", name="任务名称", exact=True)).to_have_value("Partial save")
    for index, name in enumerate(["MySQL source", "PostgreSQL target"]):
        await page.locator("#datasource").get_by_role("combobox").nth(index).click()
        await page.get_by_role("option", name=name, exact=True).click()
    await page.locator("#source").get_by_role("combobox").click()
    await page.get_by_role("option", name="orders", exact=True).click()
    await page.locator("#target").get_by_role("combobox").first.click()
    await page.get_by_role("option", name="public.ods_orders", exact=True).click()
    await configure_cron(page, "0 0 6 * * ?")
    await page.get_by_role("button", name="保存", exact=True).click()
    await expect(page).to_have_url(re.compile("/data-sync/new-task/edit$"))
    await expect(page.get_by_text(re.compile("任务已保存，调度未确认保存"))).to_be_visible()
    await expect(page.get_by_role("button", name="保存", exact=True)).to_be_disabled()
    assert state["created_tasks"] == 1
    state["fail_schedule"] = False
    await page.get_by_role("button", name="重新加载状态", exact=True).click()
    await expect(page.get_by_role("button", name="保存", exact=True)).to_be_enabled()
    await page.get_by_role("button", name="保存", exact=True).click()
    await expect(page.get_by_text("同步任务已保存，当前仍为已下线", exact=True)).to_be_visible()
    assert state["created_tasks"] == 1 and state["updated_tasks"] == 0
    assert state["schedule"]["targetId"] == "new-task"
    assert state["schedule"]["cronExpression"] == "0 0 6 * * ?"
    assert state["schedule"]["enabled"] is False




async def pick_option(page, trigger, option):
    """Choose a visible option and wait until the prior dropdown closes."""
    await trigger.click()
    popup = page.locator('[data-base-ui-portal] [role="presentation"][data-side][data-open]').last
    listbox = popup.get_by_role("listbox")
    list_id = await listbox.get_attribute("id")
    assert list_id, "Select listbox must have an ID"
    await listbox.get_by_role("option", name=option).click()
    await expect(page.locator(f'[id="{list_id}"]')).to_be_hidden()
    await expect(page.locator('[data-base-ui-portal] [role="presentation"][data-side][data-open]')).to_have_count(0)


async def prepare_database_task(page, kind):
    await page.goto(site("/data-sync?create=1"))
    dialog = page.get_by_role("dialog", name="新建同步任务", exact=True)
    await expect(dialog).to_be_visible()
    for index, name in enumerate(("MySQL", "PostgreSQL", kind)):
        await pick_option(page, dialog.get_by_role("combobox").nth(index), name)
    await dialog.get_by_role("textbox", name="名称", exact=True).fill("acceptance-" + kind)
    return dialog


async def pick_datasources(page):
    for index, name in enumerate(("MySQL source", "PostgreSQL target")):
        await pick_option(page, page.locator("#datasource").get_by_role("combobox").nth(index), name)


async def verify_multitable_mapping(page):
    """Database batch selection, duplicate guard, draft restoration and saved mapping."""
    import json
    state["catalog_mode"] = "multi"
    dialog = await prepare_database_task(page, "整库离线")
    await expect(dialog.get_by_role("checkbox", name="全量同步")).to_be_checked()
    await dialog.get_by_role("button", name="下一步", exact=True).click()
    await pick_datasources(page)
    for table in ("batch_a", "batch_b"):
        await page.get_by_role("checkbox", name=re.compile(r"选择 (.*\.)?" + table + r"$")).check()
        await pick_option(
            page,
            page.get_by_role("combobox", name=re.compile(r"去向表 (.*\.)?" + table + r"$")),
            re.compile(r"(.*\.)?batch_a_copy$"))
    await expect(page.get_by_role("button", name="保存", exact=True)).to_be_disabled()
    await expect(page.get_by_text("去向表不能重复", exact=True).first).to_be_visible()
    await pick_option(
        page,
        page.get_by_role("combobox", name=re.compile(r"去向表 (.*\.)?batch_b$")),
        re.compile(r"(.*\.)?batch_b_copy$"))
    await pick_option(page, page.locator("#target").get_by_role("combobox").first, "按主键更新")
    await page.reload()
    await expect(page.get_by_text("已选 2 / 100", exact=True)).to_be_visible()
    await expect(page.get_by_role("button", name="保存", exact=True)).to_be_enabled()
    await page.get_by_role("button", name="保存", exact=True).click()
    await expect(page).to_have_url(re.compile(r"/data-sync/new-task/edit$"))
    requests = [req[2] for req in state["requests"]
                if req[0] == "POST" and req[1] == "/api/v1/task-definitions"]
    assert len(requests) == 1, requests
    parameters = json.loads(requests[0]["parameters"])
    assert parameters["solutionType"] == "DATABASE_OFFLINE", parameters
    assert [row["source"]["table"] for row in parameters["tableMappings"]] == ["batch_a", "batch_b"]
    assert [row["target"]["table"] for row in parameters["tableMappings"]] == ["batch_a_copy", "batch_b_copy"]
    assert all(row["writeMode"] == "UPSERT" for row in parameters["tableMappings"])


async def verify_schema_preview(page):
    """Optional structure stage, DDL preview without side effects, and stable draft."""
    import json
    state["catalog_mode"] = "schema"
    dialog = await prepare_database_task(page, "单表离线")
    schema = dialog.get_by_role("checkbox", name="结构迁移", exact=True)
    await expect(schema).not_to_be_checked()
    await expect(schema).to_be_enabled()
    await schema.check()
    await expect(dialog.get_by_role("checkbox", name="全量同步", exact=True)).to_be_checked()
    await dialog.get_by_role("button", name="下一步", exact=True).click()
    await pick_datasources(page)
    await pick_option(page, page.locator("#source").get_by_role("combobox").last,
                      re.compile(r"(.*\.)?sm_browser_source$"))
    target = page.locator("#target")
    schema_select = target.get_by_role("combobox", name="Schema", exact=True)
    if await schema_select.count():
        await pick_option(page, schema_select, "public")
    field = target.get_by_placeholder("去向表名称", exact=True)
    await expect(field).to_be_enabled()
    for table in ("sm_browser_unused", "sm_browser_target"):
        await field.fill(table)
        await page.get_by_role("button", name="DDL 预览", exact=True).click()
        ddl = page.get_by_role("dialog", name="DDL 预览", exact=True)
        await expect(ddl.locator("code")).to_contain_text("CREATE TABLE")
        await expect(ddl.locator("code")).to_contain_text(table)
        await expect(ddl.get_by_role("textbox")).to_have_count(0)
        await ddl.get_by_role("button", name="关闭", exact=True).click()
    assert state.get("schema_previews") == 2
    assert state["created_tasks"] == 0, "DDL preview must not save a Task"
    await page.reload()
    await expect(page.get_by_placeholder("去向表名称", exact=True)).to_have_value("sm_browser_target")
    await page.get_by_role("button", name="保存", exact=True).click()
    await expect(page).to_have_url(re.compile(r"/data-sync/new-task/edit$"))
    created = [req[2] for req in state["requests"]
               if req[0] == "POST" and req[1] == "/api/v1/task-definitions"][-1]
    params = json.loads(created["parameters"])
    assert params["steps"] == ["SCHEMA", "FULL"], params
    assert params["tableMappings"][0]["target"]["table"] == "sm_browser_target"
