# Porting notes

The build has 17 targets: 1.21, 1.21.1 through 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 and 26.3. Enabled targets are in `gradle/minecraft-targets.json`, shared by Gradle and the GitHub Actions matrix. Dependency versions are in `versions/<version>/version.properties`.

## Source layout

- `src/main/java`: 1.21 gameplay and client code, using Yarn names. Stonecutter's editor version is 1.21.1.
- `src26/main/java`: the 26.x port, using Mojang names. Compatibility branches cover the later stable releases.
- `src/main/resources`: shared textures, models, translations, recipes and other data. Each build generates its version-specific forms.
- `src/{test,gametest,clienttest}` and `src26/{test,gametest,clienttest}`: unit tests, server gameplay tests and client previews for each family.
- `build.gradle`: the remapped 1.21 build. `build-modern.gradle`: the unobfuscated 26.x build.

Keep the editor target on 1.21.1. Select modern build targets with `-Preterra.targets`; edit their sources in `src26/`. Keep gameplay changes in both source trees. Small API differences belong in compatibility helpers. Do not edit anything under `versions/*/build/generated/`; Gradle replaces it.

## Java and Gradle

Gradle 9.8 and Loom 1.19 require Java 25 to run. The 1.21 targets still compile for Java 21 and need that toolchain installed. The 26.x targets compile for Java 25. GitHub Actions installs both JDKs and builds each target separately.

The modern build uses the non-remapping Loom plugin, `implementation` dependencies and the ordinary `jar` task. It does not request Yarn mappings or ship a remapping reference map. Both families support `stageRelease`, which copies the release JAR into the root `build/libs` folder.

Shared releases are defined in `gradle/release-groups.json`. Both build families apply `gradle/release.gradle` to set the shared artifact version, Minecraft compatibility and highest required Fabric Loader version. That Loader version is used for both the dependency and release metadata, so development clients and test servers satisfy the shared JAR's requirement. Selecting a target also includes its group members. Each member keeps its own Minecraft and Fabric API dependencies and test runs.

`stageRelease` delegates to the group's first target, builds every member, and compares JAR entry hashes, including classes, assets, recipes, metadata and mixin data. It refuses to stage a group that differs. On success it copies one shared file and removes obsolete per-target files for that group and mod version from `build/libs/`. Files under each target's build directory are development artifacts; upload the files collected in root `build/libs/`.

When adding a Minecraft version, add its normal Stonecutter target and dependencies first. Keep it separate until its runtime checks pass and it can produce identical release contents to a compatible group. Then add it to the ordered group list. The group must contain only compatible consecutive releases. Packaging checks equality every time; it cannot establish runtime compatibility for an untested new Minecraft version. A failed comparison requires resolving the difference or splitting the group. Ordinary mod updates use `mod_version` from `gradle.properties`; filenames and metadata update automatically.

The 26.x source was translated from the tested 1.21.11 source, then adapted for the new menu extraction API, renamed Fabric hooks, clocks and saved-data locations. The 26.2 screen and HUD changes and the 26.3 SDL input changes are isolated in helpers. Hoe interactions use a Fabric callback because 26.3 removed the old HoeItem implementation.

Existing restoration data is imported into the namespaced save file on first load. The old file is left intact. Biome generation converts the old sky and fog colors into environment attributes; 26.3 also stores mob spawning there.

## Verification

The recorded build, gameplay and preview results have been reviewed. The README lists the remaining gaps. The 26.3 loot schema required a resource conversion for item modifiers and drop predicates; its runtime checks passed after that fix. Compile-only checks are useful during a port but cannot establish that menus, mixin injections and animations work in Minecraft.

The commands and output locations are in the [README](../README.md#building-from-source). Start with 26.1, then check each remaining target once. Keep the logs, GameTest XML reports and preview screenshots when reporting a problem.

References: [Fabric 26.1 changes](https://fabricmc.net/2026/03/14/261.html), [26.2 changes](https://fabricmc.net/2026/06/15/262.html), [26.3 changes](https://fabricmc.net/2026/09/15/263.html), and the [vanilla migration primers](https://docs.neoforged.net/primer/docs/26.1/).
