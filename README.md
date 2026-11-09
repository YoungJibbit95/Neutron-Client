<div align="center">
  <img src="assets/readme/neutron-hero.svg" alt="Neutron Client · Minecraft 1.8.9" width="100%" />

  <a href="https://github.com/YoungJibbit95/Neutron-Client/graphs/traffic"><img src="https://raw.githubusercontent.com/YoungJibbit95/Neutron-Client/readme-metrics/assets/readme/metrics/views-14d.svg" alt="Repository views · last 14 days" width="280" /></a>
  <a href="https://github.com/YoungJibbit95/Neutron-Client/graphs/traffic"><img src="https://raw.githubusercontent.com/YoungJibbit95/Neutron-Client/readme-metrics/assets/readme/metrics/clones-14d.svg" alt="Repository clones · last 14 days" width="280" /></a>

  <p>
    <a href="https://github.com/YoungJibbit95/Neutron-Client/releases"><img src="https://img.shields.io/github/v/release/YoungJibbit95/Neutron-Client?filter=v*&amp;include_prereleases&amp;sort=date&amp;label=client%20release&amp;style=flat&amp;labelColor=171321&amp;color=ec4899" alt="Latest client release, including prereleases" /></a>
    <a href="https://github.com/YoungJibbit95/Neutron-Client/releases"><img src="https://img.shields.io/github/downloads/YoungJibbit95/Neutron-Client/total?label=asset%20downloads&amp;style=flat&amp;labelColor=171321&amp;color=f97316" alt="Total downloads of all repository release assets" /></a>
    <a href="https://github.com/YoungJibbit95/Neutron-Client/stargazers"><img src="https://img.shields.io/github/stars/YoungJibbit95/Neutron-Client?style=flat&amp;labelColor=171321&amp;color=ec4899" alt="GitHub stars" /></a>
  </p>
  <p>
    <a href="https://github.com/YoungJibbit95/Neutron-Client/actions/workflows/repository-policy.yml"><img src="https://img.shields.io/github/actions/workflow/status/YoungJibbit95/Neutron-Client/repository-policy.yml?branch=main&amp;label=repository%20checks&amp;style=flat&amp;labelColor=171321" alt="Repository policy workflow status on main" /></a>
    <a href="https://github.com/YoungJibbit95/Neutron-Client/actions/workflows/codeql.yml"><img src="https://img.shields.io/github/actions/workflow/status/YoungJibbit95/Neutron-Client/codeql.yml?branch=main&amp;label=CodeQL&amp;style=flat&amp;labelColor=171321" alt="CodeQL workflow status on main" /></a>
  </p>

  <p>
    <a href="https://github.com/YoungJibbit95/Neutron-Client/releases">📥 Downloads</a> ·
    <a href="#installation">📦 Install</a> ·
    <a href="https://github.com/YoungJibbit95/Neutron-Client/issues">🐛 Report a bug</a> ·
    <a href="#development">🛠️ Build</a>
  </p>
</div>

<a name="neutron-client"></a>

# ⚛️ Neutron Client

A Minecraft **1.8.9** hacked client with a focus on modern and customizable visuals, a good user experience and good performing simple bypasses for most servers.

**🎯 Focus:** Intave and Polar Bypass Research, extended visual systems and good performance on most other anticheats. Bypasses for other anticheats are planned.

<a name="features"></a>

## ✨ Features

- 🖱️ **ClickGUI:** For Client Customization, Module Toggeling and Options, etc. Open with **Right Shift**.
- 🎨 **Themes:** PinkLemonade by default, plus presets and custom colors. Adjust blur, glow and animations.
- 🧭 **HUD:** Target, armor, session and module overlays with layout controls.
- 🧩 **Modules:** Combat, Ghost, Movement, Player, World, Exploit, Client and Render categories.

<div align="center">
  <img src="assets/readme/neutron-experience.svg" alt="ClickGUI, HUD, modules and shared client systems" width="100%" />
  <sub>Brand illustrations; game screenshots will follow.</sub>
</div>

<p align="center">
  <img src="assets/readme/neutron-divider.svg" alt="" width="100%" />
</p>

<a name="installation"></a>

## 📦 Installation

**Development preview:** use a tested bundle from [Releases](https://github.com/YoungJibbit95/Neutron-Client/releases). If none is listed, there is no published build yet.

1. Download and extract `Neutron-<version>-bundle.zip`.
2. Copy the **whole `Neutron-<version>` folder**, containing the matching JAR and JSON, into your Minecraft `versions` folder:

   | System | Folder |
   | :-- | :-- |
   | Windows | `%APPDATA%\.minecraft\versions` |
   | macOS | `~/Library/Application Support/minecraft/versions` |
   | Linux | `~/.minecraft/versions` |

3. In the official Minecraft Launcher, choose **Installations → New installation** and select `Neutron-<version>`. Enable **Modded** in the installation filters if needed. Use a **Java 8** runtime.
4. Launch and press **Right Shift**.

> Keep the folder and filenames as downloaded. Use a **separate game directory** for testing. The bundle goes in `versions`, not `mods`.

<p align="center">
  <img src="assets/readme/neutron-divider.svg" alt="" width="100%" />
</p>

<a name="development"></a>

## 🛠️ Development

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

<a name="license"></a>

## 📜 License

[GPL-3.0](LICENSE). Third-party code and assets retain their own terms. Neutron is not affiliated with Mojang or Microsoft. Follow the rules of the servers you play on.

<div align="center">
  <img src="assets/readme/neutron-footer.svg" alt="Neutron" width="100%" />
</div>
