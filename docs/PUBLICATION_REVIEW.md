# Publication review

Prepared November 11, 2026.

## Checks completed

- Built the Android APK with JDK 17, Android platform 35, and Build Tools 35.0.1; `apksigner verify` passed.
- Rebuilt from a clean source export with a newly generated development key.
- Reviewed tracked files and ran the targeted publication guard. Personal signing keys, APKs, build output, device inventories, setup notes, environment files, and local addresses are excluded.
- Started a fresh Git history rather than importing the private workspace's notes or files.
- Disabled cleartext HTTP, capped feed bodies at 2 MiB, preserved cached scores after total feed failure, and narrowed the optional accessibility event filter.
- Matched the public feature descriptions to implemented behavior. The unused weather helper and unimplemented ambient-mode claims are omitted.
- Replaced the supplied photograph with original generated artwork. Retained third-party music and runtime notices.
- Passed Hyperframes runtime, layout, lint, contrast, and craft checks with zero errors or warnings; 49 text contrast checks passed.
- Verified the final video: H.264/AAC, 1920×1080, 30 fps, 660 frames, 22 seconds. Inspected decoded frames and used a settled product frame as the first-frame poster.

## Remaining validation

The GitHub Actions workflow repeats the build and repository checks on pushes and pull requests. No cross-device Fire TV runtime testing has been performed for this public copy. Accessibility redirection, PIA widget binding, and sleep/wake behavior remain firmware-dependent. The video is explicitly a UI recreation with sample data, not evidence of hardware testing.

The repository guard checks selected credential and private-file patterns; it is not a comprehensive independent security audit. Production APK releases require a separate protected signing process.
