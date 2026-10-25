# Malli / trifecta coverage

## Data contracts

`vtranslate.fetch.schema` describes downloader options/argv/invocation, flat download, probe, engine media metadata, errors/results, provider registration, addon state, health and initialization. Extension maps remain open. No runtime restriction against Malli was found; it is a base dependency. hive-test 0.4.5 is test-only; existing test.check retained.

## Public function inventory

| Namespace | Covered by deftrifecta | Excluded public API and concrete reason |
|---|---|---|
| calc.ytdlp | command, probe-command, parse-line, interpret, interpret-probe | None |
| adapters.ytdlp | — | available? shells `yt-dlp --version`; make-fetcher consults host availability; fetch-media protocol implementation creates directories, spawns process and checks files |
| providers.register | — | register-adapters! consults host binary availability; resolve-fetcher defmethod delegates to effectful factory |
| addon | — | init-as-addon! dynamically resolves/extends host protocol and mutates singleton state; get-addon-instance dereferences singleton atom; register-adapters! consults host availability |

Generated record factories are data constructors, exercised through adapter construction, not standalone behavioral APIs. All five public pure calculation functions have named golden cases in `test/golden/*.edn`, test.check generators, and `{:in :out}` predicates checking both Malli and a relation (argv URI terminator/options, printed columns, timeout/landed precedence, and probe measurements).

## Stub-transport contracts

`transport-contract-test` stubs shell, directory creation and path queries: checks successful engine metadata mapping, exact guarded URI argv, missing-file failure, registration schema, and factory availability behavior. Existing wiring and downloader tests retained. No external command or download in new tests.

## Verification / bugs

Fresh JVM full alias: `timeout 900 clojure -Sdeps "$(cat local.deps.edn)" -M:test`: **41 tests, 112 assertions, 0 failures** (exit 0). No production bug found. Renamed a new schema from Error to FetchError before verification to avoid collision with java.lang.Error.
