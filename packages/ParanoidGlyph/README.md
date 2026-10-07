# Paranoid Glyph

Paranoid Glyph is the Settings app for devices with a Glyph LED interface. It
lets you turn the Glyph lights on or off, set their brightness, and configure
what each pattern does.

## What it supports

| Device | Codename | Module |
|---|---|---|
| Phone (1) | Spacewar | `ParanoidGlyphPhone1` |
| Phone (2) | Pong | `ParanoidGlyphPhone2` |
| Phone (3a), Phone (3a) Pro | asteroids | `ParanoidGlyphPhone3a` |

## Setup

To build the app, add the module for your device from the table above to
`PRODUCT_PACKAGES`. Example:
```make
# Paranoid Glyph
PRODUCT_PACKAGES += \
    ParanoidGlyphPhone1
```

`ParanoidGlyphPhone3a` pulls in `ParanoidGlyphPhone3aProRes`, which swaps the
preview device artwork on Phone (3a) Pro (`ro.boot.pbid=Pro`).
