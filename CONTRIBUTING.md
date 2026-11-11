# Contributing

Small, focused improvements are welcome. Build with the versions in the README and run `python3 scripts/check_public.py` before opening a pull request.

Useful areas: on-device configuration, more accessibility semantics, clearer stale-feed status, parser fixtures, and device compatibility reports. For device reports, include model, Fire OS version, and behavior; redact network addresses, account information, and device identifiers.

Keep Java compatible with Java 8 source syntax and Android API 28. Avoid new network services, SDKs, or permissions unless the PR explains why they are needed. Never commit keys, APKs, personal setup notes, or installed-app inventories. UI changes should include a screenshot or recording with private information removed.

The demo video is a labeled UI recreation. Do not describe it as evidence of physical-device testing. Production APK distribution requires a separate, protected signing process; CI only builds development artifacts.
