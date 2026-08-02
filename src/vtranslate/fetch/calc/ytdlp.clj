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

(def output-template "%(id)s.%(ext)s")

(def ^:private print-template
  "One tab-separated line per item, evaluated AFTER the file has landed so
   filepath names the finished file rather than a part-file."
  "after_move:%(filepath)s\t%(title)s\t%(duration)s\t%(ext)s")

(defn command
  "The argv that fetches `uri` into `dir`. `--print` implies quiet, so stdout
   carries the template and nothing else."
  [uri {:keys [dir format cookies-from-browser extra-args]}]
  (into []
        cat
        [["yt-dlp"
          "--no-playlist"
          "--no-progress"
          "--no-simulate"
          "-f" (or (not-empty format) default-format)
          "-o" (str (or (not-empty dir) ".") "/" output-template)
          "--print" print-template]
         (when (not-empty cookies-from-browser)
           ["--cookies-from-browser" cookies-from-browser])
         (or extra-args [])
         ["--" uri]]))

(defn- parse-duration [s]
  (when-let [n (some-> s str/trim not-empty)]
    (try (Double/parseDouble n) (catch Exception _ nil))))

(defn parse-line
  "The last tab-separated record yt-dlp printed. => map | nil.
   yt-dlp may emit several lines for one URL; the LAST completed item wins."
  [out]
  (when-let [line (some->> (str/split-lines (str out))
                           (remove str/blank?)
                           last)]
    ;; fields are trimmed individually — trimming the LINE would shift every
    ;; column left when the first field is empty
    (let [[path title duration ext] (map str/trim (str/split line #"\t" -1))]
      (when (not-empty path)
        (cond-> {:local-path path}
          (not-empty title)    (assoc :title title)
          (not-empty ext)      (assoc-in [:metadata :ext] ext)
          (parse-duration duration) (assoc-in [:metadata :duration-s]
                                              (parse-duration duration)))))))

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
   the path on disk. => (r/ok {:local-path .. :title .. :metadata ..})
    | (r/err :error/fetch-failed|:error/fetch-no-output {...})"
  [uri {:keys [exit out err]}]
  (let [parsed (parse-line out)]
    (cond
      parsed (r/ok (cond-> parsed
                     (not (zero? exit)) (assoc :warning (stderr-tail err 5))))

      (zero? exit)
      (r/err :error/fetch-no-output
             {:source uri
              :reason "yt-dlp reported success but printed no destination path"
              :message (stderr-tail err 5)})

      :else
      (r/err :error/fetch-failed
             {:source uri :exit exit :message (stderr-tail err 5)}))))
