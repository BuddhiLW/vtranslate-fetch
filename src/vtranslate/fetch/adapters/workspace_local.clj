(ns vtranslate.fetch.adapters.workspace-local
  "IFetchWorkspace on the local disk. Queries go through hive-system's IPathQuery;
   only the mkdir itself is done here, because hive-system has no IFilesystem
   implementation to delegate it to."
  (:require [clojure.java.io :as io]
            [hive-dsl.result :as r]
            [hive-system.fs.core :as fsq]
            [vtranslate.fetch.port.workspace :as port]))

(defn- already-a-dir?
  "Whether `path` is an existing directory, per hive-system. A failed query is
   treated as 'not a directory' — the mkdir below is the authority."
  [path]
  (let [res (fsq/directory? path)]
    (and (r/ok? res) (true? (:ok res)))))

(defrecord LocalWorkspace [root]
  port/IFetchWorkspace
  (ensure-dir! [_ path]
    (if (already-a-dir? path)
      (r/ok (str path))
      (try
        (.mkdirs (io/file (str path)))
        (if (already-a-dir? path)
          (r/ok (str path))
          (r/err :error/fetch-dir {:dir path :reason "could not create directory"}))
        (catch Exception e
          (r/err :error/fetch-dir {:dir path :reason (.getMessage e)})))))

  (temp-root [_]
    (if-let [r' (not-empty (str (or root "")))]
      (r/ok r')
      (r/ok (System/getProperty "java.io.tmpdir")))))

(defn make-workspace
  "The production workspace. `root` overrides the system temp dir."
  ([] (make-workspace nil))
  ([root] (->LocalWorkspace root)))
