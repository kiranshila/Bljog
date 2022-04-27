(ns main.db
  (:require-macros [main.posts :refer [frontmatters]]))

(def fronts (frontmatters))

(def db {:title "logic-memory-center"
         :pages [:home :blog :open-source :publications :hire :about]
         :active-page :home
         :post-order (map :filename fronts)
         :posts (->> fronts
                     (map (juxt :filename identity))
                     (into {}))})
