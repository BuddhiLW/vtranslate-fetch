(ns vtranslate.fetch.ytdlp-test
  (:require [clojure.test :refer [deftest is testing]]
            [hive-dsl.result :as r]
            [vtranslate.engine.port.fetcher :as port]
            [vtranslate.fetch.calc.ytdlp :as calc]
            [vtranslate.fetch.providers.register :as register]))

;; --- argv -------------------------------------------------------------------

(deftest command-names-the-binary-and-is-all-strings
  (let [argv (calc/command "https://host/watch?v=x" {:dir "/tmp/j1"})]
    (is (= "yt-dlp" (first argv)))
    (is (every? string? argv) "a process takes no keywords or nils")))

(deftest the-url-is-last-and-guarded-by-a-double-dash
  (let [argv (calc/command "-not-a-flag-really" {:dir "/tmp/j1"})]
    (is (= "-not-a-flag-really" (last argv)))
    (is (= "--" (nth argv (- (count argv) 2)))
        "a URL starting with a dash must not be read as an option")))

(deftest the-output-template-lands-under-the-requested-directory
  (is (some #{(str "/tmp/j1/" calc/output-template)}
            (calc/command "https://host/x" {:dir "/tmp/j1"})))
  (is (some #{(str "./" calc/output-template)}
            (calc/command "https://host/x" {}))
      "no directory => the working directory, never a nil path"))

(deftest a-playlist-url-fetches-one-item
  (is (some #{"--no-playlist"} (calc/command "https://host/list" {})))
  (is (some #{"--no-simulate"} (calc/command "https://host/list" {}))
      "--print alone would only simulate, producing no file"))

(deftest format-and-cookies-are-passed-only-when-asked-for
  (is (some #{calc/default-format} (calc/command "https://host/x" {})))
  (is (some #{"worst"} (calc/command "https://host/x" {:format "worst"})))
  (is (not-any? #{"--cookies-from-browser"} (calc/command "https://host/x" {})))
  (is (some #{"firefox"} (calc/command "https://host/x" {:cookies-from-browser "firefox"})))
  (is (some #{"--sleep-requests"} (calc/command "https://host/x"
                                                {:extra-args ["--sleep-requests" "1"]}))))

;; --- reading what yt-dlp printed --------------------------------------------

(deftest parse-line-reads-the-tab-separated-record
  (let [p (calc/parse-line "/tmp/j1/abc.mp4\tA Clip\t42.5\tmp4\n")]
    (is (= "/tmp/j1/abc.mp4" (:local-path p)))
    (is (= "A Clip" (:title p)))
    (is (= 42.5 (get-in p [:metadata :duration-s])))
    (is (= "mp4" (get-in p [:metadata :ext])))))

(deftest parse-line-takes-the-last-completed-item
  (is (= "/tmp/b.mp4"
         (:local-path (calc/parse-line "/tmp/a.mp4\tA\t1\tmp4\n/tmp/b.mp4\tB\t2\tmp4\n")))))

(deftest parse-line-tolerates-missing-trailing-fields
  (let [p (calc/parse-line "/tmp/a.mp4\t\t\t\n")]
    (is (= "/tmp/a.mp4" (:local-path p)))
    (is (nil? (:title p)) "an empty title is absent, not blank")
    (is (nil? (:metadata p))))
  (is (nil? (calc/parse-line "")))
  (is (nil? (calc/parse-line "\n \n")))
  (is (nil? (calc/parse-line "\tA Clip\t1\tmp4")) "no path => nothing usable"))

(deftest parse-line-survives-a-non-numeric-duration
  (let [p (calc/parse-line "/tmp/a.mp4\tA\tNA\tmp4")]
    (is (= "/tmp/a.mp4" (:local-path p)))
    (is (nil? (get-in p [:metadata :duration-s])))))

;; --- the railway ------------------------------------------------------------

(deftest interpret-maps-a-successful-fetch
  (let [res (calc/interpret "https://host/x"
                            {:exit 0 :out "/tmp/a.mp4\tA Clip\t9\tmp4" :err ""})]
    (is (r/ok? res))
    (is (= "/tmp/a.mp4" (get-in res [:ok :local-path])))))

(deftest interpret-fails-loud-and-keeps-what-a-human-needs
  (testing "a non-zero exit carries the stderr tail"
    (let [res (calc/interpret "https://host/x"
                              {:exit 1 :out ""
                               :err "line1\nERROR: Video unavailable\n"})]
      (is (= :error/fetch-failed (:error res)))
      (is (= 1 (:exit res)))
      (is (re-find #"Video unavailable" (:message res)))
      (is (= "https://host/x" (:source res)))))

  (testing "success with nothing printed is its own error, not a silent ok"
    (let [res (calc/interpret "https://host/x" {:exit 0 :out "" :err ""})]
      (is (= :error/fetch-no-output (:error res))))))

;; --- the port contract ------------------------------------------------------

(defrecord StubFetcher [path]
  port/ISourceFetcher
  (fetch-media [_ _ _] (r/ok {:local-path path})))

(deftest the-adapter-satisfies-the-engine-port
  (is (port/fetcher? (->StubFetcher "/tmp/a.mp4")))
  (is (r/ok? (port/checked-fetcher (->StubFetcher "/tmp/a.mp4")))))

(deftest requiring-the-register-ns-makes-yt-dlp-resolvable
  (let [res (register/register-adapters! {})]
    (is (r/ok? res))
    (is (= [:yt-dlp] (get-in res [:ok :providers :fetcher])))
    (is (contains? (get-in res [:ok :available]) :yt-dlp)
        "registration reports whether the binary is usable here")))
