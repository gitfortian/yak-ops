# Third-Party Notices

## HarmonyOS Sans SC

Yak Ops uses HarmonyOS Sans SC for the Simplified Chinese interface.

Copyright 2021 Huawei Device Co., Ltd. HarmonyOS Sans Fonts Software is licensed under the HarmonyOS Sans Fonts License Agreement.

The UI install step downloads the official HarmonyOS Sans package from Huawei and copies selected Simplified Chinese TTF files without modifying their font data. The original font license from that package is copied to `public/licenses/HarmonyOS-Sans-LICENSE.txt` and is included in the built application.

No HarmonyOS Sans font binary is committed directly to this repository.

Official HarmonyOS design resource: https://developer.huawei.com/consumer/cn/design/resource

For offline builds, download the official ZIP yourself and set `HARMONYOS_SANS_ZIP` to its local path before installing dependencies.
