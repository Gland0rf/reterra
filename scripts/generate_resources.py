"""Reproducible gameplay data. Pixel art sources live in generate_art.py."""
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1] / "src/main/resources"

def write(path, value):
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

ITEMS = {
    "organic_compost": ("Organic Compost", "Mix with coarse dirt or gravel to make soil."),
    "grass_culture": ("Grass Culture", "Use on dirt to start a grass patch."),
    "nutrient_culture": ("Nutrient Culture", "Feeds the Revival Chamber. Also a machine fuel."),
    "water_filter": ("Charcoal Filter", "For the Water Restorer. Lasts eight cycles."),
    "archive_fragment": ("Control Circuit", "A controller for the Revival Chamber."),
    "sheep_sample": ("Sheep Sample", "Preserved tissue for the Revival Chamber."),
    "cow_sample": ("Cow Sample", "Preserved tissue for the Revival Chamber."),
    "chicken_sample": ("Chicken Sample", "Preserved tissue for the Revival Chamber."),
    "bee_sample": ("Bee Sample", "Preserved tissue for the Revival Chamber."),
    "field_journal": ("Field Notes", "Use with a compass in your other hand."),
    "recovery_bundle": ("Seed Pouch", "Use to open. Save the seeds for planting."),
    "environmental_scanner": ("Environmental Scanner", "Surveys sites before Core placement."),
}
BLOCKS = {"soil_reclaimer": "Soil Reclaimer", "restoration_core": "Restoration Core", "water_restorer": "Water Restorer", "revival_chamber": "Revival Chamber", "heart_of_the_world": "Heart of the World"}
lang = {"itemGroup.reterra": "ReTerra - Restoration", "hint.reterra.machine": "Right-click for controls.",
        "hint.reterra.sterile_soil": "Sterile soil; requires compost.",
        "hint.reterra.discovery": "No registered region here."}
lang.update({"key.reterra.guide":"Open ReTerra Field Guide", "category.reterra":"ReTerra", "key.category.reterra.reterra":"ReTerra",
    "gamerule.reterra.barren_world":"ReTerra barren terrain",
    "gamerule.reterra.barren_world.description":"Generate a dead Overworld for restoration.",
    "gamerule.reterraBarrenWorld":"ReTerra barren terrain",
    "gamerule.reterraBarrenWorld.description":"Strip new Overworld chunks of life. Existing chunks stay unchanged. Disable before exploring an ordinary world.",
    "hint.reterra.environmental_scanner":"Use: survey. Sneak-use: region boundary.",
    "hint.reterra.empty_bottle":"Use on grass to collect a grass culture."})
for key,(name,hint) in ITEMS.items():
    lang[f"item.reterra.{key}"] = name
    lang[f"hint.reterra.{key}"] = hint
    write(f"assets/reterra/models/item/{key}.json", {"parent":"minecraft:item/generated", "textures":{"layer0":f"reterra:item/{key}"}})
def machine_model(key, lamp):
    elements = [{"from":[0,0,0],"to":[16,16,16],"faces":{
        side:{"texture":"#"+({"north":"front","up":"top","down":"bottom"}.get(side,"body")),"cullface":side}
        for side in ["north","south","east","west","up","down"]}}]
    for start,end,side in [([11,12,-.02],[13,14,0],"north"),([3,12,16],[5,14,16.02],"south"),([-.02,12,3],[0,14,5],"west"),([16,12,11],[16.02,14,13],"east")]:
        elements.append({"from":start,"to":end,"faces":{side:{"texture":"#lamp"}}})
    if key == "heart_of_the_world" and lamp == "active":
        elements.append({"from":[6,16,6],"to":[10,23,10],"shade":False,
            "rotation":{"origin":[8,19,8],"axis":"y","angle":45},
            "faces":{side:{"texture":"#crystal"} for side in ["north","south","east","west","up","down"]}})
    return {"parent":"minecraft:block/block","textures":{"body":f"reterra:block/{key}_side",
        "front":f"reterra:block/{key}_front", "top":f"reterra:block/{key}_top",
        "bottom":"minecraft:block/smooth_stone", "particle":f"reterra:block/{key}_side",
        "lamp":f"reterra:block/indicator_{lamp}", "crystal":"minecraft:block/emerald_block"},
        "elements":elements}
