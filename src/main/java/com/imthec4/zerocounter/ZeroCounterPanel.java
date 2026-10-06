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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

/**
 * Side panel: the current streak and session at the top, then a date range with the zeros and
 * accuracy in it, day by day. Lives on the Swing thread and only works on the snapshot the
 * plugin hands over. Colours come from RuneLite's ColorScheme and are set on every component,
 * so nothing falls back to the look-and-feel's light defaults.
 */
class ZeroCounterPanel extends PluginPanel
{
	private static final Color ERROR = new Color(0xE57373);
	private static final Color GOOD = new Color(0x4CAF50);
	private static final Color BLUE = new Color(0x6E8CFF);
	/** Discord's brand colour and its darker hover shade, so the button reads as Discord at a glance. */
	private static final Color DISCORD = new Color(0x5865F2);
	private static final Color DISCORD_HOVER = new Color(0x4752C4);
	/** Support server for ImTheC4's plugins. Only opened when the player clicks the link. */
	static final String DISCORD_URL = "https://discord.gg/XgxjhyznbZ";
	private static final Color BACKGROUND = ColorScheme.DARK_GRAY_COLOR;
	private static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color CARD_HOVER = ColorScheme.DARKER_GRAY_HOVER_COLOR;

	/** What the panel shows; an immutable copy made on the client thread. */
	static final class Snapshot
	{
		final boolean loggedIn;
		final long streak;
		final long best;
		final long sessionZeros;
		final long sessionAttacks;
		final long todayZeros;
		final long todayAttacks;
		final long totalZeros;
		final long totalAttacks;
		/** date -> {zeros, attacks} */
		final NavigableMap<String, long[]> days;

		Snapshot(boolean loggedIn, long streak, long best, long sessionZeros, long sessionAttacks,
			long todayZeros, long todayAttacks, long totalZeros, long totalAttacks,
			NavigableMap<String, long[]> days)
		{
			this.loggedIn = loggedIn;
			this.streak = streak;
			this.best = best;
			this.sessionZeros = sessionZeros;
			this.sessionAttacks = sessionAttacks;
			this.todayZeros = todayZeros;
			this.todayAttacks = todayAttacks;
			this.totalZeros = totalZeros;
			this.totalAttacks = totalAttacks;
			this.days = days;
		}

		static final Snapshot EMPTY = new Snapshot(false, 0, 0, 0, 0, 0, 0, 0, 0, new TreeMap<>());
	}

	private final ZeroCounterConfig config;

	private final JLabel streakLabel = new JLabel(" ", SwingConstants.CENTER);
	private final JLabel streakInfo = new JLabel(" ", SwingConstants.CENTER);
	private final JLabel sessionLabel = new JLabel(" ", SwingConstants.CENTER);
	private final JLabel sessionInfo = new JLabel(" ", SwingConstants.CENTER);
	private final AccuracyRow accSession = new AccuracyRow("Session");
	private final AccuracyRow accToday = new AccuracyRow("Today");
	private final AccuracyRow accTotal = new AccuracyRow("Total");
	private final JTextField fromField = new JTextField();
	private final JTextField toField = new JTextField();
	private final JLabel rangeLabel = new JLabel(" ", SwingConstants.CENTER);
	private final JLabel rangeInfo = new JLabel(" ", SwingConstants.CENTER);
	private final JPanel dayList = new JPanel(new GridLayout(0, 1, 0, 2));
	private final List<QuickRange> quickRanges = new ArrayList<>();

	private final JLabel resetAll = new JLabel("Reset all data", SwingConstants.CENTER);

	private Snapshot snapshot = Snapshot.EMPTY;

	/** Asks for confirmation and wipes this character's data; set by the plugin. */
	@Nullable
	private Runnable resetAllAction;

	@Inject
	ZeroCounterPanel(ZeroCounterConfig config)
	{
		this.config = config;

		setBackground(BACKGROUND);
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setLayout(new BorderLayout(0, 10));

		JPanel top = panel(new BorderLayout(0, 8));
		JLabel title = new JLabel("Zero Counter");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ColorScheme.BRAND_ORANGE);

		JPanel now = panel(new GridLayout(1, 2, 6, 0));
		now.add(card("Zeros in a row", streakLabel, streakInfo));
		now.add(card("This session", sessionLabel, sessionInfo));

