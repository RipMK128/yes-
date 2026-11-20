# EclipsePowers

Eclipse powers for Paper 1.21.11. Every player rolls one power like a dice, gets one ability
from the start and a second one after 5 kills.

* Server: **Paper 1.21.11**, Java 21
* Ability 1: **offhand key** (F by default)
* Ability 2: **Shift + offhand key**, unlocked at 5 kills (a locked press only tells that player, privately)

## Build

You need JDK 21 and Maven.

```
mvn package
```

The plugin is `target/EclipsePowers.jar`. Put it in your server's `plugins` folder and restart.

No tools installed? Upload the contents of this folder (not the folder itself, `pom.xml` must be at the
top) to a GitHub repository. Open the **Actions** tab, wait for "Build EclipsePowers"
to turn green, then download the `EclipsePowers` artifact.

## How it plays

1. A player who joins without a power sees a countdown in the action bar, and **5 seconds after joining**
   (`roll-delay-seconds`) the roll starts, right in the world, no menu and nothing in your inventory.
   Eight glowing orbs, one per power, circle you and spin faster and faster, then slow down until one
   stops at the golden pointer straight ahead. The big title shows the power in front and the subtitle
   shows its neighbours, so you always see the other powers you could get. The winning orb flies into you.
   You can't be hurt while it plays.
2. `/power` opens a menu with your power, both abilities, cooldowns and your kill progress.
   The book button opens a menu of every power.
3. Everyone's power shows as an aura (a turning magic circle, orbiting orbs and a signature effect per
   power), so you can tell what someone has. Abilities, casting, kills and level-ups all have their own effects.

| Power | Ability 1 (F) | Ability 2 (Shift + F) |
|---|---|---|
| Void | Void Pull | Void Rift |
| Null | Null Pulse | Null Shield |
| Light | Light Beam | Solar Flare |
| Dark | Dark Veil | Soul Drain |
| Shadow | Shadow Step | Shadow Strike |
| Eclipse | Eclipse Flash | Totality |
| Astral | Astral Dash | Star Fall |
| Chaos | Chaos Roll | Chaos Storm |

## Damage

Ability damage ignores armor, Protection and Resistance (`true-damage: true`), so Protection 4 diamond or
netherite cannot shrink it to nothing. A hit that is cancelled (Null Shield, a no-PvP region) still does
nothing. Damage amounts are in `config.yml`, in half hearts.

Abilities only hit **other players and hostile mobs**. Villagers, pets and animals are safe. Change this
with `target-mode` (`hostile`, `players` or `all`).

## Commands

| Command | Who | What |
|---|---|---|
| `/power` | everyone | open your power menu |
| `/power list` | everyone | menu of all powers |
| `/power set <player> <power>` | admin | give a power instantly |
| `/power reroll <player>` | admin | play the roll again |
| `/power kills <player> <n>` | admin | set kill count (5 unlocks ability 2) |
| `/power reload` | admin | reload `config.yml` |

Permissions: `eclipsepowers.use` (default: everyone) and `eclipsepowers.admin` (default: op).
Take `eclipsepowers.use` away from someone and F works normally for them again.

## Config

See `plugins/EclipsePowers/config.yml`. The useful ones: `kills-to-unlock`, `count-mob-kills`,
`assign-on-join`, `roll-delay-seconds`, `target-mode`, `particle-density` (lower it if the server lags), `aura`, `aura-style` (`fancy` or `subtle`), `true-damage`
and the `damage:` section.

## If something does not work

* Build fails: send the red error from the Actions run or from `mvn package`.
* Server will not load it: send the error from the console at startup.
* An ability errors in game: the console prints `Ability <name> failed for <player>` with the details.
* Anything looks wrong on screen (flashing hotbar or inventory, missing effects): say what you see and
  which ability or menu you used.