for key,name in BLOCKS.items():
    lang[f"block.reterra.{key}"] = name
    write(f"assets/reterra/blockstates/{key}.json", {"variants":{f"lit={str(lit).lower()},supplied={str(stored).lower()}":{"model":f"reterra:block/{key}"+("_active" if lit else "_stored" if stored else "")} for lit in [False,True] for stored in [False,True]}})
    for suffix,lamp in [("","off"),("_stored","stored"),("_active","active")]:
        write(f"assets/reterra/models/block/{key}{suffix}.json", machine_model(key,lamp))
    write(f"assets/reterra/models/item/{key}.json", {"parent":f"reterra:block/{key}"})
    write(f"data/reterra/loot_table/blocks/{key}.json", {"type":"minecraft:block", "pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":f"reterra:{key}"}],"conditions":[{"condition":"minecraft:survives_explosion"}]}]})
statuses={"ready":"Ready - supplies pay only for actual work", "assembly":"Assembly incomplete - open the Assembly tab", "register":"Register a region before restoration or revival", "overlap":"This region overlaps another Core", "paused":"Paused - stored supplies are preserved", "compost":"Add compost (or a charcoal filter for water)", "fuel":"Add charcoal, coal or a nutrient culture", "water":"Add a water bucket; remove returned buckets", "material":"Insert gravel or empty buckets in Process", "output":"Output full - collect the processed items", "habitat":"Habitat not ready - check the Habitat tab", "sample":"Insert a preserved animal sample", "food":"Insert 4 matching food items", "nutrients":"Insert 1 nutrient culture", "no_space":"Clear two air blocks above nearby grass", "revived":"Wildlife revived - protect the breeding population", "reclaimed":"Reclaimed - no machine supplies required", "objectives":"Reclaim all four ecosystems to awaken the Heart", "complete":"The Heart is alive. Restoration can continue.", "culture":"Add a living grass culture to seed restored dirt"}
statuses.update({"ready":"Ready", "assembly":"Assembly incomplete - open Build", "material":"Insert gravel or empty buckets in the marked slot", "habitat":"Habitat needs attention - see your next step below"})
statuses.update({"assembly":"Incomplete assembly", "register":"No registered region", "paused":"Paused", "compost":"Supply needed", "water":"Water needed", "food":"Food needed", "sample":"Sample needed", "nutrients":"Nutrient culture needed", "fuel":"Fuel needed", "habitat":"Habitat incomplete", "culture":"Plant grass on restored dirt", "revived":"Wildlife revived", "reclaimed":"Reclaimed", "incubating":"Incubating", "population":"Population at capacity", "awakening":"Awakening"})
statuses["stabilizing"]="Habitat stabilizing"
for key,text in statuses.items():lang[f"status.reterra.{key}"]=text
for family,grass,foliage,water,temp in [("plains",0x829969,0x718A5E,0x547C89,.7),("forest",0x6E8965,0x59774F,0x487887,.6),("swamp",0x798465,0x66734F,0x596C55,.8),("highlands",0x81958A,0x6A8573,0x587E98,.3)]:
    write(f"data/reterra/worldgen/biome/reclaimed_{family}.json", {"has_precipitation":True,"temperature":temp,"downfall":.5,"effects":{"fog_color":0xB8BFB8,"sky_color":0x8DA6B3,"water_color":water,"water_fog_color":0x243C3E,"grass_color":grass,"foliage_color":foliage},"spawners":{},"spawn_costs":{},"carvers":{},"features":[]})
    lang[f"biome.reterra.reclaimed_{family}"]=f"Reclaimed {family.title()}"
