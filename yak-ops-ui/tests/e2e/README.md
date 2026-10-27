# Playwright UI Tests

Playwright checks browser interaction, rendering, validation, navigation, and failure states. Database writes, CDC transitions, checkpoint correctness, scheduler admission, and Task lifecycle belong to backend JUnit integration tests.

| Layer | Technology | When |
| --- | --- | --- |
| Unit and frontend contracts | JUnit / TypeScript | Every PR |
| Backend integration and E2E | JUnit + Docker | Related PR and release |
| UI automation | Playwright | Release; manual as needed |
| Deployment smoke | Docker Compose | Release |

The suite keeps stable business scenarios instead of adding a new script for every feature PR:

- `data_sync.py`: solution selection, draft restoration, multi-table mapping and selection/removal, schema preparation configuration, realtime stage observations, partial schedule save/retry
- `scheduling.py`: schedule history, navigation, responsive mode, conflicts, Workspace isolation
- `login.py`: real login and UI navigation through the candidate Compose images
- `mock_api.py`: deterministic browser-only API fixtures
- `run.py`: isolated browser context, bounded waits, trace and failure screenshot, versioned report

Manual `Playwright UI Tests` workflow serves a built frontend and runs the mocked suite. The formal Release Gate runs **full** on the built Compose images, including login through nginx. Only test framework PRs trigger an additional automatic UI CI run; ordinary PRs do not.

```bash
python3 -m pip install playwright==1.57.0
python3 -m playwright install chromium
# Serve the built yak-ops-ui on localhost:5173 first.
python3 yak-ops-ui/tests/e2e/run.py --suite mocked --base-url http://127.0.0.1:5173
```

Evidence appears at `/tmp/yak-playwright/v<VERSION>/<SHA>/playwright-results.json`, with traces for mocked scenarios and failure screenshots. Live login intentionally does not record a trace to avoid storing a typed test password. Reports must record both release version and exact commit SHA. A skipped or failed Playwright suite does not pass release tests.

Tests must be isolated, use role/label locators, controlled fixtures, condition-based waits, and bounded timeouts. No arbitrary sleep, real CDC/Cron wait in the browser, implicit mutable shared state, or retry solely to hide flakes.
