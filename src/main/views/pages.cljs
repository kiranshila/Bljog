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
        [views/post-card (posts post)])]]))

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

(defn paper-row [[title data]]
  [:span
   [:> drac/Heading {:size "md"} title]
   (when-let [preprint (:preprint data)]
     [:> drac/Anchor {:href preprint :m "sm" :color "pinkPurple" :hoverColor "yellowPink"} "preprint"])
   (when-let [paper (:paper data)]
     [:> drac/Anchor {:href paper :m "sm" :color "pinkPurple" :hoverColor "yellowPink"} "paper"])
   (when-let [talk (:talk data)]
     [:> drac/Anchor {:href talk :m "sm" :color "pinkPurple" :hoverColor "yellowPink"} "talk"])])

(defn publications []
  (let [pubs @(rf/subscribe [::subs/publications])]
    [:> drac/Box
     @(rf/subscribe [::subs/page-body "publications.md"])
     [:> drac/Heading {:size "xl" :align "center"} "Conference Papers"]
     (for [paper (:conference pubs)]
       ^{:key (first paper)}
       (paper-row paper))
     [:> drac/Heading {:size "xl" :align "center"} "Journal Papers"]
     (for [paper (:journal pubs)]
       ^{:key (first paper)}
       (paper-row paper))]))

(defn hire []
  @(rf/subscribe [::subs/page-body "hire-me.md"]))

(defn about []
  @(rf/subscribe [::subs/page-body "about.md"]))
