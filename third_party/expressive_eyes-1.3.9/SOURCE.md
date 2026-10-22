# Source provenance: espp/expressive_eyes v1.3.9

Retrieved 2026-10-22 by Claude (Stage 2 of [plan/eyes](../../plan/eyes/plan.md)). Files in `component/` and `example_registry/` are **unmodified** registry content. Do not edit them; Android adaptations live outside `third_party/`.

## Registry artifact

| Item | Value |
|---|---|
| Registry API | `https://components.espressif.com/api/components/espp/expressive_eyes` → version `1.3.9`, created 2026-10-21T06:26:33+03:00, not yanked |
| Archive URL | `https://components-file.espressif.com/components/espp/expressive_eyes/1.3.9/espp__expressive_eyes-v1.3.9.zip` |
| Archive SHA-256 | `B6CC6A616792114F5503CD091D5EB7B19601E18674510A386480639AC4D02E14` (20,249 bytes) |
| Registry `component_hash` | `94872b197545ccbf5f412e1c6638d6f1388bc23aa1da96dd78d448df320770b9` |
| CHECKSUMS.json SHA-256 | `4DF490008276B0AAD064A51610420F1A46F75410C8B20E80B3A0152DD946DD3A` |
| Per-file verification | **14 / 14** files match `CHECKSUMS.json` SHA-256; no extra files |
| Example archive | `…/examples/07a1d007-1aac-484d-80a3-678f72de3c62.tgz`, SHA-256 `23752F0E621DA6D4235D31291BA1507D281EA39DBE1D5FC60FF67AA1F6F86F49` → `example_registry/`, identical to `component/example/` |
| Registry license link | `…/1.3.9/license.txt` returns **NoSuchKey** (missing). The archive contains no LICENSE file. License text taken from the upstream repository at the tag (below). |

Raw downloads are kept in `_downloads/`.

## Upstream identity

| Item | Value |
|---|---|
| Repository | `https://github.com/esp-cpp/espp` |
| Commit (from archive `idf_component.yml` → `repository_info.commit_sha`) | `792c56e1a5cdf3938868990425d8e45cd0e6ed0c`, committed 2026-10-20T21:50:10-05:00 |
| Tag | `v1.3.9` points exactly at this commit (`git tag --points-at`); also on `origin/main` and `origin/release/v1.3` |
| Diff archive vs `components/expressive_eyes` at the tag | All source, header, example, CMake, Kconfig and README files **identical**. Only `idf_component.yml` differs: the registry rewrites it at publish time (adds `version: 1.3.9` and `repository_info`, drops `examples:`). |

## Required transitive sources (same tag unless noted)

| Dependency | Pin | License |
|---|---|---|
| `espp/base_component` | espp tag v1.3.9 `components/base_component` | MIT (espp repo) |
| `espp/logger` | espp tag v1.3.9 `components/logger` | MIT (espp repo) |
| `espp/format` | espp tag v1.3.9 `components/format` | MIT (espp repo) |
| libfmt (submodule `components/format/detail/fmt`) | `fmtlib/fmt` commit `93e26fa578712891ee0a5302e7563e0ba79efef9` (2026-05-22, `FMT_VERSION 120101`, 12.1.1 dev) | MIT-style, `LICENSES/fmt-93e26fa5-LICENSE.txt` |
| LVGL (submodule `components/lvgl`, required by blue drawer) | `lvgl/lvgl` commit `02814ddb90d38a8a46794579a126154bb2b9f473` (2026-05-28, `9.6.0-dev`, before tag v9.6.0) | MIT, `LICENSES/lvgl-02814ddb-LICENCE.txt` |

License texts: `../LICENSES/` (espp LICENSE unchanged since 2022-10-05, commit `ca5a408`).
