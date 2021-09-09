(ns main.core
  (:require
   [reagent.dom :as rdom]
   [re-frame.core :as rf]
   [main.views.core :as views]
   [main.routes :as routes]
   [main.events :as events]
   [main.config :as config]))

(defn dev-setup []
  (when config/debug?
    (println "Development mode enabled")))

(defn ^:dev/after-load mount-root []
  (let [root-el (.getElementById js/document "app")]
    (rdom/unmount-component-at-node root-el)
    (rdom/render [views/root] root-el)))

(defn init []
  (rf/clear-subscription-cache!)
  (rf/dispatch-sync [::events/initialize-db])
  (dev-setup)
  (routes/init-routes!)
  (mount-root))

(init)
