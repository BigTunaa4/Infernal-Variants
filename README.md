# Infernal Variants

Repaint your infernal cape in 26 designs plus a custom one. Each style has its own pattern, from rune stones and scale mail to wave scallops and sunbird feathers, and everything flows down the cape. The lava-crack styles (Infernal, Dragonfire, Zamorak, Tormented) keep the real cape's exact crack pattern.

![animated preview](preview.gif)

![all styles](preview.png)

| Style | Look |
|---|---|
| Infernal (original) | Untouched lava |
| Holy | Ivory cloth with a gold diamond lattice and stars, holy star emblem |
| Shadow | Drifting purple smoke wisps on black |
| Ice | Shattered ice shards with frosted edges |
| Blood | Blood running down the cape |
| Nature | Climbing vines with leaves, leaf emblems |
| Lightning | Forked lightning bolts over storm cloud |
| Rune | Elemental rune stones (air, water, earth, fire) joined by glowing channels, rune circle emblems |
| Dragonfire | Lava cracks burning orange, turning blue at the hem |
| Void | Purple nebula and stars, void eye emblem |
| Sandstone | Layered sandstone strata |
| Crystal | Pale crystal facets with a soft prismatic sheen |
| Roguelike | Quilted black leather with crimson stitching, hood sigil |
| Golden | Polished gold scale mail |
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
| Custom | Your colours and your choice of pattern |

## Settings
- **Cape style** — pick the design.
- **Custom style** — plate, vein and glow colour pickers plus a pattern choice, used when the style is Custom.
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
- Each pattern sits on its own material: lit rock (cracks, strata, rune stones), woven cloth (Holy, Saradomin, Roguelike), polished metal (Golden), flat crystal faces (Ice, Crystal, Corrupted) or soft surfaces.
- The glowing lines are two-tone: a hot core plus a glow that spills onto the surface in a second colour.

## How it works
The game can't recolour a texture, and the cape's outer panel is a single lava texture. Each frame the plugin reads your animated model, finds the faces using that texture, computes their texture coordinates the same way the client does, and draws a repainted version of the texture onto them — so the design moves with the cape. The crack pattern is taken from the game's own texture at runtime and the other patterns are generated in code; nothing from the game cache is bundled. The rock trim, hem accents and inner lining are recoloured through the client's per-player colour overrides.

All changes are client-side; only you see them.

## Known limits
- Drawn on top of the scene: other players and NPCs standing in front of your cape are cut out, but small objects passing directly in front of it (e.g. a held weapon swung across your back) can appear behind the design.
- Emblems are placed for the standard infernal cape; the infernal max cape gets the repaint and effects without emblems.