write("assets/reterra/lang/en_us.json",lang)
write("data/minecraft/tags/block/mineable/pickaxe.json",{"replace":False,"values":[f"reterra:{key}" for key in BLOCKS]})

recipes=[]
catalog=[]
def ing(name):return {"tag":name[1:]} if name.startswith("#") else {"item":name if ":" in name else "minecraft:"+name}
def predicate(item):return {"trigger":"minecraft:inventory_changed","conditions":{"items":[{"items":item if ":" in item else "minecraft:"+item}]}}
def recipe(name,inputs,result,count=1,pattern=None,unlock="reterra:organic_compost"):
    data={"type":"minecraft:crafting_shaped" if pattern else "minecraft:crafting_shapeless","category":"misc","result":{"id":result if ":" in result else "reterra:"+result,"count":count}}
    if pattern:data.update(pattern=pattern,key={key:ing(item) for key,item in inputs.items()})
    else:data["ingredients"]=[ing(item) for item in inputs]
    write(f"data/reterra/recipe/{name}.json",data);recipes.append(name)
    icons={"#minecraft:logs":"minecraft:oak_log", "#minecraft:leaves":"minecraft:oak_leaves"}
    def display(item):return {"item":icons.get(item,item if ":" in item else "minecraft:"+item),"count":1}
    grid=[display(inputs[c]) if c!=" " else None for row in pattern for c in row] if pattern else [display(item) for item in inputs]
    catalog.append({"id":"reterra:"+name,"result":data["result"],"grid":grid,"shaped":bool(pattern)})
    write(f"data/reterra/advancement/recipes/{name}.json",{"parent":"minecraft:recipes/root","criteria":{"discovery":predicate(unlock)},"rewards":{"recipes":["reterra:"+name]}})
recipe("compost_from_mushrooms",["#minecraft:logs","brown_mushroom"],"organic_compost",4,unlock="brown_mushroom")
recipe("compost_from_decay",["#minecraft:logs","rotten_flesh"],"organic_compost",4,unlock="rotten_flesh")
recipe("compost_from_wheat",["wheat","wheat","wheat"],"organic_compost",8,unlock="wheat")
recipe("compost_from_carrots",["carrot","carrot","carrot"],"organic_compost",8,unlock="carrot")
recipe("compost_from_potatoes",["potato","potato","potato"],"organic_compost",8,unlock="potato")
recipe("compost_from_leaves",["#minecraft:leaves","#minecraft:leaves"],"organic_compost",4,unlock="#minecraft:leaves")
recipe("compost_from_pumpkin",["pumpkin"],"organic_compost",8,unlock="pumpkin")
recipe("compost_from_melon",["melon_slice","melon_slice","melon_slice"],"organic_compost",4,unlock="melon_slice")
recipe("fertile_soil",["gravel","gravel","gravel","gravel","reterra:organic_compost"],"minecraft:dirt",4)
recipe("fertile_soil_from_coarse",["coarse_dirt","coarse_dirt","coarse_dirt","coarse_dirt","reterra:organic_compost"],"minecraft:dirt",4)
recipe("nutrient_culture",["glass_bottle","wheat","bone_meal"],"nutrient_culture",unlock="wheat")
recipe("water_filter",["sand","charcoal","gravel"],"water_filter",unlock="charcoal")
recipe("environmental_scanner",{"C":"copper_ingot","G":"glass","R":"redstone","M":"compass"},"environmental_scanner",pattern=["CGC","RMR","CGC"],unlock="copper_ingot")
recipe("grass_culture",["glass_bottle","wheat_seeds","reterra:organic_compost"],"grass_culture",unlock="wheat_seeds")
recipe("archive_fragment",{"C":"copper_ingot","I":"iron_ingot","R":"redstone","G":"glass"},"archive_fragment",pattern=["CIC","RGR","CIC"],unlock="redstone")
recipe("soil_reclaimer",{"C":"cobblestone","I":"iron_ingot","P":"piston"},"soil_reclaimer",pattern=["CIC","IPI","CIC"])
recipe("restoration_core",{"C":"copper_ingot","G":"reterra:grass_culture","I":"iron_ingot","R":"reterra:soil_reclaimer","P":"redstone_block"},"restoration_core",pattern=["CGC","IRI","CPC"],unlock="reterra:grass_culture")
recipe("water_restorer",{"C":"copper_ingot","P":"piston","I":"iron_ingot","F":"reterra:water_filter","B":"bucket"},"water_restorer",pattern=["CPC","IFI","CBC"],unlock="reterra:water_filter")
recipe("revival_chamber",{"I":"iron_ingot","G":"glass","B":"piston","C":"copper_ingot","A":"reterra:archive_fragment"},"revival_chamber",pattern=["IGI","GBG","CAC"],unlock="reterra:archive_fragment")
recipe("heart_of_the_world",{"C":"copper_ingot","G":"gold_ingot","R":"reterra:restoration_core","D":"diamond"},"heart_of_the_world",pattern=["CGC","GRG","CDC"],unlock="reterra:restoration_core")
write("data/reterra/recipe/compact_soil.json",{"type":"reterra:compact_soil","category":"misc"})
recipes.append("compact_soil")
for feedstock in ("coarse_dirt","gravel"):
    catalog.append({"id":"reterra:compact_soil_"+feedstock,"result":{"id":"minecraft:dirt","count":4},
        "grid":[{"item":"minecraft:"+feedstock,"count":4},{"item":"reterra:organic_compost","count":1}],"shaped":False})
