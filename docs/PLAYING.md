# Playing ReTerra 0.9.0

Survival restoration for **Minecraft Java 1.21 through 1.21.11**, Java 21 and the matching Fabric API. Use Fabric Loader 0.16.14+ for 1.21 through 1.21.2, or 0.18.4+ for 1.21.3 through 1.21.11.

## Install or upgrade

Replace the previous ReTerra JAR with the build for your Minecraft version, for example `build/libs/reterra-0.9.0+mc1.21.1.jar`. Servers and clients need the same build and Fabric API. No additional resource pack is needed.

Machines now animate while doing work. The Heart has a smoother awakening with orbiting crystal fragments, flowing ribbons and a terrain-following wave. Its full-screen titles have been removed. Existing awakening progress still works.

The Core now restores soil only. Plant grass cultures by hand. Old stored culture credits are returned once as removable cultures in the Core; already restored grass and earned reclamations are preserved. Improved ground scans and canopy recovery also work in existing regions.

The Core recipe now uses a redstone block instead of a lodestone. Heart assemblies use chiseled stone bricks instead of moss; replace the four side blocks on existing assemblies. Both materials use their vanilla recipes. The added moss and lodestone recipes have been removed.

Existing saves are supported. Stored supplies are converted to the new work capacities once when loading an older save. **Existing Restoration Cores now require a furnace underneath:** replace their former foundation. Older returned buckets remain removable from their legacy output slot.

New ruins appear in unexplored terrain. Existing regions receive the faster surveys and spreading behavior without starting another world. No signs are generated.

**Start a new world for the intended dead-world experience.** In Create World > Game Rules, **ReTerra barren terrain** controls new Overworld generation and natural wildlife suppression. It defaults to on. For ordinary new terrain, turn it off before creating/exploring the world, or use `/gamerule reterraBarrenWorld false`. Changing it does not strip or repair existing chunks. Previously reclaimed regions stay reclaimed; active projects use the new ecosystem goals below.

On an older save with no completed ecosystem, the first registered project becomes an introductory sanctuary. Completed projects retain their progress. Recovered species and research benefits carry forward.

## The world and the first farm

Caves, ores, terrain shapes and ordinary mining still exist. The catastrophe removes living vegetation and wildlife and sterilizes dirt, including buried dirt. Flowers do not naturally survive in the barren starting landscape. Dead trees provide wood; skeletons provide bones; mining provides iron, copper and redstone.

There are no starter inventory gifts. Recover a Seed Pouch from a farmhouse or seed vault: it contains two wheat seeds and one brown mushroom. Newly opened farmhouse pantries guarantee four bread, four carrots and four potatoes. Save some roots to plant beside the surviving irrigation pool; carrots are edible directly and potatoes can be cooked in a furnace. Previously opened chests keep their contents.

First farmhouse placement retries as nearby terrain becomes available, without a first-minute cutoff. It skips unsuitable terrain and player builds. Once a site is found, an animated farmhouse compass appears above the world view and points to it. This needs no inventory item and disappears after recovering the pouch or seeds. Chimney smoke helps at close range. Unusual island or steep spawns can still need exploration; `/reterra locate farmhouse` remains a fallback.

- One log + brown mushroom, or one log + rotten flesh, makes **4 compost**.
- Four coarse dirt or four gravel + one compost makes **4 dirt**.
- The four coarse dirt/gravel can also be **stacked in one slot**, with compost in another. This works in the inventory's 2 x 2 grid, a crafting table and an automated crafter. Each batch consumes four blocks and one compost; the original spread-out recipes remain available.
- Till the restored dirt beside water and establish a farm. Hoes cannot reclaim coarse/rooted dirt.
- Three wheat, three carrots or three potatoes makes **8 compost**. Leaves, pumpkins and melon slices provide alternative compost recipes; logs and rotten flesh remain another early source.
- In barren-world mode, a successful natural growth step on **watered farmland advances four crop stages**, capped at maturity. Wheat therefore needs two successful growth events instead of seven. Light, hydration and random ticks still matter, but a small farm no longer needs the vanilla-length wait. Ordinary worlds with the barren rule disabled retain vanilla growth.
- **Use compost on an immature crop to advance it by two growth stages.** This helps the first small farm without giving away extra seeds or equipment. Mature crops consume no compost.
- A glass bottle + wheat seeds + compost makes **Living Grass Culture**. Apply it to dirt to seed a **5 x 5 patch** of exposed restored soil. The empty bottle is returned; use it on living grass to collect another culture without destroying the grass. Plant several patches and let them spread. Cores do not accept or consume grass cultures.
- **Use compost or bone meal on restored grass to grow up to five flowers.** This works in reclaimed biomes too; flowers are renewable and do not require repeated ruin searches.

