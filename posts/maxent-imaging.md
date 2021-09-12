---
title: Maximum Entropy Image Reconstruction for Radio Astronomy
author: kiran
draft: true
date: 2021-05-01
tags:
  - julia
  - school
---

# Introduction

In radio astronomy imaging, we commonly want to image points in the sky with
less than an arcsecond of resolution (that's 1/3600 of a degree). With modern
systems like the Event Horizon Telescope, imaging resolution can be under 10 $`\mu\text{as}`$. To accomplish
this, we would need to create a telescope whose
electrical size (physical size over wavelength) is inversely proportional to the
resolution. At microwave frequencies, this theoretical telescope would be by far
the largest human-made object. As science usually isn't high up in the
priorities of government, an alternative solution is required.

The way around this problem is to split a huge telescope into an array of many smaller
telescopes. However, given some physical antenna size we are trying to
reproduce, we usually sparsely fill that region. That is, if the one giant
antenna was a mirror, we break that mirror into shards and only use a small
subset of those shards. You might now start to see the problem.

If someone were to remember one thing about radio interferometry, imaging with an
array, it is that a radio interferometer operates by taking a Fourier
transform of the sky. If there is some frequency dependent image of the sky
$`I_{\nu}`$, a radio telescope measures "complex visibilities" $`V`$. This relation is
given by the van Cittert-Zernike Theorem:

```latex
\begin{equation}
 V \approx \iint_{\infty}^{\infty}I_{\nu}e^{-2i\pi\left(ul + vm\right)}dldm = \mathcal{F}\left(I_{\nu}\left(l,m\right)\right)
\end{equation}
```

This is a pretty interesting equation. Conceptually, a radio telescope is
measuring a noise signal. As there exists orders of magnitude more thermal and
environmental noise than the actual source of interest, we must isolate the
signal of interest. To do so, we can take a pair of antennas (called a
baseline), and take the time-averaged correlation of the signal.
In statistical optics, this is the mutual spatial coherence
function. As the radiation pattern of a pair of antennas casts an
interference pattern on the sky (like a double slit), this correlation product can be thought of as
sampling a single spatial frequency of the true image of the sky. The proof of
this is a little more involved, and isn't worth discussing here. Given that an
array of antennas is finite, the baselines formed by pairs of antennas dictate
the finite spatial frequencies the array is sensitive to. Earth rotation
provides additional baselines, as the projected distance between antennas
changes from the perspective of a source as the earth rotates. Yet, the true
image of the sky is a continuous function, implying a continuous set of spatial
frequencies. This is the heart of the so called "imaging problem" in radio
interferometry. The best we can do is sample a limited subset of the spatial
frequencies of the true image of the sky.

The question then becomes, how do we make an image with incomplete information?
This is in essence the same as any compressive sensing problem. How do we fill
in missing information using the data we have in a statistically meaningful way?
There are numerous methods, but an increasingly popular technique is the one of
maximum entropy. In this report, I will discuss the formulation and motivation of MEM (Maximum
entropy methods), implementation details in the Julia programming language, and
showcase some results.

# Methods

## Motivation

One of the first methods of image reconstruction in radio astronomy was an
iterative technique called CLEAN. In this algorithm, the image of
the sky is assumed to be a collection of point sources. Then, the algorithm
finds the point of highest value and removes a small portion of it, convolved
with the point-spread function of the array, known as the dirty beam. While
quite simple, this algorithm has been the backbone of radio interferometric
imaging for almost 50 years. There are quite a few downsides, however. The first
of which is a computational problem. The sampled visibilities lie on an
irregular grid. The u-v (baseline) coordinates are whatever they happened to be
at the time of measurement. As the image is an inverse Fourier transform of
these components, we need to place them on a grid in order to use efficient
algorithms such as the Fast Fourier Transform. This is a nontrivial task, and
there are many solutions. One of which is to "blur" the visibilities onto a grid
through a convolution. This is the most popular approach, with many recent
improvements in performance. An
example implementation is provided in Julia (outperforming CyGrid in under 30
lines of code). A consequence of this gridding is that any noise in the data has
now been "smeared" across the image. This makes understanding the image
statistics very difficult as the correlation between pixel noise has been
increased. Also, the actual deconvolution process of CLEAN introduces further
confusion into the statistics of the image. In fact, the artifacts introduced
by CLEAN are not able to be well described.

{{< figure src="/figures/grided_visibilites.svg" caption="Gridded ALMA
Visibilites" >}}

## MEM Methods

An alternative approach is to tackle the problem from a primarily statistical
perspective. Just as we can use image statistics to help regularize the inverse
problem in deblurring of conventional images, we can formulate a
statistics-motivated optimization problem for astronomical images.

The first main difference, however, is that the image statistics for astronomical
sources are very different from conventional images, and can vary wildly from
source to source. Therefore, to formulate a "general purpose" algorithm, we must
consider all possible image statistics. One universal constraint in these
images, however, is that of positivity. No pixel will have negative brightness.
This then fits perfectly into scope of maximum entropy, introduced by E.T.
Jaynes in 1957. Simply, the distribution
of maximum entropy is the one which makes the fewest assumptions about the data
in question. To solve for this, a meta-statistic of the "entropy" of a
distribution is used to constrain the probability density function, for which
the entropy is maximized. The choice of this meta-statistic is then an important
feature, as different choices of entropy formulate different distributions.

Two popular forms of entropy are

```latex
S(I) = \log\left(I\right)
```

and

```latex
S(I) = -I\log\left(I\right)
```

The latter of which has is proven from an information theory perspective by
Brookes (1959) with the former coming from
thermodynamic entropy Kikuchi (1977).
The resulting probability of a given pixel is then

```latex
P(I) \propto e^{S(I)}.
```

Including a "prior image"and considering all pixels results in a compact
metric for the "entropy" of the entire image.

```latex
S = -\sum_{i = 1}^{n} I_{i}\log\left(\frac{I_{i}}{P_{i}}\right)
```

Therefore, we can use this metric of entropy to help regularize our inverse
problem, as we want to maximize the likelihood of an image. In practical ML
problems, we normally work in the log-space of probability. This then transforms
the ML problem into a simple maximization of entropy.
Our main constraint, however, is that the image we synthesize _must_ match the
frequency components we have physically measured. As in, if one were to sparsely
sample the frequency components of the model image at the same locations of the
measurement, those "model visibilities" should match the data, otherwise the
model is meaningless.

We can then neatly re-write the problem as a constrained, nonlinear optimization
problem. We can use the $`\chi^{2}`$ goodness of fit metric to constrain the complex
visibilities of the model and then regularize with the entropy. As is standard
in these types of problems, lagrange multipliers are added as hyperparameters to
each cost term to adjust the relative weight of each term.

```latex
\begin{equation}
  J(I) = \alpha \chi^{2}(I) - \beta S(I)
\end{equation}
```

Where $`\alpha`$ weighs the goodness of fit and $`\beta`$ weighs the regularization. As the
variance of the measured visibilities is a part of the data from the telescope,
we can use that in the $`\chi^{2}`$ definition.

```latex
\begin{equation}
 \chi^{2}(I) = \frac{1}{2N} \sum_{j} \frac{|V_{j} - V_{j}\prime|^{2}}{\sigma_{j}^{2}}
\end{equation}
```

Where the image goodness of fit takes the mean squared error of the model
visibilities $`V\prime`$ to the data visibilities $`V`$ weighted by the variance of
the data visibility $`\sigma^{2}`$.

## Implementation

The implementation of this problem is written in the Julia Programming Language,
a modern, performant language geared towards scientific computing. As Julia is a
"just in time" compiled language, it can easily meet the performance of C/C++
with the expressiveness of Python. One of the primary benefits of using Julia
is the powerful ecosystem of Automatic Differentiation libraries. As Julia
treats code as data (a feature known as homoiconicity), libraries can generate
Julia source code of the gradient of functions in place and pass it through
the highly optimized LLVM backend. This type of development is known as differentiable
programming and can greatly reduce the lines of code required to express
gradient-based optimization problems.

One issue in the gradient formulation of this particular problem is the
ill-posed adjoint of the nonuniform FFT (NFFT). The NFFT is used to sparsely
sample the frequency components of a given image, a process that reduces the
amount of information. Therefore, the inverse operation is at best an
approximation. The pure Julia implementation has a few issues with the
approximate adjoint, and the Python wrapper for the NFFT C library is called
through `PyCall.jl`. This is a performance bottleneck and an issue has been
raised with the `NFFT.jl` authors. Therefore, the gradient of the $`\chi^{2}`$ term
is given explicitly by

```latex
\begin{equation}
 \nabla\chi^{2} = -\frac{1}{N} \sum_{j}Re\left[g\left(\frac{V_{j}-V_{j}\prime}{\sigma^{2}_{j}}\right)\right]
\end{equation}
```

where $`G(V)`$ is the adjoint of the NFFT.

For this problem, the gradient of the regularization is calculated through the `Zygote.jl` backend,
Then, the problem is passed to the `Optim.jl` LBFGS Conjugate Gradient solver.

Due to the abstract nature of the regularization function, any pure Julia
functions can be used and will be appropriately differentiated. As such, a
simple implementation of constraining total flux of a model is added as well as
imaging in the log-space of the pixels (to avoid domain errors of the entropy
terms).

Simulated data was constructed using the `eht-imaging` package in Python and exported in HDF5
format for ease of transfer to Julia.

# Results

Three models of interferometric images were used in testing. One of SgrA, one
of M87, and one that is a collection of point sources and extended emission.
The former two of which were as included in the `eht-imaging` models repository.
The data was formed as a simulated observation from the Event Horizon Telescope,
as configured in 2017. For these particular observations, the baseline pairs
forms the following uv coverage:

As is typical, we can start with a
gaussian prior and model image, then focus of matching the visibility data.
Then, in later iterations, increase the weight of the regularization
(particularly the entropy).

TODO FIGURES

Neither look as good as the results above. Interestingly enough, the different
entropy term actually formed a valid image, then diverged into image above as
can be seen in an animation of the solution.

## Further Details

The implementation of the adjoints proved to be the most difficult part. I tried
many different ways of sampling and unsampling the visibilities. Between my
gridding utility, `NFFT.jl`, interpolating `FFTW` results, and using `PyCall.jl`
to invoke `pyNFFT`, nothing was consistent. Additionally, I tried some "real"
data from ALMA, but got really caught up in trying to read the file, as
astronomers think it's funny to use file formats from the 70s.

From a bit more interesting perspective, I tried "modern" gradient solvers, such
as ADAM (common in machine learning), and got similar results, but is much
longer time then LBFGS.

The random black pixels in the results could be helped with additional
regularization, such as Total Variation and/or L$`n`$ norms.

In systems with more data terms, such as closure amplitudes and phases from
VLBI, those would be added into the data part of the regularization as well.
