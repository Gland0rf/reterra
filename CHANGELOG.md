# ReTerra 0.9.0

## Player-facing changes

- Replaced the item artwork with 16px sprites using a shared palette. Samples have distinct labels, the circuit has visible contacts, and bottles, notes and supplies have clearer silhouettes.
- Added separate front, side and top textures for every machine, smaller supply lights, and a project icon.
- Reworked menus around stone panels and paper notes. Selected tabs are visible; simple machines no longer show a Habitat tab.
- Put Recipes on every machine. Searching, filtering, ingredient links and recipe history make the crafting chain easier to follow. Back returns to the same machine without clearing its cursor or inventory.
- Added a counted materials list to Build. Assemblies still accept any horizontal orientation.
- Made supply reserves visible even after items have been consumed into the machine's buffer. Hover their bars for exact amounts.
- Fixed the Water Restorer's status and working light when its tank is empty but a pond or full intake cauldron supplies water.
- Reduced machine-menu height for smaller GUI layouts.
- Shortened item names, tooltips, objectives and guide chapters. Notes have direct chapter buttons and a Current task shortcut.
- Added a clickable welcome link that uses the player's bound guide key. Removed the second farmhouse reminder.
- Kept the Heart's revised animation, title removal, machine animations and growth pulses from the previous development build.

## Compatibility

The build now uses Stonecutter with shared sources and separate targets for every Minecraft release from 1.21 through 1.21.11. Compatibility covers item registration and models, recipes, text events, menus, particles and saved machine data. Release filenames include their Minecraft version. Gradle and Loom have been updated, and a GitHub Actions workflow builds and tests enabled targets. The 26.1, 26.1.1, 26.1.2, 26.2 and 26.3 ports use a separate Mojang-named source tree and Java 25. Their gameplay and rendering checks are pending.

Fixed a world-generation test fixture that could place its tree across a chunk boundary while only transforming one chunk.

Minecraft Java 1.21 through 1.21.11, Java 21 and the matching Fabric API. Use Fabric Loader 0.16.14+ for 1.21 through 1.21.2, or 0.18.4+ for 1.21.3 through 1.21.11. Both server and clients need the mod. Existing 0.8 saves retain their inventories, regions, ecosystem progress and awakening state. Registry IDs and crafting requirements are unchanged.