Greenhouses preserve cultures, an oak sapling, sugar cane and a small flower stock. Seed vaults guarantee another tree variety plus a different plant stock. Plant and propagate your finds.

## Machines

Right-click a machine for controls. Build lists the support blocks and their quantities. The ? button explains its purpose. J, `/reterra guide`, or Field Notes open the guide. **Recipes** opens a searchable crafting browser with Machines and Supplies filters, including the Reclaimer, Core, Chamber, circuit and stacked-soil recipe; hover for names and click an ingredient to view its recipe. Every machine has a Recipes button. Back returns to the same machine, with its inventory intact. The vanilla recipe book unlocks the full chain when you obtain compost or iron. Advancement descriptions celebrate discoveries without machine instructions.

Guide chapters follow saved milestones: leaving a region cannot send you backward, and the first revived pair stays in the wildlife chapter until reclamation. **Current task** returns to the recommended chapter. The first join introduces the guide and farmhouse once.

Recovered notes contain historical survey routes. **Use Field Notes with an ordinary compass in the other hand** to trace a route. The compass points to a confirmed intact ruin; neither item is consumed. Farmhouse records lead to greenhouses, greenhouse records to seed vaults, and vault records to Archives. Archive records lead to another Archive. Nearby and already visited sites are skipped. Keep both items in hand while the route is traced. Searches are bounded to 2,048 blocks and may generate terrain; commands remain a fallback. Older notes choose a route from your current chapter.

**Every assembly can rotate in any of four horizontal directions.** The diagram is an example orientation, not a compass requirement. Foundations stay directly below. Water cauldrons must contain water. Sneak with a block in hand to place supports against a machine.

| Machine | Foundation | Horizontal supports, in any rotation |
| --- | --- | --- |
| Soil Reclaimer | Furnace | Barrel on one side, water cauldron opposite |
| Restoration Core | Furnace | Polished andesite on all four sides; water cauldron two blocks out on any side |
| Water Restorer | Copper block | Water cauldron on any side |
| Revival Chamber | Iron block | Glass on four sides; water cauldron two blocks out on any side |
| Heart of the World | Diamond block | Chiseled stone bricks on four sides |

Indicator plates distinguish empty (gray), supplied (amber) and powered/incubating (green) machines. Water is shown as a tank bar with **current / 256 units**. One bucket supplies **128 units** and becomes an empty bucket **in the same input slot**. Remove or swap the empty bucket to insert another. Tanks accept a second bucket at 128/256; if less than 128 units of space remain, an inserted bucket waits until it fits. Machine inventories and reserves persist when the menu closes.

Working machines also have moving parts: the Reclaimer's press rises and falls with steam and soil crumbs; the Core's copper vanes rotate and green trails lead to newly restored soil; the Water Restorer's wheel turns and blue trails show water delivery. These effects follow successful work and fade when it stops. Using compost or grass cultures produces a small growth ripple, and habitat milestones send a wider pulse across the ground.

### Soil Reclaimer

Supply compost, fuel and water. Make Soil mode converts each gravel/coarse dirt input into one dirt, using the menu or its barrel. The barrel can face any direction as long as it is opposite the cauldron. Terrain mode repairs a **15 x 15** patch. Placed blocks and scar columns are protected during active terrain repair.

### Restoration Core

Crafted from a Soil Reclaimer, copper, iron, a redstone block and a grass culture. The redstone block uses the vanilla recipe: nine redstone dust. Build on a **furnace**, supply resources, then register the region.

A region is the **3 x 3 chunks around the Core's chunk (48 x 48 blocks)**. It cannot overlap another registered/reclaimed region. The entire footprint counts toward the survey, including crops across biome boundaries. Its ecosystem family comes from the Core's original biome.

The Scanner recipe unlocks with copper. **Survey before building:** use it outside a registered region to preview the ecosystem, targets, loaded chunks, overlap and remaining soil/grass work. Estimate shows approximate compost, culture, fuel and water costs, plus soil that must be placed by hand on rocky ground. Show preview border marks the 48 x 48 footprint for 30 seconds. Scans are gradual and never force chunks to load or register a project.

