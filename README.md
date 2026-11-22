<div align="center">
<p>
    <img width="160" src="src/main/resources/assets/paradox/icons/paradox-logo.svg">
</p>

[Releases](https://github.com/swelink/Paradox/releases) |
[Issues](https://github.com/swelink/Paradox/issues)
</div>

Paradox is a free and open-source mixin-based utility client for Minecraft **26.3**, built on Fabric.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.5 or newer for Minecraft 26.3.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) in your `mods` folder.
3. Download the latest `paradox-x.y.z.jar` from [Releases](https://github.com/swelink/Paradox/releases) and drop it in `mods`.
4. Launch the game with Java 25 or newer.

Press **Right Shift** in game to open the ClickGUI.

<details>
<summary><b>Modules</b></summary>

**Combat:** AimBot, AnchorAura, AntiBot, AutoAnchor, AutoCart, AutoCrystal, AutoMace, AutoTotem, AutoTrap, Backtrack, BowBot, Criticals, CrystalAura, DiveMace, FakeLag, HoverTotem, KillAura, KnockbackDelay, PearlCatch, PearlGrappler, SafeAnchor, SafeCart, ShieldBreaker, SpearSwap, SprintReset, StickyTarget, TriggerBot, Velocity

**Movement:** AutoEly, ElytraBoost, NoJumpDelay, NoSlow, NoWeb, OmniSprint, Speed, Target Strafe

**Player:** AntiResourcePack, AutoDrain, AutoExp, AutoMLG, AutoWeb, Clutch, FastPlace, MiddleClickFriend, MultiActions, SpinBot, Timer

**Render:** Ambience, Aspect Ratio, BlockHighlight, Cape, DynamicIsland, EffectsHud, ESP, FakePlayer, Fog, Freecam, Freelook, HandShaders, HandView, InventoryHud, KeybindHud, Keystrokes, ModuleList, Nametags, Place, Radar, Rotations, Smooth Camera, SpeedGraph, SwingAnimations, TargetESP, TargetHUD, Time, Trajectories, Watermark

**Client:** ClickGUI, Config, HudEditor

</details>

## Issues

Found a bug or missing a feature? Open an issue [here](https://github.com/swelink/Paradox/issues).

## License

Paradox is licensed under the [GNU General Public License v3.0](LICENSE).

For those unfamiliar with it, here is a short summary. This is not legal advice.

*You are allowed to:*

- Use
- Share
- Modify

*If you distribute anything that uses code from Paradox:*

- **You must release its full source code under the GPL as well.** You can't use Paradox code, even partially, in a
  closed-source or obfuscated client.

## Setting up a Workspace

Paradox uses Gradle and requires JDK 25.

1. Clone the repository using `git clone https://github.com/swelink/Paradox`.
2. CD into the local repository. (`cd Paradox`)
3. Open the folder as a Gradle project in your preferred IDE.
4. Build the jar with `./gradlew build`. It ends up in `build/libs/`.
5. Run the client with `./gradlew runClient`.

## Mixins

Paradox uses [Mixin](https://github.com/SpongePowered/Mixin/wiki) to inject its code into Minecraft at runtime, so none
of Mojang's code is shipped with the client.

## Contributing

Contributions are welcome. Feel free to fork the repository, make your changes and open a pull request.
