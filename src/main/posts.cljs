(ns main.posts
  (:require
   [main.shortcodes :as sc]
   [reagent.core :as r]
   [cybermonday.core :as cm]
   [cybermonday.utils :as cmu]
   [cybermonday.ir :as ir]
   [cybermonday.lowering :as lowering]
   ["@dracula/dracula-ui" :as drac]
   ["react-syntax-highlighter/dist/esm/styles/prism/dracula" :default code-style]
   ["react-syntax-highlighter" :as highlighter]
   [clojure.string :as str]
   ["@matejmazur/react-katex" :as TeX]
   [clojure.walk :as walk]))

(def heading-level ["xl" "lg" "md" "sm" "xs" "xs"])

(def Highlighter (.-Prism highlighter))

(defn highlight-pre [props]
  [:pre {:class (:className props)}
   (:children props)])

(defn lower-heading [[_ attrs & [body] :as node]]
  (let [[_ heading id] (re-matches #"^(.+?)(?: \{#(\S+)})?$" body)
        id (or id (cmu/gen-id node))
        level (dec (:level attrs))]
    [:> drac/Heading
     [:> drac/Anchor
      {:color "white"
       :id id
       :size (nth heading-level level)
       :class "anchor"
       :href (str "#" id)}
      heading]]))

(defn lower-inline-math [[_ _ math]]
  [:> TeX {:math math
           :class "drac-text-white"}])

(defn lower-fenced-code-block [[_ {:keys [language]} code]]
  (if (or (= language "math")
          (= language "latex")
          (= language "tex"))
    [:> TeX {:class "drac-text-white"
             :block true} code]
    [:> Highlighter {:PreTag (r/reactify-component highlight-pre)
                     :language (if (str/starts-with? language "jupyter-")
                                 (subs language (count "jupyter-"))
                                 language)
                     :wrapLongLines true
                     :class (str "language-" language)
                     :style code-style
                     :codeTagProps {:class (str "language-" language)}}
     code]))

(defn lower-link-ref [[_ {:keys [reference]} body]]
  (when reference
    [:> drac/Anchor {:color "cyan"
                     :hoverColor "yellowPink"
                     :href (:url (second reference))
                     :title (:title (second reference))}
     body]))

(defn lower-list-item [[_ _ & [body]]]
  (if (string? (body 2))
    [:li {} [:> drac/Text (body 2)]]
    [:li {} body]))

(defn lower-wrap-component [component & [attrs]]
  (fn [[_ attr-map & body]]
    (apply vector :> component (merge attr-map attrs) body)))

(def lower-fns
  {:ul    (lower-wrap-component drac/List         {:color "purple"
                                                   :variant "unordered"})
   :ol     (lower-wrap-component drac/OrderedList {:color "purple"})
   :div    (lower-wrap-component drac/Box)
   :hr     (lower-wrap-component drac/Divider     {:color "orange"})
   :p      (lower-wrap-component drac/Paragraph   {:size "md"})
   :em     (lower-wrap-component drac/Text        {:weight "semibold"})
   :strong (lower-wrap-component drac/Text        {:weight "bold"})
   :a      (lower-wrap-component drac/Anchor      {:color "cyanGreen"
                                                   :hoverColor "yellowPink"})
   :table  (lower-wrap-component drac/Table       {:color "cyan"
                                                   :style {:margin "auto"
                                                           :width "auto"}
                                                   :align "center"
                                                   :variant "striped"})
   :markdown/bullet-list-item lower-list-item
   :markdown/ordered-list-item lower-list-item
   :markdown/link-ref lower-link-ref
   :markdown/heading lower-heading
   :markdown/inline-math lower-inline-math
   :markdown/fenced-code-block lower-fenced-code-block})

(def default-attrs
  {:th {:class ["drac-text" "drac-text-white"]}
   :td {:class ["drac-text" "drac-text-white"]}})

(defn process-shortcodes [mdast]
  (walk/postwalk
   (fn [item]
     (if (cmu/hiccup? item)
       (let [[tag _ & [body]] item]
         ; Shortcodes must appear as a single string and the shortcode replaces the parent
         (if (and (not= tag :code)
                  (string? body))
           (if-let [shortcode (second (re-matches sc/figure-re body))]
             (sc/lower-figure shortcode)
             item)
           item))
       item))
   mdast))

(defn parse [md]
  (let [[_ _ body] (re-matches cm/frontmatter-re md)]
    (-> body
        ir/md-to-ir
        ; This has to happen before lowering as the react interop makes things *not quite* hiccup
        process-shortcodes
        (lowering/to-html-hiccup {:lower-fns lower-fns :default-attrs default-attrs})
        cmu/cleanup)))
