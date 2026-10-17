# Infernal Variants

Repaint your infernal cape in 26 designs (plus your own custom colours), each with veins that flow down the cape in their own way, while keeping the exact crack pattern of the real cape.

![animated preview](preview.gif)

![all styles](preview.png)

| Style | Look |
|---|---|
| Infernal (original) | Untouched lava |
| Holy | Ivory plates, gold veins, holy star emblem |
| Shadow | Black-violet plates, purple veins |
| Ice | Slate plates with rime, ice-blue veins |
| Blood | Crimson veins |
| Nature | Mossy stone, green veins, leaf emblems |
| Lightning | Charcoal plates, white-hot yellow veins |
| Rune | Teal veins, three linked rune circles |
| Dragonfire | Red-orange veins that burn blue at the hem |
| Void | Purple veins, void eye emblem |
| Sandstone | Gritty sandstone with dark cracks |
| Crystal | Pale crystal with a prismatic shimmer |
| Roguelike | Black with dim crimson veins, hood sigil |
| Golden | Burnished amber with a polished sheen |
| Toxic | Venom-green veins on teal-black |
| Corrupted | Crimson crystal shards |
| Sanguine | Deep red plates, gold veins |
| Amascut | Sandstone gold, sun disc emblem |
| Tidal | Deep blue, sea-green veins |
| Saradomin | Blue plates, gold veins, Saradomin star |
| Zamorak | Red-black, Zamorak mark |
| Guthix | Earthy stone, green veins, balance emblem |
| Abyssal | Maroon with pink-red veins |
| Tormented | Black with demon-green veins |
| Varlamore Sunset | Orange veins fading to dusk purple at the hem |
| Twisted | Dark green, twisted bow emblem |
| Custom | Your plate, vein and glow colours |

## Settings
- **Cape style** — pick the design.
- **Custom style** — plate, vein and glow colour pickers, used when the style is Custom.
- **Animated veins** — each style has its own motion (see below).
- **Emblems** — toggle the style emblems.
- **Include infernal max cape** — also repaint the infernal max cape.
- **Other players' capes** — Off, Same style, or Random per player (stable per name).

## Motion
On every style the veins flow down the cape, each at its own pace, with a glow that moves in its own way:
- **Flow** — a steady downward drift with a gentle glow (Blood, Toxic, Sandstone).
- **Pulse** — the glow swells and fades (Shadow, Void, Nature, Sanguine, Abyssal, Guthix, Roguelike).
- **Flicker** — irregular electric crackle with sudden surges (Lightning, Corrupted).
- **Shimmer** — a band of light sweeps from collar to hem (Holy, Ice, Crystal, Golden, Amascut, Saradomin).
- **Ripple** — soft waves travel down the cape (Rune, Tidal, Twisted).
- **Burn** — uneven firelight, hottest at the hem (Dragonfire, Zamorak, Tormented, Varlamore Sunset).

Emblems glow softly and breathe with the veins.

## Art details
- The stone plates are lit, rocky surfaces, each plate its own shade and domed toward its centre.
- Veins are two-tone: a hot core plus a glow that spills onto the surrounding stone in a second colour (gold veins with a white glow on Holy, cyan with deep blue on Rune, and so on).
- Style touches: frosted crack edges (Ice), moss patches (Nature, Guthix, Twisted), a starfield in the stone (Void), demon sparks (Tormented), sunlight caustics (Tidal), drifting tendrils (Abyssal), crystal facets (Corrupted, Crystal), sediment grain (Sandstone, Amascut), venom bubbles (Toxic), carved rune marks (Rune), charge crackles (Lightning) and a polished sheen (Golden).

## How it works
The game can't recolour a texture, and the cape's outer panel is a single lava texture. Each frame the plugin reads your animated model, finds the faces using that texture, computes their texture coordinates the same way the client does, and draws a repainted version of the texture onto them — so the design moves with the cape. The vein pattern is taken from the game's own texture at runtime; nothing from the game cache is bundled. The rock trim, hem accents and inner lining are recoloured through the client's per-player colour overrides.

All changes are client-side; only you see them.

## Known limits
- Drawn on top of the scene: other players and NPCs standing in front of your cape are cut out, but small objects passing directly in front of it (e.g. a held weapon swung across your back) can appear behind the design.
- Emblems are placed for the standard infernal cape; the infernal max cape gets the repaint and effects without emblems.
