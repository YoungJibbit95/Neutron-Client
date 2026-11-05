# Current playtest gates

- Current GitHub-main `76bd6f4f` has normalized POM SHA256 `b48d11b137513a09916a49bb12c4dd689456af02c639fd2a7d06bfea40b9e582`. The adapter accepts `5fc71918162988da5bcfc761e9fcc97febc82e84665793e5ba1203cb96e8a432`. Fresh builds therefore stop before Maven. Do not blindly expand this allowlist or overwrite the upstream POM; independently review its launcher-dist/bootstrap path first.
- A verified cache entry requires the exact source commit, adapter fingerprint, runtime identifier/hash and a successful rehash of all client files. It does not validate a different upstream build configuration or authorize activation of corrupt bytes.
- No comparable full cold/warm Maven build was completed during this timeboxed pass. Cache lookup timings are explicitly separate from full build duration.
- The early rendered-window handshake was measured on a populated developer data fixture, not a clean Windows machine. The earlier blank-start report still requires repeatable normal-start, crash/recovery and deployment acceptance.
- Signing is unavailable. Do not publish an unsigned public release from this draft.
- Complete theme/language/DPI/reduced-motion and GPU-loss acceptance, installer update/uninstall, client settings, offline launch and export checks remain required.
