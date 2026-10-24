# Installation agent build plan

Newly authored replacement based on the visible shared discussion, not the original attachment. Date: 2026-10-24.

## Goal and authorization

Prepare and, when authorized, deploy one containerized Hermes gateway plus an independent vision worker on the target older computer. Cloud inference supplies the language model. Preserve Android voice and eye behavior; Android modifications are a separate stage.

Use concise English Markdown for model requests and reports. Respect existing user authorization. This document alone does not authorize installations, system changes, network exposure, paid API requests, or Android changes. Obtain missing approvals after preparing a concrete reviewable plan. Never copy authentication stores or reveal secrets in reports.

## Stage 1: inspect and plan

Record OS/build, CPU, available RAM/disk, virtualization support, existing Docker/WSL installation, running services, port conflicts, network topology, and current application transport if available. Keep inspection read-only. Do not assume Docker Desktop is installed or that its resource cost fits the machine. Do not reset, overwrite, or remove existing installations/data.

Read the official references in 03. Select explicit compatible Hermes and plugin versions and pin deployment images by digest where feasible. Confirm supported provider authentication on the selected release. Do not install Codex CLI merely because the design mentions Codex; Hermes documents its own Codex OAuth provider.

Produce a plan naming files to create, services, ports, persistent storage, resource budgets, secret provisioning, rollback steps, required approvals, and unverified integration points. Distinguish documented capability from observed behavior.

## Stage 2: implement a bounded server prototype

After necessary authorization, create a dedicated deployment directory containing:

- compose.yaml with one Hermes service and one vision worker service; include a coordinator separately only if the verified interfaces require it.
- .env.example with placeholders only, plus secret provisioning instructions; never commit real credentials.
- A minimal worker implementation with mock and real-provider modes, bounded image input, asynchronous jobs, timeout/cancellation, and duplicate suppression.
- A coordinator or adapter using documented Hermes interfaces. Identify the real result-insertion mechanism before claiming this integration exists.
- A mock client, acceptance runner, operational README, version manifest, and rollback instructions.

Run Hermes itself inside its container boundary. Do not confuse this with host Hermes using a Docker terminal backend. Use dedicated minimal storage, no host drive mounts or Docker socket, non-root execution, and bounded resources. Disable unneeded agent tools. Publish only necessary endpoints; start with local-only testing. Do not expose a dashboard by default.

If no supported nonblocking result integration is available, report the precise blocker and propose an adapter design. Do not silently replace a real conversation integration with printing a result to a log.

## Stage 3: prove conversation and concurrency

With authorized provider access, establish a real text request/reply through Hermes. First test the vision worker in mock mode with a roughly 20-second delay. Complete at least three chat exchanges while it runs. Return the result through the actual coordinator to the correct session exactly once.

Run the acceptance cases in 03, including stale results, reset, failure, retries, and disconnection. Capture timing evidence rather than inferring concurrency from process startup.

## Stage 4: real vision

After the user authorizes the provider and any cost/data transfer, submit a nonsensitive test image. Validate real analysis, delayed delivery, and continuing conversation. Keep API secrets only in the worker that needs them. Record provider/model identifiers and observed timing without logging secrets.

## Stage 5: Android integration handoff

Inspect the selected hermes-mobile release and its protocol/SDK compatibility. Define concrete client work for the existing Android app. Do not modify it without separate authorization. Until the phone is tested, label the result server prototype only.

Once authorized, verify on the actual phone: Hebrew speech recognition, text request/reply, existing TTS, eye behavior, image upload, interruption, reconnection, and result timing. Keep emotional speech synthesis outside this initial scope.

## Stage 6: deployment and report

Only after acceptance, configure authorized startup/restart behavior, bounded logs, health checks, persistent state, backup and restore. Report verified/failed/untested items separately, include measured CPU/RAM and response latency, and supply exact run/stop/restore commands for the implemented version. Container health is not end-to-end acceptance.
