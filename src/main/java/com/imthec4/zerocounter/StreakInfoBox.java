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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.annotation.Nullable;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.infobox.InfoBox;

/**
 * The game's blue zero hitsplat with the current streak of zeros drawn in the middle, like a
 * real hitsplat. Hidden while the streak is 0 unless "Always show" is on.
 */
class StreakInfoBox extends InfoBox
{
	private static final Color SPLAT = new Color(0x2E4FC9);
	private static final Color SPLAT_EDGE = new Color(0x0A1A66);

	private final ZeroCounterConfig config;

	/** The hitsplat sprite from the game cache; null until it has loaded. */
	@Nullable
	private BufferedImage sprite;
	private long streak;
	private long best;
	/** A character is logged in and loaded. */
	private boolean active;

	StreakInfoBox(Plugin plugin, ZeroCounterConfig config)
	{
		super(draw(null, 0), plugin);
		this.config = config;
	}

	void setSprite(BufferedImage sprite)
	{
		this.sprite = sprite;
		setImage(draw(sprite, streak));
	}

	/** Returns true if the picture changed and the info box manager needs to know. */
	boolean update(long streak, long best, boolean active)
	{
		this.best = best;
		this.active = active;
		if (streak == this.streak)
		{
			return false;
		}
		this.streak = streak;
		setImage(draw(sprite, streak));
		return true;
	}

	@Override
	public boolean render()
	{
		return active && config.showStreak() && (streak > 0 || config.alwaysShowStreak());
	}

	@Override
	public String getText()
	{
		return ""; // the number is part of the picture
	}

	@Override
	public Color getTextColor()
	{
		return Color.WHITE;
	}

	@Override
	public String getTooltip()
	{
		return "Zeros in a row: " + streak + "</br>Best: " + best;
	}

	/** Splat (the game's sprite, or a plain blue blob until it loads) with the number centred. */
	static BufferedImage draw(@Nullable BufferedImage sprite, long number)
	{
		int w = sprite != null ? Math.max(sprite.getWidth(), 24) : 26;
		int h = sprite != null ? Math.max(sprite.getHeight(), 24) : 26;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		try
		{
			if (sprite != null)
			{
				g.drawImage(sprite, (w - sprite.getWidth()) / 2, (h - sprite.getHeight()) / 2, null);
			}
			else
			{
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setColor(SPLAT);
				g.fillOval(2, 2, w - 4, h - 4);
				g.setColor(SPLAT_EDGE);
				g.setStroke(new BasicStroke(2));
				g.drawOval(2, 2, w - 4, h - 4);
			}

			String text = String.valueOf(number);
			Font font = FontManager.getRunescapeSmallFont();
			FontMetrics fm = g.getFontMetrics(font);
			if (fm.stringWidth(text) > w - 2)
			{
				font = font.deriveFont(font.getSize2D() * (w - 2) / fm.stringWidth(text));
				fm = g.getFontMetrics(font);
			}
			g.setFont(font);
			int x = (w - fm.stringWidth(text)) / 2;
			int y = (h - fm.getHeight()) / 2 + fm.getAscent();
			g.setColor(Color.BLACK);
			g.drawString(text, x + 1, y + 1);
			g.setColor(Color.WHITE);
			g.drawString(text, x, y);
		}
		finally
		{
			g.dispose();
		}
		return img;
	}
}
