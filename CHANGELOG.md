# MapMakerMusic changelog

## [2.0.0] — queues, conditions, commands

Old saves load. Boxes saved before queues existed gain one empty queue; pre-2.0 condition rows, if any, show as inert "Unknown condition" leaves — re-create them with the tools below.

### Queues and playlists (new)
- Each audiobox holds an ordered queue list; top to bottom, first match wins.
- Queues mix library tracks, a STOP entry, per-track volume/pitch, loop, shuffle with reshuffle, fade in/out, and Music/Sound channels (single resumable track vs overlapping one-shots).
- Track durations with mm:ss display, 9:59:59 add validation, server-synced library with per-key downloads, cached throttled track listing, 20MB playlist cap dropped.

### Conditions (new)
- Every queue carries a nested AND/OR rule tree with depth brackets, clickable pills, inline add-filter/add-group per group, click-to-edit, move up/down and remove. Clicks resolve by index path, so async reloads can't strand them.
- Rules are real Minecraft commands: `mmcheck time day`, `execute if data entity @s {Health:20.0f}`, `scoreboard players get @s kills` — bare `execute if ...` without `run` works. Results cache per player and command each tick; malformed commands never match and warn once in the log.
- New `mmcheck` command (perm 2): `time day|night`, `time range <min> <max>` with anchor suggestions, `weather clear|rain|thunder`, `biome <id>` with live suggestions.
- Command editor: wide entry with server-driven autocomplete that works mid-command and preserves the tail, 13 preset buttons with hover explanations, Test-now with inline green PASS / red FAIL.

### Security
- Configuring boxes, saving edits, wand placement/selection and Test-now require operator (perm 2); denied players get a red action-bar message. Creative alone no longer suffices.

### Boxes, areas, wand
- Area outlines broadcast on place/save, so every holder sees them without reopening screens or relogging.
- Wand places the audiobox at the second corner, refuses same-block corners; HSB outline-color picker with hex and presets; outlines follow box color.
- Big high-priority boxes are no longer blocked by smaller low-priority ones they outrank.

### Playback
- Server-clock engine with SavedData resume: relog resumes exact track and offset without fade; leaving and re-entering an area restarts cleanly; area chain beats global chain.
- Sequential fade gaps, fades skipped for sub-fade-length tracks, resume backs off after recent activity, uploaded-buffer cache for rapid switching, stb_vorbis buffers freed after upload, resume/decode livelock fixed.

### Interface
- All interface, command and chat text lives in `en_us.json`.
- Gui-wide layout unification, aligned Point/Radius fields, compact command editor (entry plus Test row, PASS/FAIL line, captioned preset grid).

## [1.6.0]
- Server-synced music library, per-player areas, audiobox access. No queues, no conditions.
