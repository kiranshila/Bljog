(ns main.views.pages
  (:require
   [re-frame.core :as rf]
   [tick.locale-en-us]
   ["@dracula/dracula-ui" :as drac]
   [main.subs :as subs]
   [main.views.core :as views]))

(defn blog-list [num]
  (let [post-order @(rf/subscribe [::subs/post-order])
        posts @(rf/subscribe [::subs/posts])]

    (try
      [:div
       [:> drac/Box {:width "4xl" :style {:margin "auto"}}
        (take num (for [post post-order
                        :when (not (:draft (posts post)))]
                    ^{:key post}
                    [views/post-card (posts post)]))]]
      (catch js/Exception e (println e)))))

(defn home []
  [:div
   [:> drac/Heading {:align "center"}  "Recent Blog Posts"]
   [blog-list 5]])

(defn blog []
  [:div
   [:> drac/Heading {:align "center"}  "All Blog Posts"]
   [blog-list (count @(rf/subscribe [::subs/post-order]))]])

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

(defn paper-data-link [keyword data]
  (when-let [url (keyword data)]
    [:> drac/Anchor {:href url :m "sm" :color "pinkPurple" :hoverColor "yellowPink"} (name keyword)]))

(defn paper-row [[title data]]
  [:span
   [:> drac/Heading {:size "md"} title]
   (for [kw (keys data)]
     ^{:key kw}
     (paper-data-link kw data))])

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