"""Deterministic HTTP fixtures for UI interaction tests; never evidence for backend E2E."""

import json
from urllib.parse import urlparse, parse_qs

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
    "createTime": "2030-10-14T00:00:00",
    "updateTime": "2030-10-14T00:00:00",
}
instance = {
    "id": "i1",
    "definitionId": "task1",
    "definitionVersion": 1,
    "taskType": "DATA_SYNC",
    "triggerType": "SCHEDULE",
    "scheduleId": "s1",
    "scheduleRevision": 2,
    "scheduledTime": "2030-10-14T00:01:00Z",
    "status": "FINISHED",
    "progressPercent": 100,
    "debug": False,
    "createTime": "2030-10-14T00:01:00",
    "startTime": "2030-10-14T00:01:00",
    "endTime": "2030-10-14T00:01:02",
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
    "fail_batch": False,
    "conflict": False,
    "fires": [],
    "fail_solutions": False,
    "solution_requests": [],
    "task": task,
    "unexpected_requests": [],
    "catalog_mode": "single",
}


def paging(rows):
    return {
        "bizData": rows,
        "pagination": {"pageNo": 1, "pageSize": 200, "total": len(rows), "pages": 1},
    }


async def mock_planning(route):
    request = route.request
    path = urlparse(request.url).path.removeprefix("/api/v1/data-modeling/warehouse-planning")
    query = parse_qs(urlparse(request.url).query)
    workspace = request.headers.get("x-workspace-id")
    method = request.method
    body = request.post_data_json if request.post_data else {}
    rows = state.get("planning", [])
    data, code, status, message = None, 200, 200, "成功"
    def tree(parent=None):
        return [{**{k: v for k, v in row.items() if k != "workspaceId"}, "children": tree(row["id"])}
                for row in rows if row["workspaceId"] == workspace and row["parentId"] == parent]
    if path == "/tree":
        if state.get("planning_fail_tree"):
            status, code, message = 503, 999, "加载失败"
        else:
            # The page renders two presentational trees, so roots split by kind.
            roots = tree()
            data = {
                "categories": [node for node in roots if node["kind"] != "DATASET"],
                "datamarts": [node for node in roots if node["kind"] == "DATASET"],
            }
    elif path == "/owners":
        data = [{"userId": "user1", "displayName": "Tester"}]
    elif path == "/codes/availability":
        excluded = query.get("excludeId", [None])[0]
        data = {"available": not any(row["code"] == query["code"][0] and row["id"] != excluded for row in rows)}
    elif path.endswith("/logical-models"):
        data = {"availability": "NOT_ENABLED", "page": None}
    elif method == "POST":
        parts = path.strip("/").split("/")
        if any(row["code"] == body["code"] for row in rows):
            status, code, message = 409, 19002, "英文编码已存在"
        else:
            kind = {"categories": "CATEGORY", "domains": "DOMAIN", "processes": "PROCESS"}[parts[-1]]
            identity = "created_" + str(len(state["requests"]))
            row = {**body, "id": identity, "kind": kind, "parentId": parts[1] if len(parts) == 3 else None,
                   "workspaceId": workspace, "ownerName": "Tester", "ownerActive": True, "revision": 1}
            rows.append(row)
            data = {"id": identity, "kind": kind, "revision": 1}
    elif method in ("PUT", "DELETE"):
        identity = path.rsplit("/", 1)[-1]
        row = next(row for row in rows if row["id"] == identity and row["workspaceId"] == workspace)
        revision = body.get("revision") if method == "PUT" else int(query["revision"][0])
        if revision != row["revision"]:
            status, code, message = 409, 19004, "数据已变更，请刷新后重试"
        elif method == "DELETE" and any(child["parentId"] == identity for child in rows):
            status, code, message = 409, 19003, "存在关联子节点或资产，无法删除，请先清空"
        elif method == "DELETE":
            rows.remove(row)
            data = True
        elif any(other["code"] == body["code"] and other["id"] != identity for other in rows):
            status, code, message = 409, 19002, "英文编码已存在"
        else:
            row.update({**body, "revision": row["revision"] + 1})
            data = {"id": row["id"], "kind": row["kind"], "revision": row["revision"]}
    else:
        state["unexpected_requests"].append((method, path))
    await route.fulfill(status=status, content_type="application/json",
                        body=json.dumps({"code": code, "message": message, "data": data}))