The Core restores coarse dirt/gravel to dirt, stopping at its fertile-soil target. Work starts in the central chunk and proceeds through nearby ground without skipped stripes. It finds soil beneath trunks, overhanging branches and shallow water, preserving the trees and water. Surveys count fertile pond beds as well as surface water. Scattered scars and ground beyond the coverage target can remain until reclamation.

**Players plant grass cultures**, farms, ponds, flowers and trees. The Core changes up to 16 soil blocks per second, or 24 after highlands research. A full loaded habitat survey takes approximately **18 seconds**; measurements refresh in sectors, so keep a small supply reserve. Surveys continue while paused or missing supports; terrain work and the trial require a working assembly.

### Water Restorer: irrigation and river recovery

An optional convenience for a larger base, rather than a way to obtain otherwise unavailable water.

Place a pump **within two horizontal blocks of pond/source water**, at the same height or one block above it. Supply charcoal filters and fuel. It draws from the source and automatically supplies tanks of other machines **within eight blocks**. It never removes the pond source. Manual water buckets and a full intake cauldron are alternative supplies.

A filter is crafted from **sand + charcoal + gravel**, with no paper requirement. Each filter supports eight pumping cycles. Transferring up to 32 water units costs one filter cycle and one fuel credit. Output buckets can also be filled from 128 tank units, using four fuel credits and one filter cycle. Idle pumps do not burn supplies.

Use the mode button to choose **recover river**. This refills exposed natural river channels within **12 blocks horizontally**, from their bed up to the original sea level. It places up to two source blocks per second, consuming one water unit and fuel credit per source, and one filter cycle per successful batch. The menu counts restored sources. It leaves banks, covered channels, unsupported beds, deep caves and protected columns alone. Water then flows normally. Move or build pumps along longer rivers; river recovery and irrigation are separate modes.

### Revival Chamber

The central mechanism uses a **piston**, not a brewing stand. No Nether materials are required. A Control Circuit can be crafted from copper, iron, redstone and glass; Research Stations provide a salvage alternative. Biological Archives preserve one sample each. Newly opened loot favors species the server has not recovered, avoiding repeat rolls until all four have been found. Opened chests never reroll; exploration and discoveries are shared in multiplayer.

Build inside a registered region. First-revival habitat needs:

| Measurement | Minimum |
| --- | ---: |
| Fertile soil | 35% |
| Grass | 15% |
| Surface water columns | 4 |
| Crop columns | 4 in plains/highlands; none in forest/swamp |
| Leaf-cover columns | 1 |
| Flower columns | 1 |
| Plant types | 3 |
| Existing adults | None |

Insert a sample, one Nutrient Culture, water, and food: **4 wheat for sheep/cows, 4 wheat seeds for chickens, or 2 flowers for bees**. The food slot and hint follow the selected sample and reject mismatched food. Nutrient Culture uses a glass bottle, wheat and bone meal.

Start incubation. It takes **20 seconds**, with a visible progress bar, a rotating crystal that grows with progress, intertwined green/blue strands and quiet chimes. A warm release pulse marks successful revival. Inputs are locked during incubation and consumed only when a pair successfully emerges. Missing habitat/supplies pause progress and the working effect fades. Cancel incubation unlocks the inputs without consuming them. Leave open grass with two air blocks above it beside the Chamber; animals emerge within six blocks horizontally, inside the region. No-space attempts preserve the inputs and wait for room. Destroying the Chamber drops the stored inputs.

Revived animals have a home area and return toward the habitat edge instead of wandering indefinitely. They still move normally and can be led or transported. Nearby home animals have a small census margin, and bees inside hives count. Provide flowers and an empty hive for bees. The adult capacity for additional revival is 16.

## Habitat goals and reclamation

A column is one horizontal ground position, not a tree or a stack of plants. Crop seedlings count. Wheat, carrots, potatoes and beetroot qualify as crops. Leaves within 32 blocks above ground provide canopy coverage. Different crop, flower and leaf block types supply plant diversity.

The first registered project is a **First sanctuary**, with lower targets in the same 48 x 48 footprint. One introductory contract is available at a time until the first reclamation. It still needs two animal species and a healthy environment.

