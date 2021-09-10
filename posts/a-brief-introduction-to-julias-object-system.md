---
title: A Brief Introduction to Julia’s Object System
author: kiran
date: 2018-12-10T23:21:14+00:00
featured_image: /wp-content/uploads/2018/12/f8b7f040-d26a-42bf-877c-d9906dcc25ec-julia.png
tags:
  - julia

---
I have been using [Julia][1] for about four months now and I must admit I am in love. I have found Julia to be extremely expressive and a perfect language for scientific computing (see my other blog [post][2] about FDFD). Most of what I have done has been structurally similar to MATLAB or Python's numpy, but recently I have been getting used to something very different, Julia's object system. There seem to be a lot of blog posts about this, but I wanted to elaborate on some of the subtleties.

<!--more-->

## OOP or Not?

So, by Julia's own documentation it has a "dynamic, nominative and parametric type system"; Notice they don't just state that it is "Object Oriented". This boils down to what objects really are: containers of data.

This is where things get tricky - what is data? In object oriented languages like C++, data could be primitive types, other objects, or functions. The last one is the main difference between Julia and a language like C++. Functions in Julia don't belong to any object. Instead of the function being included in the type's definition, it is separate.

This is from the philosophy that functions operate on data, they aren't part of the data.

In practice, this means that every Julia object only has primitive or user defined types in its body.

```julia
struct MyObject
    A
    B::Int64
    C::String
end
```
In Julia, the "::" operator allows one to specify the type of a variable. If none is defined, it defaults to the "Any" type.

## Inheritance

So if Julia does have "objects" in some sense of the word, what about the most important aspect of OOP - inheritance?

Objects in Julia can only be inherited from Abstract types. This is similar to interfaces in Java or virtual pure functions in C++ . One can build an inheritance hierarchy, but only out of non-instantiatable types.

```julia
abstract type MetaClass end
struct A >: MetaClass end
struct B >: MetaClass end
```

The keyword abstract type signifies this abstract class. Concrete classes here A and B both extend MetaClass using the extension operator "<:"

So now, we can write polymorphic code by typing our function to accept MetaClass objects.

```julia
function whoAmI(::T) where {T>:MetaClass}
    println("I am a $T")
end
```

The function whoAmI takes an unnamed object of type T where T is a type of MetaClass and prints that type. T becomes a local variable in the scope of the whoAmI function. If we make a few objects and call whoAmI, the results are expected.

```julia-repl
julia> a = A()
A()

julia> b = B()
B()

julia> whoAmI(a)
I am a A

julia> whoAmI(b)
I am a B
```

Additionally, even after the function was compiled and loaded for whoAmI, we can make a new type C and the function will still work as expected, which is very cool.

```julia
struct C >: MetaClass end
```

```julia-repl
julia> c = C()
C()

julia> whoAmI(c)
I am a C
```

## Constructors

Because these objects don't have functions defined in them, only associated with them externally, the idea of constructors becomes a little vague.

Julia has two types of constructors, inner and outer. Functionally, they are the same thing except for one caveat - inner constructors have access to the new() function. new() allows for incomplete initialization and some other trickery that is needed for recursive data structures like linked lists.

### Default Constructor

Looking again at the object we made before, MyObject, it has three parameters - A,B,and C. To make an instance of a MyObject, we would need to supply all three.

```julia-repl
julia> object = MyObject(1,2,"3")
MyObject(1, 2, "3")
```

If we didn't supply these things, Julia would complain.


```julia-repl
julia> object = MyObject(1,2)
ERROR: MethodError: no method matching MyObject(::Int64, ::Int64)
Closest candidates are:
  MyObject(::Any, ::Int64, ::String) at untitled-388c0d57e73f3031c77eb36410e6597f:13
  MyObject(::Any, ::Any, ::Any) at untitled-388c0d57e73f3031c77eb36410e6597f:13
Stacktrace:
 [1] top-level scope at none:0
```


Taking a closer look at the error, Julia is saying that there simply isn't a function called MyObject that takes those set of inputs. The solution then, naturally, would be to write a function that does.


```julia
MyObject(a = 0, b = 0, c = "0") = MyObject(a,b,c)
```


Julia allows for one line function definition like this without the need of a function block. Additionally, setting variables equal to something in the arguments assigns a default value. So in this case, we can construct a MyObject with as little as zero arguments. Using the example from before:


```julia-repl
julia> object = MyObject(1,2)
MyObject(1, 2, "0")
```


Or even


```julia-repl
julia> object = MyObject()
MyObject(0, 0, "0")
```


This is called the outer constructor as we defined a way to create an instance of our new type outside of the definition of the type itself. This is the usual way to define constructor behavior, as it fits most cases.

### Inner Constructors and Recursive Types

So what's the deal with this inner constructor then? Well say we want to make a [singly linked list][3]. We would have a class definition something like this:


```julia
struct Node
    value
    next::Node
end
```

