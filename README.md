
# Orebfuscator - Anti X-Ray
[![Release Status](https://github.com/Imprex-Development/Orebfuscator/workflows/Releases/badge.svg)](https://github.com/Imprex-Development/Orebfuscator/releases/latest) [![Build Status](https://github.com/Imprex-Development/Orebfuscator/workflows/Build/badge.svg)](https://github.com/Imprex-Development/Orebfuscator/actions?query=workflow%3ABuild)

<picture width="220">
  <img align="right" src="https://github.com/user-attachments/assets/6d05fbe5-6a60-4634-a9a1-9285c2aeafe4" width="220" height="220" alt="Orebfuscator logo" />
</picture>

Orebfuscator empowers server owners to protect their server from X-Ray Clients and Texture Packs, all while offering a high degree of configurability. This is achieved through modifying network packets without altering your game world, guaranteeing a secure and reliable experience for users. With Orebfuscator, you can tailor the settings to suit your server's needs, ensuring precise control over the visibility of specific blocks. This means that not only does Orebfuscator safeguard your world's integrity, but it also empowers you to fine-tune your Anti-X-Ray measures for the best gameplay experience.

## Changes in this fork (orebfuscator-packetevent)

A fork of [Imprex-Development/Orebfuscator](https://github.com/Imprex-Development/Orebfuscator) that has been migrated from ProtocolLib to [PacketEvents](https://github.com/retrooper/packetevents).

**PacketEvents instead of ProtocolLib**
* The plugin depends on `packetevents` (2.14.0+) instead of ProtocolLib.
* Chunk packets are parsed and rewritten directly in the `ByteBuf`, without PacketEvents wrappers and without re-encoding.
* Delayed packets (chunks, chunk batches, block updates, respawn packets) are sent through a per-channel queue in their original order, just like the old ProtocolLib async listener did.
* Neighboring chunks are fetched without blocking: immediately on Paper and Folia, and through asynchronous loading on Spigot.

**Hiding behind blocks**
* The new `tag(orebfuscator:block_entities)` tag contains every block with a block entity (chests, furnaces, spawners, barrels, shulker boxes, etc.). They are hidden by the proximity hider until the player can actually see them (`rayCastCheck`).
* The new `entityHider` option in proximity configs hides entities and players behind blocks: it removes them on the client, drops their packets and resends them once they become visible. The tab list and `canSee` are not affected.
* Always visible: projectiles, lightning, the ender dragon, glowing entities, the player's own vehicle and passengers, and everything closer than 4 blocks.
* Config migration to version 7 automatically enables `rayCastCheck` and `entityHider` and adds the block entity tag. The previous config is saved as `config-old.yml`.

**Version support and build**
* Minecraft 1.21.11 and newer only (NMS adapters `v1_21_R7` and `v26_1`), Java 25 only.
* Removed the Fabric module, bStats and all NMS adapters below 1.21.11.
* Spigot, Paper and Folia API updated to 1.21.11, SpecialSource updated to 1.11.6.

### Features
* Seamless Integration: Plug & Play functionality for effortless use.
* Extensive Configuration: Highly customizable settings to tailor the experience to your liking.
* Server Compatibility: Designed for Spigot-based servers 1.21.11 and newer.
* Block Obfuscation: Conceal non-visible blocks from players' view.
* Block-Entity Support: Hide every block entity (`tag(orebfuscator:block_entities)`) behind blocks.
* Entity Hiding: Hide entities and players that are behind blocks (`entityHider` in proximity configs).
* Dynamic Block Visibility: Adjust block visibility based on player proximity and distance.

### Requirements
* Java 25 or higher
* Spigot, Paper, Folia or compatible forks (1.21.11 or newer)
* [PacketEvents](https://modrinth.com/plugin/packetevents) 2.14.0 or later

### Installation
1. Download [PacketEvents](https://github.com/retrooper/packetevents/releases)
2. Download [Orebfuscator](https://github.com/Imprex-Development/Orebfuscator/releases)
3. Place both plugins in your _plugins_ directory
4. Start your server and [configure Orebfuscator](https://github.com/Imprex-Development/Orebfuscator/wiki/Config) to your liking

Still having trouble getting Orebfuscator to run check out our [common issues](https://github.com/Imprex-Development/Orebfuscator/wiki/Common-Issues).

## Version Support Policy

### Supported Minecraft Versions

Orebfuscator follows a **rolling support window** for Minecraft versions.

Starting with **Minecraft 27.x**, Orebfuscator will officially support **only the two most recent major Minecraft releases**.

Example:

| Latest Minecraft Version | Supported Versions |
|--------------------------|--------------------|
| 27.x                     | 27.x and 26.x      |
| 28.x                     | 28.x and 27.x      |

When a new major Minecraft version is released, the oldest supported version will be dropped in a future Orebfuscator release.

Older versions may still work but are **not guaranteed to receive fixes or compatibility updates**.

### Testing Policy

Testing policy differs depending on the Minecraft release cycle.

**Until Minecraft 27.x**

Each Orebfuscator release is tested against all supported Minecraft versions (1.21.11 and newer) on PaperMC and Spigot.

**After Minecraft 27.x**

Testing will focus on the **latest Minecraft major version only** for each Orebfuscator release.

Other versions within the support window may remain compatible but will not necessarily be tested for every release.

Servers running untested versions may still function, but issues specific to those versions may not be prioritized.

### Legacy support
For compatibility with Java 11 or Minecraft 1.9.4 and later, you can use any legacy release prior to version 5.5.0. Please note that these legacy releases will no longer receive regular support or updates. However, they may receive critical security and vulnerability patches if necessary.

### Maven

To include the API in your Maven project, add the following configuration to your `pom.xml`:

```xml
<repositories>
  <repository>
    <id>codemc-repo</id>
    <url>https://repo.codemc.io/repository/maven-public/</url>
  </repository>
  <!-- Additional repositories can be added here if needed -->
</repositories>

<dependencies>
  <dependency>
    <groupId>net.imprex</groupId>
    <artifactId>orebfuscator-api</artifactId>
    <version>5.2.4</version>
  </dependency>
  <!-- Add other dependencies as required -->
</dependencies>
```

## License

### Modifications

Copyright (C) 2020–2026 Imprex-Development  
Completely rewritten to support Minecraft v1.14 and higher.

Copyright (C) 2016 Aleksey_Terzi  
Significant rework to support Minecraft v1.9.

These modifications are distributed under the same license as the original work.

---

### Original Work

Copyright (C) 2011–2015 lishid

This program is free software: you can redistribute it and/or modify  
it under the terms of the GNU General Public License as published by  
the Free Software Foundation, version 3.

This program is distributed in the hope that it will be useful,  
but WITHOUT ANY WARRANTY; without even the implied warranty of  
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the  
GNU General Public License for more details.

You should have received a copy of the GNU General Public License  
along with this program. If not, see <https://www.gnu.org/licenses/>.

See the `LICENSE` file for the full license text.
