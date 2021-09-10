(ns main.subs
  (:require
   [re-frame.core :as rf]))

(rf/reg-sub
 ::posts
 (fn [db _]
   (:posts db)))

(rf/reg-sub
 ::post-order
 (fn [db _]
   (:post-order db)))

(rf/reg-sub
 ::title
 (fn [db _]
   (:title db)))

(rf/reg-sub
 ::pages
 (fn [db _]
   (:pages db)))

(rf/reg-sub
 ::active-page
 (fn [db _]
   (:active-page db)))

(rf/reg-sub
 ::active-post
 (fn [db _]
   (get-in db [:posts (:active-post db)])))

(rf/reg-sub
 ::current-route
 (fn [db _]
   (:current-route db)))

(rf/reg-sub
 ::page-body
 (fn [db [_ page]]
   (get-in db [:page-body page])))

(rf/reg-sub
 ::publications
 (fn [db _]
   (:publications db)))
