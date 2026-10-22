"""Table column stability, fixed action column and first-load skeleton in the real browser."""
import asyncio

from playwright.async_api import expect
from mock_api import mock
from data_sync import site


async def verify_table_layout(page):
    gate = asyncio.Event()

    async def hold_first_task_page(route):
        await gate.wait()
        await mock(route)

    # Keep the first data request pending to verify skeletons without a timing sleep.
    await page.route("**/api/v1/task-definitions/page**", hold_first_task_page)
    try:
        await page.goto(site("/data-sync"), wait_until="domcontentloaded")
        table = page.locator("table").first
        await expect(table.locator("tbody tr")).to_have_count(3)
        await expect(table.locator('tbody tr [aria-hidden="true"]').first).to_be_visible()
    finally:
        gate.set()

    await expect(table.get_by_text("Orders sync", exact=True)).to_be_visible()
    await expect(table.locator("tbody tr")).to_have_count(1)

    geometry = await page.evaluate("""() => {
      const table = document.querySelector("table");
      const viewport = table.parentElement;
      const headers = Array.from(table.querySelectorAll("thead th"));
      const find = (label) => headers.find((th) => th.textContent.trim() === label);
      const action = find("操作");
      const route = find("同步链路");
      const execution = find("执行概况");
      const updated = find("更新信息");
      const row = table.querySelector("tbody tr");
      const beforeAction = action.getBoundingClientRect().left;
      const beforeRoute = route.getBoundingClientRect().left;
      const maxScroll = viewport.scrollWidth - viewport.clientWidth;
      viewport.scrollLeft = Math.min(360, maxScroll);
      return {
        rowHeight: row.getBoundingClientRect().height,
        actionWidth: action.getBoundingClientRect().width,
        executionWidth: execution.getBoundingClientRect().width,
        updatedWidth: updated.getBoundingClientRect().width,
        maxScroll,
        shift: beforeRoute - route.getBoundingClientRect().left,
        actionShift: Math.abs(beforeAction - action.getBoundingClientRect().left),
      };
    }""")
    assert abs(geometry["rowHeight"] - 64) <= 2, geometry
    assert abs(geometry["actionWidth"] - 280) <= 2, geometry
    assert abs(geometry["executionWidth"] - 180) <= 2, geometry
    assert abs(geometry["updatedWidth"] - 180) <= 2, geometry
    assert geometry["maxScroll"] >= 360, geometry
    assert geometry["shift"] >= 300, geometry
    assert geometry["actionShift"] <= 2, geometry
