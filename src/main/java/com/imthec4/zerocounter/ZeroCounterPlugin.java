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

import com.google.inject.Provides;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;

/**
 * Counts the zeros the player hits, shows the current streak of zeros in a row as the game's
 * blue zero hitsplat, plays a sound when a streak reaches a number the player picked, and keeps
 * accuracy for the session, the day and in total.
 *
 * How it detects things was established from an in-game capture (discovery build):
 * <ul>
 * <li>The player's own hitsplats are the "mine" types; a zero is BLOCK_ME with amount 0. Hits
 * the player takes are "mine" types too, so hitsplats on the player are always ignored.</li>
 * <li>A dragon dagger special shows its two hitsplats on two consecutive ticks, burning claws
 * three over two ticks. Hitsplats within one tick of each other are one attack
 * ({@link AttackTracker}).</li>
 * <li>Burn damage is its own BURN type, not "mine", so it never counts.</li>
 * <li>A splashed spell shows no hitsplat at all, only spot animation 85 (FAILEDSPELL_IMPACT)
 * on the target, in the same tick the player's cast animation starts.</li>
 * <li>Thrall hits are "mine" hitsplats too; {@link ThrallWatcher} predicts them so they can be
 * left out (setting "Ignore thralls", on by default).</li>
 * </ul>
 * Data is stored locally, one JSON file per character. Nothing is sent anywhere.
 */
@Slf4j
@PluginDescriptor(
	internalName = "zero-counter",
	name = "Zero Counter",
	description = "Counts the zeros you hit: a streak box, sounds at the streaks you choose, and your accuracy",
	tags = {"zero", "miss", "splash", "hitsplat", "accuracy", "streak", "combat", "sound"}
)
public class ZeroCounterPlugin extends Plugin implements AttackTracker.Listener
{
	/** Save at most this often while fighting; always on logout and shutdown. */
	private static final int SAVE_EVERY_TICKS = 10;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ZeroCounterConfig config;

	@Inject
	private ZeroStore store;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private InfoBoxManager infoBoxManager;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private AudioPlayer audioPlayer;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ZeroCountOverlay countOverlay;

	@Inject
	private AccuracyOverlay accuracyOverlay;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ZeroCounterPanel panel;

	@Nullable
	private NavigationButton navButton;

	/** The side panel needs a fresh snapshot; sent at most once per tick. */
	private boolean panelStale;

	private final AttackTracker tracker = new AttackTracker(this);
	private final ThrallWatcher thralls = new ThrallWatcher();

	/** Last Hitpoints XP seen, to notice when it goes up (the player's attack did damage). */
	private int hitpointsXp = -1;
	/** Target of the player's last own hitsplat: the thrall attacks what the player attacks. */
	@Nullable
	private Actor lastHitTarget;

	@Nullable
	private StreakInfoBox streakBox;

	/** Data of the logged-in character; null while logged out or still loading. */
	@Getter
	@Nullable
	private ZeroData data;

	/** Counts for the logged-in character; null while {@link #data} is. */
	@Getter
	@Nullable
	private Tally tally;

	private long accountHash = -1;
	private boolean dirty;
	private int lastSaveTick;

	private int lastAnimationTick = -1;
	private int lastSplashTick = -1;

	private SoundRules soundRules = SoundRules.parse("");
	/** Missing sound files already reported, so the chat is not spammed. */
	private final Set<String> reportedMissing = new HashSet<>();

