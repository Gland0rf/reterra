# ReTerra

The Overworld is still here, but almost nothing grows.

ReTerra turns Minecraft into a survival game about bringing life back to a damaged world. Search abandoned buildings for surviving seeds, establish your first farm and gradually restore the land around your home.

## Start with a patch of dirt

Your first supplies come from old farmhouses, greenhouses and research buildings. Recover what you can, keep something for planting and make compost to turn sterile ground into fertile soil.

Once you have a farm running, build machines to help restore larger areas. The Soil Reclaimer and Restoration Core handle the ground, while you plant grass, flowers and trees. Prepare a suitable habitat, then use the Revival Chamber to return wildlife.

## Build a living ecosystem

Different environments need different care:

- **Plains** need productive farms and open grassland.
- **Forests** need varied trees and a healthy canopy.
- **Swamps** need vegetation around the water.
- **Highlands** need life brought back to their slopes.

Healthy regions become self-sustaining and gradually spread into the surrounding land. Restore all four ecosystem types to awaken the Heart of the World.

## Features

- A barren Overworld with dead trees, damaged soil and empty habitats.
- Abandoned structures containing seeds, supplies and preserved animal samples.
- Farming and composting that let you produce your own restoration materials.
- Restoration machines with animated effects and dedicated menus.

## Installation

1. Install **Fabric Loader** for your Minecraft version.
2. Download the ReTerra file that lists your Minecraft version.
3. Install **Fabric API** for that same Minecraft version.
4. Put both JAR files in your `mods` folder.
5. Start Minecraft with Fabric and create a new world.

For multiplayer, install ReTerra and Fabric API on both the server and every player's client.
## License

ReTerra is licensed under [LGPL 3.0](LICENSE) (`LGPL-3.0-only`). The accompanying GPL 3.0 terms are included in [COPYING](COPYING).

## Testing the Heart

With cheats enabled or operator permission, look at an assembled Heart and run `/reterra heart activate`. This skips the four-ecosystem requirement and plays the normal awakening animation. The Heart still needs its supports.

You can also target it with `/reterra heart activate <x> <y> <z>`. Use `/reterra heart disable` to stop its effects and reset the animation for another test.

## Publishing to Modrinth

After a GitHub release is ready, preview its Modrinth uploads locally:

```powershell
py -3 scripts/publish_modrinth.py --tag v0.1.1
```

To upload, run the same command with `--publish`:

```powershell
py -3 scripts/publish_modrinth.py --tag v0.1.1 --publish
```

The command reads `MODRINTH_TOKEN` from your terminal environment first, then from the project's root `.env` file. In `.env`, use `MODRINTH_TOKEN=your_token_here`; quoted values are also supported. If no token is set, `--publish` and `--auth` ask for it without displaying it. Create a token in your [Modrinth account settings](https://modrinth.com/settings/pats) with permission to create versions. Environment files are ignored by Git.

The destination defaults to `reterra`. You can set `MODRINTH_PROJECT_ID` in your terminal environment or root `.env`, or override it with `--project PROJECT_ID`. Use the actual project ID if the slug returns 404. For private projects, the token needs both **Read projects** and **Read versions**, as well as **Create versions** for uploading. Use `--auth` to preview with authentication. Python 3.10 or newer is required.

Modrinth attachment filenames use `0.1.1+26.3.jar`, or `0.1.1+26.1-26.1.2.jar` for a shared JAR. GitHub filenames, internal versions and JAR contents stay unchanged.

This downloads the published GitHub JARs, verifies their checksums and packaging, and creates one Modrinth version per JAR with the correct Minecraft versions and Fabric API dependency. Matching uploads are skipped on reruns; conflicting files stop the upload. Normal version tags use the release channel; tags with a suffix use beta unless you pass `--channel release`, `beta` or `alpha`. Replace the tag to publish a future release. Nothing is uploaded automatically by GitHub Actions.
