package com.infernalvariants;

/**
 * Emblem anchor points on the infernal cape's back panel, measured offline from the
 * cape's bind-pose geometry (male and female worn models). Each anchor is three surface
 * points (centre, a step sideways, a step down), each stored as
 * {textured face index, barycentric w0, w1, w2}. Because the points ride on the
 * animated faces, emblems follow the cape as it sways.
 */
final class CapeAnchors
{
	private CapeAnchors()
	{
	}

	static final String[] NAMES = {"C1", "C2", "C3", "CS", "CL", "LS", "RS", "LL", "RL"};

	static final float[][][] MALE = {
		{{15, 0.3000f, 0.3000f, 0.4000f}, {15, 0.4923f, 0.1077f, 0.4000f}, {15, 0.3714f, 0.3714f, 0.2571f}}, // C1
		{{14, 0.3158f, 0.3684f, 0.3158f}, {14, 0.5081f, 0.3684f, 0.1235f}, {14, 0.2500f, 0.5000f, 0.2500f}}, // C2
		{{25, 0.1538f, 0.0000f, 0.8462f}, {26, 0.1538f, 0.6378f, 0.2083f}, {25, 0.3462f, 0.0000f, 0.6538f}}, // C3
		{{14, 0.5000f, 0.0000f, 0.5000f}, {14, 0.6923f, 0.0000f, 0.3077f}, {14, 0.4342f, 0.1316f, 0.4342f}}, // CS
		{{25, 0.3846f, 0.0000f, 0.6154f}, {26, 0.3846f, 0.4071f, 0.2083f}, {25, 0.5769f, 0.0000f, 0.4231f}}, // CL
		{{19, 0.5285f, 0.0529f, 0.4186f}, {17, 0.1145f, 0.5267f, 0.3588f}, {19, 0.6342f, 0.0634f, 0.3023f}}, // LS
		{{10, 0.5412f, 0.4324f, 0.0265f}, {11, 0.4376f, 0.4186f, 0.1438f}, {16, 0.6565f, 0.0992f, 0.2443f}}, // RS
		{{20, 0.0000f, 0.6842f, 0.3158f}, {27, 0.6206f, 0.3158f, 0.0636f}, {20, 0.0000f, 0.8158f, 0.1842f}}, // LL
		{{5, 0.4235f, 0.2245f, 0.3520f}, {5, 0.5893f, 0.0714f, 0.3393f}, {5, 0.5765f, 0.2755f, 0.1480f}}, // RL
	};

	static final float[][][] FEMALE = {
		{{15, 0.2059f, 0.2059f, 0.5882f}, {15, 0.4142f, -0.0025f, 0.5882f}, {15, 0.2794f, 0.2794f, 0.4412f}}, // C1
		{{14, 0.3889f, 0.2222f, 0.3889f}, {14, 0.5972f, 0.2222f, 0.1806f}, {14, 0.3194f, 0.3611f, 0.3194f}}, // C2
		{{13, 0.0000f, 0.0000f, 1.0000f}, {13, 0.0000f, 0.2174f, 0.7826f}, {25, 0.2000f, 0.0000f, 0.8000f}}, // C3
		{{15, 0.4118f, 0.4118f, 0.1765f}, {15, 0.6201f, 0.2034f, 0.1765f}, {15, 0.4853f, 0.4853f, 0.0294f}}, // CS
		{{25, 0.2400f, 0.0000f, 0.7600f}, {26, 0.2400f, 0.5426f, 0.2174f}, {25, 0.4400f, 0.0000f, 0.5600f}}, // CL
		{{19, 0.2987f, 0.1299f, 0.5714f}, {18, 0.3855f, 0.1627f, 0.4518f}, {19, 0.4069f, 0.1407f, 0.4524f}}, // LS
		{{10, 0.3976f, 0.3253f, 0.2771f}, {11, 0.2078f, 0.5714f, 0.2208f}, {10, 0.5181f, 0.3102f, 0.1717f}}, // RS
		{{21, 0.4444f, 0.4127f, 0.1429f}, {20, 0.3636f, 0.5556f, 0.0808f}, {21, 0.3056f, 0.5516f, 0.1429f}}, // LL
		{{5, 0.3443f, 0.1632f, 0.4925f}, {5, 0.5158f, 0.0055f, 0.4787f}, {5, 0.5021f, 0.2181f, 0.2798f}}, // RL
	};

	static int indexOf(String name)
	{
		for (int i = 0; i < NAMES.length; i++)
		{
			if (NAMES[i].equals(name))
			{
				return i;
			}
		}
		return -1;
	}
}
