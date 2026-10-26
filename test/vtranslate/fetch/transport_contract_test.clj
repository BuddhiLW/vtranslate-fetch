(ns vtranslate.fetch.transport-contract-test
  (:require [clojure.java.shell :as shell]
            [clojure.test :refer [deftest is]]
            [hive-dsl.result :as r]
            [hive-system.fs.core :as fsq]
            [hive-system.fs.filesystem :as fs]
            [malli.core :as m]
            [vtranslate.engine.port.fetcher :as port]
            [vtranslate.fetch.adapters.ytdlp :as adapter]
            [vtranslate.fetch.providers.register :as register]
            [vtranslate.fetch.schema :as s]))

(deftest fetch-contract-with-stubbed-transport
  (let [calls (atom [])]
    (with-redefs [shell/sh (fn [& argv]
                            (swap! calls conj argv)
                            {:exit 0 :out "/cache/video.mp4\tTitle\t12\tmp4" :err ""})
                  fs/mkdirs! (fn [path] (r/ok {:path path}))
                  fsq/file? (fn [_] (r/ok true))]
      (let [result (port/fetch-media (adapter/->YtDlpFetcher {:dir "/cache"}) "https://example.invalid/a" {})]
        (is (m/validate s/EngineMedia (:ok result)))
        (is (= {:local-path "/cache/video.mp4" :title "Title" :metadata {:ext "mp4" :duration-s 12.0}} (:ok result)))
        (is (= ["--" "https://example.invalid/a"] (vec (take-last 2 (first @calls)))))))
    (with-redefs [shell/sh (fn [& _] {:exit 0 :out "/missing" :err ""})
                  fs/mkdirs! (fn [path] (r/ok {:path path}))
                  fsq/file? (fn [_] (r/ok false))]
      (is (= :error/fetch-missing-file
             (:error (port/fetch-media (adapter/->YtDlpFetcher {:dir "/cache"}) "https://example.invalid/a" {})))))))

(deftest registration-and-construction-contract
  (doseq [available? [false true]]
    (with-redefs [adapter/available? (constantly available?)]
      (is (m/validate s/Registration (:ok (register/register-adapters!))))
      (is (= available? (contains? (adapter/make-fetcher {}) :ok)))
      (is (= available? (get-in (register/register-adapters!) [:ok :available :yt-dlp]))))))
