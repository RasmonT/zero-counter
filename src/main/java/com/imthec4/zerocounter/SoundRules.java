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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Which sound plays at which streak, from a setting such as {@code 3=annoyed, 5, 15=FML}.
 * A number alone uses {@link #DEFAULT_SOUND}. A built-in name ({@link #BUILT_IN}) plays that
 * sound; any other name is a .wav file the player put in the plugin's data folder, with or
 * without the .wav ending ({@code FML} and {@code FML.wav} both mean FML.wav).
 */
final class SoundRules
{
	static final List<String> BUILT_IN = Collections.unmodifiableList(Arrays.asList("annoyed", "angry", "meltdown"));
	static final String DEFAULT_SOUND = "angry";
	static final int MAX_STREAK = 100_000;

	/** A plain file name without the .wav ending: no folders, no path tricks. */
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9 _.()!-]{1,60}");

	/** streak -> sound */
	final Map<Integer, String> rules;
	/** Entries that could not be read, as typed. */
	final List<String> errors;

	private SoundRules(Map<Integer, String> rules, List<String> errors)
	{
		this.rules = Collections.unmodifiableMap(rules);
		this.errors = Collections.unmodifiableList(errors);
	}

	static SoundRules parse(String text)
	{
		Map<Integer, String> rules = new TreeMap<>();
		List<String> errors = new ArrayList<>();
		if (text == null)
		{
			return new SoundRules(rules, errors);
		}
		for (String raw : text.split("[,;\\n]"))
		{
			String entry = raw.trim();
			if (entry.isEmpty())
			{
				continue;
			}
			int eq = entry.indexOf('=');
			String number = (eq < 0 ? entry : entry.substring(0, eq)).trim();
			String sound = eq < 0 ? DEFAULT_SOUND : entry.substring(eq + 1).trim();
			int streak;
			try
			{
				streak = Integer.parseInt(number);
			}
			catch (NumberFormatException e)
			{
				errors.add(entry);
				continue;
			}
			String name = normalize(sound);
			if (streak < 1 || streak > MAX_STREAK || name == null)
			{
				errors.add(entry);
				continue;
			}
			rules.put(streak, name);
		}
		return new SoundRules(rules, errors);
	}

	/** Built-in names in lower case; anything else as a file name ending in .wav; null if not allowed. */
	private static String normalize(String sound)
	{
		String lower = sound.toLowerCase(Locale.ROOT);
		if (BUILT_IN.contains(lower))
		{
			return lower;
		}
		String base = lower.endsWith(".wav") ? sound.substring(0, sound.length() - 4) : sound;
		if (!NAME.matcher(base).matches() || base.startsWith(".") || base.contains(".."))
		{
			return null;
		}
		return base + ".wav";
	}

	static boolean isFile(String sound)
	{
		return sound.toLowerCase(Locale.ROOT).endsWith(".wav");
	}

	String soundFor(long streak)
	{
		return streak > 0 && streak <= MAX_STREAK ? rules.get((int) streak) : null;
	}
}
