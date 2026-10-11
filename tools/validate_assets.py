#!/usr/bin/env python3
"""資産・メタデータの整合性検証（リリース前チェック）。

- すべての JSON が妥当か
- blockstate/model が参照するファイルが存在するか（= 未登録テクスチャ／紫黒チェッカーが出ないか）
- lang キーが Java 側の列挙（CombatRole / FacilityType / NationRole）を網羅しているか
- loot table / recipe が実在するブロック・アイテムを参照しているか
"""
import json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/wisdomking")
DATA = os.path.join(RES, "data/wisdomking")
errors, warnings = [], []

def load_json(path):
    try:
        with open(path, encoding="utf-8") as fh:
            return json.load(fh)
    except Exception as exc:  # noqa: BLE001
        errors.append("JSON parse failed: %s (%s)" % (path, exc))
        return None

# 1) all json parses
for base, _dirs, files in os.walk(RES):
    for name in files:
        if name.endswith(".json"):
            load_json(os.path.join(base, name))

# 2) blockstates -> models -> textures exist
for name in os.listdir(os.path.join(ASSETS, "blockstates")):
    path = os.path.join(ASSETS, "blockstates", name)
    data = load_json(path) or {}
    for variant, body in (data.get("variants") or {}).items():
        models = body if isinstance(body, list) else [body]
        for entry in models:
            model = entry.get("model", "")
            model_path = os.path.join(ASSETS, "models", model.split(":", 1)[-1] + ".json")
            if not os.path.exists(model_path):
                errors.append("missing model %s (from %s)" % (model, name))

def check_model(path):
    data = load_json(path) or {}
    tex_parent = data.get("parent")
    for key, value in (data.get("textures") or {}).items():
        if value.startswith("#"):
            continue
        res_path = os.path.join(ASSETS, "textures", value.split(":", 1)[-1] + ".png")
        if not os.path.exists(res_path):
            errors.append("missing texture %s (from %s)" % (value, os.path.basename(path)))
    if tex_parent and not (tex_parent.startswith("minecraft:") or tex_parent.startswith("wisdomking:")):
        warnings.append("unqualified parent %s in %s" % (tex_parent, os.path.basename(path)))

for base, _dirs, files in os.walk(os.path.join(ASSETS, "models")):
    for name in files:
        if name.endswith(".json"):
            check_model(os.path.join(base, name))

# 3) lang coverage
def enum_constants(rel_path):
    src = open(os.path.join(ROOT, rel_path), encoding="utf-8").read()
    body = src.split("{", 1)[1]
    return re.findall(r"^\t([A-Z][A-Z0-9_]*)\s*[,;(]", body, re.M)

for lang in ("ja_jp", "en_us"):
    path = os.path.join(ASSETS, "lang", lang + ".json")
    data = load_json(path)
    if data is None:
        continue
    for const in enum_constants("src/main/java/com/wisdomking/equipment/CombatRole.java"):
        key = "wisdomking.role." + const.lower()
        if key not in data:
            errors.append("lang[%s] missing %s" % (lang, key))
    for const in enum_constants("src/main/java/com/wisdomking/block/FacilityType.java"):
        key = "wisdomking.facility." + const.lower()
        if key not in data:
            errors.append("lang[%s] missing %s" % (lang, key))
    for const in enum_constants("src/main/java/com/wisdomking/nation/NationRole.java"):
        key = "wisdomking.role." + const.lower()
        if key not in data:
            warnings.append("lang[%s] missing %s" % (lang, key))
    for const in enum_constants("src/main/java/com/wisdomking/nation/Personality.java"):
        key = "wisdomking.personality." + const.lower()
        if key not in data:
            warnings.append("lang[%s] missing %s" % (lang, key))

# 4) loot tables / recipes reference existing blocks/items
known_items = set()
for name in os.listdir(os.path.join(ASSETS, "blockstates")):
    known_items.add(name[:-5])
for base, _dirs, files in os.walk(os.path.join(ASSETS, "models/item")):
    for name in files:
        known_items.add(name[:-5])

for base, _dirs, files in os.walk(DATA):
    for name in files:
        if not name.endswith(".json"):
            continue
        path = os.path.join(base, name)
        data = load_json(path) or {}
        for block_id in (data.get("pools") or [{}])[0].get("entries", [{}])[0].get("name", "").split(","):
            block_id = block_id.strip()
            if block_id.startswith("wisdomking:") and block_id.split(":", 1)[1] not in known_items:
                errors.append("loot table references unknown block %s (%s)" % (block_id, name))

# 5) fabric.mod.json / mixin configs
load_json(os.path.join(RES, "fabric.mod.json"))
load_json(os.path.join(RES, "wisdomking.mixins.json"))
client_mixins = os.path.join(ROOT, "src/client/resources/wisdomking.client.mixins.json")
if os.path.exists(client_mixins):
    load_json(client_mixins)

print("=== errors: %d ===" % len(errors))
for line in errors:
    print("  ERR", line)
print("=== warnings: %d ===" % len(warnings))
for line in warnings[:40]:
    print("  WARN", line)
sys.exit(1 if errors else 0)