| First sanctuary | Soil | Grass | Water | Crops | Leaf cover | Flowers | Plant types | Special goal |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Plains | 35% | 15% | 4 | 4 | 4 | 2 | 3 | None |
| Forest | 35% | 15% | 4 | Optional | 16 | 2 | 3 | 1 tree species |
| Swamp | 35% | 15% | 12 | Optional | 4 | 2 | 3 | 4 waterside columns |
| Highlands | 35% | 15% | 4 | 4 | 2 | 3 | 3 | 4 fertile slopes |

On a flat sterile plains site, the introductory targets are about **807 fertile and 346 grass columns**. A starting machine estimate is **26 compost, 7 coal and 7 bucketfuls of water**; terrain and survey timing change the actual cost. Seed grass yourself: fourteen full 5 x 5 patches cover 350 positions, and natural spreading can reduce the number of applications. A single bottle can be reused to collect and apply cultures. A pond-fed pump handles water delivery while you plant and build.

Later projects use the full ecosystem goals. **Every family needs two adult animal types.**

| Ecosystem | Soil | Grass | Water | Crops | Leaf cover | Flowers | Plant types | Distinctive goal |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Plains | 65% | 45% | 8 | 12 | 4 | 3 | 5 | A productive grassland farm |
| Forest | 60% | 25% | 6 | Optional | 48 | 2 | 5 | Leaves from 2 tree species |
| Swamp | 45% | 15% | 32 | Optional | 12 | 2 | 5 | 8 columns of waterside vegetation |
| Highlands | 50% | 25% | 4 | 4 | 2 | 5 | 4 | 12 fertile slope columns |

Waterside vegetation includes sugar cane, lilies, vines, mushrooms, saplings and mature trunks beside surface water. Saplings and mature trunks each count as one column, so growing a wetland tree keeps its column. Fertile slopes are living soil with a neighboring lower surface within four blocks; built terraces count. Leaves determine tree species. Cane, mushrooms, vines and saplings also contribute plant diversity.

Use the **Environmental Scanner** outside a project for a 48 x 48 site preview. Inside a Core's original region it opens the habitat menu. Sneak-use there toggles boundary particles; the Core's Habitat tab also has **Show boundary**. `/reterra status` without a Scanner retains a quick 16 x 16 readout.

All nine sectors must be surveyed, with at least 128 sampled columns. Earn **90 healthy seconds for the First sanctuary**, or **180 for later projects**. The Core displays a timer and progress bar. Unloading, pausing and restarting preserve earned time, without offline progress. Habitat deterioration removes one second per five observed seconds.

During a healthy loaded trial, **plant suitable local vegetation, feed an adult into breeding condition, and tend an immature crop with compost**. Each distinct action earns 15 seconds, once per region: up to 45 seconds total. These are optional activities. The Core names suitable plants, using its original biome.

| Habitat | Examples for the planting care bonus |
| --- | --- |
| Plains and ordinary forest | Oak saplings, dandelions |
| Cherry grove | Cherry saplings, pink petals |
| Taiga or snowy grove | Spruce saplings, oxeye daisies |
| Jungle | Jungle saplings, poppies |
| Birch forest | Birch saplings, dandelions |
| Dark forest | Dark oak saplings, poppies |
| Swamp and mangrove swamp | Blue orchids, waterside sugar cane, lily pads, matching tree saplings |
| Highlands | Spruce saplings, oxeye daisies |

A rose bush contributes to flower coverage and plant diversity in a swamp, but **does not earn its local planting bonus**. Compost/bone-meal flower patches use the local palette: blue orchids in swamps and pink petals in cherry groves. Bonuses and used actions persist through saves.

New stages produce a short message, chime and particles. Growth and rewilding also establish small plant patches. These rewards happen once per region, rather than repeating when measurements fluctuate.

After reclamation:

- No further machine fuel, compost or water is needed.
- Soil and gravel scars heal beneath branches and shallow water. Swamp beds become mud. Passive recovery grows grass on exposed land and fills vegetation in patches.
- Dead trees recover their own leaf species. Resumable growth follows existing branch tips and fills rounded, three-dimensional crowns. Protected log pillars and player blocks are preserved. New plants follow the original biome, so cherry groves keep cherry vegetation.
- The recovery area expands **one chunk ring per loaded minute**: 48 x 48, then 80 x 80, 112 x 112, and finally **144 x 144**. This is bounded natural succession, not unlimited world conversion.
- Recovery is faster, gradual and limited to loaded chunks. It preserves placed blocks and other registered projects. It does not fill caves or change ores.
- The Core can be removed after reclamation. The persistent region continues recovering and occasionally replenishes previously revived wildlife.

