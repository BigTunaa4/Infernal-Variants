# Infernal Variants

Repaint your infernal cape in 14 designs — each with its own effect — while keeping the exact crack pattern of the real cape.

![preview](preview.png)

| Style | Look | Effect |
|---|---|---|
| Infernal (original) | Untouched lava | Rising embers |
| Holy | Ivory plates, gold veins, holy star emblem | Golden motes |
| Shadow | Black-violet plates, purple veins | Shadow wisps |
| Ice | Slate plates with rime, ice-blue veins | Falling snow |
| Blood | Crimson veins | Blood drips off the hem |
| Nature | Mossy stone, green veins, leaf emblems | Drifting leaves |
| Lightning | Charcoal plates, white-hot yellow veins | Crackling arcs |
| Rune | Teal veins, three linked rune circles | Floating rune marks |
| Dragonfire | Red-orange veins that burn blue at the hem | Embers + blue hem flames |
| Void | Purple veins, void eye emblem | Motes pulled into the cape |
| Sandstone | Gritty sandstone with dark cracks | Trickling sand |
| Crystal | Pale crystal with a prismatic shimmer | Crystal glints |
| Roguelike | Black with dim crimson veins, hood sigil | Low smoke |
| Golden | Burnished amber with a polished sheen | Gold glints |

## Settings
- **Cape style** — pick the design.
- **Flowing veins** — the veins slowly flow like the original lava.
- **Emblems** — toggle the style emblems.
- **Include infernal max cape** — also repaint the infernal max cape.
- **Cape effects / Effect amount** — particles on/off and Subtle / Normal / Strong.
- **Other players' capes** — Off, Same style, or Random per player (stable per name). Effects on others is a separate toggle.

## How it works
The game can't recolour a texture, and the cape's outer panel is a single lava texture. Each frame the plugin reads your animated model, finds the faces using that texture, computes their texture coordinates the same way the client does, and draws a repainted version of the texture onto them — so the design moves with the cape. The vein pattern is taken from the game's own texture at runtime; nothing from the game cache is bundled. The rock trim, hem accents and inner lining are recoloured through the client's per-player colour overrides.

All changes are client-side; only you see them.

## Known limits
- Drawn on top of the scene: other players and NPCs standing in front of your cape are cut out, but small objects passing directly in front of it (e.g. a held weapon swung across your back) can appear behind the design.
- Emblems are placed for the standard infernal cape; the infernal max cape gets the repaint and effects without emblems.
