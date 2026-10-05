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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Turns the player's hitsplats into attacks. One attack can show several hitsplats: a dragon
 * dagger special lands on two consecutive ticks, burning claws on two, a scythe several on the
 * same tick. Hitsplats on the same target that follow each other within {@link #MERGE_TICKS}
 * belong to one attack, and the attack is a zero only if all of them were 0.
 *
 * A zero is reported at once, so the streak box reacts without delay. If a later hitsplat of
 * the same attack does damage, the zero is taken back with {@link Listener#zeroWasHit()}.
 */
class AttackTracker
{
	/** Hitsplats at most this many ticks after the previous one are the same attack. */
	static final int MERGE_TICKS = 1;

	interface Listener
	{
		void zero();

		void hit();

		/** The zero reported last for this attack turned out to do damage after all. */
		void zeroWasHit();
	}

	private static final class Attack
	{
		int lastTick;
		boolean damaged;
	}

	private final Listener listener;
	private final Map<Object, Attack> open = new HashMap<>();

	AttackTracker(Listener listener)
	{
		this.listener = listener;
	}

	/** One of the player's own hitsplats on {@code target} (never on the player). */
	void hitsplat(Object target, int tick, int amount)
	{
		Attack attack = open.get(target);
		if (attack != null && tick - attack.lastTick > MERGE_TICKS)
		{
			attack = null; // an older attack on this target is over
		}
		if (attack == null)
		{
			attack = new Attack();
			attack.lastTick = tick;
			attack.damaged = amount > 0;
			open.put(target, attack);
			if (attack.damaged)
			{
				listener.hit();
			}
			else
			{
				listener.zero();
			}
			return;
		}
		// Max: a hit held back for a thrall check can arrive after a later one
		attack.lastTick = Math.max(attack.lastTick, tick);
		if (amount > 0 && !attack.damaged)
		{
			attack.damaged = true;
			listener.zeroWasHit();
		}
	}

	/** A spell that splashed: no hitsplat at all, always a zero on its own. */
	void splash()
	{
		listener.zero();
	}

	/** Forgets attacks that can no longer get another hitsplat. */
	void tick(int tick)
	{
		for (Iterator<Attack> it = open.values().iterator(); it.hasNext(); )
		{
			if (tick - it.next().lastTick > MERGE_TICKS)
			{
				it.remove();
			}
		}
	}

	void clear()
	{
		open.clear();
	}
}
