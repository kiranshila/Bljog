(ns main.posts
  (:require
   [cybermonday.core :as cm]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def posts-path "resources/public/posts")

(defn gen-frontmatters []
  (->> (for [file (rest (file-seq (io/file posts-path)))
             :when (and
                    (= "md" (last (str/split (.getName file) #".")))
                    (not (.isDirectory file)))
             :let [filename (.getName file)]]
         (assoc (cm/parse-front (slurp file)) :filename filename))
       (sort-by :date #(compare %2 %1))
       (into [])))

(defmacro frontmatters [] (gen-frontmatters))
