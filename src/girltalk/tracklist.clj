(ns girltalk.tracklist
  (:require [net.cgrand.enlive-html :as html]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [instaparse.core :as insta]
            [clojure.pprint :refer :all]))

(defn mm:ss->ms [mm:ss]
  (let [[mm ss] (map #(Integer/parseInt %)
                     (.split mm:ss ":"))]
    (* 1000 (+ ss (* 60 mm)))))

(def wiki-parse
  (insta/parser "
wiki-line = title-track-line | sample-track-line | <''>

title-track-line = <'!! '> track-number <'. '> track-name <' - '> track-time
track-number = #'\\d+'
track-name = #'[^-]+(?= - )'
track-time = time

sample-track-line = <'* '> start-time <' - '> end-time <' '> artist-name <' - '> sample-name
artist-name = (link | artist-plain-text)*
artist-plain-text = #'[^\\[]+(?= - )' | #'[^\\[]+(?=\\[)'

sample-name = (link | sample-plain-text)*
sample-plain-text = #'[^\\[]*'

link = <'[['> url <' | '> text <']]'>
url = #'[^|]+(?= | )'
text = #'[^]]*'

start-time = time
end-time = time
<time> = #'\\d+:\\d+'
"
                :output-format :enlive))

(defn parse-markdown [file]
  (with-open [r (io/reader file)]
    (->> r
         line-seq
         (map wiki-parse)
         (keep identity)
         vec)))

(defn links-as-vecs [link-tree]
  (vec
   (map #(if (or (= (:tag %) :sample-plain-text)
                 (= (:tag %) :artist-plain-text))
           (first (:content %))
           (->> % :content (map (comp first :content)) vec)) link-tree)))

(defn sample-track-details [parsed-line]
  {:start (-> parsed-line (html/select [:start-time]) first :content first mm:ss->ms)
   :end (-> parsed-line (html/select [:end-time]) first :content first mm:ss->ms)
   :artist (-> parsed-line (html/select [:artist-name]) first :content links-as-vecs)
   :song (-> parsed-line (html/select [:sample-name]) first :content links-as-vecs)
   :song-link (-> parsed-line (html/select [:sample-name]) (html/select [:url]) first :content first)})

(defn title-track-details [parsed-line]
  {:length (-> parsed-line (html/select [:track-time]) first :content first mm:ss->ms)
   :title (-> parsed-line (html/select [:track-name]) first :content first)
   :track-number (-> parsed-line (html/select [:track-number]) first :content first Integer/parseInt)})

(defn line->details [parsed-line]
  (condp = (-> parsed-line :content first :tag)
    :title-track-line (title-track-details parsed-line)
    :sample-track-line (sample-track-details parsed-line)
    nil))

(defn extract-sample-details [parsed-markdown]
  (->> parsed-markdown
       (map line->details)
       (keep identity)))

(defn samples-with-absolute-times [parsed-lines]
  (let [pairs (->> parsed-lines
                   (partition-by :track-number)
                   (partition 2))
        track-starts (->> pairs
                          (map ffirst)
                          (map :length)
                          (reductions + 0))
        samples (map second pairs)]
    (mapcat (fn [track-samples track-start]
              (->> track-samples
                   (map #(update-in % [:start] (partial + track-start)))
                   (map #(update-in % [:end] (partial + track-start)))))
            samples track-starts)))

(defn collapse-intertrack-samples [samples]
  (->> samples
       (group-by (juxt :artist :song))
       (mapcat (fn [[_ sample-group]]
                 (let [sample-group (sort-by :start sample-group)]
                   (if (and (= 2 (count sample-group))
                            (= (:end (first sample-group))
                               (:start (second sample-group))))
                     [(assoc (first sample-group)
                        :end (:end (second sample-group)))]
                     sample-group))))
       (sort-by :start)))

(defn extend-short-samples [samples]
  (map #(if (= (:start %) (:end %))
          (update-in % [:end] (partial + 1000))
          %)
       samples))

(defn album [album-name]
  (->> (str album-name ".md")
       parse-markdown
       extract-sample-details
       samples-with-absolute-times
       collapse-intertrack-samples
       extend-short-samples))


;; "* 2:37 - 4:02 [[http://en.wikipedia.org/wiki/White%20Zombie%20%28band%29 | White Zombie]] - \"[[http://en.wikipedia.org/wiki/Thunder Kiss%20'65 | Thunder Kiss '65]]\""
