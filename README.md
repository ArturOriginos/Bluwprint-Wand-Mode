# Blueprint Wand

🇷🇺 [Русская версия](README.ru.md)

A **Minecraft 1.21.1 (NeoForge)** mod. The Blueprint Wand selects an area of the world and saves the build
into a plain-text JSON file that is easy to read for humans and AI assistants (e.g. Claude).
Saved schematics can be previewed with a frame, rotated and pasted back.

## Features

- "Blueprint Wand" item (looks like an enchanted stick). Recipe: stick + paper.
- Two-point selection; the selected area is outlined while you hold the wand.
- `/schem save <name>` writes `schematics/<name>.json` in the game folder.
- `/schem preview <name> ...` shows the schematic as translucent blocks inside a yellow frame where it will be placed, `/schem confirm` pastes it. Builds above 40,000 non-air blocks show only the frame; blocks farther than 64 blocks from you are not drawn.
- Saved schematic names from the `schematics` folder are suggested automatically while typing `/schem paste` and `/schem preview`.
- `/schem paste <name> ...` pastes immediately, with optional rotation (90 / 180 / 270°).
- `/schem undo` reverts the last paste.
- **Schematic List** item (recipe: book + paper): right click to open a screen with every schematic from the folder (with sizes). Pick one, choose rotation, position (where you look / under you) and whether to clear air, press **Preview** (yellow frame), then open the list again and press **Place**. The buttons run the same `/schem` commands, so operator rights are required.
- Blocks are saved with all their properties (`facing`, `half`, `waterlogged`, ...) plus a list of block-entity positions
  (chests, signs). Container contents, NBT and entities are not saved.
- English and Russian translations (`assets/blueprintwand/lang`).

## Controls

| Action | Result |
|---|---|
| Left click a block with the wand | Point 1 (blue frame) |
| Right click a block with the wand | Point 2 (red frame) |
| Shift + left click | Clear the selection |

The green frame is the whole area that will be saved. Maximum size: 1,000,000 blocks.

## Commands

| Command | Description |
|---|---|
| `/schem save <name>` | Save the selected area |
| `/schem list` | List saved schematics |
| `/schem preview <name> [r90\|r180\|r270] [x y z] [with_air]` | Show a yellow preview frame (re-run it to move or rotate) |
| `/schem confirm` / `/schem cancel` | Paste the previewed schematic / drop the preview |
| `/schem paste <name> [r90\|r180\|r270] [x y z] [with_air]` | Paste right away |
| `/schem undo` | Undo the last paste |

- The schematic's minimum corner (lowest x, y, z) is placed at the target position, which defaults to the block you stand in.
  Coordinates can be relative (`~ ~ ~`) or the `origin_world` values from the file.
- Rotation turns the build clockwise; block states (stairs, doors, ...) are rotated with it.
- `with_air` also pastes the air blocks of the schematic, clearing the area. Without it air is skipped.
- Pasting, preview and undo need operator rights (permission level 2; in singleplayer, cheats enabled).
- Blocks are placed without neighbour updates, so nothing pops off or falls during the paste.
  Blocks from missing mods are skipped and listed in chat.
- Undo does not restore chest contents or other block data.

## Installation

Put the jar into the `mods` folder of the client (and of the server, if you play on one).
The mod is required on both sides (NeoForge 1.21.1).

## Changelog

- **1.4.0** — translucent block preview: `/schem preview` (and the list) shows the actual blocks of the schematic as a see-through ghost, with rotation applied.
- **1.3.0** — "Schematic List" item: a GUI with all saved schematics, rotation / position options, preview and paste.
- **1.2.1** — saved schematic names are suggested (Tab) in `/schem paste` and `/schem preview`.
- **1.2.0** — rotation, frame preview (`/schem preview`, `/schem confirm`, `/schem cancel`), English translation.
- **1.1.0** — `/schem paste` and `/schem undo`.
- **1.0.0** — wand, selection, `/schem save`, `/schem list`.

## License

MIT — see [LICENSE](LICENSE).
