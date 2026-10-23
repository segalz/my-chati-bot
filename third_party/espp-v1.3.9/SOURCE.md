# Source provenance: espp components at v1.3.9

Copied 2026-10-22/23 by Claude (Stage 4.1 of [plan/eyes](../../plan/eyes/plan.md)) with
`git archive v1.3.9 components/<name>` from `https://github.com/esp-cpp/espp`, tag `v1.3.9` =
commit `792c56e1a5cdf3938868990425d8e45cd0e6ed0c`. **Files are unmodified.** Removed only what is
not used on Android: `components/logger/example/` and the `components/format/detail/fmt` submodule
pointer (libfmt is the separate submodule [`third_party/fmt`](../fmt) at the same pinned commit
`93e26fa578712891ee0a5302e7563e0ba79efef9`).

| Component | Used for |
|---|---|
| `base_component` | `espp::BaseComponent` base class of the eye engine |
| `logger` | `espp::Logger` (`ESP_PLATFORM` undefined → steady_clock path) |
| `format` | `format.hpp` header-only libfmt wrapper |

`LICENSE` is the espp repository license at the tag (MIT, © 2022 esp-cpp).

SHA-256 of the copied files: see [SHA256SUMS](./SHA256SUMS).
