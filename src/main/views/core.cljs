(ns main.views.core
  (:require
   ["@dracula/dracula-ui" :as drac]
   [re-frame.core :as rf]
   [tick.core :as t]
   [tick.locale-en-us]
   [main.subs :as subs]))

(def date-format (t/formatter "yyy MMM dd"))

(defn post-tags [frontmatter]
  [:> drac/Text {:size "sm" :color "blackSecondary"}
   "Tags: "  (for [tag (:tags frontmatter)]
               ^{:key tag}
               [:> drac/Anchor {:size "sm"
                                :color "cyanGreen"
                                :hoverColor "yellowPink"
                                :href (str "/tags/" tag)}
                (str tag " ")])])

(defn post-body [post]
  (let [body (:body post)]
    (when body
      [:> drac/Box {:color "black"
                    :rounded "2xl"
                    :p "md"
                    :m "md"}
       (when post
         [:div
          [:> drac/Paragraph {:lineHeight "lg"}
           [:> drac/Text {:size "sm" :color "blackSecondary"}
            "Published: "
            [:> drac/Text {:size "sm" :color "cyan"} (t/format date-format (t/date (:date post)))]]
           [:br]
           [post-tags post]]
          [:> drac/Heading {:color "purpleCyan"
                            :size "xl"
                            :align "center"}
           (:title post)]
          [:> drac/Divider]])
       body])))

(defn top-banner []
  [:div
   [:span {:style {:display "inline-block" :margin "0.5rem"}}
    [:> drac/Text {:size "lg"} "("]
    [:> drac/Anchor {:size "lg" :href "/"} @(rf/subscribe [::subs/title])]
    [:> drac/Text {:size "lg" :color "cyan"} (str " :" @(rf/subscribe [::subs/active-page]))]
    [:> drac/Text {:size "lg"} ")"]]
   [:br]
   [:> drac/Tabs {:style {:margin-top "0"}}
    (for [page (rest @(rf/subscribe [::subs/pages]))]
      ^{:key page}
      [:li {:class ["drac-tab"]}
       [:a {:class ["drac-tab-link" "drac-text"]
            :href (str "/" (name page))}
        page]])]])

(defn post-card [frontmatter]
  [:div {:style {:display "flex" :align-items "center"}}
   [:> drac/Box {:style {:margin-right "1rem"}}
    [:> drac/Text {:color "blackSecondary"}
     (t/format (t/formatter "MMM dd, yy") (t/date (:date frontmatter)))]]
   [:> drac/Box {:m "md"}
    [:> drac/Anchor {:size "lg"
                     :href (str "/blog/" (:filename frontmatter))
                     :hoverColor "yellowPink"}
     (:title frontmatter)]
    [:br]
    [post-tags frontmatter]]])

(defn root []
  [:div#root
   [top-banner]
   [:div
    (when-let [current-route @(rf/subscribe [::subs/current-route])]
      [(-> current-route :data :view)])]])
