(ns vtranslate.fetch.trifecta-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [clojure.test.check.generators :as gen]
            [hive-test.trifecta :refer [deftrifecta]]
            [malli.core :as m]
            [vtranslate.fetch.schema :as s]
            [vtranslate.fetch.calc.ytdlp :as calc]))

(def uri "https://example.invalid/video")
(defn command-observation [opts] {:in opts :out (calc/command uri opts)})
(deftrifecta command command-observation
  {:golden-path "test/golden/command.edn"
   :cases {:default {} :custom {:binary "custom" :dir "/tmp/cache" :format "best" :max-bytes 123 :cookies-from-browser "firefox" :extra-args ["--verbose"]}}
   :gen (gen/let [dir gen/string-alphanumeric retries (gen/choose 0 20) size (gen/choose -1 10000)]
          {:dir dir :retries retries :max-bytes size})
   :pred (fn [{:keys [in out]}]
           (and (m/validate s/Command out)
                (= ["--" uri] (subvec out (- (count out) 2)))
                (= (or (not-empty (:binary in)) calc/default-binary) (first out))
                (= (str (or (:retries in) 3)) (nth out (inc (.indexOf out "--retries"))))
                (= (pos? (or (:max-bytes in) 0)) (boolean (some #{"--max-filesize"} out)))) )})

(defn probe-command-observation [opts] {:in opts :out (calc/probe-command uri opts)})
(deftrifecta probe-command probe-command-observation
  {:golden-path "test/golden/probe-command.edn" :cases {:default {} :custom {:binary "custom" :socket-timeout-s 7}}
   :gen (gen/fmap #(hash-map :socket-timeout-s %) (gen/choose 1 100))
   :pred (fn [{:keys [in out]}]
           (and (m/validate s/Command out) (= ["--" uri] (subvec out (- (count out) 2)))
                (some #{"--simulate"} out) (some #{"--skip-download"} out)
                (= (str (or (:socket-timeout-s in) 15)) (nth out (inc (.indexOf out "--socket-timeout"))))))})

(defn parse-observation [stdout] {:in stdout :out (calc/parse-line stdout)})
(deftrifecta parse-line parse-observation
  {:golden-path "test/golden/parse-line.edn"
   :cases {:empty "" :columns " /tmp/a \t title \t12.5\tmp4\n" :last "first\tA\t2\twebm\nsecond\tB\t3\tmp4\n" :missing-path "\ttitle\t2\tmp4"}
   :gen (gen/let [id gen/string-alphanumeric seconds (gen/choose 0 100000)]
          (str "/tmp/" id "\tTitle\t" seconds "\tmp4\n"))
   :pred (fn [{:keys [in out]}]
           (let [[path title seconds ext] (str/split (str/trim in) #"\t")]
             (and (m/validate s/Download out) (= path (:local-path out)) (= title (:title out))
                  (= ext (:extension out)) (= (Double/parseDouble seconds) (:duration-seconds out)))))})

(defn interpret-observation [process] {:in process :out (calc/interpret uri process)})
(deftrifecta interpret interpret-observation
  {:golden-path "test/golden/interpret.edn"
   :cases {:ok {:exit 0 :out "/tmp/a\tTitle\t5\tmp4"}
           :fallback {:exit 1 :out "/tmp/a\tTitle\t5\tmp4" :err "fallback"}
           :timeout {:timed-out? true :exit 0 :out "/tmp/a"}
           :empty {:exit 0 :out ""} :failed {:exit 2 :out "" :err "failure"}}
   :gen (gen/let [exit (gen/choose 0 3) timed-out? gen/boolean landed? gen/boolean]
          {:exit exit :timed-out? timed-out? :out (if landed? "/tmp/a\tTitle\t5\tmp4" "") :err "error"})
   :pred (fn [{:keys [in out]}]
           (and (m/validate s/FetchResult out)
                (= (and (not (:timed-out? in)) (some? (calc/parse-line (:out in)))) (contains? out :ok))
                (if (:timed-out? in) (= :error/fetch-timeout (:error out)) true)))})

(defn probe-observation [process] {:in process :out (calc/interpret-probe process)})
(deftrifecta interpret-probe probe-observation
  {:golden-path "test/golden/interpret-probe.edn"
   :cases {:video {:exit 0 :out "5\t1920\t1080"} :audio {:exit 0 :out "5\tNA\tNA"}
           :timeout {:exit 0 :out "5\t1920\t1080" :timed-out? true}
           :failed {:exit 2 :out ""} :none {:exit 0 :out "NA\t0\t0"}}
   :gen (gen/let [seconds (gen/choose 1 100000) width (gen/choose 1 8000) height (gen/choose 1 8000) timed-out? gen/boolean]
          {:exit 0 :out (str seconds "\t" width "\t" height) :timed-out? timed-out?})
   :pred (fn [{:keys [in out]}]
           (and (m/validate s/ProbeResult out)
                (if (:timed-out? in)
                  (= :error/probe-timeout (:error out))
                  (let [[seconds width height] (str/split (:out in) #"\t")]
                    (= {:duration-seconds (Double/parseDouble seconds) :width (parse-long width) :height (parse-long height)} (:ok out))))))})
