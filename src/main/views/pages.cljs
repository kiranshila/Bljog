(ns main.views.pages
  (:require
   [re-frame.core :as rf]
   [tick.locale-en-us]
   ["@dracula/dracula-ui" :as drac]
   [main.subs :as subs]
   [main.views.core :as views]))

(defn home []
  (let [top-posts (take 5 @(rf/subscribe [::subs/post-order]))
        posts @(rf/subscribe [::subs/posts])]
    [:div
     [:> drac/Heading {:size "xl" :align "center"} "Recent Posts"]
     [:> drac/Box {:width "4xl" :style {:margin "auto"}}
      (for [post top-posts]
        ^{:key post}
        [views/post-card (posts post)])]
     [:> drac/Heading {:size "xl" :align "center"} "Recent Publications"]]))

(defn blog []
  (let [post-order @(rf/subscribe [::subs/post-order])
        posts @(rf/subscribe [::subs/posts])]
    [:div
     [:> drac/Heading {:size "xl" :align "center"} "All Posts"]
     [:> drac/Box {:width "4xl" :style {:margin "auto"}}
      (for [post post-order]
        ^{:key post}
        [views/post-card (posts post)])]]))

(defn blog-post []
  [views/post-body @(rf/subscribe [::subs/active-post])])

(defn tags []
  (let [tag (:tag (:path-params @(rf/subscribe [::subs/current-route])))
        post-order @(rf/subscribe [::subs/post-order])
        posts @(rf/subscribe [::subs/posts])]
    [:div
     [:> drac/Heading {:size "xl" :align "center"} (str "Posts Tagged: " tag)]
     [:> drac/Box {:width "4xl" :style {:margin "auto"}}
      (for [post post-order
            :let [frontmatter (posts post)]
            :when (some #{tag} (:tags frontmatter))]
        ^{:key post}
        [views/post-card frontmatter])]]))

(defn publications []
  [:div
   [:> drac/Text "Here are my publications"]])

(defn hire []
  [:div
   [:> drac/Text "Pls hire me"]])

(defn about []
  [:div
   [:> drac/Text "about kiran uwu"]])
