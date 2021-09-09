(ns main.posts
  (:require
   [cybermonday.core :as cm]
   [clojure.java.io :as io]))

(def posts-path "resources/public/posts")

(defn gen-frontmatters []
  (->> (for [file (rest (file-seq (io/file posts-path)))
             :let [filename (.getName file)]]
         (assoc (cm/parse-front (slurp file)) :filename filename))
       (sort-by :date #(compare %2 %1))
       (into [])))

(defmacro frontmatters [] (gen-frontmatters))
