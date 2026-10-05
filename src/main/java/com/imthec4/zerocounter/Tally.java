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

/**
 * Applies attacks to the session, today and total counts and to the streak of zeros in a row.
 * Kept free of RuneLite types so it can be tested on its own.
 */
class Tally
{
	final ZeroData.Count session = new ZeroData.Count();
	private ZeroData data;

	/** Zeros in a row right now. */
	private long streak;
	/** Best streak before the last zero raised it, to undo that if the zero is taken back. */
	private long bestBeforeLastZero = -1;

	Tally(ZeroData data)
	{
		this.data = data;
		session.reset();
	}

	void setData(ZeroData data)
	{
		this.data = data;
	}

	ZeroData getData()
	{
		return data;
	}

	long getStreak()
	{
		return streak;
	}

	/** @return the new streak */
	long zero()
	{
		session.zero();
		data.today.zero();
		data.total.zero();
		ZeroData.Day day = data.day();
		if (day != null)
		{
			day.zeros++;
			day.attacks++;
		}
		streak++;
		if (streak > data.bestStreak)
		{
			bestBeforeLastZero = data.bestStreak;
			data.bestStreak = streak;
		}
		else
		{
			bestBeforeLastZero = -1;
		}
		return streak;
	}

	void hit()
	{
		session.hit();
		data.today.hit();
		data.total.hit();
		ZeroData.Day day = data.day();
		if (day != null)
		{
			day.attacks++;
		}
		streak = 0;
		bestBeforeLastZero = -1;
	}

	void zeroWasHit()
	{
		session.zeroWasHit();
		data.today.zeroWasHit();
		data.total.zeroWasHit();
		ZeroData.Day day = data.day();
		if (day != null && day.zeros > 0)
		{
			day.zeros--;
		}
		if (bestBeforeLastZero >= 0)
		{
			data.bestStreak = bestBeforeLastZero;
		}
		bestBeforeLastZero = -1;
		streak = 0;
	}

	void resetSession()
	{
		session.reset();
		streak = 0;
		bestBeforeLastZero = -1;
	}

	void resetToday()
	{
		data.today.reset();
	}

	void resetTotal()
	{
		data.total.reset();
		data.bestStreak = streak;
		bestBeforeLastZero = -1;
	}
}
