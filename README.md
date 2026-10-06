# Zero Counter

Counts the zeros you hit. Every zero in a row shows up in a box with the game's blue zero
hitsplat and the streak inside it, and a sound plays when the streak reaches a number you
pick: three angry voices come with the plugin, or use your own .wav files.

The side panel keeps your accuracy for the session, today and in total, your best streak, and
your zeros day by day for any date range. A special attack or a scythe swing counts as one
attack, a splashed spell counts as a zero, and burn, poison, your thrall or other players' hits
never count.

## The streak box

The box appears with your first zero and counts every zero in a row. It disappears after an
attack that does damage. Turn on *Always show* to keep it on screen; it then shows 0 after a
hit. Hover it to see your best streak.

**One attack, one count.** A special attack with several hits (dragon dagger, claws) or a
scythe swing is one attack: it is a zero only if none of its hits did damage. A splashed
spell counts as a zero. Damage over time (burn, poison, venom) and other players' hits are
never counted.

**Thralls.** Your Arceuus thrall's hits look like your own in the game. With *Ignore thralls*
(on by default) they are left out, so a thrall hit never breaks your streak or changes your
accuracy. Turn it off to count them as before.

## Sounds

*Streak sounds* takes a list of `streak=sound`, separated by commas:

```
3=annoyed, 5=angry, 10=meltdown
```

- `annoyed`, `angry` and `meltdown` come with the plugin. On its first start it puts them in
  `%USERPROFILE%\.runelite\plugin-data\zero-counter\` as `annoyed.wav`, `angry.wav` and
  `meltdown.wav`. Overwrite one with your own sound (same file name) to change it; delete it
  and the original comes back on the next start.
- Your own extra sounds: put a `.wav` file in the same folder and write its name, with or
  without `.wav`. `FML.wav` there and `15=FML` in the setting plays it at 15 zeros in a row.
- A number alone, like `15`, plays `angry`.
- As many numbers as you like. Volume has its own setting.

## Side panel

The Zero Counter icon in RuneLite's sidebar shows your current streak and best streak, this
session's zeros and accuracy, and your zeros and accuracy for any date range (last 7 days, 30
days, this month, everything, or your own *From* and *To*), day by day. Nothing has to be on
the game screen for it. Resets never change this history.

## Zero counts and accuracy in the game

Two boxes for the game screen, both off by default and each with its own switches:

| Box | Shows |
| --- | --- |
| Zeros | Zeros today and in total |
| Accuracy | Hits / attacks and the percentage, for the session, today and in total |

Right-click a box to reset a period; it asks for confirmation first. The day starts at 00:00
UTC (the game's daily reset) or at local midnight.

## Data

Everything stays on your computer, one small file per character:

```
%USERPROFILE%\.runelite\plugin-data\zero-counter\<account hash>.json
```

The file is named after RuneLite's account hash and holds only the counts. The plugin makes
no network requests.

## Support

Questions, ideas or a bug? Join the [Discord](https://discord.gg/XgxjhyznbZ) or open an issue
on GitHub. The side panel has a *Join the Discord* button as well; it only opens your browser
when you click it.
