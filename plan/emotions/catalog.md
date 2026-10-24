# Catalog of expressive ideas

Owner accepted all ideas on 2026-10-24 and asked Claude to order them. Kinds:
- **Model** — the on-phone eye model must learn a new label (prompt + grammar change, new test sentences). Highest risk to accuracy and timing.
- **App** — the app decides by itself (state, timer, existing labels, confidence). No model change.
- **Look** — drawing only (new shape, effect or motion) attached to an existing label or state.

"Where it shows" lists the trigger. Colors are 0xRRGGBB and live in `colors.txt` once #15 exists.

| # | Idea | Kind | Looks like | Trigger | Stage |
|---|---|---|---|---|---|
| 1 | Love | Model | eyes become red hearts with a light beat | new label LOVE | E2 |
| 2 | Laughing | Model | ^ ^ arcs, small bounce | new label LAUGH | E5 |
| 3 | Crying | Model | blue tears falling from the lower lid | new label CRY | E5 |
| 4 | Fear | Model | eyes shrink and tremble, pale color | new label FEAR | E6 |
| 5 | Embarrassed / shy | Model | pink cheeks under the eyes, looking down | new label SHY | E5 |
| 6 | Excited | Model | yellow star eyes | new label EXCITED | E6 |
| 7 | Confused | Model | eyes turn as spirals | new label CONFUSED | E6 |
| 8 | Pleading | Model | big eyes with a shine | new label PLEADING | E6 |
| 9 | Thinking | App | eyes look up to the side while the model works | the eye model is busy (> 300 ms) | E1 |
| 10 | Sleeping | App | eyes close to lines, floating "Zzz" | 2 min of quiet; any input wakes | E1 |
| 11 | Listening | App | gentle purple pulse | speech input active (needs STT, P3) | later |
| 12 | Strong anger | Look | red plus a short shake | ANGRY with high confidence (#13) | E4 |
| 13 | Intensity | App | stronger color and bigger expression when the model is sure | logit margin of the label | E1 |
| 14 | Halo | Look | soft glow in the emotion's color around the eyes | any shown emotion | E8 |
| 15 | Colors file | App | `assets/colors.txt`, one color per state/emotion, owner-editable | — | E1 |
| 16 | Shape morph | Look | eyes shrink and reopen when the shape changes (oval ↔ heart …) | any shape change | E8 |
| 17 | × × eyes for errors | App | × × for a second | voice/model/cloud failure | E1 |
| 18 | Dollar eyes | Model/App | $ $ | money words (rule) or label | E7 |
| 19 | Hypnotic spiral | Look | spinning spiral | shares the CONFUSED drawer | E6 |
| 20 | Flat / skeptical | Model | flat upper lid, half closed | new label SKEPTICAL | E7 |
| 21 | Suspicious squint | Look | narrow eyes glancing aside | SKEPTICAL with a side glance | E7 |
| 22 | Huge round eyes | Look | larger than normal | SURPRISED with high confidence | E7 |
| 23 | One eye open | App | one eye opens while falling asleep | the step before #10 | E7 |
| 24 | Wink | App | existing wink | jokes/teasing (rule) | E4 |
| 25 | Sweat drop | Look | drop on the side | SHY, "oops" | E3 |
| 26 | Anger mark | Look | 💢 in a corner | ANGRY | E3 |
| 27 | Sparkles | Look | ✨ around the eyes | EXCITED / HAPPY (high) | E3 |
| 28 | ? / ! above | Look | ❓ or ❗ | CONFUSED / SURPRISED | E3 |
| 29 | Music notes | Look | 🎵 floating | HAPPY while speaking | E3 |
| 30 | Rain cloud | Look | small cloud above | SAD (high) | E3 |
| 31 | Eye roll | Look | eyes roll up | BORED / SKEPTICAL | E4 |
| 32 | Shifty glance | Look | quick right-left | secrets, nervous | E4 |
| 33 | Double blink | Look | two fast blinks | "what?" / CONFUSED | E4 |
| 34 | Tremble | Look | small shake | FEAR, strong anger (#12) | E4 |
| 35 | Pulse | Look | eyes swell and shrink | LOVE (#1), excitement | E2 |
| 36 | Follow the user | App | eyes look at the user's face | front camera (P4) | later |

Owner question 2026-10-24, "what are × × eyes": in cartoons a knock-out — stunned, exhausted, "dead". Used here first as a visible **error state (#17)**, later possibly as an emotion ("overwhelmed").
