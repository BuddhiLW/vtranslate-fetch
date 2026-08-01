(ns vtranslate.fetch.port.workspace
  "Port (DIP) for the filesystem MUTATIONS a fetch needs.

   hive-system owns path QUERIES — `hive-system.fs.core` implements IPathQuery
   and this addon calls it directly. It also declares IFilesystem/fs-tmpdir!,
   but ships no implementation of it, so directory creation and temp-root
   selection live behind this port until hive-system provides one; swapping to
   it then is a new record, not a rewrite."
  (:require [hive-dsl.result :as r]))

(defprotocol IFetchWorkspace
  "Where a fetch is allowed to write."
  (ensure-dir! [this path]
    "Create `path` if absent. => (r/ok path) | (r/err :error/fetch-dir {...}).")
  (temp-root [this]
    "A writable root for callers that name no directory. => (r/ok path)."))

(defn workspace?
  "True when `x` satisfies the port."
  [x]
  (satisfies? IFetchWorkspace x))

(defrecord ReadOnlyWorkspace [reason]
  IFetchWorkspace
  (ensure-dir! [_ path] (r/err :error/fetch-dir {:dir path :reason reason}))
  (temp-root [_] (r/err :error/fetch-dir {:reason reason})))

(def read-only
  "A workspace that writes nothing — the safe default for a caller that has not
   said where downloads may land."
  (->ReadOnlyWorkspace "no writable workspace was injected"))
