---
title: Teaching Myself Machine Learning with Julia! – Part 2
author: Kiran Shila
date: 2020-06-17
lastmod: 2020-06-04T22:18:17-04:00
draft: true
tags:
  - julia

---
In the last post, we build up a generic abstraction for training a model given some training algorithm and a struct/functor pair that form that model. In this post, I am going to start working on building a classification model.

From last time, our model predicted a given numeric output given some single input x. Instead, let's now have our model give percent certainties that an input has some given classification.

<!--more-->

For our test data, we are going to have an input that represents a Cartesian (x,y) pair. The output will be either Red or Blue. The model we will build will try to predict this behavior, specifically try to approximate the function we will use to develop our training classification.

For this example, I am going to use sin(x) to form a boundary that separates the red and blue points.

```julia
inputs = map(tuple,rand(-π:0.001:π,1000),rand(-1:0.001:1,1000))
perfectModel((x,y)) = sin(x) > y
toColor(x) = if x return :red else return :blue end
scatter(inputs,color=@. toColor(perfectModel(inputs)))
>>--------->-----||--------<<----|----->----|
```

```clojure
 (defmacro defkey [key & body]
  `(def ~(.-name key) ~@body))

(defkey ::args
  (->> (range)
       #_(filterv even?) ;;FIXME
       (take-while #(<= % 0xFF))
       (remove #{1 2 3 4 5})
       (into [])))
```

So now, given an (x,y) point, y values higher than sin(x) are blue while those beneath are red.
  
{{< figure src="/wp-content/uploads/2020/02/Screen-Shot-2020-02-14-at-12.03.31-PM.png" >}}

As an aside, I think this is a neat showcase of Julia's broadcast feature. I am using the dot syntax to both classify each point in our training data using the ideal model and apply the color symbol to each classified point to plot correctly.

Now, assuming we are oblivious to the structure of the perfect model, we have to observe the behavior of the classification to guess a model structure. I am going to pick a cubic function as there are these two inflection points.

```julia
struct Cubic
    a
    b
    c
    d
end
(c::Cubic)(x::Real) = c.a[1]*x^3 + c.b[1]*x^2 + c.c[1]*x + c.d[1]
Cubic() = Cubic([rand()],[rand()],[rand()],[rand()])
```

So far so good, except the output of this is of course a number, we don't have a way to classify this output number directly. For this, we need to feed the output of the functor evaluation into some classifier so we can compare the output with the training data. We can simply build this model like this:

```julia
c = Cubic()
model((x,y)) = c(x) > y
```

We can take a look at our model's performance so far
```julia
scatter(inputs,color=@. toColor(model(inputs)))
```

{{< figure src="/wp-content/uploads/2020/02/Screen-Shot-2020-02-14-at-12.25.40-PM.png">}}

We have now run into a problem, however.
