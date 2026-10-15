"""Minimal authenticated browser smoke against a disposable release Compose stack."""
import os
import re

from playwright.async_api import expect

async def verify_product_login(page):
    base_url = os.environ["YAK_UI_BASE_URL"].rstrip("/")
    username = os.environ["YAK_UI_LOGIN_USERNAME"]
    password = os.environ["YAK_UI_LOGIN_PASSWORD"]
    await page.goto(base_url + "/login")
    form = page.get_by_role("form", name="账号登录")
    await expect(form).to_be_visible()
    await form.get_by_role("textbox", name="用户名").fill(username)
    await form.locator('input[name="userPassword"]').fill(password)
    await form.get_by_role("button", name="登录", exact=True).click()
    await expect(page).to_have_url(re.compile(r"/data-source(?:\?.*)?$"), timeout=20000)
    await expect(form).to_have_count(0)
    await page.goto(base_url + "/data-sync")
    await expect(page.get_by_role("button", name="新建同步任务")).to_be_visible()
