(ns main.utils
  (:require
   [tick.core :as t]))

(defn parse-time-to-instant [time-str]
  (t/instant
   (try
     (t/at (t/date time-str) (t/time "00:00"))
     (catch js/Error _
       (try
         (t/date-time time-str)
         (catch js/Error _
           (t/offset-date-time time-str)))))))
