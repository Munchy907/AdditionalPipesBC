package buildcraft.additionalpipes;

import java.util.*;

import buildcraft.core.client.BuildCraftLaserManager;
import buildcraft.lib.client.render.laser.LaserBoxRenderer;
import buildcraft.lib.misc.data.Box;
import com.google.common.collect.SetMultimap;

import buildcraft.additionalpipes.network.PacketHandler;
import buildcraft.additionalpipes.network.message.MessageChunkloadData;
import buildcraft.additionalpipes.network.message.MessageChunkloadRequest;
import buildcraft.additionalpipes.utils.Log;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ChunkLoadViewDataProxy implements Comparator<ChunkPos>{
	public static final int MAX_SIGHT_RANGE = 31;

	// used by server
	private int sightRange;

	// used by client
	private List<Box> laserBoxs;
	private Set<ChunkPos> persistentChunks;
	private boolean active = false;

	//private final Box box = new Box();

	public ChunkLoadViewDataProxy(int chunkSightRange) {
		setSightRange(chunkSightRange);
		laserBoxs = new ArrayList<Box>();
		persistentChunks = new HashSet<ChunkPos>();
		active = false;
	}
	
	private void addLasersToList(Box[] entityBlocks)
	{
		for(Box laser : entityBlocks)
		{
			laserBoxs.add(laser);
		}
	}

	// laser methods
	
	@SideOnly(Side.CLIENT)
	public void toggleLasers() {
		if(lasersActive()) {
			deactivateLasers();
		} else {
			activateLasers();
		}
	}

	@SideOnly(Side.CLIENT)
	public void activateLasers()
	{
		try
		{
/*			Log.info("persistent Chunks:" + persistentChunks);*/
			//TODO: Persistent Chunks is empty when it shouldn't be, so laser won't render
			deactivateLasers();
			EntityPlayerSP player = FMLClientHandler.instance().getClient().player;
			int playerY = (int) player.posY - 1;
			for(ChunkPos coords : persistentChunks) {

				//Log.info("chunk coords x - z: " + coords.x + " " + coords.z);
				//Log.info("chunk start x - z: " + coords.getXStart() + " " + coords.getZStart());
				//Log.info("chunk end x - z: " + coords.getXEnd() + " " + coords.getZEnd());

				BlockPos blockPosMin = new BlockPos(coords.getXStart(), playerY, coords.getZStart());
				BlockPos blockPosMax = new BlockPos(coords.getXEnd(), playerY, coords.getZEnd());
				Box laserBox = new Box(blockPosMin, blockPosMax);
				laserBoxs.add(laserBox);

/*				BlockPos northWestCorner = new BlockPos(coords.getXStart(), playerY, coords.getZStart());
				BlockPos northEastCorner =  new BlockPos(coords.getXEnd(), playerY, coords.getZStart());
				BlockPos southWestCorner =  new BlockPos(coords.getXStart(), playerY, coords.getZEnd());
				BlockPos southEastCorner =  new BlockPos(coords.getXEnd(), playerY, coords.getZEnd());*/
				//Log.info("NW BlockPos:" + northWestCorner);
				//Log.info("I should be rendering");

/*				box.extendToEncompass(northWestCorner);
				box.extendToEncompass(northEastCorner);
				box.extendToEncompass(southWestCorner);
				box.extendToEncompass(southEastCorner);*/
			}
			active = true;
		}
		catch(ConcurrentModificationException ex)
		{
			// it seems like updates and reads of persistentChunks can crash together sometimes.
			// we catch that here to prevent a game crash
			Log.error("ConcurrentModificationException activating lasers");
			ex.printStackTrace();
		}
	}

	@SideOnly(Side.CLIENT)
	public void deactivateLasers() {
		for(Box laserBox : laserBoxs) {
			laserBox.reset();
		}
		laserBoxs.clear();
		active = false;
	}

	@SideOnly(Side.CLIENT)
	public boolean lasersActive() {
		return active;
	}

	// packet methods

	@SideOnly(Side.CLIENT)
	public void requestPersistentChunks() {
		
		MessageChunkloadRequest message = new MessageChunkloadRequest();
		PacketHandler.INSTANCE.sendToServer(message);
	}

	@SideOnly(Side.CLIENT)
	public void receivePersistentChunks(Set<ChunkPos> chunks)
	{
		//Log.info("recieved Chunks: " + chunks);

		//if equal, nothing has changed. if not equal, something has changed
		boolean changed = !persistentChunks.equals(chunks);

		if(changed) {
			persistentChunks = chunks;
			if(active) {
				activateLasers();
			}
		}
	}

	// sets how far the server will search for chunkloaded chunks
	// when sending data to the player
	public void setSightRange(int range) {
		sightRange = range;
		if(sightRange > MAX_SIGHT_RANGE)
			sightRange = MAX_SIGHT_RANGE;

	}

	public void sendPersistentChunksToPlayer(EntityPlayerMP player)
	{
		if(sightRange > MAX_SIGHT_RANGE)
			sightRange = MAX_SIGHT_RANGE;

		SetMultimap<ChunkPos, Ticket> forgePersistentChunks = ForgeChunkManager.getPersistentChunksFor(player.getEntityWorld());
		HashSet<ChunkPos> chunksInRange = new HashSet<ChunkPos>();
		int playerX = (((int) player.posX) >> 4) - sightRange / 2, playerZ = (((int) player.posZ) >> 4) - sightRange / 2;

		// find all chunks in sight range
		for(int i = -sightRange; i <= sightRange; i++) {
			for(int j = -sightRange; j <= sightRange; j++) {
				ChunkPos coords = new ChunkPos(playerX + i, playerZ + j);
				if(forgePersistentChunks.containsKey(coords)) {
					chunksInRange.add(coords);
				}
			}
		}
		
		MessageChunkloadData message = new MessageChunkloadData(chunksInRange);
		
		PacketHandler.INSTANCE.sendTo(message, player);
		
		Log.debug("[ChunkLoadViewDataProxy] Sent chunks within " + sightRange + " of player.");
	}

	@SideOnly(Side.CLIENT)
	@SubscribeEvent
	public void tickEnd(WorldTickEvent event) {
		if(event.phase == Phase.END)
		{
			if(APConfiguration.chunkSightAutorefresh && lasersActive()) {
				requestPersistentChunks();
			}
		}
	}

	
	public String getLabel() {
		return getClass().getSimpleName();
	}

	public List<Box> getLaserBoxs() {
		return laserBoxs;
	}

	public int nextTickSpacing() {
		return 20 * 5;
	}

	// Comparator

	// first - other
	// assume non-null
	@Override
	public int compare(ChunkPos first, ChunkPos other) {
		int dx = first.x - other.x;
		return dx != 0 ? dx : first.z - other.z;
	}

	@SideOnly(Side.CLIENT)
	public void renderInWorld(EntityPlayer player, float partialTicks, Box box) {
/*		double px = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
		double py = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
		double pz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;*/
		Log.info("I should be rendering");
		LaserBoxRenderer.renderLaserBoxStatic(box, BuildCraftLaserManager.STRIPES_WRITE_DIRECTION, true);
	}
}
