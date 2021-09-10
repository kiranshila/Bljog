(ns main.routes
  (:require
   [main.views.pages :as pages]
   [re-frame.core :as rfc]
   [reitit.frontend :as rf]
   [reitit.coercion.spec :as rss]
   [reitit.frontend.easy :as rfe]
   [main.events :as events]
   [main.subs :as subs]))

(defn request-missing-page [page]
  (when-not @(rfc/subscribe [::subs/page-body page])
    (rfc/dispatch [::events/request-page page])))

(def routes
  ["/"
   [""
    {:name      ::home
     :view      pages/home
     :link-text "Home"}]
   ["blog"
    [""
     {:name      ::blog
      :view      pages/blog
      :link-text "Blog"}]
    ["/:post"
     {:name      ::blog-post
      :view      pages/blog-post
      :link-text "Blog Post"
      :controllers [{:parameters {:path [:post]}
                     :start (fn [parameters]
                              (let [post (:post (:path parameters))]
                                (rfc/dispatch [::events/set-active-post post])
                                (when-not (:body (@(rfc/subscribe [::subs/posts]) post))
                                  (rfc/dispatch [::events/request-post post]))))}]}]]
   ["publications"
    {:name      ::publications
     :view      pages/publications
     :link-text "Publications"
     :controllers [{:start #(do
                              (request-missing-page "publications.md")
                              (when-not @(rfc/subscribe [::subs/publications])
                                (rfc/dispatch [::events/request-edn "publications.edn"])))}]}]
   ["hire"
    {:name      ::hire
     :view      pages/hire
     :link-text "Hire"
     :controllers [{:start (partial request-missing-page "hire-me.md")}]}]
   ["about"
    {:name      ::about
     :view      pages/about
     :link-text "About"

     :controllers [{:start (partial request-missing-page "about.md")}]}]
   ["tags/:tag"
    {:name      ::tags
     :view      pages/tags
     :link-text "Tags"}]])

(defn on-navigate [new-match]
  (when new-match
    (rfc/dispatch [::events/navigate new-match])))

(def router
  (rf/router
   routes
   {:data {:coercion rss/coercion}}))

(defn init-routes! []
  (rfe/start!
   router
   on-navigate
   {:use-fragment false}))
