# Validation addendum for the installation agent

Date: 2026-10-24

## Scope and status

This is a proposed acceptance checklist derived from an architecture review. It is not an implementation, an executed test report, or proof that the Android application integrates with Hermes. The original four Markdown files from the shared conversation were unavailable for download and have not been reviewed. Reconcile this addendum with those files when supplied; report conflicts explicitly.

Keep the original authorization boundaries: preparing this package does not authorize installations, system changes, paid provider calls, or Android modifications. Inspect existing state before proposing changes and preserve existing work.

## Architecture decisions to validate

- One Hermes instance handles conversation and memory; an independent vision worker performs image analysis.
- Run Hermes itself inside the intended container boundary. Configuring a Docker terminal backend alone is a different deployment model.
- Verify the selected provider integration. Hermes documents a Codex OAuth provider that does not require a Codex CLI installation; do not add a CLI dependency without a concrete reason.
- Treat hermes-mobile as a community integration requiring client implementation and compatibility checks, not a ready-made Android connection.
- Identify an actual supported mechanism for returning vision results to the active conversation. Concurrent independent sessions do not prove safe injection into a running turn.

## Coordination contract

Document the concrete owner and transport of each step before implementing it. Carry at least session_id, job_id, capture timestamp, status, and result/error metadata. Ensure identifiers cannot route one client's result into another client's session.

Use asynchronous jobs and a serialized conversation/output coordinator. Do not mutate the conversation transcript concurrently. Define timeout, cancellation, retry, duplicate suppression, out-of-order results, stale results, disconnect recovery, and session-reset behavior. Serialize speech playback and define how user interruption affects queued results. These are proposed requirements, not claims about existing Hermes interfaces.

## Deployment constraints

Use dedicated minimal persistent storage; no Windows drive or personal-folder mounts, Docker socket, privileged mode, or host administrative access. Use a non-root runtime and appropriate resource limits. Disable unneeded agent tools and verify the restriction. Keep provider secrets scoped to the component that needs them. Authenticate the phone connection, protect transport, restrict network access, and define retention for images and transcripts. Do not claim absolute host isolation.

## Acceptance sequence

1. Record hardware, OS, selected versions, and existing Android transport without changing the app.
2. Prove a real text request/reply through Hermes and the chosen provider when authorized.
3. Start a mock vision job delayed by approximately 20 seconds. Complete at least three chat exchanges before that job finishes.
4. Deliver the result exactly once to the correct session at an appropriate output boundary, with no overwritten reply or overlapping speech.
5. Test topic changes, a second image, cancellation, out-of-order completion, timeout, provider failure, phone disconnect, and session reset. A vision failure must not stop chat.
6. When authorized, repeat with a real image and provider. A mock establishes scheduling only, not real image analysis.
7. At the separately authorized Android integration stage, verify actual Hebrew voice input, reply playback, image submission, and existing eye behavior on the target phone.
8. Capture timestamps and IDs showing overlap, observed response latency, CPU/memory use, and expected versus actual outcomes. Redact secrets and minimize private content. Agree on latency targets using target-device measurements.

Report verified, failed, and untested items separately. Container startup, a successful health check, or a green mock test is not end-to-end acceptance.

## References

- https://hermes-agent.nousresearch.com/docs/user-guide/docker
- https://hermes-agent.nousresearch.com/docs/integrations/providers
- https://hermes-agent.nousresearch.com/docs/plugins/hermes-mobile
- https://hermes-agent.nousresearch.com/docs/user-guide/configuration
- https://docs.docker.com/engine/security/
