# Security and privacy

## Scope

Quiet Home is an experimental sideloaded launcher. Review the source and try manual launch before granting optional permissions. Public availability is not a security certification or a promise of compatibility with every Fire OS release.

## Data handling

- No user account, telemetry, analytics SDK, ads, or project-operated server.
- HTTPS requests go directly to MLB and ESPN. Those providers receive your IP address and request metadata.
- Sports and widget identifiers are cached in app-private preferences. Android backup is disabled.
- Installed app information is queried locally for display; it is not sent to a server by this code.
- Network and Wi-Fi permissions support the connection indicator. No microphone, camera, storage, location, contacts, or credential permissions are requested.
- The accessibility service is opt-in, filters Amazon launcher window-state events, and cannot retrieve window contents. The service is protected by Android's `BIND_ACCESSIBILITY_SERVICE` permission.
- Cleartext HTTP is disabled. Feed requests have timeouts and response size limits.

## Build and install boundaries

The build script creates a local **development** keystore using standard Android debug credentials. This is intentional and not suitable for distributing trusted production updates. Generated keys, APKs, environment files, and build artifacts are ignored. No original personal signing key, device inventory, local addresses, or deployment notes are part of this repository.

Keep ADB on a trusted LAN, never forward its port to the internet, and disable debugging after installation. Do not disable Amazon's launcher. See the [recovery guide](docs/INSTALL.md).

## Reporting an issue

Use GitHub's **Report a vulnerability** option if available. If it is unavailable, open an issue asking for a private reporting channel without including exploit details, keys, logs containing private data, or device addresses. There is no guaranteed response SLA.
