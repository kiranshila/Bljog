---
title: My First Driver Crate in Rust
author: kiran
date: 2022-02-15T00:00:00+00:00
lastmod: 2022-03-04T00:00:00+00:00
tags:
  - rust
  - draft
---

I've been playing around with [rust](https://www.rust-lang.org/) a lot recently
have decided to use it for some university research projects. Specifically, I'm
working on a receiver for a radio telescope! It's a really fun
project, and seems like a perfect use case for a language targetting memory safety.

The main embedded aspect of this project is controlling a frequency synthesizer
IC, the [STuW81300](https://www.st.com/en/wireless-connectivity/stuw81300.html).
Before I try to make a big board with many chips on it, I made a test board
with one of these ICs and a MCU. It took me some time to find a MCU that was:

- in stock
- cheap
- has good Rust support

The last one there is tricky, because I've gone down the rabbit hole in the past
of trying to get Rust working on some ST MCU to then find out hours later that ADC
DMA isn't working yet. As far as I can tell, the SAMD stuff from Atmel seems to
have good support. If I were to guess, a good reason is that there are much
fewer chips to support over the ST line. Atmel did a good job making a limited
set of MCUs that have a ton of features (for not a bad price either).

Irregardless of MCU, however, Rust lends itself to the embedded ecosystem by
abstracting away common embedded features in traits. So, if I know the VCO IC is
an SPI device, I can write that as a general SPI library crate and use it on any
microcontroller that implements the embedded hal traits.

So that's step one. Write a driver crate for the ST VCO part. I couldn't find
any comprehensive resources on how to do this, so I'll document my process here.
Do let me know if I'm messing something up as I'm quite new to Rust.

## Setting up

I use emacs for all my development work, and as such I have the fabulous
rust-analyzer set up with `lsp-mode`. To start, I use `cargo new --lib stuw81300`.
This sets up the basic directory structure and the required files.
Next, we add the dependencies we need.

```toml
[dependencies]
embedded-hal = "0.2"
```

I'm only adding up to the minor version here as I'm trusting `embedded-hal` to
be stable enough in patch versions. Eventually we'll add the
`[dev-dependencies]` of our microcontroller for testing, but that can come
later.

We also need to add `#![no_std]` to the top of Cargo.toml to indicate this is a
`no_std` crate.

Starting with an empty `lib.rs`, we can start writing.

## SPI Interface

We'll make a new file `spi.rs` that holds all the low-ish level SPI stuff and
add `mod spi;` to `lib.rs` so it's included in the module tree.

The STuW81300 has seven connections to a microcontroller. Four of these are the standard
MOSI/MISO/SCK/LE SPI pins and three are GPIOs that control power states. Really
these should be optional, as the end user (myself) might tie these high or low
if I want to chip to be always on. All of these will live in a struct, with the
types of each thing being generic. Note: `embedded_hal`'s `spi` trait has the
chip select pin (LE/latch endable) separate from the SPI instance, so we'll do the same here.

```rust
pub struct STuW81300<SPI, LE, HWPD, PDRF1, PDRF2> {
    spi: SPI,
    le: LE,
    hwpd: Option<HWPD>,
    pdrf1: Option<PDRF1>,
    pdrf2: Option<PDRF2>,
}
```

At this point, the generics really don't mean anything. To actually enforce that
`spi` needs to be the right thing, we can implement the constructor `new` for
this struct given trait bounds. The trait for the SPI pins comes from the
`embedded_hal` crate and is called `Transfer`. Additionally, we'll need the
trait for the output pins like latch enable. `OutputPin` is also within
`embedded_hal`. Therefore, we'll add those requires to the top of our file.

```rust
use embedded_hal as hal;

use hal::blocking::spi::Transfer;
use hal::digital::v2::OutputPin;
```

Now, we write the simple constructor, adding the `where` block that enforces
trait bounds on the generics.

```rust
impl<SPI, LE, HWPD, PDRF1, PDRF2, E> STuW81300<SPI, LE, HWPD, PDRF1, PDRF2>
where
    SPI: Transfer<u8, Error = E>,
    LE: OutputPin<Error = E>,
    HWPD: OutputPin<Error = E>,
    PDRF1: OutputPin<Error = E>,
    PDRF2: OutputPin<Error = E>,
{
    pub fn new(
        spi: SPI,
        le: LE,
        hwpd: Option<HWPD>,
        pdrf1: Option<PDRF1>,
        pdrf2: Option<PDRF2>,
    ) -> Result<Self, E> {
        Ok(STuW81300 {
            spi,
            le,
            hwpd,
            pdrf1,
            pdrf2,
        })
    }
}
```

Now we can get into the meat of this. The datasheet for the part indicates that
the device has 12 registers. Each register has 32 bits: one for the read/write
flag, 4 address bits, and 27 data bits. We can represent the adresses in an
enum.

```rust
#[repr(u8)]
#[derive(Debug, PartialEq)]
enum Register {
    ST0,
    ST1,
    ST2,
    ST3,
    ST4,
    ST5,
    ST6,
    ST7,
    ST8,
    ST9,
    ST10,
    ST11,
}
```

By having the enum `repr`ed, we'll be able to do stuff like `let addr = Register::ST0 as u8;`, which will just adds a bit of purpose to address bits
instead of raw bytes.

We can `impl Register` to add the check for the read only addresses

```rust
impl Register {
    fn read_only(&self) -> bool {
        matches!(self, Register::ST10 | Register::ST11)
    }
}
```

We can also add an enum that represents the access mode, read or write. Instead
of using a boolean directly, this gives more semantic meaning.

```rust
#[repr(u8)]
#[derive(Debug, PartialEq)]
enum AccessMode {
    Write = 0,
    Read = 1,
}
```

We might as well set up some tests to make sure all of this does what we expect.

```rust
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn register() {
        assert_eq!(Register::ST5 as u8, 0x05);
    }

    #[test]
    fn access_mode() {
        assert_eq!(AccessMode::Write as u8, 0);
    }
}
```

And a quick run of `cargo test` shows us that we're right!

```
running 2 tests
test spi::tests::access_mode ... ok
test spi::tests::register ... ok
```

### Payload Packing

The datasheet for this part isn't great as it just contains a screenshot of
ascii art representing the timing diagram, but it gives the basic structure. We
want the 27 bits of data to be packed into a u32 with the 4 bit address and
read/write bit at the top. We have to make a decision about byte order and we'll
go with the "most common" MSB first, which means we need to pack this in a "big
endian" way. Lucky for us, new versions of Rust has `<to|from>_be_bytes` which
makes this easy, especially considering we don't know the endinaness of the
machine this will be compiled on (do we?).

```rust
fn pack(addr: Register, data: u32, mode: AccessMode) -> [u8; 4] {
    // Guard against data size and read-only registers
    assert!(data < (2_u32.pow(27)), "Data must be 27 bits");
    if mode == AccessMode::Write {
        assert!(!addr.read_only(), "Address is read only");
    }
    // data_bytes[0] contains the msb
    let mut buf = data.to_be_bytes();
    // Zeroth index gets sent first, MSB first order
    buf[0] |= ((mode as u8) << 7) | ((addr as u8) << 3);
    buf
}
```

This function is simple enough, just adding in the addr and read/write bit to
the bytes from `to_be_bytes()`.

Adding the test

```rust
    #[test]
    fn payload() {
        assert_eq!(
            pack(Register::ST5, 0x07FFFFFF, AccessMode::Read),
            [0xAF, 0xFF, 0xFF, 0xFF]
        );
    }
```

Aaannddd

```rust
running 3 tests
test spi::tests::access_mode ... ok
test spi::tests::payload ... ok
test spi::tests::register ... ok
```

It works! Now we can add the must simple `operate` fn to our impl block.

```rust
    fn operate(&mut self, addr: Register, data: u32, mode: AccessMode) -> Result<u32, E> {
        // Pack data
        let mut buf = pack(addr, data, mode);
        // Perform transaction. Do we care about timing?
        self.le.set_low()?;
        self.spi.transfer(&mut buf)?;
        self.le.set_high()?;
        // Extract data
        Ok(u32::from_be_bytes(buf))
    }
```

All this does is toggles the latch enable, transfers our packed payload and
reads back the resulting payload as a u32.

### Testing the SPI

According to the datasheet, ST11 contains the device ID, which is one of two
values. This is a perfect opprotunity to setup the test transaction to make sure
everything is doing what we expect. We can do this with the `embedded_hal_mock`
crate.

We'll add

```toml
[dev-dependencies]
embedded-hal-mock = "0.8"
```

to our Cargo.toml

and then write the test! First, we should work up our abstraction heiarchy and
write separate read and write functions, as we don't want `AccessMode` to be
public. Then we'll write a `device_id` function that reads the right register,
as we don't want `read` and `write` to be public either.

These functions are straightforward enough:

```rust
    fn read(&mut self, addr: Register) -> Result<u32, E> {
        self.operate(addr, 0, AccessMode::Read)
    }

    fn write(&mut self, addr: Register, data: u32) -> Result<(), E> {
        self.operate(addr, data, AccessMode::Write)?;
        Ok(())
    }
```

And then finally for our first public facing function

```rust
    pub fn device_id(&mut self) -> Result<u32, E> {
        self.read(Register::ST11)
    }
```
