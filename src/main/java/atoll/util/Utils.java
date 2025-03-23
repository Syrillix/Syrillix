package atoll.util;

import net.minecraft.client.Minecraft;

public class Utils {
    public static boolean canUpdate() {
        return Minecraft.getMinecraft().thePlayer != null && Minecraft.getMinecraft().theWorld != null;
    }
}
