(ns main.events
  (:require
   [main.posts :as posts]
   [main.utils :as utils]
   [ajax.core :as ajax]
   [re-frame.core :as rf]
   [reitit.frontend.controllers :as rfc]
   [day8.re-frame.http-fx]
   [main.db :as db]))

(rf/reg-event-db
 ::initialize-db
 (fn [_ _]
   db/db))

(rf/reg-event-db
 ::set-active-post
 (fn [db [_ post-name]]
   (assoc db :active-post post-name)))

(rf/reg-event-db
 ::set-active-page
 (fn [db [_ page]]
   (assoc db :active-page page)))

(rf/reg-event-fx
 ::request-post
 (fn
   [{db :db} [_ post-name]]
   {:http-xhrio {:method          :get
                 :uri             (str "/posts/" post-name)
                 :response-format (ajax/text-response-format)
                 :on-success      [::process-post post-name]
                 :on-failure      [::process-post-error post-name]}
    :db  (assoc db :loading? true)}))

(rf/reg-event-db
 ::navigate
 (fn [db [_ new-match]]
   (let [old-match   (:current-route db)
         controllers (rfc/apply-controllers (:controllers old-match) new-match)
         route (assoc new-match :controllers controllers)]
     (-> db
         (assoc :active-page (name (get-in route [:data :name])))
         (assoc  :current-route route)))))

(rf/reg-event-db
 ::process-post
 (fn
   [db [_ post-name response]]
   (let [post (posts/parse response)]
     (-> db
         (assoc :loading? false)
         (assoc-in [:posts post-name :body] post)
         (update-in [:posts post-name  :date] utils/parse-time-to-instant)))))
