# vtranslate-fetch

Source-fetching addon for **vtranslate**. It lets a job start from a URL instead
of a path: the addon resolves the locator to a file on disk, and everything
downstream — probe, demux, ASR — runs unchanged.

The `ISourceFetcher` port lives in the engine; this repo supplies an adapter.

## Why it is an addon

The engine ships no scraper. A local job must not pay for one, and a scraper is
the part of this system most likely to need updating on someone else's schedule.
So the engine declares the port with a refusing default: without a fetch addon a
remote job fails loud, naming the URL it refused, rather than handing a URL to
ffmpeg.

## Use

```bash
# in ~/.config/vtranslate/config.edn
{:providers {:fetcher :yt-dlp}
 :fetcher-opts {:dir "/home/you/Downloads/vtranslate"}
 :addons [:vtranslate/fetch]}
```

```bash
vtranslate run https://some.host/watch?v=xxxx pt-BR
```

Needs [`yt-dlp`](https://github.com/yt-dlp/yt-dlp) on `PATH`. If it is absent the
provider refuses to build and says so — it never degrades quietly.

`:fetcher-opts` accepts `:dir`, `:format` (a yt-dlp format selector, default
`bv*+ba/b`), `:cookies-from-browser`, and `:extra-args` for anything else.

## Shape

| Layer | Namespace | |
|---|---|---|
| Pure | `calc.ytdlp` | argv, output parsing, exit → Result. No process, no disk. |
| Port | `port.workspace` | the filesystem mutations a fetch needs |
| Adapter | `adapters.ytdlp` | runs the binary |
| Adapter | `adapters.workspace-local` | writes to local disk |
| Registry | `providers.register` | `defmethod resolve-fetcher :yt-dlp` |
| Facade | `addon` | IAddon shape, no compile-time hive-mcp dependency |

Path *queries* go through hive-system's `IPathQuery` (`hive-system.fs.core`).
hive-system declares `IFilesystem/fs-tmpdir!` but ships no implementation, so
directory creation and temp-root selection sit behind `port.workspace` until it
does; adopting it then is a new record, not a rewrite.

## Not yet: GrayJay sources

[GrayJay](https://github.com/grayjay-sources/) plugins are JS/TS written against
a host API the GrayJay Android app provides — `fetch`/`fetchJson`/`fetchHtml`,
pagers, `Type` globals. Consuming them from the JVM means GraalJS **plus** a
reimplementation of that host surface. yt-dlp covers ~1800 sites with a single
binary, so it goes first. A GrayJay provider would sit behind the same port with
no rework above it.

## Dev

```bash
clojure -M:test                       # cold suite
clojure -Sdeps "$(cat local.deps.edn)" -M:test   # against a sibling engine checkout
```

`local.deps.edn` (gitignored) is where a `:local/root` override for the engine
belongs — `deps.edn` carries public coordinates only.

## License

AGPL-3.0-or-later. See [LICENSE](LICENSE).
