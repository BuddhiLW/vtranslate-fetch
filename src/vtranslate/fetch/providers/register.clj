(ns vtranslate.fetch.providers.register
  "Register this addon's fetch providers on the engine's registry. Requiring this
   ns is what makes [:providers :fetcher] :yt-dlp resolvable."
  (:require [hive-dsl.result :as r]
            [vtranslate.engine.providers.fetcher-registry :as reg]
            [vtranslate.fetch.adapters.ytdlp :as ytdlp]))

(defmethod reg/resolve-fetcher :yt-dlp
  [_ config]
  (ytdlp/make-fetcher (:fetcher-opts config)))

(defn register-adapters!
  "Idempotent: the defmethod above lands on require. Reports what this addon
   contributed and whether its binary is actually usable here."
  ([] (register-adapters! {}))
  ([_config]
   (r/ok {:providers {:fetcher [:yt-dlp]}
          :available {:yt-dlp (ytdlp/available?)}})))
