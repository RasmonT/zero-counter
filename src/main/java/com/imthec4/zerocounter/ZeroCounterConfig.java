/*
 * Copyright (c) 2026, ImTheC4
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.imthec4.zerocounter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(ZeroCounterConfig.GROUP)
public interface ZeroCounterConfig extends Config
{
	String GROUP = "zerocounter";

	@ConfigSection(
		name = "Streak box",
		description = "The blue zero box showing your zeros in a row",
		position = 0
	)
	String STREAK = "streak";

	@ConfigSection(
		name = "Sounds",
		description = "Sounds played when a streak of zeros reaches a number",
		position = 1
	)
	String SOUNDS = "sounds";

	@ConfigSection(
		name = "Side panel",
		description = "Streak, session and zeros by date in RuneLite's sidebar",
		position = 2
	)
	String PANEL = "panel";

	@ConfigSection(
		name = "Zero counts box",
		description = "Zeros today and in total, as a box in the game",
		position = 3
	)
	String COUNTS = "counts";

	@ConfigSection(
		name = "Accuracy box",
		description = "How many of your attacks hit, as a box in the game",
		position = 4
	)
	String ACCURACY = "accuracy";

	@ConfigItem(
		keyName = "ignoreThralls",
		name = "Ignore thralls",
		description = "Your Arceuus thrall's hits show up as your own hitsplats. With this on they are never"
			+ " counted as your attacks, so they cannot break a streak or change your accuracy",
		position = -1
	)
	default boolean ignoreThralls()
	{
		return true;
	}

	// ------------------------------------------------------------------ streak box

	@ConfigItem(
		keyName = "showStreak",
		name = "Show streak box",
		description = "A blue zero hitsplat with the number of zeros you hit in a row",
		section = STREAK,
		position = 0
	)
	default boolean showStreak()
	{
		return true;
	}

	@ConfigItem(
		keyName = "alwaysShowStreak",
		name = "Always show",
		description = "Keep the box on screen and show 0 after a hit, instead of hiding it until your next zero",
		section = STREAK,
		position = 1
	)
	default boolean alwaysShowStreak()
	{
		return false;
	}

	// ------------------------------------------------------------------ sounds

	@ConfigItem(
		keyName = "playSounds",
		name = "Play sounds",
		description = "Play a sound when your zeros in a row reach one of the numbers below",
		section = SOUNDS,
		position = 0
	)
	default boolean playSounds()
	{
		return true;
	}

	@ConfigItem(
		keyName = "soundRules",
		name = "Streak sounds",
		description = "Streak=sound, separated by commas, as many as you like. annoyed, angry and meltdown"
			+ " are .wav files in .runelite/plugin-data/zero-counter: overwrite them to change them. Add"
			+ " your own there, e.g. FML.wav, and write 15=FML. A number alone plays angry",
		section = SOUNDS,
		position = 1
	)
	default String soundRules()
	{
		return "3=annoyed, 5=angry, 10=meltdown";
	}

	@Range(min = 0, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "volume",
		name = "Volume",
		description = "Volume of the streak sounds",
		section = SOUNDS,
		position = 2
	)
	default int volume()
	{
		return 70;
	}

	// ------------------------------------------------------------------ side panel

	@ConfigItem(
		keyName = "showPanel",
		name = "Show side panel",
		description = "Zeros and accuracy per day, for any date range, without a box in the game",
		section = PANEL,
		position = 0
	)
	default boolean showPanel()
	{
		return true;
	}

	@ConfigItem(
		keyName = "dayBoundary",
		name = "New day starts at",
		description = "00:00 UTC is the Old School daily reset",
		section = PANEL,
		position = 1
	)
	default DayBoundary dayBoundary()
	{
		return DayBoundary.UTC;
	}

	// ------------------------------------------------------------------ counts

	@ConfigItem(
		keyName = "showCounts",
		name = "Show zero counts",
		description = "A box with your zeros today and in total",
		section = COUNTS,
		position = 0
	)
	default boolean showCounts()
	{
		return false;
	}

	@ConfigItem(
		keyName = "countToday",
		name = "Today",
		description = "Zeros today",
		section = COUNTS,
		position = 1
	)
	default boolean countToday()
	{
		return true;
	}

	@ConfigItem(
		keyName = "countTotal",
		name = "Total",
		description = "Zeros since the total was last reset",
		section = COUNTS,
		position = 2
	)
	default boolean countTotal()
	{
		return true;
	}

	// ------------------------------------------------------------------ accuracy

	@ConfigItem(
		keyName = "showAccuracy",
		name = "Show accuracy",
		description = "A box with how many of your attacks hit: hits / attacks and the percentage."
			+ " A special attack or scythe swing counts as one attack",
		section = ACCURACY,
		position = 0
	)
	default boolean showAccuracy()
	{
		return false;
	}

	@ConfigItem(
		keyName = "accuracySession",
		name = "Session",
		description = "Since you logged in",
		section = ACCURACY,
		position = 1
	)
	default boolean accuracySession()
	{
		return true;
	}

	@ConfigItem(
		keyName = "accuracyToday",
		name = "Today",
		description = "Today",
		section = ACCURACY,
		position = 2
	)
	default boolean accuracyToday()
	{
		return true;
	}

	@ConfigItem(
		keyName = "accuracyTotal",
		name = "Total",
		description = "Since the total was last reset",
		section = ACCURACY,
		position = 3
	)
	default boolean accuracyTotal()
	{
		return true;
	}
}
