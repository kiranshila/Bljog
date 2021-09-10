---
title: Marconi.jl – A Julia Library for RF/Microwave Engineering
author: kiran
date: 2019-07-10T18:59:18+00:00
featured_image: /wp-content/uploads/2019/07/logo_full.png
tags:
  - julia

---
I have been working on a new library for a while now for RF/Microwave calculations in Julia and I got to a good stopping point to release v0.1
  
Check it out on my GitHub [here][1]

In this library, I provide functionality for some basic linear network analysis, plotting, cascading, stability calculations, and gain calculations.

<!--more-->

The Julia language itself is an up and coming scientific computing language that aims to “Walk like Python and run like C” in that it has the productivity and readability of Python or MATLAB with the performance of highly optimized C code. I now use it for all of my computing tasks and have replaced MATLAB/Python in my workflow. More about Julia is found [here](https://julialang.org/>)

I just published the v0.1 release which is the bare-bones functionality to get people up and running, but I plan to leverage the speed of Julia to provide lightning-fast calibration and eventually non-linear analysis that isn’t written in C or FORTRAN *shudders*

If anyone has any feature requests, feel free to open an issue on my GitHub.

Check out the docs [here](https://kiranshila.github.io/Marconi.jl/latest/) to see examples and use cases.

 [1]: https://github.com/kiranshila/Marconi.jl
