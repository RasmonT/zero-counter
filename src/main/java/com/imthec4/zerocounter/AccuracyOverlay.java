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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Locale;
import javax.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/** Hits / attacks and the hit percentage for the session, today and in total. */
class AccuracyOverlay extends OverlayPanel
{
	private static final Color GOOD = new Color(0x4CAF50);
	private static final Color BAD = new Color(0xE57373);

	private final ZeroCounterPlugin plugin;
	private final ZeroCounterConfig config;

	@Inject
	AccuracyOverlay(ZeroCounterPlugin plugin, ZeroCounterConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		panelComponent.setPreferredSize(new Dimension(150, 0));
		for (ZeroCounterPlugin.Period period : ZeroCounterPlugin.Period.values())
		{
			addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Reset", "Accuracy " + period.getLabel().toLowerCase(Locale.ROOT),
				e -> plugin.confirmReset(period));
		}
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		ZeroData data = plugin.getData();
		Tally tally = plugin.getTally();
		if (!config.showAccuracy() || data == null || tally == null
			|| !(config.accuracySession() || config.accuracyToday() || config.accuracyTotal()))
		{
			return null;
		}
		plugin.rollDay();
		panelComponent.getChildren().add(TitleComponent.builder().text("Accuracy").color(Color.CYAN).build());
		if (config.accuracySession())
		{
			line("Session", tally.session);
		}
		if (config.accuracyToday())
		{
			line("Today", data.today);
		}
		if (config.accuracyTotal())
		{
			line("Total", data.total);
		}
		return super.render(graphics);
	}

	private void line(String left, ZeroData.Count count)
	{
		String right;
		Color color = Color.WHITE;
		if (count.attacks == 0)
		{
			right = "-";
		}
		else
		{
			double percent = 100.0 * count.hits() / count.attacks;
			right = format(count.hits(), count.attacks, percent);
			color = percent >= 75 ? GOOD : percent < 50 ? BAD : Color.WHITE;
		}
		panelComponent.getChildren().add(LineComponent.builder()
			.left(left)
			.right(right)
			.rightColor(color)
			.build());
	}

	/** "85.0% (68/80)" */
	static String format(long hits, long attacks, double percent)
	{
		return String.format(Locale.US, "%.1f%% (%,d/%,d)", percent, hits, attacks);
	}
}