		JPanel accuracy = new JPanel(new GridLayout(0, 1, 0, 3));
		accuracy.setBackground(CARD);
		accuracy.setBorder(new EmptyBorder(8, 8, 8, 8));
		JLabel accCaption = new JLabel("Accuracy (hits / attacks)", SwingConstants.CENTER);
		accCaption.setFont(FontManager.getRunescapeSmallFont());
		accCaption.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		accuracy.add(accCaption);
		accuracy.add(accSession.row);
		accuracy.add(accToday.row);
		accuracy.add(accTotal.row);

		JPanel header = panel(new BorderLayout(0, 8));
		header.add(title, BorderLayout.NORTH);
		header.add(now, BorderLayout.CENTER);
		header.add(accuracy, BorderLayout.SOUTH);
		top.add(header, BorderLayout.NORTH);

		JPanel controls = panel(new GridLayout(0, 1, 0, 6));
		controls.add(dateRow("From", fromField));
		controls.add(dateRow("To", toField));
		JPanel quick = panel(new GridLayout(1, 4, 4, 0));
		quick.add(new QuickRange("7d", 7).label);
		quick.add(new QuickRange("30d", 30).label);
		quick.add(new QuickRange("Month", -1).label);
		quick.add(new QuickRange("All", 0).label);
		controls.add(quick);
		top.add(controls, BorderLayout.CENTER);
		top.add(card("Zeros in range", rangeLabel, rangeInfo), BorderLayout.SOUTH);
		add(top, BorderLayout.NORTH);

		dayList.setBackground(BACKGROUND);
		add(dayList, BorderLayout.CENTER);

