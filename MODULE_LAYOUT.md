# Module source layout

Main modules live directly under their category. Larger module-specific settings
and algorithms live in a lowercase module directory. Small private helpers remain
inside their owning module; do not create a file for every callback or record.

    moon/module/
      NativeModule.java
      combat/
        KillAuraModule.java
        killaura/              settings, selection, rotations, click scheduling
      player/
        ScaffoldWalkModule.java
        scaffoldwalk/          ScaffoldSettings.java
        InventoryManagerModule.java   includes private InventoryItemPolicy
        inventorymanager/      InventorySettings.java
        ChestStealerModule.java       includes settings and setting builders
        SessionInformationModule.java
      visual/
        TargetHudModule.java          settings, drawing, masks, animation, dragging
        EffectsModule.java
        effects/               EffectsSettings.java
        ClickGuiModule.java
        clickgui/              ClickGuiSettings.java
        hud/                   HudSettings.java
        glow/                  GlowSettings.java, EspSettings.java

Shared rendering, shader, font, widget and HUD geometry remain in moon/visual.
Minecraft event/configuration/framebuffer adapters remain in moon/client.
The package migration preserves setting keys and saved configuration locations.

TargetHUD layout is traced to jS/ga/gb: player targets, chat preview, minimum width
110, dynamic width font.width(name,10)+45.5, height 40, radius 8, 32px rounded face
and hat, Roboto Medium, health interpolation, effect masks. Color synchronization
uses HUD al-color-one (textColorOne), as confirmed by jP.g():ColorSetting.

Known fidelity limits: gb's encrypted animation duration, background color,
health track color and health suffix are unresolved. The source explicitly marks
300ms, destination colors and HP suffix as temporary policy. This is a working
migration with source-grounded layout, not a claim of full original equivalence.
No original obfuscated classes or native bootstrap were executed.
