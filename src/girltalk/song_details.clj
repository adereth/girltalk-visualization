(ns girltalk.song-details
  (:require [net.cgrand.enlive-html :as html]
            [clojure.pprint :refer :all]
            [girltalk.tracklist :as tl]
            [girltalk.url :refer [fetch-url]]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [cheshire.core :as json]))

(defn image-link [wikipage]
  (-> wikipage
      (html/select [:table.infobox])
      (html/select [:img])
      first :attrs :src
      (#(if % (str "http:" %)))))

(defn album-link [wikipage]
  (let [album-tr (->> (html/select wikipage [:tr.description])
                      (filter (fn [tr] (-> tr (html/select [:th.description])
                                           first :content first (= "from the album ")))))
        relative-link (-> (html/select album-tr [:a]) first :attrs :href)]
    (if  relative-link (str "http://en.wikipedia.org" relative-link))))

(defn year [wikipage]
  (let [trc (-> wikipage
                (html/select [:table.infobox])
                (html/select [:tr]))
        released-tr (->> trc
                         (filter #(->  % (html/select [:th]) first :content first (= "Released")))
                         first)
        released-content (-> released-tr (html/select [:td]) first :content first)
        released-string (if (string? released-content)
                          released-content
                          (-> released-content (html/select [:a]) first :content first))
        year (if (string? released-string)
               (re-find #"\d\d\d\d" released-string))]
    year))

(defn genre [wikipage]
  (->> (html/select wikipage [:tr])
       (filter (fn [tr] (-> tr (html/select [:a]) first :content first (= "Genre"))))
       (map (fn [tr]
              (map (comp :title :attrs)
                   (-> tr (html/select [:td]) first (html/select [:a])))))
       flatten))

(defn song-details [song-wikipage]
  {:single-year (year song-wikipage)
   :image-link (image-link song-wikipage)
   :single-genre (genre song-wikipage)
   :album-link (album-link song-wikipage)})

(defn album-title [wikipage]
  (-> (html/select wikipage [:th.summary.album])
      first :content first))

(defn album-details [album-wikipage]
  {:album-title (album-title album-wikipage)
   :album-year (year album-wikipage)
   :album-genre (genre album-wikipage)
   :album-image-link (image-link album-wikipage)})

(defn song-details-from-wikipedia [parsed-details]
  (if-let [song-link (:song-link parsed-details)]
    (song-details (fetch-url song-link))))

(defn album-details-from-wikipedia [parsed-details]
  (if-let [album-link (:album-link parsed-details)]
    (album-details (fetch-url album-link))))

(defn dump-album-links!
  "Creates a dump of all the album links that are known.  I then manually add
album links for the tracks that couldn't be done automatically.  If the album
can't be found, I add a :year key.  The manually completed file has .generated
removed from the name."
  [album]
  (spit (str album "AlbumLinks.generated.edn")
        (with-out-str
          (->> "AllDay"
               tl/album
               (map #(merge % (song-details-from-wikipedia %)))
               (map #(select-keys % [:artist :song :album-link] ))
               (sort-by :album-link)
               pprint))))

(defn get-album-link-year-lookup-map
  [album]
  (let [link-cache (edn/read-string (slurp (str album "AlbumLinks.edn")))]
    (zipmap (map #(select-keys % [:artist :song]) link-cache)
            (map #(select-keys % [:album-link :year]) link-cache))))

(defn year-int [year]
  (cond (string? year) (Integer/parseInt year)
        (integer? year) year
        :else 9999))

(defn earliest-year [song-details]
  (min (year-int (get song-details :album-year))
       (year-int (get song-details :single-year))
       (year-int (get song-details :year))))

(defn plain-textify [parts]  
  (str/join (map #(if (string? %) % (second %)) parts)))

(defn itunes-link [parsed-details]
  (let [search-term (java.net.URLEncoder/encode (str (plain-textify (take 1 (:artist parsed-details))) " "
                                                     (plain-textify (take 1 (:song parsed-details)))))]
    (-> (fetch-url (str "https://itunes.apple.com/search?limit=1&term=" search-term))
        first :content first :content first
        json/decode
        (get "results")
        first
        (get "collectionViewUrl"))))

(defn add-all-details [album]
  (let [album-link-year-lookup-map (get-album-link-year-lookup-map album)]
    (->> album
         (map #(merge % (song-details-from-wikipedia %)))
         (map #(merge % (album-link-year-lookup-map (select-keys % [:artist :song]))))
         (map #(merge % (album-details-from-wikipedia %)))
         (map #(if (:song %) (assoc % :year (earliest-year %))))
         (map #(assoc %2 :index %1) (range))
         (map #(assoc % :itunes-link (itunes-link %))))))
