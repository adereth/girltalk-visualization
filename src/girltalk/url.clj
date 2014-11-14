(ns girltalk.url
  (:require [net.cgrand.enlive-html :as html]
            [clojure.java.io :as io]
            [clojure.edn :as edn]))

(def ^:dynamic cache-path "data/cache/")

(defn cached-location [id]
  (str cache-path id))

(defn cached? [id]
  (-> id cached-location (java.io.File.) .exists))

(defn read-cache [id]
  (-> id cached-location slurp edn/read-string))

(defn read-or-generate [x f]
  (let [id (java.net.URLEncoder/encode x)]
    (if (cached? id)
      (read-cache id)
      (let [value (f x)]
        (spit (cached-location id) value)
        value))))

(defn fetch-url' [url]
  (read-or-generate url
                    #(-> % java.net.URL. html/html-resource vec)))

(def fetch-url (memoize fetch-url'))
