# Raidborn: Join the Illagers

Join the wrong side.

Forge mod for Minecraft 1.20.1. You get to play on the illager side for once: recruit them
into a squad, settle them around a warbell, raid villages, and turn junk into artifacts at the
transmutation table.

Still 0.1, so expect rough edges. Bugs and ideas go in the
[issues](https://github.com/ChargePC/raidborn/issues).

## What you need

Required at runtime:

- BentosLib `0.1-1.20.1` (my own lib, see the build section)
- [Curios API](https://www.curseforge.com/minecraft/mc-mods/curios) `5.14.1+1.20.1`

Optional, the mod just hooks into them if present:

- [Patchouli](https://www.curseforge.com/minecraft/mc-mods/patchouli) `1.20.1-84.1` (the in-game book)
- [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) `15.20.0.106`

Java 17, Forge 47.4.9.

## Building

BentosLib comes from `mavenLocal()`, so publish it first or Gradle won't resolve it:

```bash
cd ../bentoslib && ./gradlew publishToMavenLocal
```

then

```bash
./gradlew build
```

and the jar lands in `build/libs/`.

Heads up: `gradlew` here is a bootstrap script, not the usual wrapper. It downloads Gradle 8.8
into `.gradle/wrapper/` the first time you run it, which is why there's no `gradle-wrapper.jar`
committed.

## Dev

```bash
./gradlew runClient
```

`runServer` for the other side. Both run out of `run/`, which is gitignored.

## Code layout

Everything sits under `net/randomcara/raidborn`:

- `core` - registries, config, compat checks, misc helpers
- `content` - items, entities, effects, artifacts
- `gameplay` - recruiting, settlements, attacks, banners, trading, loot
- `transmutation` - the table, its block entity, menu and recipes
- `world` - worldgen and settlement data
- `client` - renderers, models, HUD
- `integration` - JEI
- `mixin` - listed in raidborn.mixins.json

## Credits

Randomcara7 ([ChargePC](https://github.com/ChargePC)) - code, the recruitment and settlement
systems, balancing.

I_DRAW_THINGS - all the art. Item and block textures, plus the models and textures for the beast,
grumblager, iron gollet and juggernaut.

## License

All rights reserved. Full terms in [LICENSE.txt](LICENSE.txt), which also ships inside the jar.

Short version: play it, stream it, depend on it, stick it in a modpack as long as the copy came
from here and you list it. Don't reupload it, don't sell it, don't put it behind a paywall.
