# Contributing

Thank you for helping make Bunny Burrow kinder and more accessible.

## Before opening a pull request

- Keep the game offline and child-safe by default.
- Do not add advertising, analytics, accounts, dynamic code, or network SDKs.
- Keep English and Russian string resources in sync.
- Use only assets with a clearly documented open-source or public-domain license.
- Run `./scripts/verify_android.sh`.

## Visual changes

Runtime art is drawn by `BunnyBurrowView` at a 1280×720 logical resolution.
Keep controls at least 48 logical pixels across. Overlay cards must preserve a
minimum 68-pixel inner margin in both languages and must not rely only on color
to communicate state.

## Commit hygiene

- Never commit a keystore, signing password, local SDK path, or Play credential.
- Keep generated build output out of Git.
- Add the exact license text for every new third-party asset.