The first completed ecosystem unlocks **25% greater compost and fuel yields** for Cores and Reclaimers. Each family also provides a permanent shared benefit:

| Ecosystem | Additional benefit |
| --- | --- |
| Plains | Richer compost: **48 soil blocks** per item instead of the generic 40 |
| Forest | Better fuel: **192 work per coal**, 384 per nutrient culture |
| Swamp | Irrigation reaches **16 blocks**; pond intake reaches four blocks |
| Highlands | Cores make up to **24 terrain changes per second** |

Water and filter capacities are unchanged. These benefits apply to existing machines and remain available after a completed Core is removed. Choose your next ecosystem for its benefit; the Heart's Habitat tab shows world progress.

Reclaim plains, forest, swamp and highlands to awaken the **Heart of the World**. Its foundation needs a diamond block and four chiseled stone bricks, made with vanilla crafting or a stonecutter.

Awakening takes **16 seconds**: six streams gather across the ground, an emerald crystal eases upward with four orbiting fragments, and intertwined green/teal ribbons climb toward a golden light column. The crystal and its fragments draw back into the Heart as a widening wave follows the surrounding terrain. The menu closes so you can watch; quiet rising chimes and a final resonant note accompany the sequence, without full-screen titles. Progress persists across saves and pauses while the Heart's chunk or required supports are unavailable. Removing the Heart before completion cancels the sequence.

After completion the Heart stays lit with its permanent crystal. Its **272 x 272 recovery aura** gradually heals loaded terrain without supplies, respecting player blocks and other registered projects. Completed regions expand twice as fast: one ring per 30 loaded seconds. The aura persists if the Heart is removed. Multiplayer players share world progress.

For testing, **`/reterra heart disable`** requires operator level 2 or single-player cheats. It cancels an awakening, removes its temporary crystal, switches off the aura and spread bonus, and lets you activate the Heart again. Ecosystem progress and already restored terrain are kept.

## Resource balance

| Supply | Work per item |
| --- | ---: |
| Compost | 32 soil blocks |
| Coal/charcoal | 128 terrain changes |
| Nutrient Culture as fuel | 256 terrain changes |
| Water bucket | 128 water units |
| Grass Culture, applied by hand | Up to 25 grass blocks; bottle returned |
| Charcoal Filter | 8 pumping cycles |

One machine soil conversion costs one compost credit, one fuel credit and one water unit. Hand planting and natural grass spreading use no machine fuel or water. Scanning, blocked outputs and waiting do not consume work. Incubation uses 16 water units per successful pair. Post-reclamation succession is free.

## Structures and commands

Available site names: `farmhouse`, `greenhouse`, `seed_vault`, `waterworks`, `biological_archive`, `research_station`.

Use `/reterra locate farmhouse` in survival. Operators may also use `/locate structure reterra:biological_archive`. Searches check actual sites within 2,048 blocks on each horizontal axis and can generate new terrain. Ruins contain purpose-specific salvage and plants, not complete mod machines or player-directed signs.

## Development

```powershell
.\gradlew.bat build
.\gradlew.bat runGametest
.\gradlew.bat runUiPreview
.\gradlew.bat runClient
```

Building requires Java 25 to run Gradle, with a Java 21 toolchain installed for 1.21 targets. `runGametest` checks world/inventory behavior in an isolated server; `runUiPreview` renders machine and guide screens in a separate client without opening a player save. Test mods are excluded from the release JAR.

`runAnimationPreview` renders the effects in the disposable `.tools/animation-preview/saves/AnimationCheck` world and saves six screenshots under `.tools/animation-preview/animation-screenshots`. Seed that test world with a copy of `build/gametest/world/level.dat` after running GameTests. It must never be pointed at a player save. Work cues are coalesced and sent only to nearby players; moving machine parts and bounded particle trails are rendered locally and add no persistent entities. The Heart retains its single temporary saved crystal during awakening.

Regenerate gameplay data with `python scripts/generate_resources.py`. Art sources are described in [the art notes](ART.md). The original concept is [original-concept.txt](original-concept.txt); current behavior above supersedes earlier designs. Automated checks do not replace extended survival and multiplayer playtesting.

For a blind survival playtest, give a fresh world to someone unfamiliar with the code. Observe whether they discover J, find seeds, grow food, preview a site, assemble a Core, follow an Archive route and understand the trial timer. Record where they hesitate and how long each milestone takes before offering help.
