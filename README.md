# Bljog

This repo contains the source code for my blog, Logic Memory Center. It is
accessible at blog.kiranshila.com

While certain things are tuned to my liking, the code base is quite simple to modify and
is "static enough" to run on GitHub Pages, as this is.

Feel free to fork and customize to your liking.

## Building

The posts are as a git submodule to a repo that just contains the markdown. You
could simply change this in a fork to point to your own posts.

To update

```bash
git submodule update --remote --recursive
```

Simply

```bash
yarn install
yarn shadow-cljs release app
```
