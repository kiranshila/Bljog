---
title: Embedded Rust, Radio Astronomy, and a New Driver Crate
author: kiran
draft: false
date: 2022-04-10
tags:
  - rust
  - embedded
---

It's been a while since I tried experimenting with embedded Rust. About two years
ago, I tried to do something nontrivial (DMA from an ADC) on a supposedly
well-supported STM32 chip with very little success. Both the peripheral access
crate (PAC) and hardware abstraction layer (HAL) were nowhere near production
ready.

But now it's 2022 and the Rust community has been chugging along and I was in
the mood to check in on their progress.

For context, I'm working on a radio telescope project, the Galactic Radio
Explorer ([GReX](https://arxiv.org/abs/2101.09905)). One of the fundamental
building blocks in this system is a board to filter, amplify, and downconvert
the 1.4 GHz signal from the low noise amplifier (LNA) to a power range and frequency
that best suits the analog to digital converters on the digitization board we're
using. I'm calling this device the frontend module, or FEM.
As this system will be distributed all over the world, we need to be able
to monitor and control the system with high reliability.

That's where Rust fits in. There is a microcontroller on this board that
controls the power supplies for the LNAs and digital attenuators as well as
monitoring temperature, output RF power, and voltages and currents. This code
needs to be extremely robust - which fits in nicely to Rust's compile-time
guarantees. Also, Rust is a modern language - something that the embedded scene
doesn't really see a lot of. That means there is a real package manager, nice
functional conveniences, and a plethora of nice tooling.

## Embedded HAL Magic

One of the missing pieces in the design was a Rust driver for the particular
DC power monitoring chip I wanted use, the
[PAC1944](https://www.microchip.com/en-us/product/PAC1944). I chose this chip
because it was:

1. In stock
2. Small
3. I2C
4. Covered the voltages I care about

As far as I could tell, there aren't _any_ Rust driver crates that cover a
device like this, so I thought this would be a nice opportunity to contribute to
the community.

For those who don't know, the embedded Rust community has organized a set
of generic "traits" that abstract the basic functionality of core features of
embedded systems called the [embedded
hal](https://github.com/rust-embedded/embedded-hal). The objective is that this
allows for authors to write libraries that target these traits instead of any
specific implementation to allow the drivers to be generic. This means that this
driver will work on any system that has `embedded-hal` implementations,
including most/all microcontrollers, some embedded linux platforms, other
bare-metal systems. This saves us from so much code duplication as writing code
that reads from say a temperature sensor on a raspberry pi can use the same
library for that sensor that you would use on an Arduino.

## Mapping Registers in Rust

Writing this crate was pretty straightforward, as it more or less just maps
reading/writing registers over I2C to Rust data structures. There is a fantastic
library called [packed_struct](https://github.com/hashmismatch/packed_struct.rs)
that makes translating from a datasheet's register mapping very simple.

![](/figures/ctrl.png)

For example, take the first register in the datasheet, `CTRL`. It contains a
four-bit register to select the sample mode, two sets of two bits to control the
behavior of the alert pin, and four flags to turn on and off any of the four channels.

Those settings for the sample mode and pin mode are simple enums:

```rust
#[derive(PrimitiveEnum_u8, Clone, Copy, Debug, PartialEq)]
pub enum SampleMode {
    _1024Adaptive,
    _256Adaptive,
    _64Adaptive,
    _8Adaptive,
    _1024,
    _256,
    _64,
    _8,
    SingleShot,
    SingleShot8X,
    Fast,
    Burst,
    Sleep = 0b1111,
}

#[derive(PrimitiveEnum_u8, Clone, Copy, Debug, PartialEq)]
pub enum GpioAlert {
    Alert,
    Input,
    Output,
    Slow,
}
```

and the flags to select the channel is just a struct with explicit bits set:

```rust
#[derive(PackedStruct, Default, Debug, PartialEq)]
#[packed_struct(bit_numbering = "msb0")]
pub struct Channels {
    #[packed_field(bits = "4")]
    pub _1: bool,
    pub _2: bool,
    pub _3: bool,
    pub _4: bool,
}
```

then to put this all together I just make a struct for `Ctrl` that combines
these compound types. I have to explicitly state the packed number of bytes, the
bit ordering, and positions.

```rust
#[derive(PackedStruct, Debug, PartialEq, Register)]
#[packed_struct(size_bytes = "2", bit_numbering = "lsb0")]
pub struct Ctrl {
    #[packed_field(bits = "15:12", ty = "enum")]
    pub sample_mode: SampleMode,
    #[packed_field(bits = "11:10", ty = "enum")]
    pub gpio_alert2: GpioAlert,
    #[packed_field(bits = "9:8", ty = "enum")]
    pub slow_alert1: GpioAlert,
    #[packed_field(bits = "7:4")]
    pub channel_n_off: Channels,
}
```

But that's literally all I need to do to go back and forth between a payload of
bytes to a Rust data structure! In this same file, I added a simple test case to
make sure the round-trip behavior is what is should be.

```rust
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn round_trip_ctrl() {
        let ctrl = Ctrl {
            sample_mode: SampleMode::_256,
            gpio_alert2: GpioAlert::Input,
            slow_alert1: GpioAlert::Output,
            channel_n_off: Channels {
                _1: false,
                _2: true,
                _3: false,
                _4: false,
            },
        };
        let bytes = ctrl.pack().unwrap();
        assert_eq!(ctrl, Ctrl::unpack(&bytes).unwrap());
    }
}
```

### Macros and Addresses

This library uses procedural (proc) macros extensively to generate a ton of code
for us just based on annotations of the enums and structs. I wanted to use that
same interface to allow for easy access to the registers' address in memory. To
do that, I created an enum for `Address` that contained the same name as the
register struct and mapped it to the datasheet-specified address. Then (in a
separate crate), I wrote a quick little macro `register_derive` that allows me
to `#[derive(Register)]` on the struct and get an `addr()` method for free. I
could have used a non-procedural macro for this, but I also wanted the
experience of writing a proc macro :)

```rust
extern crate proc_macro;

use proc_macro::TokenStream;
use quote::quote;
use syn::{parse_macro_input, DeriveInput};

#[proc_macro_derive(Register)]
pub fn derive_register(tokens: TokenStream) -> TokenStream {
    let input = parse_macro_input!(tokens as DeriveInput);
    let name = input.ident;

    let addr_impl = quote! {
        impl #name {
            pub(crate) fn addr() -> Address {
                Address::#name
            }
        }
    };
    TokenStream::from(addr_impl)
}
```

## High Level Interface

Now that all the registers are mapped, we need a nice user-facing interface.
Some of these are straightforward like the constructor of the instance of the
device. This includes using an enum of the resistor value for the address
selecting, so you don't need to look it up from the datasheet:

```rust
#[repr(u8)]
#[derive(Debug, Copy, Clone)]
pub enum AddrSelect {
    GND = 0b10000,
    _499 = 0b10001,
    _806 = 0b10010,
    _1270 = 0b10011,
    _2050 = 0b10100,
    _3240 = 0b10101,
    _5230 = 0b10110,
    _8450 = 0b10111,
    _13300 = 0b11000,
    _21500 = 0b11001,
    _34000 = 0b11010,
    _54900 = 0b11011,
    _88700 = 0b11100,
    _140000 = 0b11101,
    _226000 = 0b11110,
    VDD = 0b11111,
}

pub struct PAC194X<I>
where
    I: i2c::Read + i2c::Write + i2c::WriteRead,
{
    i2c: I,
    address: u8,
}

impl<E, I> PAC194X<I>
where
    I: i2c::Read<Error = E> + i2c::Write<Error = E> + i2c::WriteRead<Error = E>,
{
    pub fn new(i2c: I, addr_sel: AddrSelect) -> Self {
        Self {
            i2c,
            address: addr_sel as u8,
        }
    }
}
```

Then, many of the methods are just reading and writing those registers we
defined. That would be a ton of code that is just the same thing over and over.
Here, we can use the `macro_rules!` "macro by example" to generate all that for
us. I wrote several for reading and writing, but this was the most interesting:

```rust
macro_rules! write_fn {
    ($var:ident: $type:ty) => {
        paste! {
            #[doc = stringify!(Writes out the $type register)]
            pub fn [<write_ $var>](&mut self, $var: $type) -> Result<(), Error<E>> {
                const PACKED_SIZE_WITH_ADDR: usize = core::mem::size_of::<<$type as PackedStruct>::ByteArray>() + 1;
                let mut bytes = [0u8; PACKED_SIZE_WITH_ADDR];
                bytes[0] = $type::addr() as u8;
                $var.pack_to_slice(&mut bytes[1..]).unwrap();
                self.block_write(&bytes)?;
                Ok(())
            }
        }
    };
}
```

As the size of the particular register is known at compile time, and I need to
add the address of the register to the actual serialized payload, I can use a
const expr to parameterize the byte array. This is a little funky because I have
to cast the register type to the trait `PackedStruct` so I can extract the
associated type that has the const `core::mem::size_of` method defined. Super cool!

Lastly, the most obvious use case of this crate is to read the channel voltages,
so it makes sense to have one high level method that just does that. For this,
we need to read the register that control the range as that dictates the scaling
factors of the numbers we get and then just return it!

```rust
pub fn read_bus_voltage_n(&mut self, n: u8) -> Result<f32, Error<E>> {
    assert!((1..=4).contains(&n), "Channel n must be between 1 and 4");
    let fsr_reg = self.read_neg_pwr_fsr_lat()?;
    let fsr = match n {
        1 => fsr_reg.cfg_vb1,
        2 => fsr_reg.cfg_vb2,
        3 => fsr_reg.cfg_vb3,
        4 => fsr_reg.cfg_vb4,
        _ => unreachable!(),
    };
    Ok(vbus_to_real(self.read_vbusn(n)?.voltage, fsr))
}
```

where this function `vsense_to_real` performs that conversion based on the enum
variant of the range:

```rust
fn vsense_to_real(raw: u16, fsr: VSenseFSR) -> f32 {
    0.1 * match fsr {
        VSenseFSR::Unipolar => (raw as f32) / 65536.0,
        VSenseFSR::BipolarHV => (i16::from_ne_bytes(raw.to_le_bytes()) as f32) / 65536.0,
        VSenseFSR::BipolarLV => (i16::from_ne_bytes(raw.to_le_bytes()) as f32) / 32768.0,
    }
}
```

## Integration and Testing

So, after completing the crate [here](https://github.com/kiranshila/pac194x), I
added a ton of documentation and published to crates.io! Integrating in my
firmware for the FEM was as easy as any other driver crate. I created the
instance, attached the bus, and use it as normal. I ran into one bug where I got
the endianness wrong of a few of the fields, but they are fixed now.

## Pain Points

Overall, the embedded Rust experience has gotten a _lot_ better. The tooling has
improved, the ecosystem keeps getting bigger, and the HALs and PACs are
stabilizing. Still, it's not perfect. I ran into a few issues in the process of
all this, none of which had to do with the driver crate itself, but rather
around the implementation of this non-trivial firmware.

First, it was not obvious how to share the I2C bus between driver crates. Most
drivers (including the one we just wrote) take ownership of the bus. This means
you can't pass it back to the caller without destructing (assuming you wrote a
destructor that does that). I2C, as well as many other busses, are meant to be
shared, which is pretty orthogonal to this idiomatic usage. After
asking on the embedded Rust matrix, they recommended the [shared
bus](https://docs.rs/shared-bus/latest/shared_bus/) crate, which worked a charm.
It would be nice if this was more advertised as the solution to this problem as
it took some digging to find.

The next problem was the particular chip I'm using wasn't supported by the HAL I
wanted to use. Specifically, I grabbed the "L-Variant" of the SAMD21E17, which
the folks over at [atsamd-rs](https://github.com/atsamd-rs/atsamd) hadn't heard
of. This is just to show that even the "popular" PAC and HAL crates still have
blind spots in their device coverage. On the plus side, they wrote their crates
generically enough that it was simple to add the feature flags and `cfg` logic
to [make it work](https://github.com/atsamd-rs/atsamd/pull/583).

Finally, the story with uploading firmware is still kinda a hot mess. I have a
JLink EDU, which supports the common SWD protocol. I tried [knurling
tools's](https://knurling.ferrous-systems.com/tools/) `probe-run`, but I
couldn't for the life of me get it to work. I ended up using
[cargo-embed](https://github.com/probe-rs/cargo-embed), which works fine. It's
strange though because `probe-run` is supposed to use the `cargo-embed` in the
backend. I opened an issue, but it hasn't gained much traction.

## Closing

If you would like to check out the code for the frontend module, check it out [on
github](https://github.com/kiranshila/FEM_Firmware).
The code for the driver crate is [here](https://crates.io/crates/pac194x).
It's kinda funny because if you search for `PAC194x`, you get my crate as one of
the first few results. You're welcome Microchip.
