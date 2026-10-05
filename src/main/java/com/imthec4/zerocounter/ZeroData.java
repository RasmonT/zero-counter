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

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

/**
 * Everything stored for one character, serialised as
 * ~/.runelite/plugin-data/zero-counter/&lt;accountHash&gt;.json. Nothing in here identifies the
 * player beyond the account hash that names the file.
 */
class ZeroData
{
	static final int VERSION = 1;

	int version = VERSION;
	Count total;
	Today today;
	/** Longest run of zeros in a row since the total was last reset. */
	long bestStreak;
	/** Zeros and attacks per day (yyyy-MM-dd), kept for the side panel; resets never touch it. */
	Map<String, Day> days;

	static class Day
	{
		long zeros;
		long attacks;
	}

	/** Zeros among all attacks; hits = attacks - zeros. */
	static class Count
	{
		long zeros;
		long attacks;
		String since;

		void zero()
		{
			zeros++;
			attacks++;
		}

		void hit()
		{
			attacks++;
		}

		/** A counted zero turned out to be a hit: the attack stays, the zero goes. */
		void zeroWasHit()
		{
			if (zeros > 0)
			{
				zeros--;
			}
		}

		long hits()
		{
			return attacks - zeros;
		}

		void reset()
		{
			zeros = 0;
			attacks = 0;
			since = now();
		}
	}

	static class Today extends Count
	{
		String date;
	}

	static ZeroData fresh()
	{
		return new ZeroData().normalize();
	}

	/** Fills anything a missing, older or hand-edited file left out. */
	ZeroData normalize()
	{
		version = VERSION;
		if (total == null)
		{
			total = new Count();
		}
		if (total.since == null)
		{
			total.since = now();
		}
		if (today == null)
		{
			today = new Today();
		}
		days = days == null ? new TreeMap<>() : new TreeMap<>(days);
		days.values().removeIf(d -> d == null || d.attacks <= 0);
		for (Day d : days.values())
		{
			d.zeros = Math.max(0, Math.min(d.zeros, d.attacks));
		}
		if (days.isEmpty() && today.date != null && today.attacks > 0)
		{
			// Files from before the per-day record: start it with today's counts
			Day d = new Day();
			d.zeros = today.zeros;
			d.attacks = today.attacks;
			days.put(today.date, d);
		}
		total.zeros = Math.max(0, Math.min(total.zeros, total.attacks));
		today.zeros = Math.max(0, Math.min(today.zeros, today.attacks));
		bestStreak = Math.max(0, bestStreak);
		return this;
	}

	/** Starts a new day if the stored one is not today. Returns true if it changed. */
	boolean rollDay(String date)
	{
		if (date.equals(today.date))
		{
			return false;
		}
		today.date = date;
		today.zeros = 0;
		today.attacks = 0;
		today.since = now();
		return true;
	}

	/** Today's entry in {@link #days}; null until the first {@link #rollDay}. */
	Day day()
	{
		return today.date == null ? null : days.computeIfAbsent(today.date, k -> new Day());
	}

	static String now()
	{
		return Instant.now().toString();
	}
}