catalog.append({"id":"minecraft:bread","result":{"id":"minecraft:bread","count":1},
    "grid":[{"item":"minecraft:wheat","count":1}]*3,"shaped":True})
write("assets/reterra/field_recipes.json",catalog)
# Discover the full crafting chain early, even when a later machine's ingredient is still missing.
write("data/reterra/advancement/recipes/field_crafting.json",{
    "criteria":{"compost":predicate("reterra:organic_compost"),"iron":predicate("iron_ingot")},
    "requirements":[["compost","iron"]],"rewards":{"recipes":["reterra:"+name for name in recipes if name!="compact_soil"]}})
# Remove only the former generated shortcuts; no broad directory deletion.
obsolete=["compost_from_wood","recovery_bundle","field_journal","sheep_sample","cow_sample","chicken_sample","bee_sample","starter_beehive","restored_moss","lodestone"]
for name in obsolete:
    for folder in ("recipe", "advancement/recipes"):
        target=ROOT/f"data/reterra/{folder}/{name}.json"
        if target.is_file():target.unlink()

chapters=[
("wasteland",None,"A Silent World","Even here, something may have survived.","minecraft:coarse_dirt",None),
("the_last_seed","wasteland","The Last Seed","A small beginning.","reterra:recovery_bundle","reterra:recovery_bundle"),
("soil_science","the_last_seed","Soil, Not Stone","Life takes root again.","reterra:organic_compost","reterra:organic_compost"),
("first_roots","soil_science","First Roots","A little help for tired ground.","reterra:soil_reclaimer",None),
("first_growth","soil_science","Living Ground","The first green breaks through.","minecraft:grass_block",None),
("greener_world","first_growth","A Greener World","A place worth coming home to.","reterra:restoration_core",None),
("natures_return","greener_world","Nature Returns","The silence is broken.","reterra:bee_sample",None),
("reclaimed","natures_return","New Nature","An ecosystem stands on its own.","minecraft:oak_sapling",None),
("new_beginning","reclaimed","A New Beginning","The world begins to breathe again.","reterra:heart_of_the_world",None)]
for name,parent,title,desc,icon,item in chapters:
    data={"display":{"icon":{"id":icon},"title":{"text":title},"description":{"text":desc},"frame":"challenge" if name in ("reclaimed","new_beginning") else "task","show_toast":True,"announce_to_chat":False},"criteria":{"discovery":predicate(item) if item else {"trigger":"minecraft:impossible"}}}
    if parent:data["parent"]="reterra:"+parent
    else:data["display"]["background"]="minecraft:textures/block/coarse_dirt.png"
    write(f"data/reterra/advancement/{name}.json",data)

