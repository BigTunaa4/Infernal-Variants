package com.infernalvariants;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class InfernalVariantsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(InfernalVariantsPlugin.class);
		RuneLite.main(args);
	}
}
