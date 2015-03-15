package me.planetguy.remaininmotion.core.interop.buildcraft;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import me.planetguy.remaininmotion.util.Position.BlockRecord;
import me.planetguy.remaininmotion.api.event.IBlockPos;
import me.planetguy.remaininmotion.api.event.TEPostPlaceEvent;
import me.planetguy.remaininmotion.api.event.TEPreUnpackEvent;
import me.planetguy.remaininmotion.spectre.TileEntityMotiveSpectre;
import net.minecraft.nbt.NBTTagCompound;
import buildcraft.core.TileBuildCraft;
import buildcraft.factory.TileQuarry;
import buildcraft.transport.Pipe;
import buildcraft.transport.PipeTransportItems;
import buildcraft.transport.TileGenericPipe;
import buildcraft.transport.TravelingItem;

public class EventHandlerBuildcraft {
	
	@SubscribeEvent
	public void onBCMoved(TEPreUnpackEvent e) {
		performBuildcraftPreInit((BlockRecord) e.location, ((TileEntityMotiveSpectre) e.spectre).getOffset((BlockRecord) e.location));
	}
	
	@SubscribeEvent
	public void onBCMoved(TEPostPlaceEvent e) {
		performBuildcraftPostInit((BlockRecord) e.location, ((TileEntityMotiveSpectre) e.spectre).getOffset((BlockRecord) e.location));
	}
	
	
    private void performBuildcraftPreInit(IBlockPos record, int[] offset) {
        if (record.entityTag().hasKey("box")) {
            NBTTagCompound boxNBT = record.entityTag().getCompoundTag("box");
            if (!boxNBT.hasNoTags()) {
                int xMax = boxNBT.getInteger("xMax");
                int xMin = boxNBT.getInteger("xMin");
                int yMax = boxNBT.getInteger("yMax");
                int yMin = boxNBT.getInteger("yMin");
                int zMax = boxNBT.getInteger("zMax");
                int zMin = boxNBT.getInteger("zMin");

                boxNBT.setInteger("xMax", xMax += offset[0]);
                boxNBT.setInteger("xMin", xMin += offset[0]);
                boxNBT.setInteger("yMax", yMax += offset[1]);
                boxNBT.setInteger("yMin", yMin += offset[1]);
                boxNBT.setInteger("zMax", zMax += offset[2]);
                boxNBT.setInteger("zMin", zMin += offset[2]);

            }
        }

        // reset states on filler and mining well (and anything based off it)
        if(record.entityTag().hasKey("done")) {
            record.entityTag().setBoolean("done", false);
        }
        if(record.entityTag().hasKey("digging")) {
            record.entityTag().setBoolean("digging", true);
        }
        // reset quarry
        if (record.entityTag().getString("id").equals("Machine")) {
            record.entityTag().setInteger("targetX", 0);
            record.entityTag().setInteger("targetY", 0);
            record.entityTag().setInteger("targetZ", 0);

            record.entityTag().setDouble("headPosX", 0.0D);
            record.entityTag().setDouble("headPosY", 0.0D);
            record.entityTag().setDouble("headPosZ", 0.0D);
        }
    }

    private void performBuildcraftPostInit(IBlockPos record, int[] offset) {
        try {

            if (record.entity() instanceof TileGenericPipe) {
                TileGenericPipe tile = (TileGenericPipe) record.entity();
                Pipe pipe = tile.pipe;
                if (!tile.initialized) {
                    tile.initialize(pipe);
                }

                if (pipe.transport instanceof PipeTransportItems) {
                    if (!((PipeTransportItems) pipe.transport).items.iterating) {
                        for (TravelingItem item : ((PipeTransportItems) pipe.transport).items) {
                            // to set up for correct displacement when
                            // teleporting
                            item.xCoord += offset[0];
                            item.yCoord += offset[1];
                            item.zCoord += offset[2];
                        }
                    }
                }
            } else if (record.entity() instanceof TileBuildCraft) {

                record.entity().invalidate();
                ((TileBuildCraft)record.entity()).initialize();
                if(record.entity() instanceof TileQuarry) {
                    ((TileQuarry) record.entity()).createUtilsIfNeeded();
                }

            }
        } catch (Throwable Throwable) {
            //Throwable.printStackTrace();
        }
    }

}