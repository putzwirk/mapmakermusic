# MapMakerMusic — map-maker wiki

Audioboxes play your `.ogg` tracks when players match your rules. Place one with the audiobox item, punch a second corner to bind its area, then right-click to configure. Everything needs operator (perm 2).

## Tracks and queues

Drop `.ogg` files in the `music` folder (there is an **Audio folder** button in the GUI), then build **queues**: ordered track lists with loop, shuffle, fade in/out, and a Music/Sound channel (single resumable track vs overlapping one-shots). Queues run top to bottom — the first one whose conditions all match wins. A STOP entry ends playback.

## Conditions are commands

Each queue holds a nested AND/OR tree of **command conditions**. A condition matches when its command returns a positive result. Type freely (autocomplete included) or click a preset:

| Preset | Command | Matches when |
|---|---|---|
| Health | `execute if data entity @s {Health:20.0f}` | full HP |
| Hunger | `execute if data entity @s {foodLevel:0}` | starving |
| Name | `execute if entity @s[name=Steve]` | you are Steve |
| Score | `execute if score @s kills matches 10..` | kills ≥ 10 |
| Day / Night | `mmcheck time day` | daytime |
| Weather | `mmcheck weather clear` | clear sky |
| Biome | `mmcheck biome minecraft:plains` | standing in plains |
| Entities | `execute if entity @e[type=minecraft:cow,distance=..30]` | a cow within 30 blocks |
| Item | `clear @s minecraft:diamond 0` | carrying diamonds (tests only) |
| Gamemode | `execute if entity @s[gamemode=creative]` | creative mode |
| Team | `execute if entity @s[team=Red]` | on team Red |
| Holding | `execute if data entity @s {SelectedItem:{id:"minecraft:torch"}}` | torch in main hand |

Bare `execute if ...` needs no trailing `run`. Combine clauses for AND: `execute if score @s kills matches 10.. if entity @s[team=Red]`. Conditions run as the nearby player at perm 2 with output suppressed, re-checked every tick (cached within the tick).

**Test now** dry-runs the command against you and shows green PASS / red FAIL inline.

## Positional vs global

- **Positional**: music plays around a point with a radius, fading with distance. Walk outside the radius and it stops, freeing the claim so a nearer box takes over.
- **Global**: same volume everywhere, no distance cutoff.

## Priority and overlap

Higher priority wins outright. Equal priority: for overlapping areas, the smaller area wins. A box only competes while the player is inside its area *and* (if positional) its radius — silent boxes neither play nor block others.

## Tips

- Boxes and the config GUI are operator-only; players just hear the result.
- Old (pre-2.0) condition rows load as inert "Unknown condition" leaves — rebuild them with the presets above.
- Full command list: `/mapmakermusic play|stop ...`, `/mmcheck time|weather|biome ...`.
