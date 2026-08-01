(ns vtranslate.fetch.addon
  "vtranslate fetch addon facade.

   Mirrors the Hive IAddon shape without making vtranslate-fetch require hive-mcp
   at compile time. When hive-mcp is on the classpath, VTranslateFetchAddon is
   extended to hive-mcp.addons.protocol/IAddon at runtime and init-as-addon!
   returns the real IAddon record; otherwise init-as-addon! performs provider
   registration only."
  (:require [vtranslate.fetch.adapters.ytdlp :as ytdlp]
            [vtranslate.fetch.providers.register :as register]))

(def ^:private addon-id "vtranslate.fetch")

(def ^:private addon-capabilities
  #{:source-fetching :url-ingress :health-reporting})

(defonce ^:private addon-instance (atom nil))

(defonce ^:private extended?* (atom false))

(defrecord VTranslateFetchAddon [state])

(defn- try-resolve [sym]
  (try
    (requiring-resolve sym)
    (catch Exception _ nil)))

(defn- addon-config [config]
  (or (:addon/config config)
      (:config config)
      config
      {}))

(defn- registration-result [config]
  (register/register-adapters! (addon-config config)))

(defn- iaddon-method-map []
  {:addon-id (fn [_this] addon-id)
   :addon-type (fn [_this] :native)
   :capabilities (fn [_this] addon-capabilities)
   :initialize! (fn [this config]
                  (let [config (addon-config (or config (:config @(:state this))))
                        registration (registration-result config)]
                    (swap! (:state this) assoc
                           :initialized? true
                           :config config
                           :registration registration)
                    {:success? true
                     :metadata {:providers [:vtranslate-fetch/yt-dlp]
                                :registration registration}}))
   :shutdown! (fn [this]
                (swap! (:state this) assoc :initialized? false)
                nil)
   :tools (fn [_this] [])
   :schema-extensions (fn [_this] {})
   :health (fn [this]
             (cond
               (not (:initialized? @(:state this)))
               {:status :down
                :details {:message "vtranslate fetch addon not initialized"}}

               (not (ytdlp/available?))
               {:status :degraded
                :details {:addon-id addon-id
                          :message "yt-dlp is not on PATH — URL ingress will fail loud"}}

               :else
               {:status :ok
                :details {:addon-id addon-id
                          :capabilities addon-capabilities}}))
   :excluded-tools (fn [_this] #{})
   :hooks (fn [this]
            {:vtranslate/source-fetcher
             (fn
               ([] (:registration @(:state this)))
               ([config] (registration-result (merge (:config @(:state this)) config))))})})

(defn- ensure-extended! []
  (or @extended?*
      (when-let [iaddon (try-resolve 'hive-mcp.addons.protocol/IAddon)]
        (extend VTranslateFetchAddon @iaddon (iaddon-method-map))
        (reset! extended?* true)
        true)))

(defn- make-addon [config]
  (when (ensure-extended!)
    (->VTranslateFetchAddon (atom {:initialized? false
                                   :config (addon-config config)}))))

(defn init-as-addon!
  ([] (init-as-addon! {}))
  ([config]
   (if-let [addon (make-addon config)]
     (do
       (reset! addon-instance addon)
       addon)
     (registration-result config))))

(defn get-addon-instance []
  @addon-instance)

(defn register-adapters!
  ([] (register-adapters! {}))
  ([config]
   (registration-result config)))
