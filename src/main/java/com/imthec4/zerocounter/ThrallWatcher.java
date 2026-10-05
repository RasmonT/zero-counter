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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Keeps the player's Arceuus thrall out of the count. The game shows thrall hits as the
 * player's own hitsplats, so without this they would count as the player's attacks.
 *
 * From an in-game capture (discovery build), every thrall attacks every 4 ticks:
 * <ul>
 * <li>ghostly (magic): animation 11101 and spot animation 1908 on the target, hit 1 tick later
 * (its first attack shows only the spot animation);</li>
 * <li>skeletal (ranged): animation 13672, hit 2 ticks later;</li>
 * <li>zombified (melee): animation 13681, hit 1 tick later.</li>
 * </ul>
 * The thrall spawns in the same tick as the player's resurrect cast (animation 8973), which is
 * how the player's own thrall is told apart from other players' thralls. Thrall NPC names come
 * back empty, so thralls are recognised by NPC id.
 *
 * The player's hitsplats in a tick where a thrall hit is due are held until the tick is over and
 * then passed on without the thrall's. When the player's hit and the thrall's land together,
 * Hitpoints XP tells them apart: it comes in the tick of the player's attack and only when the
 * attack does damage (thralls give none).
 */
final class ThrallWatcher
{
	static final int RESURRECT_CAST = 8973;
	static final int GHOST_ATTACK = 11101;
	static final int SKELETON_ATTACK = 13672;
	static final int ZOMBIE_ATTACK = 13681;
	static final int GHOST_IMPACT = 1908;

	/** The player's hits land at most this many ticks after the attack (and its XP). */
	static final int MAX_HIT_DELAY = 5;

