---
title: Easy Lossless Trees with Nom and Rowan
author: kiran
draft: false
date: 2022-05-05
tags:
  - rust
---

In the past few months I've been idly working on a few utilities in Rust to facilitate writing tooling
for the Julia programming language. The main reason being, I don't think it
makes sense to have things like code formatters require the Julia runtime. I
want quick little CLI tools for those tasks, not giant binaries that have warmup
time.

To make any progress, I needed to write a Julia parser in Rust. Julia's syntax
is _mostly_ standard recursive descent, with a few strange gotchyas along the
way. It seems that the best approach to start is to borrow from Rust Analyzer's
wisdom and build linked trees between the concrete realization (Green Tree) and
the syntax (Red Tree). That way we can easily work with both - CST for syntax
highlighting, formatting, etc and the AST for linting, other analysis.

The Rust Analyzer team has a library
[rowan](https://github.com/rust-analyzer/rowan) that is built for this purpose.
It has a few rough edges, but seems to be ubiquitous. Every example and blog
post I have found seems to indicate that one should write a parser by hand when
working with rowan. So, I tried this along with the
[logos](https://github.com/maciejhirsz/logos) lexer by following the [fanstastic
blog posts by Luna](https://arzg.github.io/lang/). This worked, but there was so
much boilerplate and minutia that I was loosing my mind. Not only that, but
logos wasn't quite expressive enough for some of the tricky edge cases in the
language.

So instead, I chose to use the parser combinator library [nom](https://github.com/Geal/nom). Before the hand-written
parser attempt, though, I tried to use a few of the parser generators ([LALRPOP](https://github.com/lalrpop/lalrpop)
and [pest](https://pest.rs/)), but they were either not expressive enough or too
slow.

The only hiccup here is that nom seems geared towards producing typed ASTs. For
example, the function `separated_list0` returns a vector of matches that are
separated by the result of another parser. This would be perfect for something
like the contents of a vector, but this function throws out the commas, which we
need if we are building a lossless tree. The functions in nom are generic in
their return type, so we're fine to emit `GreenNode`s, we just have to be
careful not to throw anything out.

Additionally, the examples for rowan use a builder abstraction which makes sense if we
are building the tree top down. Because the nom use case is structured as the
combination of smaller parsers, we want to build bottom up.

After a bit of tinkering, I've managed to find a nice workflow that allows us to
build trees in Rowan with combinators from nom. The rest of this post is how I
did that, by building a very simple
[S-expression](https://en.wikipedia.org/wiki/S-expression) parser.

# Working with Rowan

The Green tree in Rowan is untyped. That is, the nodes of its tree are all the
same struct with a type tag and a vector of children. Rowan has it's own
internal type for this tag, `rowan::SyntaxKind`, which we need to

1. Define all our node types
2. Connect to the internal representation

Rowan settled on using `struct SyntaxKind(u16)` for it's internal type, which
means we need a way to create unique IDs for each node. This is a perfect fit
for enums, except there isn't a safe, vanilla rust way to convert back from a
`u16` to an enum variant. In fact, the official rowan example does

```rust
unsafe { std::mem::transmute::<u16, SyntaxKind>(raw.0) }
```

Following Luna's advice from the Make a Language series, we'll make use of the
[num-derive](https://crates.io/crates/num-derive) crate to avoid these.

So, we'll start by importing those macros and traits and setting up our node types

```rust
use num_derive::{FromPrimitive, ToPrimitive};
use num_traits::{FromPrimitive, ToPrimitive};

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, FromPrimitive, ToPrimitive)]
#[allow(non_camel_case_types)]
#[repr(u16)]
enum SyntaxKind {
    L_PAREN = 0, // '('
    R_PAREN,     // ')'
    ATOM,        // '+', '15'
    WHITESPACE,  // whitespaces is explicit

    // composite nodes
    LIST, // `(+ 2 3)`
    ROOT, // top-level node
}

// Bring the variants into our scope
use SyntaxKind::*;
```

We'll want to use `.into()` in certain cases, which is easy thanks to num-derive

```rust
impl From<SyntaxKind> for rowan::SyntaxKind {
    fn from(kind: SyntaxKind) -> Self {
        rowan::SyntaxKind(kind.to_u16().unwrap())
    }
}
```

Rowan's `Language` trait is how we connect the two trees together. Here, we'll
not worry about anything but mapping the `SyntaxKind` variants back and forth.
Again, this is made easy because of num-derive.

```rust
#[derive(Debug, Copy, Clone, Ord, PartialOrd, Eq, PartialEq, Hash)]
enum Lang {}

impl rowan::Language for Lang {
    type Kind = SyntaxKind;

    fn kind_from_raw(raw: rowan::SyntaxKind) -> Self::Kind {
        Self::Kind::from_u16(raw.0).unwrap()
    }

    fn kind_to_raw(kind: Self::Kind) -> rowan::SyntaxKind {
        rowan::SyntaxKind(kind.to_u16().unwrap())
    }
}

// Node type for the red tree
type SyntaxNode = rowan::SyntaxNode<Lang>;
```

# Connecting to Nom

This is where we diverge from the examples, we want to now write the parsers in
a way that builds a `rowan::GreeNode`.

We'll start with importing the things we need

```rust
use rowan::{GreenNode, GreenToken, NodeOrToken};
```

In `GreenNode::new`, the `children` can be either a `GreenToken` (terminal) or
another `GreenNode` (non-terminal). They have this exposed as `NodeOrToken`,
which we'll make a type alias for.

```rust
type Child = NodeOrToken<GreenNode, GreenToken>;
```

Nom has a ton of imports, here are all the ones we'll use

```rust
use nom::{
    branch::alt,
    bytes::complete::tag,
    character::complete::{multispace1, none_of},
    combinator::{all_consuming, map, opt, recognize},
    multi::{many0, many1},
    sequence::{pair, tuple},
};
```

Every parser will return a `Child`, so we can type alias that as well, to make
our function signatures a little cleaner.

```rust
type IResult<'a> = nom::IResult<&'a str, Child>;
```

Last little bit of boilerplate, here is a function that takes a `SyntaxKind` and
some `&str` and builds the `NodeOrToken::Token` out of it.

```rust
fn leaf(kind: SyntaxKind, input: &str) -> Child {
    NodeOrToken::Token(GreenToken::new(kind.into(), input))
}
```

## Lexing

The line between lexing and parsing is blurred in nom, which makes things a
little strange, but all of our terminals are basically lexemes. So, for each we
write a simple function that does the match and creates the leaf.

```rust
fn lparen(input: &str) -> IResult {
    map(tag("("), |s| leaf(L_PAREN, s))(input)
}

fn rparen(input: &str) -> IResult {
    map(tag(")"), |s| leaf(R_PAREN, s))(input)
}

fn whitespace(input: &str) -> IResult {
    map(multispace1, |s| leaf(WHITESPACE, s))(input)
}
```

The atomics (in this example) are anything that aren't spaces or parens. This is
of course not quite true, as there are string literals and other more
complicated things. But we'll keep it simple here

```rust
fn atom(input: &str) -> IResult {
    map(recognize(many1(none_of("() \t"))), |s| leaf(ATOM, s))(input)
}
```

## Parsing

Now the fun part, lists! S-expressions have a very simple grammar. We need to
match an open paren, then there are zero or more atoms or other lists, separated
by spaces, and then a closing paren. As discussed before, we can't use the
`separated_list0` function as we need to keep track of whitespace. We use `alt`
for the two cases of the atom or the recursive `list` and `many0` of `pair`s for
the inner body. The sticky bit here is that there is optional whitespace before
the first element and after the last element. The `opt(whitespace)` at the end
takes care of the latter, but we need to be explicit about the first optional
whitespace and list element. Nom is greedy, so this all works as you'd expect.

To then build the tree, we can simply build a children vector from the matches
of `many0` and build the `GreenNode` directly.

```rust
fn list(input: &str) -> IResult {
    map(
        tuple((
            lparen,
            opt(whitespace),
            opt(alt((list, atom))),
            many0(pair(whitespace, alt((list, atom)))),
            opt(whitespace),
            rparen,
        )),
        |(l, ws1, li1, body, ws2, r)| {
            let mut children: Vec<Child> = vec![l];
            if let Some(s) = ws1 {
                children.push(s)
            }
            if let Some(n) = li1 {
                children.push(n)
            }
            for (ws, elem) in body {
                children.push(ws);
                children.push(elem);
            }
            if let Some(s) = ws2 {
                children.push(s)
            }
            children.push(r);
            NodeOrToken::Node(GreenNode::new(LIST.into(), children))
        },
    )(input)
}
```

# Wrapping Up

Finally, we can create a top-level `parse` function that panics if we don't
consume all the input and builds the associated `SyntaxNode`.

```rust
fn parse(input: &str) -> SyntaxNode {
    let body = all_consuming(list)(input).unwrap().1;
    let cst = GreenNode::new(ROOT.into(), [body]);
    SyntaxNode::new_root(cst)
}
```

We can test this by using the `SyntaxNode` debug pretty print

```rust
println!("{:#?}",parse("defn foo (x) (+ x x))"))
```

et viola, a beautiful lossless tree!

```plain
ROOT@0..22
  LIST@0..22
    L_PAREN@0..1 "("
    ATOM@1..5 "defn"
    WHITESPACE@5..6 " "
    ATOM@6..9 "foo"
    WHITESPACE@9..10 " "
    LIST@10..13
      L_PAREN@10..11 "("
      ATOM@11..12 "x"
      R_PAREN@12..13 ")"
    WHITESPACE@13..14 " "
    LIST@14..21
      L_PAREN@14..15 "("
      ATOM@15..16 "+"
      WHITESPACE@16..17 " "
      ATOM@17..18 "x"
      WHITESPACE@18..19 " "
      ATOM@19..20 "x"
      R_PAREN@20..21 ")"
    R_PAREN@21..22 ")"
```

From here, we would add more syntax into our grammar to get more semantic
meaning as well as create some more specific atoms.

The code for all of this is in a gist
[here](https://gist.github.com/kiranshila/e2535298bd63d94fcad1d5547d691842). If
you'd like to follow along with my Julia parser, that's happening
[here](https://github.com/kiranshila/julia-syntax).

Thanks for reading!
