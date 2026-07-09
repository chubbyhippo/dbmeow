# dbmeow — meow modal editing for DBeaver

If you love [meow](https://github.com/meow-edit/meow) in Emacs and sigh every
time you open DBeaver, this plugin is for you. It implements meow's suggested
**QWERTY layout** as a native modal editing engine over Eclipse text editors —
DBeaver's SQL editor included. Just meow: select first, then act.

It is the third sibling of [ideameow](https://github.com/chubbyhippo/ideameow)
(IntelliJ) and [codemeow](https://github.com/chubbyhippo/codemeow)
(VS Code/VSCodium): the three share their keymap format, their default layout,
and a behavior-identical BDD suite, so your muscle memory transfers unchanged.

## Status

The engine (`core/`) is a full headless port — modes, the meow layout,
selections with history, words/things/find/till/search, kill/save/yank
editing, grab, digit expand, the `SPC` keypad, and the `.dbmeowrc` config
layer — tested by the shared BDD suite. The Eclipse adapter (`plugin/`)
wires it into editors via the same mechanism vrapper proved for vim
emulation (a prepended `VerifyKeyListener`).

Deliberate gaps for now: windmove, tree MOTION maps, and the avy overlay
painting — the core logic is ported, the SWT surfaces are staged. Beacon's
logic is ported and tested too; SWT's single caret just means the adapter
shows only the primary cursor (the multi-range edit still applies).

## Build & install

```sh
cd dbmeow
./setup.sh                   # build + test the core, build the Eclipse bundle,
                             # then print the DBeaver dropins install step
./setup.sh --core-only       # just the headless meow behavior suite (no
                             # Eclipse target-platform download)
```

The bundle lands at `plugin/target/dbmeow-plugin-*.jar`; drop it into DBeaver's
`dropins/` and restart, then open a SQL editor — you're in NORMAL mode. The
toolchain is pinned in `mise.toml` (java 21, maven) and `setup.sh` falls back
to `mise exec` when your PATH tools are older. Behind a TLS-inspecting proxy it
sets the system trust store for you (the p2 target-platform download needs it);
the full plugin build flow and its runtime-unverified status are in
[plugin/BUILD.md](plugin/BUILD.md).

## ~/.dbmeowrc

Same syntax and defaults model as the siblings: the bundled `.dbmeowrc` is
the whole keymap (one `nmap <key> <meow-command>` line per key plus the
`SPC` keypad table of Eclipse command ids); `~/.dbmeowrc` overrides it entry
by entry. Either sibling's rc pastes in — unknown lines are ignored.
