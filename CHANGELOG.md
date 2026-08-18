# Changelog

All notable changes to ZkrWatch are documented here.

## v1.1.0 — 2026-08-18

A big UX update: two headline features (Sentry Mode and customizable buttons) plus a
wave of glanceability, feedback, and polish improvements.

### Added
- **Sentry Mode** — arm/disarm the car's surveillance mode; the button reflects the
  live state (read from the vehicle's remote-control endpoint).
- **Customizable buttons** — a **Buttons** settings screen lets you show/hide each action,
  so smaller watches stay uncluttered. Extra actions ship hidden until you opt in.
- **Flash lights** (find-my-car) action *(opt-in)*.
- **Start / stop charging** action *(opt-in)*.
- **Lock / Unlock from the Tile** — act without opening the app (runs in the background).
- **Charging ETA** — time-to-full shown while charging.
- **Cabin temperature** on the Climate button.
- **Low-battery warning** — the charge bar turns amber (≤20%) / red (≤10%).
- **"Updated ago"** freshness line under the range.
- **Haptic feedback** on send and on the car's confirm/reject response.
- **In-place progress ring** around the button while a command is in flight (and it
  blocks accidental double-taps).
- **Long-press the battery** to force an on-demand refresh.
- **Swipe-to-dismiss** on the settings screen.

### Changed
- Clearer, cause-specific command errors: *Car unreachable*, *Sign-in expired*, *Car declined*.
- The action buttons now cluster toward the centre and wrap to a 2×2 grid so the round
  screen never clips the corner buttons.
- The battery/SOC hero no longer overlaps the clock.

### Notes
- The published APK still contains **no keys** — configure it with your own credentials
  via the setup script (see [setup/README.md](setup/README.md)).
- The Tile Lock/Unlock action sends a real command; give it a quick test on first use.

## v1.0.0 — 2026-08-14

Initial public release: battery/range, lock/unlock, open trunk, climate, glanceable Tile
and watch-face complication. Direct-to-cloud, keyless distribution.
