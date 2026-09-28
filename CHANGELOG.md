# MapMakerMusic changelog

## [2.0.0] — conditions are commands

Old saves load, but pre-2.0 condition rows show as inert "Unknown condition" leaves. Re-create them with the new tools.

### Conditions are real commands now
- One `Command` condition kind replaces the nine built-in kinds (time, weather, scoreboard, player, health, hunger, entity count, biome, coordinates). Anything the old kinds did is a command: `mmcheck time day`, `execute if data entity @s {Health:20.0f}`, and so on. Bare `execute if ...` without `run` works.
- New `mmcheck` command (perm 2): `time day|night`, `time range <min> <max>` with anchor suggestions (0/6000/12000/18000), `weather clear|rain|thunder`, `biome <id>` with live biome-id suggestions.
- Per-tick result cache per player and command; malformed commands never match and warn once in the log.
- Command editor: wide entry with server-driven autocomplete that works mid-command and preserves the tail, 13 clickable preset buttons with hover explanations, Test-now button with inline green PASS / red FAIL.
- Conditions tab is a nested tree: depth brackets, clickable AND/OR pills, inline add-filter/add-group per group, click-to-edit, move up/down, remove. Clicks resolve by index path so async reloads can't strand them.

### Security
- Configuring boxes, saving edits, wand placement/selection and Test-now require operator (perm 2); denied players get a red action-bar message. Creative alone no longer suffices.

### Boxes, areas, wand
- Area outlines broadcast on place/save, so every holder sees them without reopening screens or relogging.
- Big high-priority boxes are no longer blocked by smaller low-priority ones they outrank.
- Wand places the audiobox at the second corner, refuses same-block corners, silent same-block clicks; HSB outline-color picker with hex and presets; outline swatch follows box color.

### Playback engine
- Keyed playback engine with server clock, SavedData resume, per-key downloads, named track-finish packets.
- Per-queue shuffle with reshuffle on loop, sequential fade gaps, fades skipped for sub-fade-length tracks, resume backs off after recent activity.
- Relog resumes exact track and offset without fade; leaving and re-entering an area restarts cleanly; area chain beats global chain; uploaded-buffer cache for rapid switching; stb_vorbis buffers freed after upload; resume/decode livelock fixed.

### Tracks and library
- Server-synced library, per-player areas, track durations with mm:ss display, 9:59:59 add validation, STOP rows, per-track mix screen, three-line queue rows.
- 20MB playlist cap dropped; track listing cached and throttled.

### Interface
- All interface, command and chat text lives in `en_us.json` (preset labels, tooltips, button hints included); dynamic rule text stays literal.
- Gui-wide layout unification, aligned Point/Radius fields, compact command editor (entry plus Test row, PASS/FAIL line, captioned preset grid).

## [1.6.0]
- Server-synced music library, per-player areas, audiobox access.