# Silent inventory milestones catch discoveries even when an item is used before the next journal tick.
for note,items in {"seeds":["minecraft:wheat_seeds"],"compost":["reterra:organic_compost"],
        "dirt":["minecraft:dirt"],"wheat":["minecraft:wheat"],"culture":["reterra:grass_culture"],
        "reclaimer":["reterra:soil_reclaimer"],"sample":["reterra:sheep_sample","reterra:cow_sample","reterra:chicken_sample","reterra:bee_sample"]}.items():
    criteria={str(i):predicate(item) for i,item in enumerate(items)}
    write(f"data/reterra/advancement/notes/{note}.json",{"criteria":criteria,"requirements":[list(criteria)]})

def entry(item,count=1):
    item=item if ":" in item else "minecraft:"+item
    result={"type":"minecraft:item","name":item}
    if count!=1:result["functions"]=[{"function":"minecraft:set_count","count":count}]
    return result
def fixed(item,count=1):return {"rolls":1,"entries":[entry(item,count)]}
def choice(items,rolls=1):return {"rolls":rolls,"entries":[entry(*item) if isinstance(item,tuple) else entry(item) for item in items]}
loot={
"farmhouse":[fixed("reterra:recovery_bundle"),fixed("bread",4),fixed("carrot",4),fixed("potato",4)],
"greenhouse":[fixed("reterra:grass_culture"),fixed("oak_sapling"),choice([("dandelion",3),("poppy",3)]),fixed("sugar_cane")],
"seed_vault":[fixed("reterra:recovery_bundle"),choice([("spruce_sapling",2),("jungle_sapling",4),("dark_oak_sapling",4),("birch_sapling",2),("acacia_sapling",2),("cherry_sapling",2),("mangrove_propagule",2)]),choice(["bamboo","cactus","cocoa_beans",("pumpkin_seeds",2),("melon_seeds",2)])],
"waterworks":[fixed("reterra:water_filter",2),fixed("copper_ingot",2),fixed("bucket")],
"biological_archive":[choice(["reterra:sheep_sample","reterra:cow_sample","reterra:chicken_sample","reterra:bee_sample"])],
"research_station":[fixed("reterra:archive_fragment"),fixed("redstone",4),fixed("reterra:nutrient_culture",2)]}
history={"farmhouse":"Supply route: cuttings from the greenhouse complex.",
    "greenhouse":"Tree varieties transferred from the underground seed vault.",
    "seed_vault":"Survey route: specimens stored in the biological archive.",
    "waterworks":"Maintenance route: filtration research at the station.",
    "biological_archive":"Specimen transfers between the archive annexes.",
    "research_station":"Survey route: preserved cultures at the archive."}
clues={"farmhouse":"greenhouse","greenhouse":"seed_vault","seed_vault":"biological_archive",
    "waterworks":"research_station","biological_archive":"biological_archive","research_station":"biological_archive"}
for name,pools in loot.items():
    notes=entry("reterra:field_journal")
    notes["functions"]=[{"function":"minecraft:set_lore","lore":[{"text":history[name],"color":"gray","italic":True}],"mode":"replace_all"},
        {"function":"minecraft:set_custom_data","tag":json.dumps({"reterra_clue":clues[name]})}]
    write(f"data/reterra/loot_table/chests/{name}.json",{"type":"minecraft:chest","pools":pools+[{"rolls":1,"entries":[notes]}]})
write("data/reterra/loot_table/chests/research_cache.json",{"type":"minecraft:chest","pools":loot["farmhouse"]})
print(f"Generated {len(recipes)} purposeful recipes, {len(chapters)} milestones, discovery unlocks and six distinct loot tables.")
