---
title: We Don't Need No Stinkin' Backend
author: kiran
draft: false
date: 2021-08-10
tags:
  - clojure
  - emacs
---

I liked my old blog, truly I did. But it had problems. As I wanted to include
more features, the "old school CLJS" way of depending on Node dependencies was
pretty weird. Specifically, you had to either generate externs by hand, or use
the CLJSJS repo that contained the externs.

After I finished the blog, I discovered
[shadow-cljs](https://github.com/thheller/shadow-cljs). Shadow integrates the whole
ClojureScript build process into a typical Node development process. This cleans
up a lot of the boilerplate and makes it dead simple to integrate normal npm
deps into the process.

The other big change since I wrote the old blog was in [cybermonday](https://github.com/kiranshila/cybermonday), my markdown
library. Before, it was strictly Clojure-only as the parser I was wrapper was a
JVM library. However, I discovered the JS markdown parser [remark](https://github.com/remarkjs/remark), which gives
me access to the AST. This was necessary as cybermonday really just creates a
consistent Clojure data structure which can then be transformed into anything.
This transformation target has been HTML hiccup for the time being, but I kept
wanting to transform the nodes into reagent components.

I rewrote basically all of cybermonday to have full ClojureScript support. As of
now, I think I worked out all the kinks. You can test it out on the test app [here](https://kiranshila.github.io/cybermonday-test-app/).

But the real kicker here is that I can render the markdown documents in the
frontend. I had to have a fullstack application for the old blog as the backend
was intercepting the GETs of the markdown files and passing them through
cybermonday (trying its best to cache the results). Now, I can just GET to the
static resource of the markdown file itself! In fact, the frontend can do all
the transformation, templating, and style stuff. I can easily tell it to use
react-katex to build reagent components for $`\LaTeX`$ blocks,
react-syntax-highlighter for code blocks, etc. Check out the source for that
[here](https://github.com/kiranshila/Bljog/blob/master/src/main/posts.cljs).

I'm using [Dracula UI](https://draculatheme.com/ui) for its simple, lightweight UI components - which saves me
from Bootstrap. They provide react components (even though I could just use the
CSS).

For example, to tell cybermonday to render links to Dracula UI Anchor components
with some coloring:

```clojure
(defn lower-wrap-component [component & [attrs]]
  (fn [[_ attr-map & body]]
    (apply vector :> component (merge attr-map attrs) body)))

(defn parse []
  (cybermonday.core/parse-body md {:lower-fns {:a (lower-wrap-component drac/Anchor {:color "cyanGreen"})}}))
```

I love this so much. It makes everything so much simpler.

The last glitch was getting GitHub pages to render my SPA. When the user starts
from the main page, the front end router takes over and does the right thing.
However, if you were to just navigate to one of the links on my site, GHP by
default would look for that static resource (which makes sense). I found some
[hackery](https://github.com/rafgraph/spa-github-pages) that customizes the GHP
404 page and redirects back to `index.html` with the path as query params, then
a script that runs _before_ the SPA redirects to the path. It's hacky, but works.

As a last bit of miscellanea, my old blog had several reagent atoms that stored
the app state. After a while, I got rather overwhelmed with how that state gets
updated and where everything went. I found
[re-frame](https://github.com/day8/re-frame) which provides a wonderful mental
model on managing frontend state. It comes with the benefit of making things
simple, consistent, and fast. I'll probably use it for any larger CLJS projects
from here on out.

Thanks for reading, and do let me know if things break. The source code for this
blog is [here](https://github.com/kiranshila/Bljog), so don't be afraid to open
any issues.
