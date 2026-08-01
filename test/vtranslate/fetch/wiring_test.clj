(ns vtranslate.fetch.wiring-test
  "The addon's contract with the engine: loading it through the catalog is what
   makes [:providers :fetcher] :yt-dlp resolvable, and nothing else changes."
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [hive-dsl.result :as r]
            [hive-system.fs.core :as fsq]
            [hive-system.fs.filesystem :as hfs]
            [vtranslate.engine.addons :as addons]
            [vtranslate.engine.domain.ingestion :as ing]
            [vtranslate.engine.main :as main]
            [vtranslate.engine.port.fetcher :as port]
            [vtranslate.engine.wiring :as wiring]
            [vtranslate.fetch.providers.register]))

(deftest with-no-provider-configured-the-port-refuses-loudly
  (let [built (wiring/build-port :fetcher {})]
    (is (r/ok? built) "a local job must not fail merely for lacking a fetcher")
    (let [res (port/fetch-media (:ok built) "https://host/x" {})]
      (is (= :error/no-source-fetcher (:error res)))
      (is (= "https://host/x" (:source res))))))

(deftest the-catalog-knows-this-addon
  (let [entry (:vtranslate/fetch addons/addon-catalog)]
    (is (= 'vtranslate.fetch.addon (:ns entry)))
    (is (= [:addon-fetch] (:classpath/aliases entry)))))

(deftest loading-through-the-catalog-registers-the-provider
  (let [[loaded] (addons/load-addons! [:vtranslate/fetch])]
    (is (:loaded? loaded) (str "load error: " (pr-str (dissoc loaded :result))))
    (is (= 'vtranslate.fetch.addon (:addon/ns loaded)))))

(deftest yt-dlp-resolves-once-the-addon-is-on-the-classpath
  (let [built (wiring/build-port :fetcher {:fetcher :yt-dlp
                                           :fetcher-opts {:dir "/tmp/vt-fetch-test"}})]
    (testing "resolution either yields the port or refuses because the binary is absent"
      (if (r/ok? built)
        (is (port/fetcher? (:ok built)))
        (is (= :error/fetcher-unavailable (:error built))
            "the only acceptable failure is a missing binary, named as such")))))

(deftest an-unregistered-provider-fails-loud-rather-than-defaulting
  (let [built (wiring/build-port :fetcher {:fetcher :grayjay})]
    (is (r/err? built))
    (is (not= :error/no-source-fetcher (:error built))
        "an unknown key is a routing error, not a missing-addon error")))

;; --- the hive-system filesystem seam ----------------------------------------

(deftest filesystem-mutations-resolve-through-hive-system
  (testing "IFilesystem is reachable on this classpath, so target-dir has a backend"
    (let [made (hfs/tmpdir! "vtranslate-fetch-test-")]
      (is (r/ok? made))
      (let [root (get-in made [:ok :path])
            sub  (str root "/a/b")]
        (try
          (is (true? (:ok (fsq/directory? root))))
          (is (r/ok? (hfs/mkdirs! sub)) "nested creation is what a per-job dir needs")
          (is (true? (:ok (fsq/directory? sub))))
          (is (r/ok? (hfs/mkdirs! sub)) "and it is idempotent")
          (finally (fs/delete-tree root)))))))

;; --- the boundary contract this addon exists to serve ------------------------

(deftest a-remote-source-becomes-a-path-before-kind-inference
  (let [spec {:job-id "j1" :source "https://host.tv/watch?v=x" :target-language "pt"}]
    (is (ing/remote-source? (:source spec)))
    (is (= :media/video (ing/infer-kind (:source spec)))
        "and it does not misread the .tv host as an extension")
    (let [out (main/localized-spec spec {:local-path "/tmp/j1/x.mkv" :title "X"})]
      (is (= "/tmp/j1/x.mkv" (:source out)))
      (is (not (ing/remote-source? (:source out))) "downstream never sees a URL"))))