	private static final Set<Integer> GHOSTS = new HashSet<>(Arrays.asList(10878, 10879, 10880));
	static final Set<Integer> THRALLS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		10878, 10879, 10880, 10881, 10882, 10883, 10884, 10885, 10886)));

	/** Where the hitsplats that are the player's own go. */
	interface Sink
	{
		void hitsplat(Object target, int tick, int amount);
	}

	private static final class Hit
	{
		final Object target;
		final int amount;

		Hit(Object target, int amount)
		{
			this.target = target;
			this.amount = amount;
		}
	}

	private int castTick = Integer.MIN_VALUE / 2;
	/** NPC index of the player's thrall, -1 if none is known. */
	private int thrallIndex = -1;
	private int thrallId = -1;
	private int lastAttackTick = Integer.MIN_VALUE / 2;
	/** tick -> number of thrall hits expected to land in it */
	private final Map<Integer, Integer> expected = new HashMap<>();
	/** tick -> the player's hitsplats in it, held because a thrall hit is due then too */
	private final TreeMap<Integer, List<Hit>> held = new TreeMap<>();
	/** Ticks of Hitpoints XP drops not yet matched to a hit of the player's that did damage. */
	private final Deque<Integer> damageXp = new ArrayDeque<>();

	void playerAnimation(int animation, int tick)
	{
		if (animation == RESURRECT_CAST)
		{
			castTick = tick;
		}
	}

	void npcSpawned(int index, int npcId, int tick)
	{
		if (THRALLS.contains(npcId) && tick - castTick <= 1)
		{
			thrallIndex = index;
			thrallId = npcId;
		}
	}

	void npcDespawned(int index)
	{
		if (index == thrallIndex)
		{
			thrallIndex = -1;
			thrallId = -1;
		}
	}

	void npcAnimation(int index, int animation, int tick)
	{
		if (index != thrallIndex)
		{
			return;
		}
		if (animation == SKELETON_ATTACK)
		{
			attack(tick, 2);
		}
		else if (animation == ZOMBIE_ATTACK || animation == GHOST_ATTACK)
		{
			attack(tick, 1);
		}
	}

	/** The ghostly thrall's impact seen on the player's target. */
	void ghostImpact(int tick)
	{
		if (thrallIndex != -1 && GHOSTS.contains(thrallId))
		{
			attack(tick, 1);
		}
	}

	/** One attack per tick at most: the ghost's animation and impact describe the same attack. */
	private void attack(int tick, int delay)
	{
		if (tick == lastAttackTick)
		{
			return;
		}
		lastAttackTick = tick;
		expected.merge(tick + delay, 1, Integer::sum);
	}

	/** The player gained Hitpoints XP in this tick: an attack of theirs in this tick did damage. */
	void hitpointsXp(int tick)
	{
		if (damageXp.isEmpty() || damageXp.peekLast() != tick)
		{
			damageXp.addLast(tick);
		}
	}

	/**
	 * One of the player's own hitsplats. Passed straight on, or held when a thrall hit is due in
	 * this tick and {@code ignoreThralls} is on.
	 */
	void hitsplat(Object target, int tick, int amount, boolean ignoreThralls, Sink sink)
	{
		if (ignoreThralls && expected.containsKey(tick))
		{
			held.computeIfAbsent(tick, t -> new ArrayList<>()).add(new Hit(target, amount));
			return;
		}
		if (amount > 0)
		{
			takeDamageXp(tick); // this XP is explained, keep it out of later checks
		}
		sink.hitsplat(target, tick, amount);
	}

	/** Called at the start of each game tick: finishes the ticks before it. */
	void tick(int tick, Sink sink)
	{
		while (!held.isEmpty() && held.firstKey() < tick)
		{
			Map.Entry<Integer, List<Hit>> e = held.pollFirstEntry();
			int hitTick = e.getKey();
			Integer due = expected.remove(hitTick);
			int thrallHits = due == null ? 0 : due;
			// Only when the player's hit landed in this tick too is there anything to tell apart
			boolean playerDidDamage = e.getValue().size() > thrallHits && takeDamageXp(hitTick);
			for (Hit h : withoutThrall(e.getValue(), thrallHits, playerDidDamage))
			{
				sink.hitsplat(h.target, hitTick, h.amount);
			}
		}
		expected.keySet().removeIf(t -> t < tick);
		while (!damageXp.isEmpty() && damageXp.peekFirst() < tick - MAX_HIT_DELAY)
		{
			damageXp.removeFirst();
		}
	}

	/**
	 * Whether a hit of the player's landing in {@code hitTick} did damage, judged by XP from an
	 * attack before it. The XP is used up, so each attack's XP explains one hit only.
	 */
	private boolean takeDamageXp(int hitTick)
	{
		for (Iterator<Integer> it = damageXp.iterator(); it.hasNext(); )
		{
			int t = it.next();
			if (t >= hitTick - MAX_HIT_DELAY && t <= hitTick - 1)
			{
				it.remove();
				return true;
			}
		}
		return false;
	}

	boolean hasThrall()
	{
		return thrallIndex != -1;
	}

	void reset()
	{
		castTick = Integer.MIN_VALUE / 2;
		thrallIndex = -1;
		thrallId = -1;
		lastAttackTick = Integer.MIN_VALUE / 2;
		expected.clear();
		held.clear();
		damageXp.clear();
	}

	/**
	 * Removes the thrall's hitsplats from those that landed in one tick. With XP the player's hit
	 * did damage, so the thrall's is a zero if there is one; without XP the player's hit was a
	 * zero, so the thrall's is the one with damage.
	 */
	private static List<Hit> withoutThrall(List<Hit> hits, int thrallHits, boolean playerDidDamage)
	{
		List<Hit> kept = new ArrayList<>(hits);
		for (int i = 0; i < thrallHits && !kept.isEmpty(); i++)
		{
			int remove = 0;
			for (int j = 0; j < kept.size(); j++)
			{
				if (playerDidDamage == (kept.get(j).amount == 0))
				{
					remove = j;
					break;
				}
			}
			kept.remove(remove);
		}
		return kept;
	}
}
