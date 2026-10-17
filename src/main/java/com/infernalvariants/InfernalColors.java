package com.infernalvariants;

/**
 * The untextured model colours (Jagex HSL) used by the worn infernal cape and infernal max cape
 * models. They are registered as "recolourable" on the cape items so the plugin can repaint the
 * rock trim, hem accents and inner lining per player through colour overrides.
 */
final class InfernalColors
{
	private InfernalColors()
	{
	}

	static final short[] ALL = {
		// infernal cape (male 33103 / female 33111): rock greys
		8, 12, 16, 20, 24, 28,
		// infernal cape: inner lining and hem accents
		924, 3005, 5056, 5066,
		// infernal max cape (33102) extras: greys, then red accents
		33, 41, (short) 43057, 522, 902, 945, 960, 968, 978,
	};
}
