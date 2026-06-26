[![9,041 downloads across all platforms](https://www.modpackindex.com/badge/mod/103869/better-mc-screenshots/downloads.svg)](https://www.modpackindex.com/mod/103869/better-mc-screenshots) 
[![Supports Minecraft 1.21 – 26.1.2](https://www.modpackindex.com/badge/mod/103869/better-mc-screenshots/version.svg?color=red)](https://www.modpackindex.com/mod/103869/better-mc-screenshots)
[![Used in 10 modpacks on Modpack Index](https://www.modpackindex.com/badge/mod/103869/better-mc-screenshots/modpacks.svg)](https://www.modpackindex.com/mod/103869/better-mc-screenshots)

# Better MC Screenshots

Make your screenshot experience actually *better*.

This mod upgrades the default Minecraft screenshot system into something modern, fast, and genuinely enjoyable to use — no more tabbing out, digging through folders, or guessing if the shot even saved correctly.

Everything you need is right there, in-game.

## 🚀 Why use this mod?

Taking screenshots in vanilla Minecraft is... fine.
But checking them? Managing them? Sharing them quickly?

That’s where things get annoying.

This mod fixes all of that by bringing a smooth, integrated screenshot workflow directly into the game — so you can focus on playing, not file hunting.

## ✨ What you get

### 📷 Instant Preview

No more guessing. The moment you take a screenshot, you’ll see it instantly in-game.

### 💬 Smarter Chat Notifications

Screenshot messages are no longer just text — they’re interactive.
Open previews, jump to the folder, and more with a single click.

### 🖼️ Built-in Gallery

Browse all your screenshots without leaving Minecraft.
Fast, simple, and always accessible.

### 📋 Copy & Share Instantly

Copy screenshots straight from the game and paste them anywhere — no need to open folders.

### 🔊 Custom Screenshot Sound

Don’t like the default sound? Change it to whatever* fits your vibe.

### ⚡ Quick Access Everywhere

New buttons and shortcuts make managing screenshots effortless.

## 👀 What does it look like in reality

<details>
<summary><b>⚙️ Configuration Menu</b></summary>

![Configuration Menu](https://cdn.modrinth.com/data/oRvsIBeW/images/ac26160363113895d4512cfead2e55e5f24d2d52.png)

</details>

<details>
<summary><b>👁️ In-Game Screenshot Viewer</b></summary>

![In-game screenshot viewer](https://cdn.modrinth.com/data/oRvsIBeW/images/37ddd6e3864e2375cb14bc6af6efe675903d35b0.png)

</details>

<details>
<summary><b>💬 Mini Preview & Updated Chat Messages</b></summary>

![Mini Preview & Updated Chat Messages](https://cdn.modrinth.com/data/oRvsIBeW/images/8413bb4380a39ce603b920f4dfa614b7c4f5dc27.png)

</details>

<details>
<summary><b>🖼️ Screenshot Gallery</b></summary>

![Screenshot Gallery](https://cdn.modrinth.com/data/oRvsIBeW/images/fbd0133748eb6c831358e76dc24301b674b0637d.png)

</details>

<details>
<summary><b>🎮 New Buttons in the Game Menu</b></summary>

![New Buttons in The Game Menu](https://cdn.modrinth.com/data/oRvsIBeW/images/0d0fb13cf9b7c6895ec6fd1c4102cf0c93dbac23.png)

</details>

## 🎯 In short

This mod turns screenshots from a basic feature into a smooth, fully integrated experience.

Take it. Preview it. Manage it. Share it.
All without ever leaving the game.

## 🔧 Installation

1. Install a supported loader: Fabric (with Fabric API), NeoForge, or Forge.
2. Download the mod from [Modrinth](https://modrinth.com/mod/better-mc-screenshots).
3. Drop the mod into your `mods` folder
4. Launch the game and you're ready to go

## 🛠️ Development

The project uses layered shared sources:

```text
common/src/main/                    Loader-independent Java and shared assets
common/src/minecraft/               Code shared by every version and loader
common/src/versions/<range>/        Code shared by loaders for a version range
common/src/loaders/<loader>/<range> Code shared by versions of one loader
common/src/loaders/forge-family/    Code shared by Forge and NeoForge
fabric/<version>/                   Fabric client-only exceptions
neoforge/<version>/                 NeoForge-only exceptions
forge/<version>/                    Forge-only exceptions and standalone build
```

The root Gradle build selects the correct layers for Fabric and NeoForge.
Forge uses its own wrapper in `forge/` because ForgeGradle requires a different
Gradle setup.

To build the project using Gradle, run the following commands in the project root, depending on your Minecraft version:

```bash
./gradlew buildAll                          # All versions

./gradlew fabric:1.21:build                 # version Fabric 1.21 - 1.21.1
./gradlew fabric:1.21.2:build               # version Fabric 1.21.2 - 1.21.4
./gradlew fabric:1.21.5:build               # version Fabric 1.21.5
./gradlew fabric:1.21.8:build               # version Fabric 1.21.6 - 1.21.8
./gradlew fabric:1.21.10:build              # version Fabric 1.21.9 - 1.21.10
./gradlew fabric:1.21.11:build              # version Fabric 1.21.11
./gradlew fabric:26.1:build                 # version Fabric 26.1 - 26.1.2
./gradlew fabric:26.2:build                 # version Fabric 26.2

./gradlew neoforge:1.21:build               # version NeoForge 1.21 - 1.21.1
./gradlew neoforge:1.21.2:build             # version NeoForge 1.21.2 - 1.21.4
./gradlew neoforge:1.21.5:build             # version NeoForge 1.21.5
./gradlew neoforge:1.21.8:build             # version NeoForge 1.21.6 - 1.21.8
./gradlew neoforge:1.21.10:build            # version NeoForge 1.21.9 - 1.21.10
./gradlew neoforge:1.21.11:build            # version NeoForge 1.21.11
./gradlew neoforge:26.1:build               # version NeoForge 26.1 - 26.1.2
./gradlew neoforge:26.2:build               # version NeoForge 26.2

./forge/gradlew -p forge :1.21:build        # version Forge 1.21 - 1.21.1
./forge/gradlew -p forge :1.21.3:build      # version Forge 1.21.3 - 1.21.4
./forge/gradlew -p forge :1.21.5:build      # version Forge 1.21.5
./forge/gradlew -p forge :1.21.8:build      # version Forge 1.21.6 - 1.21.8
./forge/gradlew -p forge :1.21.10:build     # version Forge 1.21.9 - 1.21.10
./forge/gradlew -p forge :1.21.11:build     # version Forge 1.21.11
./forge/gradlew -p forge :26.1:build        # version Forge 26.1 - 26.1.2
./forge/gradlew -p forge :26.2:build        # version Forge 26.2
```

## ❤️ Feedback & Support

Found a bug? Got an idea?  
Feel free to open an issue or share your suggestions — every bit of feedback helps improve the mod!

If you enjoy the mod, consider leaving a ⭐ on GitHub, following the project on Modrinth, or supporting me on Ko-fi.  
It’s a great motivation to keep improving the mod and adding new features!

[![Support me on Ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/gdlev)

Enjoy your new screenshot experience 📸