async def mock(route):
    r = route.request
    p = urlparse(r.url).path
    method = r.method
    b = r.post_data_json if r.post_data else {}
    state["requests"].append((method, p, b, r.headers.get("x-workspace-id")))
    status = 200
    data = None
    code = 200
    if p.startswith("/api/v1/data-modeling/warehouse-planning/"):
        await mock_planning(route)
        return
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
    elif "/users/" in p or p.endswith("/user/batch") or p == "/api/v1/user":
        data = []
    elif p == "/api/v1/data-sync/solutions":
        query = parse_qs(urlparse(r.url).query)
        state["solution_requests"].append(query)
        choices = [{"solutionType": "SINGLE_TABLE_OFFLINE", "displayName": "单表离线",
                    "steps": ["SCHEMA", "FULL"], "defaultSteps": ["FULL"],
                    "allowedCombinations": [
                        {"steps": ["FULL"], "supportsScheduling": True, "writeModes": ["APPEND", "UPSERT"]},
                        {"steps": ["SCHEMA", "FULL"], "supportsScheduling": True, "writeModes": ["APPEND", "UPSERT"]}]}]
        if query.get("sourceType") == ["MYSQL"]:
            choices.extend([
                {"solutionType": "SINGLE_TABLE_REALTIME", "displayName": "单表实时",
                 "steps": ["FULL", "INCREMENTAL"], "defaultSteps": ["FULL", "INCREMENTAL"],
                 "allowedCombinations": [{"steps": ["FULL", "INCREMENTAL"],
                                          "supportsScheduling": False, "writeModes": ["UPSERT"]}]},
                {"solutionType": "DATABASE_OFFLINE", "displayName": "整库离线",
                 "steps": ["SCHEMA", "FULL"], "defaultSteps": ["FULL"],
                 "allowedCombinations": [
                     {"steps": ["FULL"], "supportsScheduling": True, "writeModes": ["APPEND", "UPSERT"]},
                     {"steps": ["SCHEMA", "FULL"], "supportsScheduling": True, "writeModes": ["APPEND", "UPSERT"]}]},
                {"solutionType": "DATABASE_REALTIME", "displayName": "整库实时",
                 "steps": ["FULL", "INCREMENTAL"], "defaultSteps": ["FULL", "INCREMENTAL"],
                 "allowedCombinations": [{"steps": ["FULL", "INCREMENTAL"],
                                          "supportsScheduling": False, "writeModes": ["UPSERT"]}]},
            ])
        if state["fail_solutions"]:
            status, code = 503, 500
        else:
            data = choices
    elif p == "/api/v1/data-source/page":
        data = paging(sources)
    elif "/catalog/schemas" in p:
        data = []
    elif "/catalog/tables" in p:
        mode = state["catalog_mode"]
        if "/src/" in p:
            names = ["orders"] if mode == "single" else (
                ["sm_browser_source"] if mode == "schema" else ["batch_a", "batch_b"])
            data = [{"database": "app", "schema": None, "name": name, "type": "TABLE"}
                    for name in names]
        else:
            names = ["ods_orders"] if mode == "single" else (
                [] if mode == "schema" else ["batch_a_copy", "batch_b_copy"])
            data = [{"database": "ods", "schema": "public", "name": name, "type": "TABLE"}
                    for name in names]
    elif p == "/api/v1/data-sync/schema/preview" and method == "POST":
        details = json.loads(b["parameters"])
        state["schema_previews"] = state.get("schema_previews", 0) + 1
        data = [{
            "mappingIndex": index, "target": {"catalog": "ods", "schema": "public",
                                              "table": item["target"]["table"]},
            "action": "CREATE",
            "statements": ["CREATE TABLE public." + item["target"]["table"] + " (id BIGINT PRIMARY KEY)"],
            "differences": [{
                "column": None, "action": "CREATE_TABLE", "reason": "TABLE_MISSING",
                "sourceType": None, "targetType": None,
            }],
        } for index, item in enumerate(details["tableMappings"])]
    elif p.startswith("/api/v1/data-source/"):
        data = next((x for x in sources if x["id"] == p.rsplit("/", 1)[-1]), {})
    elif p == "/api/v1/task-definitions/page":
        data = paging([state["task"]])
    elif p == "/api/v1/task-definitions" and method == "POST":
        state["created_tasks"] += 1
        state["task"] = {**task, **b, "id": "new-task", "status": "UNPUBLISHED", "parameterSchemaVersion": 3}
        data = state["task"]
    elif p.startswith("/api/v1/task-definitions/"):
        if method == "PUT":
            state["updated_tasks"] += 1
            state["task"] = {**state["task"], **b, **({"parameterSchemaVersion": 3} if "parameters" in b else {})}
        data = state["task"]
    elif p == "/api/v1/task-instances/latest":
        data = (
            [instance]
            if (not b["activeOnly"] or instance["status"] in ("SUBMITTED", "LAUNCHING", "RUNNING"))
            and instance["definitionId"] in b["definitionIds"]
            and r.headers.get("x-workspace-id") == "ws1"
            else []
        )
    elif p == "/api/v1/task-instances/page":
        data = paging([instance] if state.get("show_instances") else [])
    elif p.startswith("/api/v1/task-instances/"):
        if "/logs" in p:
            data = {
                "content": "INFO JDBC query executed\nINFO JDBC sink summary\n",
                "nextCursor": "cursor1",
                "hasMore": False,
            }
        elif p.endswith("/definition"):
            definition = state["task"]
            data = {
                "definitionId": instance["definitionId"],
                "version": definition.get("definitionVersion", 1),
                "name": definition["name"],
                "taskType": "DATA_SYNC",
                "parameterSchemaVersion": definition["parameterSchemaVersion"],
                "parameters": definition["parameters"],
            }
        elif p.endswith("/metrics"):
            data = {}
        else:
            data = instance
    elif p == "/api/v1/schedules/preview":
        if b["cronExpression"] == "invalid":
            status = 400
            code = 18001
        else:
            data = {
                "after": "2030-10-14T00:00:00Z",
                "nextFireTimes": [f"2030-10-{d:02d}T18:00:00Z" for d in range(14, 19)],
            }
    elif p == "/api/v1/schedules/page":
        if state["fail_batch"]:
            status = 503
            code = 500
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
        elif method == "PUT" and state["conflict"]:
            status = 409
            code = 18002
            state["schedule"] = {**state["schedule"], "revision": state["schedule"]["revision"] + 1}
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
        state["unexpected_requests"].append((method, p))
        data = []
    await route.fulfill(
        status=status,
        content_type="application/json",
        body=json.dumps({"code": code, "data": data, "msg": "test"}),
    )


