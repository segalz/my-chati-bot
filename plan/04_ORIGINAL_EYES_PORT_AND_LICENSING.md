# Original Eyes Port and Licensing

[Index](INDEX.md)

## Required source identity

- Target: [`espp/expressive_eyes` v1.3.9 registry README](https://components.espressif.com/components/espp/expressive_eyes/versions/1.3.9/readme).
- Upstream component: [esp-cpp/espp expressive_eyes](https://github.com/esp-cpp/espp/tree/main/components/expressive_eyes).
- Repository: [esp-cpp/espp](https://github.com/esp-cpp/espp).

The versioned README could not be retrieved during this planning run. The repository landing page reports MIT. An accessible [v1.3.7 registry README](https://components.espressif.com/components/espp/expressive_eyes/versions/1.3.7/readme?language=en) describes a callback drawing architecture and example drawers including monochrome blue eyes. These are useful leads, **not verification of v1.3.9 contents, its concrete renderer, or Android portability**. `main` is mutable and must not be used as a substitute version pin.

## Required analysis before porting

1. Obtain the exact v1.3.9 component archive and map it to an immutable upstream commit where possible. Record download URL, date, archive hash, commit, and component metadata. If unavailable, report the blocker and ask before substituting another version.
2. Locate the original monochrome blue drawer and all associated assets/examples. Determine whether the registry archive includes it or whether a matching repository revision is required.
3. Trace the engine's timing, randomness, drawing callbacks, geometry, expression definitions, and build requirements. Separate portable logic from ESP display, task, timing, logging, and platform services.
4. Identify every rendering/build dependency from that concrete revision. Record name, version, license, notices, transitive dependencies, required APIs, and Android ABI/toolchain compatibility. Do not assume LVGL, SDL, OpenGL, or another drawing library is actually used until inspected.
5. Verify licenses for the core, example drawer, copied assets, fonts/shaders if present, and each dependency. A repository-level MIT label does not establish the license of every bundled part. Preserve applicable copyright and license texts.

This is a planned delegated source-analysis task under the owner's global intelligence policy; no source-code audit was performed for this documentation set.

## Preferred port strategy

Keep original C++ animation and the original blue renderer wherever feasible. Add a narrow Android NDK/CMake build plus JNI bridge and adapt the display/rendering dependencies to an Android surface. Isolate platform adapters from upstream files, record necessary patches, and retain provenance.

First prove a native compile for the actual phone ABI. Then display a fixed scene, run the upstream animation sequence, and test surface destruction/recreation. Compare behavior and blue-eye appearance against the verified upstream example. Select a graphics backend only after the dependency audit and device capabilities are known.

If the renderer cannot be carried over, document the precise blocker and assess a minimal drawing callback adapter that keeps the original engine and visual behavior. This is a fallback requiring an explicit decision record; do not label an approximate rewrite “the original renderer.” Reimplementing the animation system is the last option and needs owner agreement.

## Exit evidence

Immutable source manifest; dependency/license inventory; native build log; actual-phone visual recording or frames; tested expressions; idle/blink/gaze comparison; frame timing and memory; list of adaptations and remaining differences. No claim of a successful port before this evidence exists.
