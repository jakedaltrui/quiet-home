# Brag Plan: Quiet Home

## The nine decisions

1. App: a minimal, remote-first native Java home screen for Fire TV.
2. Strongest claim: no feed, account, analytics, or ads in the launcher.
3. Visual hook: misty autumn wallpaper, glass dock, amber LED ticker.
4. UI: Favorites focus moving, then All Apps opening, then live-game status.
5. Shortest satisfying cut: 22 seconds.
6. Tone: polished; quiet premium product film with restrained transitions.
7. Audio: low instrumental music, two soft interface clicks; no voiceover.
8. Share caption: I built a calmer home screen for my Fire TV. Native Java, a live sports ticker, and no ad feed. Quiet Home is open source.
9. User flow: Favorites → D-pad selection → All Apps → favorite live-game status.

## Angle and hook

Your TV. A little quieter. The home screen itself is the payoff, followed by the tiny native build behind it. Do not claim that the video is a physical-device recording. It is a source-based recreation with fictional scores, date.

## Identity and LOOK

LOOK: ground #0B1418; text #FFFFFF; amber #FFB530 from the native LED ticker; pale green #8CD3B1; system Helvetica Neue, 72–112 px display and 30–36 px labels; original autumn woodland photograph at 1920×1080; 64 px margins. BANNED: fake app logos, vanity metrics, neon purple, invented product claims, customer data.

## Storyboard (22 seconds)

- 0–3 s: Hook. “Your TV. A little quieter.” Still typography, quick soft arrival over the original wallpaper. Music fades in.
- 3–14 s: Product. Full-screen source-based recreation. Header ticker, clock/date, five Favorites. Selection moves between app cards at 4.64 and 6.28 s; Down opens All Apps at 8.22 s. Returning to Favorites at 11.47 s reveals a sample favorite-team LIVE strip. Ticker scrolls continuously. Tiles use real brand SVGs with proportional sizing; provenance is in docs/ASSETS.md. Sample scores are marked.
- 14–18 s: Build. “Native Java. No backend.” Actual source filenames and command-line toolchain. Settle and hold all copy for reading.
- 18–22 s: Outro. “Quiet Home” and “Make your home screen yours.” Repository address and MIT license. End title lands on the 18.01 s music cue and holds.

## Audio and cues

Music: Happy Beats / Business Moves Vol. 10, Sascha Ende, CC BY 4.0. Use first 22 seconds at 0.20 gain with edge fades. Tempo preset 109.96 BPM. Source cue grid 4.64, 6.28, 8.22, 11.47, 18.01 s supports selection and scene changes. Reading time takes precedence. Interface click_003.ogg (Kenney, CC0) supports two focus changes. No voiceover.

Audio-reactive extraction attempted using the media-use helper, but its NumPy dependency is unavailable locally. Skip audio reactivity; retain restrained product motion. Hyperframes registry skill is not installed; the cached CLI catalog returned no matching transitions. Use simple seek-safe GSAP motion.