	@Provides
	ZeroCounterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ZeroCounterConfig.class);
	}

	@Override
	protected void startUp()
	{
		store.start(this::getPluginDirectory);
		soundRules = SoundRules.parse(config.soundRules());
		overlayManager.add(countOverlay);
		overlayManager.add(accuracyOverlay);
		streakBox = new StreakInfoBox(this, config);
		infoBoxManager.addInfoBox(streakBox);
		StreakInfoBox box = streakBox;
		spriteManager.getSpriteAsync(SpriteID.Hitmark.HITSPLAT_BLUE_MISS, 0, sprite ->
		{
			box.setSprite(sprite);
			infoBoxManager.updateInfoBoxImage(box);
		});
		panel.setResetAllAction(this::confirmResetAll);
		navButton = NavigationButton.builder()
			.tooltip("Zero Counter")
			.icon(ImageUtil.loadImageResource(getClass(), "panel_icon.png"))
			.priority(9)
			.panel(panel)
			.build();
		if (config.showPanel())
		{
			clientToolbar.addNavigation(navButton);
		}
		publish();
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invoke(this::onLoggedIn);
		}
	}

	@Override
	protected void shutDown()
	{
		save();
		store.stop();
		overlayManager.remove(countOverlay);
		overlayManager.remove(accuracyOverlay);
		if (streakBox != null)
		{
			infoBoxManager.removeInfoBox(streakBox);
			streakBox = null;
		}
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		tracker.clear();
		thralls.reset();
		hitpointsXp = -1;
		lastHitTarget = null;
		data = null;
		tally = null;
		accountHash = -1;
		reportedMissing.clear();
	}

	// ------------------------------------------------------------------ loading and saving

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			save();
			tracker.clear();
			thralls.reset();
				hitpointsXp = -1;
			lastHitTarget = null;
			data = null;
			tally = null;
			accountHash = -1;
			refreshBox();
			publish();
		}
		else if (event.getGameState() == GameState.LOGGED_IN)
		{
			// Also fires after loading screens and world hops; only a new character starts over
			onLoggedIn();
		}
	}

	private void onLoggedIn()
	{
		long hash = client.getAccountHash();
		if (hash == -1 || hash == accountHash)
		{
			return;
		}
		save();
		accountHash = hash;
		data = null;
		tally = null;
		tracker.clear();
		store.load(hash, loaded -> clientThread.invoke(() -> install(hash, loaded)));
	}

	private void install(long hash, ZeroData loaded)
	{
		if (hash != accountHash)
		{
			return; // logged out or switched character while loading
		}
		data = loaded;
		data.rollDay(config.dayBoundary().today());
		tally = new Tally(data);
		dirty = true;
		save();
		refreshBox();
		publish();
	}

	private void save()
	{
		if (data != null && accountHash != -1 && dirty)
		{
			store.save(accountHash, data);
			dirty = false;
			lastSaveTick = client.getTickCount();
		}
	}

	/** Called by the overlays every frame and on every tick: starts a new day when the date changes. */
	void rollDay()
	{
		if (data != null && data.rollDay(config.dayBoundary().today()))
		{
			dirty = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		int tick = client.getTickCount();
		thralls.tick(tick, tracker::hitsplat);
		tracker.tick(tick);
		rollDay();
		if (dirty && tick - lastSaveTick >= SAVE_EVERY_TICKS)
		{
			save();
		}
		if (panelStale)
		{
			publish();
		}
	}

	/** Hands the side panel a copy of what it shows (the panel lives on the Swing thread). */
	private void publish()
	{
		panelStale = false;
		ZeroData d = data;
		Tally t = tally;
		ZeroCounterPanel.Snapshot snapshot;
		if (d == null || t == null)
		{
			snapshot = ZeroCounterPanel.Snapshot.EMPTY;
		}
		else
		{
			NavigableMap<String, long[]> days = new TreeMap<>();
			for (Map.Entry<String, ZeroData.Day> e : d.days.entrySet())
			{
				days.put(e.getKey(), new long[]{e.getValue().zeros, e.getValue().attacks});
			}
			snapshot = new ZeroCounterPanel.Snapshot(true, t.getStreak(), d.bestStreak,
				t.session.zeros, t.session.attacks, d.today.zeros, d.today.attacks,
				d.total.zeros, d.total.attacks, days);
		}
		SwingUtilities.invokeLater(() -> panel.update(snapshot));
	}

	// ------------------------------------------------------------------ detecting attacks

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Actor target = event.getActor();
		Hitsplat hitsplat = event.getHitsplat();
		if (target == null || target == client.getLocalPlayer() || !hitsplat.isMine())
		{
			return; // hits on the player, or someone else's / non-player damage (burn, poison)
		}
		int tick = client.getTickCount();
		lastHitTarget = target;
		thralls.hitsplat(target, tick, hitsplat.getAmount(), config.ignoreThralls(), tracker::hitsplat);
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() != Skill.HITPOINTS)
		{
			return;
		}
		int xp = event.getXp();
		if (hitpointsXp >= 0 && xp > hitpointsXp)
		{
			thralls.hitpointsXp(client.getTickCount());
		}
		hitpointsXp = xp;
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		NPC npc = event.getNpc();
		thralls.npcSpawned(npc.getIndex(), npc.getId(), client.getTickCount());
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		thralls.npcDespawned(event.getNpc().getIndex());
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Player me = client.getLocalPlayer();
		Actor actor = event.getActor();
		int tick = client.getTickCount();
		if (actor instanceof NPC)
		{
			thralls.npcAnimation(((NPC) actor).getIndex(), actor.getAnimation(), tick);
			return;
		}
		if (me != null && actor == me && me.getAnimation() != -1)
		{
			lastAnimationTick = tick;
			thralls.playerAnimation(me.getAnimation(), tick);
		}
	}

	/** A splash only shows as spot animation 85 on the target, in the tick of the player's cast. */
	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		Player me = client.getLocalPlayer();
		Actor actor = event.getActor();
		int tick = client.getTickCount();
		// The ghostly thrall's first attack shows only as this impact; other players' ghosts make
		// it too, so only the impact on the player's own target counts
		if (actor instanceof NPC && me != null && (actor == me.getInteracting() || actor == lastHitTarget)
			&& actor.hasSpotAnim(ThrallWatcher.GHOST_IMPACT))
		{
			thralls.ghostImpact(tick);
		}
		if (me == null || actor == null || actor == me || actor != me.getInteracting()
			|| lastAnimationTick != tick || lastSplashTick == tick
			|| !actor.hasSpotAnim(SpotanimID.FAILEDSPELL_IMPACT))
		{
			return;
		}
		lastSplashTick = tick;
		tracker.splash();
	}

	// ------------------------------------------------------------------ counting

	@Override
	public void zero()
	{
		if (tally == null)
		{
			return;
		}
		rollDay();
		long streak = tally.zero();
		dirty = true;
		panelStale = true;
		refreshBox();
		playSoundFor(streak);
	}

	@Override
	public void hit()
	{
		if (tally == null)
		{
			return;
		}
		rollDay();
		tally.hit();
		dirty = true;
		panelStale = true;
		refreshBox();
	}

	@Override
	public void zeroWasHit()
	{
		if (tally == null)
		{
			return;
		}
		tally.zeroWasHit();
		dirty = true;
		panelStale = true;
		refreshBox();
	}

	private void refreshBox()
	{
		StreakInfoBox box = streakBox;
		if (box == null)
		{
			return;
		}
		long streak = tally != null ? tally.getStreak() : 0;
		long best = data != null ? data.bestStreak : 0;
		if (box.update(streak, best, tally != null))
		{
			infoBoxManager.updateInfoBoxImage(box);
		}
	}

	// ------------------------------------------------------------------ sounds

	private void playSoundFor(long streak)
	{
		if (!config.playSounds() || config.volume() <= 0)
		{
			return;
		}
		String sound = soundRules.soundFor(streak);
		if (sound == null)
		{
			return;
		}
		float gain = (float) (20 * Math.log10(config.volume() / 100.0));
		try
		{
			executor.execute(() -> play(sound, gain));
		}
		catch (RejectedExecutionException ignored)
		{
			// client is shutting down
		}
	}

	/**
	 * Runs on the background executor: loading a sound must not hold up the game. A file in the
	 * data folder always wins, so a player can replace a built-in sound by overwriting its .wav.
	 */
	private void play(String sound, float gain)
	{
		try
		{
			boolean builtIn = !SoundRules.isFile(sound);
			String fileName = builtIn ? sound + ".wav" : sound;
			Filepath dir = store.directory();
			Filepath file = dir != null ? dir.joinSegment(fileName) : null;
			if (file != null && file.exists())
			{
				audioPlayer.play(file, gain);
			}
			else if (builtIn)
			{
				audioPlayer.play(ZeroCounterPlugin.class, fileName, gain);
			}
			else
			{
				clientThread.invokeLater(() -> reportMissing(sound));
			}
		}
		catch (Exception e)
		{
			log.warn("Zero Counter: could not play {}", sound, e);
		}
	}

	private void reportMissing(String sound)
	{
		if (reportedMissing.add(sound))
		{
			chat("Sound file " + sound + " was not found in .runelite/plugin-data/zero-counter");
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!ZeroCounterConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		if ("soundRules".equals(event.getKey()))
		{
			soundRules = SoundRules.parse(config.soundRules());
			reportedMissing.clear();
			if (!soundRules.errors.isEmpty())
			{
				String bad = String.join(", ", soundRules.errors);
				clientThread.invokeLater(() -> chat("Streak sounds: could not read " + bad
					+ ". Use streak=sound, e.g. 3=annoyed, 15=FML"));
			}
		}
		if ("showPanel".equals(event.getKey()) && navButton != null)
		{
			if (config.showPanel())
			{
				clientToolbar.addNavigation(navButton);
			}
			else
			{
				clientToolbar.removeNavigation(navButton);
			}
		}
		if ("dayBoundary".equals(event.getKey()))
		{
			clientThread.invokeLater(() ->
			{
				rollDay();
				publish();
			});
		}
		clientThread.invokeLater(this::refreshBox);
		SwingUtilities.invokeLater(panel::refresh);
	}

	private void chat(String message)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage("Zero Counter: " + message)
			.build());
	}

	// ------------------------------------------------------------------ resets

	@Getter
	@RequiredArgsConstructor
	enum Period
	{
		SESSION("Session"),
		TODAY("Today"),
		TOTAL("Total");

		private final String label;
	}

	/** Asks on the Swing thread, then resets on the client thread. */
	void confirmReset(Period period)
	{
		if (tally == null)
		{
			return;
		}
		String what = period == Period.SESSION ? "this session's accuracy"
			: period == Period.TODAY ? "today's zeros and accuracy"
			: "the total zeros, accuracy and best streak";
		SwingUtilities.invokeLater(() ->
		{
			int answer = JOptionPane.showConfirmDialog(client.getCanvas(), "Reset " + what + "?",
				"Zero Counter", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (answer == JOptionPane.YES_OPTION)
			{
				clientThread.invoke(() -> reset(period, what));
			}
		});
	}

	/** From the side panel only: asks, then wipes everything stored for this character. */
	void confirmResetAll()
	{
		if (tally == null)
		{
			return;
		}
		SwingUtilities.invokeLater(() ->
		{
			int answer = JOptionPane.showConfirmDialog(panel,
				"Delete ALL Zero Counter data for this character?\n\n"
					+ "Zeros, accuracy, best streak and the history by date all start from zero.\n"
					+ "This cannot be undone. Your sound files are kept.",
				"Zero Counter", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (answer == JOptionPane.YES_OPTION)
			{
				clientThread.invoke(this::resetAll);
			}
		});
	}

	private void resetAll()
	{
		if (data == null || accountHash == -1)
		{
			return;
		}
		data = ZeroData.fresh();
		data.rollDay(config.dayBoundary().today());
		tally = new Tally(data);
		tracker.clear();
		dirty = true;
		save();
		refreshBox();
		publish();
		chat("All data for this character was deleted.");
	}

	private void reset(Period period, String what)
	{
		Tally t = tally;
		if (t == null)
		{
			return;
		}
		switch (period)
		{
			case SESSION:
				t.resetSession();
				break;
			case TODAY:
				t.resetToday();
				break;
			case TOTAL:
				t.resetTotal();
				break;
		}
		dirty = true;
		save();
		refreshBox();
		publish();
		chat("Reset " + what + ".");
	}
}
