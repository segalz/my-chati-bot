# Architecture and proposed coordination contract

Newly authored replacement; not a recovered original. Date: 2026-10-24.

## Topology

Android client -> authenticated encrypted transport -> conversation coordinator -> Hermes -> cloud conversation provider.

Android image -> coordinator -> independent vision worker -> approved cloud vision provider -> result event -> coordinator -> appropriate conversation/output boundary -> Android.

One Hermes instance owns conversation and memory. The worker does not need another Hermes installation. Two containers are an operational separation, not a concurrency mechanism. The coordinator may live with the gateway integration or in another service after the supported interfaces are verified.

## Proposed application-level contract

The fields and operations below are design requirements to implement or map onto actual interfaces. They are not claims that Hermes or hermes-mobile provides these endpoint names.

Every job carries job_id, session_id, device_id, captured_at, accepted_at, deadline, status, and request generation/session epoch. Enforce authenticated ownership of device/session identifiers. Carry result text and completed_at on success; structured error category on failure. Use transport identifiers compatible with the selected plugin instead of assuming an arbitrary envelope will work unchanged.

States: accepted -> running -> completed | failed | cancelled | expired. Treat terminal states as immutable unless an explicitly defined retry creates a new attempt. A repeated submission with the same idempotency key must not produce duplicate announcements or duplicate billable work where avoidable. Bound queue length, image size, concurrent jobs, retries, and retention.

The job submission path acknowledges acceptance without awaiting provider inference. The worker communicates completion independently. Do not keep the conversation path waiting for the image result.

## Session and output ownership

One coordinator serializes writes to each conversation and speech output. Vision completion is an event with its original capture context, not an independent uncoordinated transcript mutation. Verify the selected Hermes interface supports the chosen delivery model.

Define explicit policies:

- During an active reply, queue the vision result until an appropriate output boundary; do not overwrite or interrupt the reply by accident.
- After a topic change, identify the original image and ask or briefly announce only if still relevant; otherwise retain an unobtrusive result or discard under a documented policy.
- After reset/new session, suppress delivery into the new conversation unless explicitly requested. Compare the session epoch, not only a reusable route identifier.
- For out-of-order images, preserve capture identity and never present an older result as the latest view.
- On cancellation or expiration, suppress queued speech and ignore late completion for announcement purposes.
- On client reconnect, recover state without replaying already acknowledged announcements. Define the acknowledgment and retention behavior explicitly.

## Voice boundaries

Speech-to-text, language-model response, and text-to-speech are distinct stages. The existing Android TTS does not establish that speech recognition already works. Validate recognition and playback separately on the device. Keep a single speech playback queue and implement an explicit interruption policy. Preserve eye updates according to the existing application contract.

## Evidence

Record traceable timestamps for chat receipt/reply and vision acceptance/start/completion/delivery. Use these to demonstrate chat replies occurred before the delayed vision job completed. Do not include raw private images, full transcripts, or credentials in routine logs.
