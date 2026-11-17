# ReTerra

**Restore a barren Overworld through farming, machines and returning wildlife.**

ReTerra starts with a world of dead trees, coarse ground and empty habitats. Old farmhouses and research buildings shelter a few surviving seeds and preserved samples. Recover them, establish a farm, and build a home worth restoring.

Start by making a patch of fertile dirt. Expand with a Soil Reclaimer and Restoration Core, plant grass and varied vegetation, then bring back animals with a Revival Chamber. Each ecosystem has its own needs: plains favor farms, forests need varied trees, swamps need waterside plants, and highlands need fertile slopes.

Healthy regions become self-sustaining and spread naturally. Reclaim all four ecosystem types to awaken the Heart of the World.

## Features

- Barren terrain with caves, ores and ordinary Minecraft survival intact.
- Distinct ruins holding food, seeds, cultures and animal samples.
- Animated restoration machines with in-world assemblies that can face any direction.
- Field Notes, searchable recipes, materials lists and habitat progress in-game.
- Renewable crops, grass cultures and flowers; restoration does not require endless ruin searches.
- Shared ecosystem progress and benefits for multiplayer worlds.
- Custom 16px items and machine textures.

## Getting started

Create a fresh world, follow the farmhouse compass and search the pantry. Open the Seed Pouch, keep seeds and spare vegetables for planting, and make compost from a log with a mushroom or rotten flesh. Mix compost with coarse dirt or gravel to make your first fertile soil.

**J opens Field Notes.** The welcome message also has a clickable link. Every machine has a Recipes button, and Build lists its support blocks.

## Requirements

**Minecraft Java 1.21 through 1.21.11, Java 21 and the matching Fabric API. Use Fabric Loader 0.16.14+ for 1.21 and 1.21.1, or 0.18.4+ for 1.21.2 through 1.21.11.** Choose the ReTerra build for your Minecraft version. Install ReTerra and Fabric API on both the server and clients. No separate resource pack is needed.

The barren-world game rule is on by default. Start a new world for the intended progression. Turn **ReTerra barren terrain** off in Game Rules if you prefer ordinary terrain; changing it affects newly explored chunks.

This is a beta build. The project is still being balanced through survival playtests.

## Shared releases

Upload each JAR collected in root `build/libs/` once. Select every Minecraft version listed for that file in the [README](../README.md#minecraft-versions). There are currently 10 release files for 17 Minecraft versions. For example, `reterra-0.9.0+mc1.21.6-1.21.8.jar` supports 1.21.6, 1.21.7 and 1.21.8.

Players install the same shared ReTerra file with Fabric API matching their Minecraft version. Rebuild with `stageRelease` after changing `mod_version`; compatibility checks and filenames update automatically. Adding a new Minecraft version still requires porting and testing before listing it as supported.
