(ns vtranslate.fetch.adapters.ytdlp
  "ISourceFetcher over the yt-dlp binary. One process per job; the file lands in
   a per-job directory so two concurrent jobs on the same URL cannot collide.
   Filesystem work goes through hive-system: IPathQuery for existence,
   IFilesystem for the directory it writes into."
  (:require [clojure.java.shell :as shell]
            [hive-dsl.result :as r]
            [hive-system.fs.core :as fsq]
            [hive-system.fs.filesystem :as hfs]
            [vtranslate.engine.port.fetcher :as port]
            [vtranslate.fetch.calc.ytdlp :as calc]))

(def binary "yt-dlp")

(defn available?
  "Whether the yt-dlp binary resolves on PATH."
  []
  (boolean
   (try
     (zero? (:exit (shell/sh binary "--version")))
     (catch Exception _ false))))

(defn- landed?
  "Whether the file yt-dlp named is really on disk, per hive-system."
  [{:keys [local-path]}]
  (and local-path
       (let [res (fsq/file? local-path)]
         (and (r/ok? res) (true? (:ok res))))))

(defn- target-dir
  "Where this fetch may write: the caller's :dir, else a fresh temp dir.
   => (r/ok path) | (r/err :fs/mkdirs-failed|:fs/tmpdir-failed {...})"
  [dir]
  (r/let-ok [made (if-let [d (not-empty (str (or dir "")))]
                    (hfs/mkdirs! d)
                    (hfs/tmpdir! "vtranslate-fetch-"))]
    (r/ok (:path made))))

(defrecord YtDlpFetcher [opts]
  port/ISourceFetcher
  (fetch-media [_ uri call-opts]
    (let [merged (merge opts call-opts)]
      (r/let-ok [dir     (target-dir (:dir merged))
                 fetched (calc/interpret
                          uri
                          (apply shell/sh (calc/command uri (assoc merged :dir dir))))]
        (if (landed? fetched)
          (r/ok fetched)
          (r/err :error/fetch-missing-file
                 {:source uri
                  :path (:local-path fetched)
                  :reason "yt-dlp named a file that is not on disk"}))))))

(defn make-fetcher
  "Build the fetcher, refusing loudly when the binary is absent — the same
   fail-loud contract the engine's other optional-native adapters keep.
   => (r/ok fetcher) | (r/err :error/fetcher-unavailable {...})."
  [opts]
  (if (available?)
    (r/ok (->YtDlpFetcher (or opts {})))
    (r/err :error/fetcher-unavailable
           {:provider :yt-dlp
            :binary binary
            :reason "yt-dlp is not on PATH — install it to translate from a URL"})))
