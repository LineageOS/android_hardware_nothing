# Paranoid Glyph

Paranoid Glyph is the Settings app for devices with a Glyph LED interface. It
lets you turn the Glyph lights on or off, set their brightness, and configure
what each pattern does.

## What it supports

| Device | Codename | Module |
|---|---|---|
| Phone (1) | Spacewar | `ParanoidGlyphPhone1` |
| Phone (2) | Pong | `ParanoidGlyphPhone2` |

## Setup

To build the app, add the module for your device from the table above to
`PRODUCT_PACKAGES`. Example:
```make
# Paranoid Glyph
PRODUCT_PACKAGES += \
    ParanoidGlyphPhone1
```
