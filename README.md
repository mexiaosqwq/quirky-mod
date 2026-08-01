# Quirky

A small Fabric mod for Minecraft 26.2 with seven vanilla-friendly mechanics inspired by Quark.

## Mechanics

- Map preview tooltip: hovering a filled map shows a 64x64 preview of the map in the tooltip.
- Right-click harvest and replant: right-click mature crops to harvest and replant where possible, including gourds and cocoa.
- Double-door sync: opening one door opens its matching adjacent door.
- Clock and compass tooltips: clocks and compasses show time, facing, and spawn or lodestone information.
- Bottled Cloud: a consumable item that grants Slow Falling and returns a glass bottle.
- Inventory equip swap: right-click an equippable item in an inventory to equip it or swap it with worn equipment.
- Melon seed drops: eating a melon slice in survival gives one melon seed.

## Build

Requires JDK 25. Run from the repository root:

```bash
JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk \
PATH=/data/data/com.termux/files/usr/lib/jvm/java-25-openjdk/bin:$PATH \
gradle build --no-daemon --console=plain
```

The mod jar is produced at `build/libs/quirky-0.1.0.jar`.

## License

This template is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
