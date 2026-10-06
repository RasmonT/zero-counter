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
 * From in-game captures (discovery build), every thrall attacks every 4 ticks: ghostly
 * (animation 11101, sometimes spot animation 1908 on the target), skeletal (13672) and zombified
 * (13681). Its hit lands 1 to 3 ticks later, depending on the thrall and the distance (a ghost
 * next to its target hits 1 tick later, at Doom of Mokhaiotl 2 ticks later). Its first attack can
 * come 4 ticks after it spawns while the spawn animation is still playing, with no attack
 * animation at all; then that attack is assumed.
 *
 * So while the player has a thrall, the player's hitsplats are held until no thrall attack can
 * claim their tick any more. At first that is up to 2 ticks: once the 3 ticks after a thrall
 * attack are over, the tick its hit landed in is known as the only one of those ticks with a
 * hitsplat a thrall can do (at most {@link #MAX_THRALL_HIT}); that delay is remembered, and from
 * then on an attack's hit is taken out of its tick at once and nothing waits. The thrall's
 * hitsplat is removed and the rest is passed on. When the player's hit and the thrall's land in
 * the same tick, Hitpoints XP tells them apart: it comes in the tick of the player's attack and
 * only when the attack does damage (thralls give none).
 *
 * Which thrall is the player's: the one that spawns within a tick of the player's resurrect cast
 * (animation 8973), in either order. When that was missed (the thrall was already out when the
 * plugin started, or it was spawned again after a teleport) and the game says the player has a
 * thrall (varbit ARCEUUS_RESURRECTION_ACTIVE), the plugin hands over the thrall nearest to the
 * player with {@link #adopt}. Thrall NPC names come back empty, so thralls are recognised by
 * NPC id.
 */
final class ThrallWatcher
{
	static final int RESURRECT_CAST = 8973;
	static final int GHOST_ATTACK = 11101;
	static final int SKELETON_ATTACK = 13672;
	static final int ZOMBIE_ATTACK = 13681;
	static final int GHOST_IMPACT = 1908;

	/** A thrall's hit lands at most this many ticks after its attack. */
	static final int MAX_DELAY = 3;
	/** The player's hitsplats are passed on this many ticks late while a thrall is out. */
	static final int HOLD_TICKS = MAX_DELAY - 1;
	/** The biggest hit a thrall can do (greater thralls hit up to 3), with a margin. */
	static final int MAX_THRALL_HIT = 4;
	/** Ticks from the spawn to the earliest first attack. */
	static final int FIRST_ATTACK = 4;
	/** Ticks between a thrall's attacks. */
	static final int ATTACK_EVERY = 4;
	/** The player's hits land at most this many ticks after the attack (and its XP). */
	static final int MAX_HIT_DELAY = 5;

	private static final Set<Integer> GHOSTS = new HashSet<>(Arrays.asList(10878, 10879, 10880));
	private static final Set<Integer> SKELETONS = new HashSet<>(Arrays.asList(10881, 10882, 10883));
	static final Set<Integer> THRALLS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		10878, 10879, 10880, 10881, 10882, 10883, 10884, 10885, 10886)));

	private static final int NONE = Integer.MIN_VALUE / 2;

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

	private int castTick = NONE;
	/** The game says the player has a thrall out. */
	private boolean active;
	/** The last thrall NPC that spawned, for a cast seen only after the spawn. */
	private int lastSpawnIndex = -1;
	private int lastSpawnId = -1;
	private int lastSpawnTick = NONE;
	/** NPC index of the player's thrall, -1 if none is known. */
	private int thrallIndex = -1;
	private int thrallId = -1;
	private int lastAttackTick = NONE;
	/** Tick of a possible first attack without an animation, NONE when not expected. */
	private int firstAttackTick = NONE;
	/** Tick of the assumed first attack, taken back if the real one shows a tick later. */
	private int assumedAttackTick = NONE;
	/** Ticks from the thrall's attack to its hit, as last seen without doubt; -1 if not yet. */
	private int learnedDelay = -1;
	/** Thrall attacks whose hit is not placed yet, oldest first. */
	private final Deque<Integer> attacks = new ArrayDeque<>();
	/** tick -> number of thrall hits that landed in it */
	private final Map<Integer, Integer> thrallHits = new HashMap<>();
	/** tick -> the player's hitsplats in it, held while a thrall is out */
	private final TreeMap<Integer, List<Hit>> held = new TreeMap<>();
	/** Ticks of Hitpoints XP drops not yet matched to a hit of the player's that did damage. */
	private final Deque<Integer> damageXp = new ArrayDeque<>();

	void playerAnimation(int animation, int tick)
	{
		if (animation != RESURRECT_CAST)
		{
			return;
		}
		castTick = tick;
		if (lastSpawnIndex != -1 && tick - lastSpawnTick <= 1)
		{
			own(lastSpawnIndex, lastSpawnId); // the spawn came first this time
			firstAttackTick = lastSpawnTick + FIRST_ATTACK;
		}
	}

	void npcSpawned(int index, int npcId, int tick)
	{
		if (!THRALLS.contains(npcId))
		{
			return;
		}
		lastSpawnIndex = index;
		lastSpawnId = npcId;
		lastSpawnTick = tick;
		if (tick - castTick <= 1)
		{
			own(index, npcId);
			firstAttackTick = tick + FIRST_ATTACK;
		}
	}

	void npcDespawned(int index)
	{
		if (index == lastSpawnIndex)
		{
			lastSpawnIndex = -1;
		}
		if (index == thrallIndex)
		{
			thrallIndex = -1;
			thrallId = -1;
		}
	}

	/** Whether the game says the player has a thrall out (varbit ARCEUUS_RESURRECTION_ACTIVE). */
	void setActive(boolean active)
	{
		this.active = active;
	}

	/** The player has a thrall but it is not known which NPC it is: see {@link #adopt}. */
	boolean needsThrall()
	{
		return active && thrallIndex == -1;
	}

	/** The thrall NPC nearest to the player, used when the player has a thrall but it is not known. */
	void adopt(int index, int npcId, int tick)
	{
		if (!THRALLS.contains(npcId))
		{
			return;
		}
		own(index, npcId);
		if (index == lastSpawnIndex && lastAttackTick < lastSpawnTick)
		{
			// Just spawned (the cast animation did not show, as when cast mid-attack): its silent
			// first attack may be over already, or still to come
			int first = lastSpawnTick + FIRST_ATTACK;
			if (first > tick)
			{
				firstAttackTick = first;
			}
			else if (first >= tick - HOLD_TICKS)
			{
				attack(first);
				assumedAttackTick = first;
			}
		}
	}

	private void own(int index, int npcId)
	{
		if (npcId != thrallId)
		{
			learnedDelay = -1; // another kind of thrall, or a new one somewhere else
		}
		thrallIndex = index;
		thrallId = npcId;
	}

	void npcAnimation(int index, int animation, int tick)
	{
		if (index == thrallIndex
			&& (animation == SKELETON_ATTACK || animation == ZOMBIE_ATTACK || animation == GHOST_ATTACK))
		{
			attack(tick);
		}
	}

	/** The ghostly thrall's impact seen on the player's target. */
	void ghostImpact(int tick)
	{
		if (thrallIndex != -1 && GHOSTS.contains(thrallId))
		{
			attack(tick);
		}
	}

	/** One attack per tick at most: the ghost's animation and impact describe the same attack. */
	private void attack(int tick)
	{
		if (tick == lastAttackTick)
		{
			return;
		}
		if (assumedAttackTick != NONE && tick - assumedAttackTick < ATTACK_EVERY && attacks.remove(assumedAttackTick))
		{
			// Thralls attack every 4 ticks, so an attack this soon after the assumed one means the
			// assumed one never happened: the first attack just showed later than usual
			assumedAttackTick = NONE;
		}
		lastAttackTick = tick;
		attacks.addLast(tick);
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
	 * One of the player's own hitsplats. Held while the player has a thrall and
	 * {@code ignoreThralls} is on, else passed straight on.
	 */
	void hitsplat(Object target, int tick, int amount, boolean ignoreThralls, Sink sink)
	{
		if (ignoreThralls && (thrallIndex != -1 || !attacks.isEmpty() || !held.isEmpty()))
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

	/** Called on each GameTick, after that tick's events. */
	void tick(int tick, Sink sink)
	{
		if (tick == firstAttackTick)
		{
			firstAttackTick = NONE;
			if (thrallIndex != -1 && lastAttackTick != tick)
			{
				attack(tick);
				assumedAttackTick = tick;
			}
		}
		// Once the delay is known, an attack's hit is looked for in its tick right away, so the
		// player's hits of that tick need not wait
		if (learnedDelay != -1)
		{
			for (Iterator<Integer> it = attacks.iterator(); it.hasNext(); )
			{
				int a = it.next();
				if (a + learnedDelay == tick && hasCandidate(tick))
				{
					thrallHits.merge(tick, 1, Integer::sum);
					if (a == assumedAttackTick)
					{
						assumedAttackTick = NONE;
					}
					it.remove();
				}
			}
		}
		// Otherwise every tick a hit of the attack can land in has to be seen first
		while (!attacks.isEmpty() && attacks.peekFirst() + MAX_DELAY <= tick)
		{
			placeHit(attacks.pollFirst());
		}
		// Pass on the held ticks no open attack can claim any more, oldest first
		while (!held.isEmpty() && held.firstKey() <= tick && !claimed(held.firstKey()))
		{
			Map.Entry<Integer, List<Hit>> e = held.pollFirstEntry();
			release(e.getKey(), e.getValue(), sink);
		}
		thrallHits.keySet().removeIf(t -> t <= tick - HOLD_TICKS);
		while (!damageXp.isEmpty() && damageXp.peekFirst() < tick - MAX_HIT_DELAY - HOLD_TICKS)
		{
			damageXp.removeFirst();
		}
	}

	/** Whether an attack still open could have its hit in this tick. */
	private boolean claimed(int hitTick)
	{
		for (int a : attacks)
		{
			if (hitTick >= a + 1 && hitTick <= a + MAX_DELAY)
			{
				return true;
			}
		}
		return false;
	}

	/** Whether this tick holds a hitsplat a thrall can do. */
	private boolean hasCandidate(int tick)
	{
		List<Hit> hits = held.get(tick);
		return hits != null && hits.stream().anyMatch(h -> h.amount <= MAX_THRALL_HIT);
	}

	/** Finds the tick the hit of the thrall's attack in {@code attackTick} landed in. */
	private void placeHit(int attackTick)
	{
		boolean assumed = attackTick == assumedAttackTick;
		if (assumed)
		{
			assumedAttackTick = NONE;
		}
		List<Integer> possible = new ArrayList<>();
		for (int d = 1; d <= MAX_DELAY; d++)
		{
			if (hasCandidate(attackTick + d))
			{
				possible.add(d);
			}
		}
		int delay;
		if (possible.isEmpty())
		{
			return; // no hitsplat a thrall could do: nothing to take out
		}
		else if (possible.size() == 1)
		{
			delay = possible.get(0);
			if (!assumed)
			{
				learnedDelay = delay; // an assumed attack may not be real, so it teaches nothing
			}
		}
		else if (possible.contains(learnedDelay))
		{
			delay = learnedDelay;
		}
		else if (possible.contains(defaultDelay()))
		{
			delay = defaultDelay();
		}
		else
		{
			delay = possible.get(0);
		}
		thrallHits.merge(attackTick + delay, 1, Integer::sum);
	}

	/** The delay seen with the thrall next to its target. */
	private int defaultDelay()
	{
		return SKELETONS.contains(thrallId) ? 2 : 1;
	}

	private void release(int hitTick, List<Hit> hits, Sink sink)
	{
		Integer n = thrallHits.remove(hitTick);
		int thrall = n == null ? 0 : n;
		// Only when the player's hit landed in this tick too is there anything to tell apart
		boolean playerDidDamage = hits.size() > thrall && takeDamageXp(hitTick);
		for (Hit h : withoutThrall(hits, thrall, playerDidDamage))
		{
			sink.hitsplat(h.target, hitTick, h.amount);
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
		castTick = NONE;
		active = false;
		lastSpawnIndex = -1;
		lastSpawnId = -1;
		lastSpawnTick = NONE;
		thrallIndex = -1;
		thrallId = -1;
		lastAttackTick = NONE;
		firstAttackTick = NONE;
		assumedAttackTick = NONE;
		learnedDelay = -1;
		attacks.clear();
		thrallHits.clear();
		held.clear();
		damageXp.clear();
	}

	/**
	 * Removes the thrall's hitsplats from those that landed in one tick. With XP the player's hit
	 * did damage, so the thrall's is a zero if there is one; without XP the player's hit was a
	 * zero, so the thrall's is the one with damage. A hit bigger than a thrall can do is never
	 * taken out.
	 */
	private static List<Hit> withoutThrall(List<Hit> hits, int thrallHits, boolean playerDidDamage)
	{
		List<Hit> kept = new ArrayList<>(hits);
		for (int i = 0; i < thrallHits; i++)
		{
			int remove = -1;
			for (int j = 0; j < kept.size(); j++)
			{
				int amount = kept.get(j).amount;
				boolean zero = amount == 0;
				if (amount <= MAX_THRALL_HIT && playerDidDamage == zero)
				{
					remove = j;
					break;
				}
			}
			if (remove == -1)
			{
				for (int j = 0; j < kept.size(); j++)
				{
					if (kept.get(j).amount <= MAX_THRALL_HIT)
					{
						remove = j;
						break;
					}
				}
			}
			if (remove == -1)
			{
				break;
			}
			kept.remove(remove);
		}
		return kept;
	}
}
