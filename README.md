<div align="center">
  <img src="assets/readme/neutron-hero.svg" alt="Neutron Client · Minecraft 1.8.9" width="100%" />

  <a href="https://github.com/YoungJibbit95/Neutron-Client/graphs/traffic"><img src="assets/readme/metrics/views-14d.svg" alt="Repository views · last 14 days" width="280" /></a>
  <a href="https://github.com/YoungJibbit95/Neutron-Client/graphs/traffic"><img src="assets/readme/metrics/clones-14d.svg" alt="Repository clones · last 14 days" width="280" /></a>

  <p>
    <a href="https://github.com/YoungJibbit95/Neutron-Client/releases">Downloads</a> ·
    <a href="#installation">Install</a> ·
    <a href="https://github.com/YoungJibbit95/Neutron-Client/issues">Report a bug</a> ·
    <a href="#development">Build</a>
  </p>
</div>

# Neutron Client

A Minecraft **1.8.9** client with customizable menus, HUDs and gameplay modules. Runs on **Java 8**.

**Focus:** Intave/Polar bypass research, consistent visuals and maintainable client systems.

## Features

- **ClickGUI:** Panel and iOS layouts, module settings and keybinds. Open with **Right Shift**.
- **Themes:** PinkLemonade by default, plus presets and custom colors. Adjust blur, glow and animations.
- **HUD:** Target, armor, session and module overlays with layout controls.
- **Modules:** Combat, Ghost, Movement, Player, World, Exploit, Client and Render categories.

<div align="center">
  <img src="assets/readme/neutron-experience.svg" alt="ClickGUI, HUD, modules and shared client systems" width="100%" />
  <sub>Brand illustrations; game screenshots will follow.</sub>
</div>

## Installation

Development preview: use a tested bundle from [Releases](https://github.com/YoungJibbit95/Neutron-Client/releases). If none is listed, there is no published build yet.

1. Download and extract `Neutron-<version>-bundle.zip`.
2. Copy the **whole `Neutron-<version>` folder**, containing the matching JAR and JSON, into your Minecraft `versions` folder:

   | System | Folder |
   | :-- | :-- |
   | Windows | `%APPDATA%\.minecraft\versions` |
   | macOS | `~/Library/Application Support/minecraft/versions` |
   | Linux | `~/.minecraft/versions` |

3. In the official Minecraft Launcher, choose **Installations → New installation** and select `Neutron-<version>`. Enable **Modded** in the installation filters if needed. Use a **Java 8** runtime.
4. Launch and press **Right Shift**.

Keep the folder and filenames as downloaded. Use a separate game directory for testing. The bundle goes in `versions`, not `mods`.

## Development

Use **Temurin 8u482** and **Maven 3.9.15**:

```bash
git clone https://github.com/YoungJibbit95/Neutron-Client.git
cd Neutron-Client
mvn test
mvn -Prelease-bundle package
```

[Development setup](docs/DEVELOPMENT-WORKFLOW.md) · [Release bundles & changelog](docs/RELEASES.md) · [Technical reference](docs/CLIENT_TECH_REFERENCE.md) · [Microsoft sign-in setup](design/microsoft-browser-login.md)

Version tags build a JAR + launcher JSON, ZIP, checksums and a short changelog automatically. Builds are tested in Actions; game and account sign-in checks are still required before publishing a release. [Traffic badge setup](docs/README_ASSETS.md#traffic-badges) is documented separately.

[Contributing](CONTRIBUTING.md) · [Security](SECURITY.md) · [Roadmap and compatibility](docs/PROJECT-DIRECTION.md)

## License

[GPL-3.0](LICENSE). Third-party code and assets retain their own terms. Neutron is not affiliated with Mojang or Microsoft. Follow the rules of the servers you play on.

<div align="center">
  <img src="assets/readme/neutron-footer.svg" alt="Neutron" width="100%" />
</div>
