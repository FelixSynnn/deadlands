package vectorwing.farmersdelight.common.utility;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public class ClientRenderUtils {
    public static boolean isCursorInsideBounds(int iconX, int iconY, int iconWidth, int iconHeight, double cursorX, double cursorY) {
        return iconX <= cursorX && cursorX < iconX + iconWidth && iconY <= cursorY && cursorY < iconY + iconHeight;
    }

    public static Player getClientPlayerHack() {
        return Minecraft.getInstance().player;
    }
}
