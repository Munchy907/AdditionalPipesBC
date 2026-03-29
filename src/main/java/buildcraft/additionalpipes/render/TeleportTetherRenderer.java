package buildcraft.additionalpipes.render;

import buildcraft.additionalpipes.AdditionalPipes;
import buildcraft.lib.client.render.DetachedRenderer.IDetachedRenderer;
import buildcraft.lib.misc.data.Box;
import net.minecraft.entity.player.EntityPlayer;

import java.util.List;

public enum TeleportTetherRenderer implements IDetachedRenderer{
    INSTANCE;
    @Override
    public void render(EntityPlayer player, float partialTicks) {
        List<Box> laserBoxs = AdditionalPipes.instance.chunkLoadViewer.getLaserBoxs();
        for (Box laserBox : laserBoxs) {
            AdditionalPipes.instance.chunkLoadViewer.renderInWorld(player, partialTicks, laserBox);
        }
    }
}
