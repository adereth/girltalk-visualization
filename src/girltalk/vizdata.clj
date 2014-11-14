(ns girltalk.vizdata
  (:require [clojure.pprint :refer :all]
            [girltalk.tracklist :as tl]
            [girltalk.song-details :as sd]
            [cheshire.core :as json]))

(defn detailed-list
  [album]
  (-> album
      tl/album
      sd/add-all-details))

(defn point-in-time-samples
  [detailed-list]
  (->> detailed-list
       (mapcat #(list {:index (:index %) :time (:start %) :event :start}
                      {:index (:index %) :time (:end %) :event :end}))
       (sort-by :time)
       (reductions
        (fn [state sample-event]
          (-> state
              (assoc :time (:time sample-event))
              (update-in [:tracks]
                         #(if (= :start (:event sample-event))
                            (conj % (:index sample-event))
                            (disj % (:index sample-event))))))
        {:time 0 :tracks #{}})))

(defn slots
  [detailed-list]
  (reduce (fn [pos-map track-set]
            (let [track-set (vec track-set)
                  slots (range)
                  occupied-slots (keep pos-map track-set)
                  free-slots (filter (complement (apply hash-set occupied-slots))
                                     slots)
                  unslotted-tracks (filter (complement pos-map) track-set)
                  new-assignments (zipmap unslotted-tracks free-slots)]
              (merge pos-map new-assignments)))
          {}
          (map :tracks (point-in-time-samples detailed-list))))

(defn generate-json [album]
  (let [dt (detailed-list album)]
    (->> {:details dt
          :time-sets (point-in-time-samples dt)
          :slots (slots dt)}
         (json/generate-string)
         (spit (str "resources/" album "SamplesWithSets.json")))))
