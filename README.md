# Minecraft-1.8.9-lwjgl3

A project focused on upgrading **MCP 1.8.9** to use **LWJGL 3**.

---

## Introduction

This project aims to provide a modern rendering backend for Minecraft 1.8.9 by integrating LWJGL 3.

> [!IMPORTANT]
> This project incorporates code and inspiration from:
> - [lwjgl3ify](https://github.com/GTNewHorizons/lwjgl3ify) by GTNewHorizons
> - [MCLWJGL3](https://github.com/Verschwiegener/MCLWJGL3) by Verschwiegener

---

## Lua runtime

The client includes a LuaJ 3.0.1 loader for Moon-style scripts. See
[`LUA_LOADER.md`](LUA_LOADER.md) for the supported API, packet callbacks,
sandbox rules and the `.lua` chat commands.

Scripts are loaded from `run/scripts` (or `.minecraft/scripts` in a normal
installation). `run/` is a local runtime directory and is not committed.

## Recovered visual modules

`src/moon` contains recovered Nightly visual code and adapters for the MCP
client: ModelModifier, TargetHUD, HUD watermark modes, ClickGUI styles and
the shadow/blur settings. The readable, standalone reference implementation
is distributed separately from this repository.

## Getting Started


**1. Set up the run directory**

```bash
mkdir run

# Linux / macOS — copy assets from your local Minecraft installation
cp -r ~/.minecraft/assets run/

# Windows
xcopy /E /I %APPDATA%\.minecraft\assets run\assets
```

**2. Compile and launch**

```bash
mvn compile exec:exec
```

---

## Known Issues

- **Window Icon:** Currently unable to set the window icon. (Investigation in progress)

---

## Contributing

Feel free to open an issue or PR.
