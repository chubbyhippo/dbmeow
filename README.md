# dbmeow

[meow](https://github.com/meow-edit/meow)-style modal editing for DBeaver —
meow's suggested **QWERTY layout** as a native modal engine over Eclipse text
editors, DBeaver's SQL editor included. Select first, then act.

| | |
|---|---|
| Siblings | [ideameow](https://github.com/chubbyhippo/ideameow) (IntelliJ), [codemeow](https://github.com/chubbyhippo/codemeow) (VS Code/VSCodium) |
| Shared with them | keymap format, default layout, a behavior-identical BDD suite |
| Keymap | bundled `.dbmeowrc`; user copy at `~/.dbmeowrc` |

## Status

| Area | State |
|---|---|
| Modes, the meow layout, selections with history | ported, BDD-tested |
| Words, things, find/till, search | ported, BDD-tested |
| Kill/save/yank editing, grab, digit expand | ported, BDD-tested |
| The `SPC` keypad, the `.dbmeowrc` config layer | ported, BDD-tested |
| Beacon | logic ported and tested; SWT's single caret means the adapter shows only the primary cursor — the multi-range edit still applies |
| Windmove geometry, tree MOTION maps | core logic ported, the SWT surfaces staged |

| Layer | Mechanism |
|---|---|
| `core/` | a full headless port |
| `plugin/` | the Eclipse adapter: a prepended `VerifyKeyListener`, the mechanism vrapper proved for vim emulation |

| Key | Does |
|---|---|
| `SPC w w`, `SPC x o` | ace-window over the open text editors — three or more each get a corner label in avy's colors and the next key activates that editor; with exactly two it hops straight across; `Esc` cancels |

## Build & install

```sh
cd dbmeow
./setup.sh                   # build + test the core, build the Eclipse bundle,
                             # then print the DBeaver dropins install step
./setup.sh --core-only       # just the headless meow behavior suite (no
                             # Eclipse target-platform download)
```

| Item | Value |
|---|---|
| Bundle lands at | `plugin/target/dbmeow-plugin-*.jar` |
| Install | drop it into DBeaver's `dropins/`, restart, open a SQL editor — you are in NORMAL |
| Toolchain | pinned in `mise.toml` (java 21, maven); `setup.sh` falls back to `mise exec` when the PATH tools are older |
| Behind a TLS-inspecting proxy | `setup.sh` sets the system trust store — the p2 target-platform download needs it |
| Full build flow, runtime-unverified status | [plugin/BUILD.md](plugin/BUILD.md) |

## ~/.dbmeowrc

Same syntax and defaults model as the siblings — either sibling's rc pastes in,
and unknown lines are ignored.

| Layer | What |
|---|---|
| Bundled `.dbmeowrc` | the whole keymap: one `nmap <key> <meow-command>` line per key, plus the `SPC` keypad table of Eclipse command ids |
| `~/.dbmeowrc` | overrides it entry by entry |

### `set` options

| line | effect |
|---|---|
| `set which-key` / `set nowhich-key` | which-key popup on/off (default on) |
| `set timeoutlen=300` | which-key popup delay in ms (bundled default 300) |
| `set overlay-color=#2ECC71` | background of the avy / ace-window / ace-click jump labels (`#RRGGBB`) |
| `set overlay-text-color=#ffffff` | the jump-label text color |
| `set expand-hint-color=#2b5db2` | the `0`–`9` expand-hint color |
| `set grab-color=#cde8cd` | the grab / beacon highlight color |

A single `#RRGGBB` applies to both light and dark themes; a malformed value is
reported like any other rc error, and unknown `set` keys are ignored.