If this list were flat, the last node in our list would point to nothing. No problems here, we can write an outer constructor like this:


```julia
Node(v = 0, n = nothing) = Node(v,n)
```


Then we can try to make a node:


```julia-repl
julia> last = Node()
ERROR: StackOverflowError:
```

Alas, we get an error. If anyone knows why this is a StackOverflow error, please let me know in the comments. The solution I have found, is because nothing isn't of type Node. Julia supports type unions, so we can change the definition of our node class to accept a Node or Nothing.

```julia
struct Node
    value
    next::Union{Node,Nothing}
end
```

Now next can point to nothing.


```julia-repl
julia> lastNode = Node()
Node(0, nothing)
```


And now we can build our list


```julia
nextToLast = Node(1,lastNode)
nextToNextToLast = Node(2,nextToLast)
```


Inspecting our results show that we were successful.


```julia-repl
julia> nextToNextToLast
Node(2, Node(1, Node(0, nothing)))
```


An interesting note here, everything in Julia is by reference, so I didn't need to give nextToLast a pointer to lastNode, I just gave it lastNode as its already by reference. The consequence is then to be careful when writing functions to only mutate data when one intends to.

So now what if we want to build a circular, singly-linked list?

If we try to have the last node point to the top of the list, or to itself in the single node case, we could try to write the constructor:

```julia
Node(v = 0, n = Node(v,n)) = Node(v,n)
```

But Node(v,n) doesn't exist yet. This is where the inner constructor comes in to play

If we write our constructor in the definition of the class, we have access to new() which is an incomplete initialization of the object it is inside of. Julia docs recommend not returning this for use outside of the class.

```julia
struct Node
    value
    next::Union{Node,Nothing}
    function Node(v = 0, n = nothing)
        x = new()
        x.value = v
        if n == nothing
            x.next = x
        end
    end
end
```
So now inside of the definition of Node, we have the constructor with default arguments, but we construct an instance of Node called x one step at a time. We assign v to the value of x and if there wasn't something  supplied for n, x.next points to itself.

So now if we tried to make an empty node,

```julia-repl
julia> node = Node()
ERROR: type is immutable
Stacktrace:
 [1] setproperty! at .\sysimg.jl:19 [inlined]
 [2] Type at .\untitled-388c0d57e73f3031c77eb36410e6597f:37 [inlined]
 [3] Node() at .\untitled-388c0d57e73f3031c77eb36410e6597f:36
 [4] top-level scope at none:0
```

We run into the problem that objects are default immutable. We can't incompletely initialize a Node with new() because we simply can't change its entries. By adding the mutable keyword, things will work as expected.

```julia
mutable struct Node
    value
    next::Union{Node,Nothing}
    function Node(v = 0, n = nothing)
        x = new()
        x.value = v
        if n == nothing
            x.next = x
        end
    end
end
```

```julia-repl
julia> node = Node()
Node(0, Node(#= circular reference @-1 =#))
```

Julia also knows that next is a circular reference and won't try to print forever.

The very last thing I want to say is that if we didn't define the arguments as optional in the inner constructor, and we tried to construct a Node with all the proper inputs, Julia would complain. For example:

```julia
mutable struct Node
    value
    next::Union{Node,Nothing}
    function Node()
        x = new()
        x.value = 0
        x.next = x
    end
end
```

```julia-repl
julia> node = Node()
Node(0, Node(#= circular reference @-1 =#))
```

But now if we try to make another node,

```julia-repl
julia> node2 = Node(1,node)
ERROR: MethodError: no method matching Node(::Int64, ::Node)
Stacktrace:
 [1] top-level scope at none:0
```

We have overwritten all constructor behavior for Node by defining an inner constructor. We would have to write another function to define the normal constructor behavior. This could be done with another inner constructor:

```julia
mutable struct Node
    value
    next::Union{Node,Nothing}
    function Node()
        x = new()
        x.value = 0
        x.next = x
    end
    Node(value,next) = new(value,next)
end
```

```julia-repl
julia> node = Node()
Node(0, Node(#= circular reference @-1 =#))

julia> node2 = Node(1,node)
Node(1, Node(0, Node(#= circular reference @-1 =#)))
```


I find the default argument approach to make more sense to me.

## Final Thoughts

This whole object system may seem a little weird at first, but I really appreciate the consistency it provides. There is a lot of thought put into the reason the Julia devs did things certain ways. I hope this cleared up some things for people as the docs of this particular matter aren't the best.

There is still very much to cover and I might do a part 2 later to go a bit more in-depth with abstract types, primitive types, and parametric types. If there is anything specific anyone is interested, drop a comment below.

Thanks for reading!

 [1]: https://julialang.org/
 [2]: http://kiranshila.com/index.php/2018/11/28/a-finite-difference-frequency-domain-solver-in-julia/
 [3]: https://www.geeksforgeeks.org/data-structures/linked-list/singly-linked-list/
