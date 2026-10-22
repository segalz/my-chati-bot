# Privacy, Security, and Connectivity

[Index](INDEX.md)

## Proposed protective defaults

No microphone or camera access until the owner enables the relevant feature and Android grants permission. Camera mode starts off. Ambient wake detection stays on-device when enabled. Raw audio/video is transient by default; no recordings, photos, transcripts, face embeddings, or debug captures are retained unless a specific retention feature is enabled. These are proposed defaults for implementation, not claims about an existing app.

Provide clear sensor/network status, touch-accessible mute and camera-off controls, and a way to inspect/delete retained conversation data if history is introduced. Do not depend exclusively on animated eyes to communicate privacy state.

## Data boundaries

| Data | Proposed local use | Network use |
|---|---|---|
| Ambient microphone audio | Wake-word detection only | None by default |
| Active utterance | STT buffer | Only if online STT is deliberately configured |
| Transcript/context | Local commands and eye cues | Selected conversation request, with bounded history |
| Camera frames | Local tracking in enabled mode | None by default |
| Approved still image | Optional contextual query | Only to configured vision-capable endpoint under upload policy |
| Face/smile/gaze observations | Transient local behavior | Excluded by default |
| API keys | Protected app-local configuration | Authentication to intended endpoint only |
| Diagnostics | Redacted performance/errors | Explicit export only |

## Permissions are necessary, not sufficient

Recheck permission and mode before acquisition and upload. An earlier permission grant cannot override a current camera-off setting. A central model's instruction is untrusted content; it cannot authorize capture, change endpoints, reveal credentials, or bypass sleep/mute. Clear queued work and invalidate session IDs when the owner disables a feature.

## API configuration and costs

Support a configurable base URL, provider adapter, model identifier, authentication method, timeouts, context limits, and cancellation. OpenAI-compatible does not imply support for every provider's streaming, tools, audio, or image schema. Probe capabilities deliberately and fail clearly.

OpenAI API billing is separate from ChatGPT subscriptions; never assume a subscription supplies API access. See [OpenAI's billing explanation](https://help.openai.com/en/articles/9039756-managing-billing-for-chatgpt-and-the-api-platform). Provider selection, budget, and credentials are **TBD / Ask owner**. Do not place secrets in Markdown, source control, screenshots, or shared logs.

Prefer protected app-private storage backed by Android Keystore where supported. A distributed client cannot perfectly conceal a shared embedded provider key; investigate owner-supplied credentials or a controlled proxy if distributing beyond personal use. Require encrypted transport for internet endpoints, validate certificates, and bind credentials to the intended host/provider.

## Operating modes

- **Offline:** local eyes, validated local policy, available local wake/commands, and local vision if enabled. No general conversation promise without a suitable local backend.
- **Online API:** bounded conversational calls only when requested; explicitly configured online speech/vision may add data transfers.
- **Optional LAN:** an Ollama or compatible backend handles conversation. LAN still means data leaves the phone. Verify host, access control, connectivity, and transport; do not expose the service publicly for convenience.

Retries must be bounded and avoid replaying sensitive uploads unexpectedly. Set usage limits and record request counts/cost metadata without storing content by default. Product history policy, upload approval frequency, and distribution model remain open decisions.
