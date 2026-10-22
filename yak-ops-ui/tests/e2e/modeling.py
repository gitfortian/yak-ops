"""Warehouse-planning navigation, editing, validation and Workspace UI scenarios."""
import os
from pathlib import Path
from playwright.async_api import expect
from data_sync import site
from mock_api import state


async def verify_warehouse_planning(page):
    state["planning"] = [
        {"id": "cat", "kind": "CATEGORY", "parentId": None, "name": "电商业务", "code": "commerce"},
        {"id": "dom", "kind": "DOMAIN", "parentId": "cat", "name": "交易域", "code": "trade"},
        {"id": "proc", "kind": "PROCESS", "parentId": "dom", "name": "下单", "code": "place_order"},
    ]
    state["planning"] = [{**row, "workspaceId": "ws1", "ownerId": "user1", "ownerName": "Tester",
                           "ownerActive": True, "description": None, "revision": 1} for row in state["planning"]]
    state["planning_fail_tree"] = False
    await page.goto(site("/data-modeling/warehouse-planning"))
    tree = page.get_by_role("navigation", name="数仓规划目录")
    details = page.get_by_role("region", name="规划详情")
    await expect(page.get_by_role("heading", name="数仓规划", exact=True)).to_be_visible()
    for name in ("电商业务", "交易域", "下单"):
        await expect(tree.get_by_role("button", name=name, exact=True)).to_be_visible()
    await expect(details.get_by_role("heading", name="电商业务", exact=True)).to_be_visible()
    if os.environ.get("YAK_UI_ARTIFACT_DIR"):
        await page.screenshot(path=str(Path(os.environ["YAK_UI_ARTIFACT_DIR"]) / "warehouse-planning.png"), full_page=True)
    await tree.get_by_role("button", name="交易域", exact=True).click()
    await expect(details.get_by_role("heading", name="交易域", exact=True)).to_be_visible()
    await details.get_by_role("button", name="下单", exact=True).click()
    await expect(details.get_by_role("heading", name="下单", exact=True)).to_be_visible()
    await expect(details.get_by_text("逻辑模型尚未开放", exact=True)).to_be_visible()

    await tree.get_by_role("button", name="交易域", exact=True).hover()
    create = tree.get_by_role("button", name="在交易域下新建业务过程", exact=True)
    await create.focus()
    await create.press("Enter")
    dialog = page.get_by_role("dialog", name="新建业务过程", exact=True)
    await expect(dialog.get_by_role("combobox", name="所属数据域", exact=True)).to_be_disabled()
    await expect(dialog.get_by_role("combobox", name="所属数据域", exact=True)).to_contain_text("交易域")
    await dialog.get_by_role("textbox", name="中文名", exact=True).fill("发货")
    await dialog.get_by_role("textbox", name="英文编码", exact=True).fill("Invalid")
    await dialog.get_by_role("button", name="保存", exact=True).click()
    await expect(dialog.get_by_text("须以小写字母开头，仅含小写字母、数字或下划线", exact=True)).to_be_visible()
    await dialog.get_by_role("textbox", name="英文编码", exact=True).fill("ship_order")
    await dialog.get_by_role("combobox", name="负责人", exact=True).click()
    await page.get_by_role("option", name="Tester", exact=True).click()
    await dialog.get_by_role("button", name="保存", exact=True).click()
    await expect(dialog).not_to_be_visible()
    await expect(details.get_by_role("heading", name="发货", exact=True)).to_be_visible()
    await expect(tree.get_by_role("button", name="发货", exact=True)).to_have_attribute("aria-current", "true")

    await details.get_by_role("button", name="编辑", exact=True).click()
    edit = page.get_by_role("dialog", name="编辑业务过程", exact=True)
    await edit.get_by_role("textbox", name="英文编码", exact=True).fill("place_order")
    await edit.get_by_role("button", name="保存", exact=True).click()
    await expect(edit.get_by_text("英文编码已存在", exact=True)).to_be_visible()
    await edit.get_by_role("button", name="取消", exact=True).click()
    await tree.get_by_role("button", name="交易域", exact=True).click()
    await details.get_by_role("button", name="删除", exact=True).click()
    deletion = page.get_by_role("dialog", name="删除交易域", exact=True)
    await deletion.get_by_role("button", name="删除", exact=True).click()
    await expect(page.get_by_text("存在关联子节点或资产，无法删除，请先清空", exact=True)).to_be_visible()
    await expect(deletion).to_be_visible()
    await deletion.get_by_role("button", name="取消", exact=True).click()

    await tree.get_by_role("button", name="发货", exact=True).click()
    await details.get_by_role("button", name="删除", exact=True).click()
    await page.get_by_role("dialog", name="删除发货", exact=True).get_by_role("button", name="删除", exact=True).click()
    await expect(tree.get_by_role("button", name="发货", exact=True)).to_have_count(0)
    await expect(details.get_by_role("heading", name="交易域", exact=True)).to_be_visible()
    state["planning_fail_tree"] = True
    await page.get_by_role("button", name="刷新", exact=True).click()
    await expect(page.get_by_role("alert").filter(has_text="数仓规划加载失败")).to_be_visible()
    state["planning_fail_tree"] = False
    await page.get_by_role("button", name="重试", exact=True).click()
    await expect(details.get_by_role("heading", name="交易域", exact=True)).to_be_visible()
    await tree.get_by_role("button", name="折叠电商业务", exact=True).click()
    await expect(tree.get_by_role("button", name="下单", exact=True)).not_to_be_visible()
    await tree.get_by_role("button", name="展开电商业务", exact=True).press("Enter")
    await expect(tree.get_by_role("button", name="下单", exact=True)).to_be_visible()
    await page.get_by_role("button", name="Workspace A", exact=True).click()
    await page.get_by_role("menuitem", name="Workspace B", exact=True).click()
    await expect(details.get_by_text("暂无业务分类", exact=True)).to_be_visible()
    await page.get_by_role("button", name="Workspace B", exact=True).click()
    await page.get_by_role("menuitem", name="Workspace A", exact=True).click()
    await expect(tree.get_by_role("button", name="下单", exact=True)).to_be_visible()
    await page.set_viewport_size({"width": 390, "height": 844})
    await expect(page.get_by_role("heading", name="数仓规划", exact=True)).to_be_visible()
    assert await page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"), "Horizontal page overflow"
