# Rebuild the launch video

The editable composition is in `composition/`; the rendered video and posters are in `docs/media/`. It is a source-based UI recreation, not a recording from a TV. Scores and time are sample data.

Use Node.js 22+, FFmpeg, and Hyperframes 0.8.222:

```sh
cd brag-output/composition
npx hyperframes@0.8.222 check --json
npx hyperframes@0.8.222 preview
npx hyperframes@0.8.222 render --quality delivery --output ../../docs/media/brag.mp4
```

Check the result before posting. The 22-second film is 1920×1080 at 30 fps. See `brag-plan.md` for timing and `../docs/ASSETS.md` for music, sound, and runtime licenses. The MP4's first frame is a settled product frame, baked in as its thumbnail.

On this build machine the editor preview was blocked by local listening-port restrictions; automated checks and frame captures work using its local-file path. `scripts/render_video.py` exports all 660 checked timeline frames through Hyperframes, then encodes and mixes them with FFmpeg as a fallback.
