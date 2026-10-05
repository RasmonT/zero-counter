package com.imthec4.zerocounter;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ZeroCounterLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ZeroCounterPlugin.class);
		RuneLite.main(args);
	}
}
