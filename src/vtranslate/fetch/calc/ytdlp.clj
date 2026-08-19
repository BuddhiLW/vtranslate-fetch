(ns vtranslate.fetch.calc.ytdlp
  "How to ask yt-dlp for a file, and how to read what it answers. Pure — no
   process, no filesystem.
     command    - opts -> argv vector
     interpret  - a finished invocation -> Result<{:local-path .. :title ..}>"
  (:require [clojure.string :as str]
            [hive-dsl.result :as r]))

(def default-format
  "Best video+audio muxed, falling back to the best single stream."
  "bv*+ba/b")

(def default-binary "yt-dlp")

(def default-output-template "%(id)s.%(ext)s")

(def ^:private print-template
  "One tab-separated line per item, evaluated AFTER the file has landed so
   filepath names the finished file rather than a part-file."
  "after_move:%(filepath)s\t%(title)s\t%(duration)s\t%(ext)s")

(defn command
  "The argv that fetches `uri` into `dir`. `--print` implies quiet, so stdout
   carries the template and nothing else.

   opts: :binary :dir :format :output-template :max-bytes :socket-timeout-s
         :retries :cookies-from-browser :extra-args"
  [uri {:keys [binary dir format output-template max-bytes socket-timeout-s
               retries cookies-from-browser extra-args]
        :or   {output-template default-output-template}}]
  (into []
        cat
        [[(or (not-empty binary) default-binary)
          "--ignore-config"
          "--no-playlist"
          "--no-progress"
          "--no-simulate"
          "--socket-timeout" (str (or socket-timeout-s 30))
          "--retries" (str (or retries 3))
          "--fragment-retries" (str (or retries 3))
          "-f" (or (not-empty format) default-format)
          "-o" (str (or (not-empty dir) ".") "/"
                    (or (not-empty output-template) default-output-template))
          "--print" print-template]
         (when (pos? (long (or max-bytes 0)))
           ["--max-filesize" (str max-bytes)])
         (when (not-empty cookies-from-browser)
           ["--cookies-from-browser" cookies-from-browser])
         (or extra-args [])
         ["--" uri]]))

(defn probe-command
  "The argv that asks how long `uri` is without downloading it.
   opts: :binary :socket-timeout-s"
  [uri {:keys [binary socket-timeout-s]}]
  [(or (not-empty binary) default-binary)
   "--ignore-config"
   "--no-playlist"
   "--no-progress"
   "--simulate"
   "--skip-download"
   "--socket-timeout" (str (or socket-timeout-s 15))
   "--retries" "1"
   "--print" "%(duration)s"
   "--" uri])

(defn- parse-duration [s]
  (when-let [n (some-> s str/trim not-empty)]
    (try (Double/parseDouble n) (catch Exception _ nil))))

(defn parse-line
  "The last tab-separated record yt-dlp printed. => map | nil.
   yt-dlp may emit several lines for one URL; the LAST one that parses wins.
   => {:local-path .. :title .. :extension .. :duration-seconds ..}"
  [out]
  (some->> (str/split-lines (str out))
           (keep (fn [line]
                   ;; fields are trimmed individually — trimming the LINE would
                   ;; shift every column left when the first field is empty
                   (let [[path title duration ext]
                         (map str/trim (str/split line #"\t" -1))]
                     (when (not-empty path)
                       (cond-> {:local-path path}
                         (not-empty title) (assoc :title title)
                         (not-empty ext)   (assoc :extension ext)
                         (parse-duration duration)
                         (assoc :duration-seconds (parse-duration duration)))))))
           last))

(defn- stderr-tail
  "The last few stderr lines, for an error a human has to act on."
  [err n]
  (some->> (str/split-lines (str err))
           (remove str/blank?)
           (take-last n)
           (str/join "\n")
           not-empty))

(defn interpret
  "A finished yt-dlp invocation becomes a Result. A printed after_move path
   wins over a non-zero exit: yt-dlp keeps the exit code of a stream-level
   error even when a fallback format landed the file. The adapter re-checks
   the path on disk. => (r/ok {:local-path .. :title .. :extension ..
                               :duration-seconds ..})
    | (r/err :error/fetch-timeout|:error/fetch-failed|:error/fetch-no-output {...})"
  [uri {:keys [exit out err timed-out?]}]
  (let [parsed (parse-line out)]
    (cond
      timed-out?
      (r/err :error/fetch-timeout {:source uri :message (stderr-tail err 5)})

      parsed
      (r/ok (cond-> parsed
              (not (zero? (long (or exit 0)))) (assoc :warning (stderr-tail err 5))))

      (zero? (long (or exit -1)))
      (r/err :error/fetch-no-output
             {:source uri
              :reason "yt-dlp reported success but printed no destination path"
              :message (stderr-tail err 5)})

      :else
      (r/err :error/fetch-failed
             {:source uri :exit exit :message (stderr-tail err 5)}))))

(defn interpret-probe
  "A finished probe becomes a duration, or an error the caller may ignore.
   => (r/ok {:duration-seconds n})
    | (r/err :error/probe-timeout|:error/probe-failed|:error/probe-no-duration)"
  [{:keys [exit out timed-out?]}]
  (let [seconds (some->> (str/split-lines (str out))
                         (remove str/blank?)
                         last
                         parse-duration)]
    (cond
      timed-out?                       (r/err :error/probe-timeout {})
      ;; pos? is already false for NaN on every host, so no explicit NaN test
      (and seconds (pos? seconds))     (r/ok {:duration-seconds seconds})
      (not (zero? (long (or exit -1)))) (r/err :error/probe-failed {:exit exit})
      :else                            (r/err :error/probe-no-duration {}))))
