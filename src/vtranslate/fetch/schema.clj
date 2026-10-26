(ns vtranslate.fetch.schema
  "Data crossing the yt-dlp, engine and addon boundaries.")
(def Options
  [:map [:binary {:optional true} [:maybe :string]]
   [:dir {:optional true} [:maybe :string]]
   [:format {:optional true} [:maybe :string]]
   [:output-template {:optional true} [:maybe :string]]
   [:max-bytes {:optional true} [:maybe :int]]
   [:socket-timeout-s {:optional true} [:maybe :int]]
   [:retries {:optional true} [:maybe :int]]
   [:cookies-from-browser {:optional true} [:maybe :string]]
   [:extra-args {:optional true} [:vector :string]]])
(def Command [:vector {:min 3} :string])
(def Invocation
  [:map [:exit {:optional true} [:maybe :int]]
   [:out {:optional true} [:maybe :string]]
   [:err {:optional true} [:maybe :string]]
   [:timed-out? {:optional true} :boolean]])
(def Download
  [:map [:local-path [:string {:min 1}]]
   [:title {:optional true} :string] [:extension {:optional true} :string]
   [:duration-seconds {:optional true} :double]
   [:warning {:optional true} [:maybe :string]]])
(def Probe
  [:map [:duration-seconds [:and :double [:fn pos?]]]
   [:width {:optional true} [:int {:min 1}]]
   [:height {:optional true} [:int {:min 1}]]])
(def EngineMedia
  [:map [:local-path [:string {:min 1}]] [:title {:optional true} :string]
   [:warning {:optional true} :string]
   [:metadata {:optional true} [:map [:ext {:optional true} :string]
                              [:duration-s {:optional true} :double]]]])
(def FetchError
  [:map [:error :keyword] [:source {:optional true} :string]
   [:exit {:optional true} [:maybe :int]] [:message {:optional true} [:maybe :string]]
   [:reason {:optional true} :string] [:path {:optional true} :string]])
(def FetchResult [:or [:map [:ok Download]] FetchError])
(def ProbeResult [:or [:map [:ok Probe]] FetchError])
(def Registration [:map [:providers [:map [:fetcher [:vector :keyword]]]] [:available [:map [:yt-dlp :boolean]]]])
(def AddonState [:map [:initialized? :boolean] [:config :map] [:registration {:optional true} [:map [:ok Registration]]]])
(def Health [:map [:status [:enum :ok :down :degraded]] [:details :map]])
(def Initialization [:map [:success? :boolean] [:metadata [:map [:providers [:vector :keyword]] [:registration [:map [:ok Registration]]]]]])
