# Security and acceptance checklist

Newly authored replacement; not a recovered original. Date: 2026-10-24. Proposed checks below have not been executed.

## Container and network boundaries

- Run Hermes itself inside the intended container boundary, as a non-root runtime with minimal capabilities and no privilege escalation where supported.
- Do not use privileged mode, Docker socket mounts, host administrative access, or Windows drive/personal-folder mounts. Use dedicated minimal persistent volumes.
- Restrict each service to necessary peers and secrets. A private Docker network alone does not authenticate an exposed endpoint.
- Keep initial server tests on loopback. For phone access, prepare explicit network bindings, firewall scope, authenticated encrypted transport, and device authorization before exposure.
- The mobile plugin documents plain WebSocket with loopback defaults and requires a suitable WSS layer for remote access. Verify the exact release configuration.
- Disable unnecessary agent tools; verify a representative disallowed action is rejected safely. Containerization alone does not remove agent tool capabilities.
- Keep secrets out of source, ZIP bundles, URLs, screenshots, and logs. Provide placeholders and user-driven authentication.
- Bound CPU/RAM, upload size, image dimensions after decoding, job concurrency, queue depth, provider timeout, retry count, log size, and media retention.
- Treat image text and model output as untrusted data. A vision result must not grant permission to run commands or alter configuration.

## Acceptance matrix

| Case | Required observation |
| --- | --- |
| Real conversation | Authorized provider returns a real response through Hermes |
| Delayed mock vision | At least three chat exchanges complete before the delayed job finishes |
| Real vision | Approved provider analyzes a test image while conversation continues |
| Delivery | Correct session and capture identity; one announcement; no overwritten reply |
| Topic change | Result follows the documented relevance/staleness policy |
| Two images out of order | Results retain their correct capture order/identity |
| Duplicate/retry | No duplicate announcement; retries remain bounded |
| Cancellation | Late result does not create unexpected speech |
| Session reset | Old job cannot contaminate a newly created conversation |
| Timeout/provider error | Clear bounded failure; chat remains usable |
| Disconnect/reconnect | State recovers without duplicate delivery |
| Unauthorized client | Connection/job/result access denied |
| Resource pressure | Limits hold and failures do not indefinitely block chat |
| Target Android | Hebrew input, reply playback, eye behavior and image upload work together |
| Restart | Intended state persists; jobs follow a documented recovery policy |

Run tests in stages. Mock success proves scheduling only. A server client proves server integration only. Final robot acceptance requires the target computer and phone.

## Evidence and completion report

Record version/digest, environment, case input, expected/observed result, timestamps, task/session identifiers, response latency, and CPU/RAM measurements. Redact private content and secrets. Report PASS, FAIL, or NOT TESTED for every case; do not infer PASS from a zero exit or service health.

Agree on practical latency and memory targets based on measured hardware capacity. Before unattended operation, document start/stop, credential revocation, backup/restore, log inspection, and rollback without deleting user data.

## Official and maintainer references

- [Hermes Docker deployment](https://hermes-agent.nousresearch.com/docs/user-guide/docker)
- [Hermes providers](https://hermes-agent.nousresearch.com/docs/integrations/providers)
- [Hermes configuration](https://hermes-agent.nousresearch.com/docs/user-guide/configuration)
- [Community mobile adapter catalog and disclosure](https://hermes-agent.nousresearch.com/docs/plugins/hermes-mobile)
- [Docker security](https://docs.docker.com/engine/security/)

Capabilities were checked in the review on 2026-10-24. Verify actual installed release interfaces before writing deployment commands.