		JLabel note = new JLabel("<html><div style='width:150px'>Enter a date and press Enter. A special attack"
			+ " counts as one attack. Only Reset all data clears this history.</div></html>");
		note.setFont(FontManager.getRunescapeSmallFont());
		note.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);

		resetAll.setOpaque(true);
		resetAll.setBackground(CARD);
		resetAll.setForeground(ERROR);
		resetAll.setFont(FontManager.getRunescapeSmallFont());
		resetAll.setBorder(new EmptyBorder(6, 0, 6, 0));
		resetAll.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		resetAll.setToolTipText("Delete all of this character's Zero Counter data, after a confirmation");
		resetAll.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (resetAllAction != null && snapshot.loggedIn)
				{
					resetAllAction.run();
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				resetAll.setBackground(CARD_HOVER);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				resetAll.setBackground(CARD);
			}
		});

		// Opens the browser only when clicked; the plugin itself never contacts Discord
		JLabel discord = new JLabel("Join the Discord", SwingConstants.CENTER);
		discord.setOpaque(true);
		discord.setBackground(DISCORD);
		discord.setForeground(Color.WHITE);
		discord.setFont(FontManager.getRunescapeBoldFont());
		discord.setBorder(new EmptyBorder(7, 0, 7, 0));
		discord.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		discord.setToolTipText("Questions, ideas or a bug? Opens " + DISCORD_URL + " in your browser");
		discord.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				LinkBrowser.browse(DISCORD_URL);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				discord.setBackground(DISCORD_HOVER);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				discord.setBackground(DISCORD);
			}
		});

		JPanel bottom = panel(new BorderLayout(0, 8));
		bottom.add(note, BorderLayout.NORTH);
		bottom.add(discord, BorderLayout.CENTER);
		bottom.add(resetAll, BorderLayout.SOUTH);
		add(bottom, BorderLayout.SOUTH);

		setRange(30);
	}

	private static JPanel panel(LayoutManager layout)
	{
		JPanel p = new JPanel(layout);
		p.setBackground(BACKGROUND);
		return p;
	}

	private JPanel dateRow(String name, JTextField field)
	{
		JPanel row = panel(new BorderLayout(8, 0));
		JLabel label = new JLabel(name);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setPreferredSize(new Dimension(36, 0));
		row.add(label, BorderLayout.WEST);

		field.setBackground(CARD);
		field.setForeground(Color.WHITE);
		field.setCaretColor(Color.WHITE);
		field.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(5, 6, 5, 6)));
		field.setToolTipText("yyyy-mm-dd, e.g. 2026-10-05 - press Enter to apply");
		field.addActionListener(e ->
		{
			selectQuick(null);
			refresh();
		});
		row.add(field, BorderLayout.CENTER);
		return row;
	}

	private static JPanel card(String captionText, JLabel value, JLabel info)
	{
		JPanel card = new JPanel(new GridLayout(0, 1, 0, 2));
		card.setBackground(CARD);
		card.setBorder(new EmptyBorder(8, 6, 8, 6));

		JLabel caption = new JLabel(captionText, SwingConstants.CENTER);
		caption.setFont(FontManager.getRunescapeSmallFont());
		caption.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		card.add(caption);

		value.setFont(FontManager.getRunescapeBoldFont());
		value.setForeground(Color.WHITE);
		card.add(value);

		info.setFont(FontManager.getRunescapeSmallFont());
		info.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		card.add(info);
		return card;
	}

	/** One line of the accuracy card: "Session      86.0%  37/43". */
	private static final class AccuracyRow
	{
		private final JPanel row = new JPanel(new BorderLayout(6, 0));
		private final JLabel percent = new JLabel("-", SwingConstants.RIGHT);
		private final JLabel counts = new JLabel(" ", SwingConstants.RIGHT);

		AccuracyRow(String name)
		{
			row.setOpaque(false);
			JLabel label = new JLabel(name);
			label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			row.add(label, BorderLayout.WEST);
			JPanel right = new JPanel(new BorderLayout(6, 0));
			right.setOpaque(false);
			counts.setFont(FontManager.getRunescapeSmallFont());
			counts.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
			right.add(counts, BorderLayout.CENTER);
			percent.setForeground(Color.WHITE);
			right.add(percent, BorderLayout.EAST);
			row.add(right, BorderLayout.EAST);
		}

		void set(long zeros, long attacks)
		{
			if (attacks <= 0)
			{
				percent.setText("-");
				percent.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
				counts.setText(" ");
				row.setToolTipText("No attacks yet");
				return;
			}
			long hits = attacks - zeros;
			double p = 100.0 * hits / attacks;
			percent.setText(String.format(Locale.US, "%.1f%%", p));
			percent.setForeground(p >= 75 ? GOOD : p < 50 ? ERROR : Color.WHITE);
			counts.setText(String.format(Locale.US, "%,d/%,d", hits, attacks));
			row.setToolTipText(hits + " hits, " + zeros + (zeros == 1 ? " zero" : " zeros") + " in " + attacks + " attacks");
		}

		void clear()
		{
			set(0, 0);
		}
	}

	/** A flat, RuneLite-style toggle: 7d / 30d / Month / All. */
	private final class QuickRange
	{
		private final JLabel label;
		private final int span;

		/** span > 0: the last N days; -1: this month; 0: everything recorded. */
		QuickRange(String text, int span)
		{
			this.span = span;
			label = new JLabel(text, SwingConstants.CENTER);
			label.setOpaque(true);
			label.setBackground(CARD);
			label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			label.setFont(FontManager.getRunescapeSmallFont());
			label.setBorder(new EmptyBorder(5, 0, 5, 0));
			label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			label.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					setRange(QuickRange.this.span);
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					label.setBackground(CARD_HOVER);
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					label.setBackground(CARD);
				}
			});
			quickRanges.add(this);
		}

		void setSelected(boolean selected)
		{
			label.setForeground(selected ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
		}
	}

	private void selectQuick(QuickRange chosen)
	{
		for (QuickRange q : quickRanges)
		{
			q.setSelected(q == chosen);
		}
	}

	private void setRange(int span)
	{
		LocalDate today = LocalDate.parse(config.dayBoundary().today());
		LocalDate from;
		if (span > 0)
		{
			from = today.minusDays(span - 1);
		}
		else if (span < 0)
		{
			from = today.withDayOfMonth(1);
		}
		else
		{
			from = snapshot.days.isEmpty() ? today : LocalDate.parse(snapshot.days.firstKey());
		}
		fromField.setText(from.toString());
		toField.setText(today.toString());
		for (QuickRange q : quickRanges)
		{
			q.setSelected(q.span == span);
		}
		refresh();
	}

	void setResetAllAction(Runnable action)
	{
		this.resetAllAction = action;
	}

	/** Called by the plugin, on the Swing thread, with a fresh snapshot. */
	void update(Snapshot snapshot)
	{
		this.snapshot = snapshot;
		refresh();
	}

	void refresh()
	{
		Snapshot s = snapshot;
		dayList.removeAll();
		resetAll.setVisible(s.loggedIn);
		if (!s.loggedIn)
		{
			streakLabel.setText("-");
			streakInfo.setText(" ");
			sessionLabel.setText("-");
			sessionInfo.setText(" ");
			accSession.clear();
			accToday.clear();
			accTotal.clear();
			range("-", "Log in to see this character's history", ColorScheme.LIGHT_GRAY_COLOR);
			return;
		}

		streakLabel.setText(String.valueOf(s.streak));
		streakLabel.setForeground(s.streak > 0 ? BLUE : Color.WHITE);
		streakInfo.setText("Best: " + s.best);
		sessionLabel.setText(s.sessionZeros + (s.sessionZeros == 1 ? " zero" : " zeros"));
		sessionInfo.setText(accuracy(s.sessionZeros, s.sessionAttacks));
		accSession.set(s.sessionZeros, s.sessionAttacks);
		accToday.set(s.todayZeros, s.todayAttacks);
		accTotal.set(s.totalZeros, s.totalAttacks);

		LocalDate from;
		LocalDate to;
		try
		{
			from = LocalDate.parse(fromField.getText().trim());
			to = LocalDate.parse(toField.getText().trim());
		}
		catch (DateTimeParseException e)
		{
			range("-", "Use dates like 2026-10-05", ERROR);
			return;
		}
		if (from.isAfter(to))
		{
			range("-", "\"From\" is after \"To\"", ERROR);
			return;
		}

		long zeros = 0;
		long attacks = 0;
		for (Map.Entry<String, long[]> day : s.days.subMap(from.toString(), true, to.toString(), true).descendingMap().entrySet())
		{
			zeros += day.getValue()[0];
			attacks += day.getValue()[1];
			dayList.add(dayRow(day.getKey(), day.getValue()[0], day.getValue()[1]));
		}
		range(zeros + (zeros == 1 ? " zero" : " zeros"),
			attacks == 0 ? "No attacks in this range" : accuracy(zeros, attacks), ColorScheme.LIGHT_GRAY_COLOR);
	}

	/** "86.0% hit (37/43)" */
	static String accuracy(long zeros, long attacks)
	{
		if (attacks <= 0)
		{
			return "No attacks yet";
		}
		long hits = attacks - zeros;
		return String.format(Locale.US, "%.1f%% hit (%,d/%,d)", 100.0 * hits / attacks, hits, attacks);
	}

	private void range(String value, String info, Color infoColor)
	{
		rangeLabel.setText(value);
		rangeInfo.setText(info);
		rangeInfo.setForeground(infoColor);
		dayList.revalidate();
		dayList.repaint();
	}

	private static JPanel dayRow(String date, long zeros, long attacks)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(CARD);
		row.setBorder(new EmptyBorder(6, 8, 6, 8));

		JLabel weekday = new JLabel(LocalDate.parse(date).getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
		weekday.setFont(FontManager.getRunescapeSmallFont());
		weekday.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		weekday.setPreferredSize(new Dimension(26, 0));
		row.add(weekday, BorderLayout.WEST);

		JLabel day = new JLabel(date);
		day.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(day, BorderLayout.CENTER);

		JPanel right = new JPanel(new GridLayout(0, 1));
		right.setOpaque(false);
		JLabel count = new JLabel(zeros + (zeros == 1 ? " zero" : " zeros"), SwingConstants.RIGHT);
		count.setForeground(BLUE);
		right.add(count);
		long hits = attacks - zeros;
		JLabel pct = new JLabel(String.format(Locale.US, "%.1f%%", attacks > 0 ? 100.0 * hits / attacks : 0), SwingConstants.RIGHT);
		pct.setFont(FontManager.getRunescapeSmallFont());
		pct.setForeground(attacks > 0 && 100.0 * hits / attacks >= 75 ? GOOD : ColorScheme.MEDIUM_GRAY_COLOR);
		right.add(pct);
		row.add(right, BorderLayout.EAST);
		row.setToolTipText(accuracy(zeros, attacks));

		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				row.setBackground(CARD_HOVER);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				row.setBackground(CARD);
			}
		});
		return row;
	}
}
