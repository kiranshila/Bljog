(ns main.shortcodes
  (:require-macros
   [instaparse.core :refer [defparser]])
  (:require
   [instaparse.core :as insta]))

(def grammar "shortcode = (pair <space>?)+
pair = attr <'=\"'> value <'\"'>
attr = #'\\S'+
value = #'.'+
space = ' '")

(defparser parser grammar)

(defn parse-shortcode [s]
  (insta/transform
   {:attr str,
    :pair #(vector (keyword %1) %2)
    :shortcode #(into {} %&)
    :value str}
   (parser s)))

(def figure-re #"\{\{< figure (.+) >\}\}")

;; There are more valid hugo figure shortcode options, but idc
(defn lower-figure [shortcode]
  (let [{:keys [src alt caption class height width]} (parse-shortcode shortcode)]
    [:figure (cond-> {}
               class (assoc :class class))
     [:img (cond-> {}
             src (assoc :src src)
             alt (assoc :alt alt)
             height (assoc :height height)
             width (assoc :width width))]
     (when caption [:figcaption caption])]))
