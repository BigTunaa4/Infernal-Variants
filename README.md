# Infernal Variants

Repaint your infernal cape in 26 designs, or recolour any of them yourself. The styles from the original concept art use dark scales with glowing chevron seams and their own touches (drips, leaves, crackles, rune circles, blue hem fire); Ice and Shadow have their own shards and smoke; the lore styles have their own patterns; and everything flows down the cape.

![animated preview](preview.gif)

![all styles](preview.png)

| Style | Look |
|---|---|
| Infernal (original) | Untouched lava |
| Holy | White cloth with gold trim down the edges and centre, holy star emblems |
| Shadow | Drifting purple smoke wisps on black |
| Ice | Shattered ice shards with frosted edges |
| Blood | Glowing red chevrons over dark scales, with blood dripping from each chevron |
| Nature | Green chevrons with leaves sprouting along them, over mossy scales, leaf emblems |
| Lightning | Yellow chevrons with crackles leaping off them |
| Rune | Dark teal scales with a glowing channel and rune circles down the centre |
| Dragonfire | Red-orange chevrons that burn blue toward the hem |
| Void | Purple channel down the centre with the void eye emblem |
| Sandstone | Weathered sandstone scales |
| Crystal | Prismatic chevrons on dark crystal scales |
| Roguelike | Dim crimson chevrons on black scales, hood sigil |
| Golden | Gold chevrons on dark scales with a polished sheen |
| Toxic | Bubbling venom ooze |
| Corrupted | Jagged red crystal splinters |
| Sanguine | Branching gold-and-blood vessels |
| Amascut | Desert bands of zigzags, sun discs, ankhs and eyes, sun emblem |
| Tidal | Rows of rolling wave scallops |
| Saradomin | Blue cloth with gold trim and stars, Saradomin star |
| Zamorak | Hellfire cracks, Zamorak crest |
| Guthix | Flowing wood grain with knots, balance emblem |
| Abyssal | Writhing tendrils with suckers |
| Tormented | Demon-green fire cracks with sparks |
| Varlamore Sunset | Sunbird feathers fading from orange to dusk purple |
| Twisted | Twisting braided strands, twisted bow emblem |
| Custom | Any design (Ice shards, Shadow smoke or any other) in your own colours |

## Settings
- **Cape style** — pick the design.
- **Custom style** — set Cape style to Custom, pick a design (Ice, Shadow or any other) and choose its base, line and glow colours. The design keeps its pattern, motion and emblems.

![custom colours](custom.png)
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
- Every pattern is drawn to look right when worn: the cape stretches its texture, so round motifs are pre-shaped to stay round.
- Each pattern sits on its own material: small lit scales (the concept-art styles), lit rock (cracks, strata), woven cloth (Holy, Saradomin), polished metal, flat crystal faces (Ice, Corrupted) or soft surfaces (Shadow).
- The glowing lines are two-tone: a hot core plus a glow that spills onto the surface in a second colour.

## How it works
The game can't recolour a texture, and the cape's outer panel is a single lava texture. Each frame the plugin reads your animated model, finds the faces using that texture, computes their texture coordinates the same way the client does, and draws a repainted version of the texture onto them — so the design moves with the cape. The crack pattern is taken from the game's own texture at runtime and the other patterns are generated in code; nothing from the game cache is bundled. The rock trim, hem accents and inner lining are recoloured through the client's per-player colour overrides.

All changes are client-side; only you see them.

## Known limits
- Drawn on top of the scene: other players and NPCs standing in front of your cape are cut out, but small objects passing directly in front of it (e.g. a held weapon swung across your back) can appear behind the design.
- Emblems are placed for the standard infernal cape; the infernal max cape gets the repaint and effects without emblems.
