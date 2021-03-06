package me.planetguy.remaininmotion.spectre;

import me.planetguy.lib.util.Debug;
import me.planetguy.lib.util.SneakyWorldUtil;
import me.planetguy.remaininmotion.motion.CarriagePackage;
import me.planetguy.remaininmotion.api.RiMRegistry;
import me.planetguy.remaininmotion.api.event.*;
import me.planetguy.remaininmotion.base.BlockCamouflageable;
import me.planetguy.remaininmotion.base.TileEntityRiM;
import me.planetguy.remaininmotion.core.ModRiM;
import me.planetguy.remaininmotion.core.RIMBlocks;
import me.planetguy.remaininmotion.core.RiMConfiguration;
import me.planetguy.remaininmotion.core.RiMConfiguration.CarriageMotion;
import me.planetguy.remaininmotion.core.interop.EventPool;
import me.planetguy.remaininmotion.drive.BlockCarriageDrive;
import me.planetguy.remaininmotion.drive.TileEntityCarriageDrive;
import me.planetguy.remaininmotion.render.CarriageRenderCache;
import me.planetguy.remaininmotion.util.position.BlockPosition;
import me.planetguy.remaininmotion.util.position.BlockRecord;
import me.planetguy.remaininmotion.util.position.BlockRecordSet;
import me.planetguy.remaininmotion.util.transformations.Directions;
import me.planetguy.remaininmotion.util.transformations.Matrix;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class TileEntityMotiveSpectre extends TileEntityRiM {
    public static Block MultipartContainerBlockId;
    public Directions motionDirection = Directions.Null;
    public BlockPosition renderCacheKey;
    public NBTTagList pendingBlockUpdates;
    public BlockRecord driveRecord;
    public boolean driveIsAnchored;
    public BlockRecordSet body;
    public HashSet<BlockRecord> spectersToDestroy;
    public int ticksExisted = 0;

    public java.util.ArrayList<CapturedEntity> CapturedEntities = new ArrayList<CapturedEntity>();
    private boolean initialized;

    // Elevator mode
    public int personalDurationInTicks = CarriageMotion.MotionDuration;
    public double personalVelocity = 1 / ((double) personalDurationInTicks);

    public TileEntityMotiveSpectre() {
        super();
    }

    public void ShiftBlockPosition(BlockRecord Record) {
        Record.Shift(motionDirection);
    }

    public void ScheduleShiftedBlockUpdate(
            NBTTagCompound PendingBlockUpdateRecord) {
        worldObj.func_147446_b // scheduleBlockUpdateFromLoad
                (PendingBlockUpdateRecord.getInteger("X") + motionDirection.deltaX,
                        PendingBlockUpdateRecord.getInteger("Y")
                                + motionDirection.deltaY,
                        PendingBlockUpdateRecord.getInteger("Z")
                                + motionDirection.deltaZ,

                        Block.getBlockById(PendingBlockUpdateRecord.getInteger("Id")),

                        PendingBlockUpdateRecord.getInteger("Delay"),

                        PendingBlockUpdateRecord.getInteger("Priority"));
    }

    @Override
    public void updateEntity() {
        worldObj.theProfiler.startSection("RiMMotiveSpecter");
        ticksExisted++;

        for (CapturedEntity Entity : CapturedEntities) {
            worldObj.theProfiler.startSection("EntityMovement");
            Entity.Update();
            worldObj.theProfiler.endSection();
        }

        if (worldObj.isRemote) {
            worldObj.theProfiler.endSection();
            return;
        }

        if (ticksExisted > 0
                && ticksExisted < personalDurationInTicks
                && ticksExisted % 20 == 0) {
            if (bodyHasCarriageDrive()) {
                ModRiM.plHelper.playSound(worldObj, xCoord, yCoord, zCoord,
                        CarriageMotion.SoundFile, CarriageMotion.volume, 1f);
            }
        }

        if (ticksExisted < personalDurationInTicks) {
            worldObj.theProfiler.endSection();
            return;
        }

        worldObj.theProfiler.startSection("Release");
        Release();
        worldObj.theProfiler.endSection();

        worldObj.theProfiler.endSection();
    }

    private boolean bodyHasCarriageDrive() {
        if (body == null || body.isEmpty()) {
            return false;
        }
        for (BlockRecord temp : body) {
            if (temp.block instanceof BlockCarriageDrive) {
                return true;
            }
            if (temp.entity != null
                    && temp.entity instanceof TileEntityCarriageDrive) {
                return true;
            }
        }
        return false;
    }

    public void Release() {

        if (body != null && !body.isEmpty()) {
            for (BlockRecord record : body) {
                ShiftBlockPosition(record);
            }
            doRelease();
            body = new BlockRecordSet(); // clear list - prevents giga-dupe with
            // Gizmos temporal dislocator
            if(!spectersToDestroy.isEmpty())
                spectersToDestroy = new HashSet<BlockRecord>();
        }
    }
    
    public void doRelease() {

        if(!spectersToDestroy.isEmpty()) {
            for (BlockRecord Record : spectersToDestroy) {
                SneakyWorldUtil.setBlock(worldObj, Record.X, Record.Y,
                        Record.Z, Blocks.air, 0);
            }
        }

        for (BlockRecord record : body) {
            record.World = worldObj;
            SneakyWorldUtil.setBlock(worldObj, record.X, record.Y, record.Z,
                    record.block, record.Meta);
        }
        
        EventPool.postBlocksReplacedEvent(this);

        for (BlockRecord record : body) {

            int[] offset = getOffset();
            
            if (record.entityRecord != null) {
                constructTE(record);

            }

        }


        try {
            TileEntityCarriageDrive Drive = (TileEntityCarriageDrive) worldObj
                    .getTileEntity(driveRecord.X, driveRecord.Y, driveRecord.Z);

            if (!driveIsAnchored) {
                Drive.Active = true;
            }

            Drive.ToggleActivity();
        } catch (Throwable Throwable) {
            // Throwable . printStackTrace ( ) ;
        }
        
        SneakyWorldUtil.refreshBlock(worldObj, xCoord, yCoord, zCoord,
                RIMBlocks.Spectre, Blocks.air);

        for (BlockRecord Record : body) {
        	onMotionFinalized(Record);
            SneakyWorldUtil.refreshBlock(worldObj, Record.X, Record.Y,
                    Record.Z, Blocks.air, Record.block);
            
        }

        if(!spectersToDestroy.isEmpty()){
            for (BlockRecord Record : spectersToDestroy) {
                SneakyWorldUtil.refreshBlock(worldObj, Record.X, Record.Y,
                        Record.Z, Blocks.air, Blocks.air);
            }
        }

        int PendingBlockUpdateCount = pendingBlockUpdates.tagCount();

        for (int Index = 0; Index < PendingBlockUpdateCount; Index++) {
            ScheduleShiftedBlockUpdate(pendingBlockUpdates
                    .getCompoundTagAt(Index));

        }

        for (BlockRecord record : body) {
            //If we don't onBlockAdded() anyway, why post events?
        	//EventPool.postCancelableOnBlockAddedEvent(worldObj,record.X,record.Y,record.Z);
            //if(!event.isCanceled()) 
            //	record.block.onBlockAdded(worldObj,record.X,record.Y,record.Z);
            EventPool.postMotionFinalizeEvent(record);
        }

        cleanupSpecter();

    }

    public void cleanupSpecter()
    {
        if (worldObj.getBlock(xCoord, yCoord, zCoord) == RIMBlocks.Spectre) {
            worldObj.setBlock(xCoord, yCoord, zCoord, Blocks.air);
        }
    }

    public int[] getOffset() {
        return new int[]{motionDirection.deltaX, motionDirection.deltaY,
                motionDirection.deltaZ};
    }

    public int[] getOffset(BlockRecord record) {
        return getOffset();
    }
    
    //we must keep this out so rotating spectre can overload it
    public void announceTEConstruction(BlockRecord record) {
    	EventPool.postTEPreUnpackEvent(this, record);
    }
    
    public void constructTE(BlockRecord record) {
    	record.entityRecord.setInteger("x", record.X);
        record.entityRecord.setInteger("y", record.Y);
        record.entityRecord.setInteger("z", record.Z);

        announceTEConstruction(record);

        //Sometimes (eg. FMP) normal TileEntity loading crashes. This lets TEPrePlaceEvent pick up the pieces.
        try{
        	record.entity = TileEntity
        			.createAndLoadEntity(record.entityRecord);
        }catch(Exception ignored){}
        
        EventPool.postTEPrePlaceEvent(this, record);

        if (record.entity != null) {
        	SneakyWorldUtil.setTileEntity(worldObj, record.X, record.Y,
                    record.Z, record.entity);
        }
        
        EventPool.postTEPostPlaceEvent(this, record);
    }

    public void onMotionFinalized(BlockRecord Record) {

    }
    
    @Override
    public void Finalize() {
        if (worldObj.isRemote) {
            CarriageRenderCache.Release(renderCacheKey);
        }
    }
    
    @Override
    public void WriteCommonRecord(NBTTagCompound TagCompound) {
        TagCompound.setInteger("motion", motionDirection.ordinal());

        if(renderCacheKey != null) {

        	TagCompound.setInteger("RenderCacheKeyX", renderCacheKey.X);
        	TagCompound.setInteger("RenderCacheKeyY", renderCacheKey.Y);
        	TagCompound.setInteger("RenderCacheKeyZ", renderCacheKey.Z);

        	TagCompound.setInteger("RenderCacheKeyD", renderCacheKey.Dimension);

        }
        
        if(driveRecord != null) {
            NBTTagCompound tag = new NBTTagCompound();
            driveRecord.writeToNBT(tag);
            TagCompound.setTag("driveRecord", tag);
        }

        // Elevator Mode
        // in common to ensure rendering is synced
        // should also fix #118
        TagCompound.setInteger("PersonalDuration", personalDurationInTicks);
    }

    @Override
    public void ReadCommonRecord(NBTTagCompound TagCompound) {
        motionDirection = Directions.values()[TagCompound.getInteger("motion")];

        renderCacheKey = new BlockPosition(
                TagCompound.getInteger("RenderCacheKeyX"),
                TagCompound.getInteger("RenderCacheKeyY"),
                TagCompound.getInteger("RenderCacheKeyZ"),
                TagCompound.getInteger("RenderCacheKeyD"));
        
        if(TagCompound.hasKey("driveRecord")) {
        	NBTTagCompound drt=TagCompound.getCompoundTag("driveRecord");
            driveRecord = new BlockRecord(drt.getInteger("DriveX"),
                    drt.getInteger("DriveY"),
                    drt.getInteger("DriveZ"));
        }

        if(TagCompound.hasKey("driveRecord")) {
            driveRecord = BlockRecord.createFromNBT(TagCompound.getCompoundTag("driveRecord"));
        }

        personalDurationInTicks = TagCompound.getInteger("PersonalDuration");
        personalVelocity = 1 / ((double) personalDurationInTicks);
    }

    @Override
    public void WriteServerRecord(NBTTagCompound TagCompound) {

        TagCompound.setBoolean("driveIsAnchored", driveIsAnchored);

        TagCompound.setInteger("ticksExisted", ticksExisted);

        // Don't need to send this whole thing over network all the time
        TagCompound.setTag("pendingBlockUpdates", pendingBlockUpdates);

        NBTTagList BodyRecord = new NBTTagList();

        if(body != null && !body.isEmpty()) {
            for (BlockRecord Record : body) {
                NBTTagCompound BodyBlockRecord = new NBTTagCompound();

                Record.writeToNBT(BodyBlockRecord);

                BodyRecord.appendTag(BodyBlockRecord);

            }
        }
        TagCompound.setTag("Body", BodyRecord);

        BodyRecord = new NBTTagList();

        if(spectersToDestroy != null && !spectersToDestroy.isEmpty()) {
            for (BlockRecord Record : spectersToDestroy) {
                NBTTagCompound BodyBlockRecord = new NBTTagCompound();

                Record.writeToNBT(BodyBlockRecord);

                BodyRecord.appendTag(BodyBlockRecord);

            }

            TagCompound.setTag("spectersToDestroy", BodyRecord);
        }
    }

    @Override
    public void ReadServerRecord(NBTTagCompound TagCompound) {

        driveIsAnchored = TagCompound.getBoolean("driveIsAnchored");

        ticksExisted = TagCompound.getInteger("ticksExisted");

        // Don't need to send this whole thing over network all the time
        pendingBlockUpdates = TagCompound.getTagList("pendingBlockUpdates", 10);

        body = new BlockRecordSet();

        NBTTagList BodyRecord = TagCompound.getTagList("Body", 10);

        int BodyBlockCount = BodyRecord.tagCount();

        for (int Index = 0; Index < BodyBlockCount; Index++) {
            NBTTagCompound BodyBlockRecord = BodyRecord.getCompoundTagAt(Index);

            BlockRecord Record = BlockRecord.createFromNBT(BodyBlockRecord);
            body.add(Record);
        }

        spectersToDestroy = new HashSet<BlockRecord>();

        if(TagCompound.hasKey("spectersToDestroy")) {
            BodyRecord = TagCompound.getTagList("spectersToDestroy", 10);

            BodyBlockCount = BodyRecord.tagCount();

            for (int Index = 0; Index < BodyBlockCount; Index++) {
                NBTTagCompound BodyBlockRecord = BodyRecord.getCompoundTagAt(Index);

                BlockRecord Record = BlockRecord.createFromNBT(BodyBlockRecord);
                spectersToDestroy.add(Record);
            }
        }
    }

    @Override
    public void WriteClientRecord(NBTTagCompound TagCompound) {
        NBTTagList CapturedEntityRecords = new NBTTagList();

        int i = 0;

        for (CapturedEntity Entity : CapturedEntities) {
            if (i++ == RiMConfiguration.Cosmetic.maxTags) { // not >= to allow
                // no
                // limit (eg.
                // singleplayer
                // only)
                break;
            }
            NBTTagCompound CapturedEntityRecord = new NBTTagCompound();

            CapturedEntityRecord.setInteger("Id", Entity.entity.getEntityId());

            CapturedEntityRecord.setDouble("InitialX", Entity.InitialX);
            CapturedEntityRecord.setDouble("InitialY", Entity.InitialY);
            CapturedEntityRecord.setDouble("InitialZ", Entity.InitialZ);

            CapturedEntityRecord.setDouble("netMotionX", Entity.netMotionX);
            CapturedEntityRecord.setDouble("netMotionY", Entity.netMotionY);
            CapturedEntityRecord.setDouble("netMotionZ", Entity.netMotionZ);

            CapturedEntityRecords.appendTag(CapturedEntityRecord);
        }

        if (RiMConfiguration.Debug.verbose) {
            Debug.dbg("Captured " + i + " tile entities.");
        }

        TagCompound.setTag("CapturedEntities", CapturedEntityRecords);
    }

    @Override
    public void ReadClientRecord(net.minecraft.nbt.NBTTagCompound TagCompound) {
        net.minecraft.nbt.NBTTagList CapturedEntityRecords = TagCompound
                .getTagList("CapturedEntities", 10);

        CapturedEntities.clear();

        int CapturedEntityCount = CapturedEntityRecords.tagCount();

        for (int Index = 0; Index < CapturedEntityCount; Index++) {
            net.minecraft.nbt.NBTTagCompound EntityRecord = CapturedEntityRecords
                    .getCompoundTagAt(Index);

            net.minecraft.entity.Entity Entity = worldObj
                    .getEntityByID(EntityRecord.getInteger("Id"));

            if (Entity == null) {
                continue;
            }

            CapturedEntities.add(new CapturedEntity(Entity, EntityRecord
                    .getDouble("InitialX"), EntityRecord.getDouble("InitialY"),
                    EntityRecord.getDouble("InitialZ"), EntityRecord.getDouble("netMotionX"), EntityRecord.getDouble("netMotionY"), EntityRecord.getDouble("netMotionZ")));
        }
    }

    public void fixLagError(CapturedEntity capture, Entity entity) {
        double motionX = 0;
        double motionZ = 0;

        motionX = fixDouble(motionX, motionDirection.deltaX, capture.netMotionX, true);
        motionZ = fixDouble(motionZ, motionDirection.deltaZ, capture.netMotionZ, true);

        if(motionDirection.deltaY != 0){
            entity.onGround = capture.WasOnGround;
            entity.isAirBorne = capture.WasAirBorne;
            capture.SetPosition(motionX, 0, motionZ);
            // try to fix double precision errors
            // we only do positive as it's okay to be 0.025 up and fall
            capture.SetYPosition(motionDirection.deltaY + 0.025);
        } else {
            if(motionX != 0 || motionZ != 0) {
                double[] motion = handleEntityCollision(entity, motionX, motionZ);
                capture.netMotionX += motion[0];
                capture.netMotionZ += motion[1];
            }
        }

    }

    /*
        TODO Separate Client and Server Movement (Player vs everything else)
     */

    public void doPerSpectreEntityUpdate(CapturedEntity capture, Entity entity) {
        entity.fallDistance = 0;

        if(motionDirection.deltaX != capture.netMotionX || motionDirection.deltaZ != capture.netMotionZ || motionDirection.deltaY != capture.netMotionY) {
            double motionX = personalVelocity * (double) motionDirection.deltaX;
            double motionY = personalVelocity * (double) motionDirection.deltaY;
            double motionZ = personalVelocity * (double) motionDirection.deltaZ;

            motionX = fixDouble(motionX, (double) motionDirection.deltaX, capture.netMotionX + motionX, false);
            motionZ = fixDouble(motionZ, (double) motionDirection.deltaZ, capture.netMotionZ + motionZ, false);

            if (motionDirection.deltaY != 0) {
                entity.onGround = capture.WasOnGround;
                entity.isAirBorne = capture.WasAirBorne;
                capture.netMotionY += motionY;
                capture.SetPosition(motionX, 0, motionZ);
                capture.SetYPosition(capture.netMotionY);
            } else {
                double[] motion = handleEntityCollision(entity, motionX, motionZ);

                motionX = motion[0];
                motionZ = motion[1];

                capture.netMotionX += motionX;
                capture.netMotionZ += motionZ;
            }
        }

        if (ticksExisted >= personalDurationInTicks) {
            fixLagError(capture, entity);
            capture.stop();
            return;
        }
    }

    private double[] handleEntityCollision(Entity entity, double motionX, double motionZ) {
        double X = motionX;
        double Z = motionZ;
        List list = this.worldObj.getCollidingBoundingBoxes(entity, entity.boundingBox.addCoord(X, 0, Z));
        int j;

        for (j = 0; j < list.size(); ++j)
        {
            X = ((AxisAlignedBB)list.get(j)).calculateXOffset(entity.boundingBox, X);
        }

        entity.boundingBox.offset(X, 0.0D, 0.0D);

        for (j = 0; j < list.size(); ++j)
        {
            Z = ((AxisAlignedBB)list.get(j)).calculateZOffset(entity.boundingBox, Z);
        }

        entity.boundingBox.offset(0.0D, 0.0D, Z);

        entity.posX = (entity.boundingBox.minX + entity.boundingBox.maxX) / 2.0D;
        entity.posZ = (entity.boundingBox.minZ + entity.boundingBox.maxZ) / 2.0D;
        entity.isCollidedHorizontally = motionX != X || motionZ != Z;
        entity.isCollided = entity.isCollidedHorizontally || entity.isCollidedVertically;

        motionX = X;
        motionZ = Z;

        return new double[] {motionX,motionZ};
    }

    private double fixDouble(double progress, double goal, double net, boolean forceBothWays) {
        double out = progress;
        if(net == goal) return 0;
        if(forceBothWays){
            if(net < goal){
                out = goal - net;
            }
        }
        if (Math.abs(net) > Math.abs(goal)) {
            out = goal - net;
        }

        return out;
    }

    public boolean ShouldCaptureEntity(Entity Entity) {
        if (Entity instanceof EntityPlayer) {
            return (RiMConfiguration.CarriageMotion.CapturePlayerEntities);
        }

        if (Entity instanceof EntityLiving) {
            return (RiMConfiguration.CarriageMotion.CaptureOtherLivingEntities);
        }

        if (Entity instanceof EntityItem) {
            return (RiMConfiguration.CarriageMotion.CaptureItemEntities);
        }

        return (RiMConfiguration.CarriageMotion.CaptureOtherEntities);
    }

    public void ProcessCapturedEntity(Entity Entity) {
        CapturedEntities.add(new CapturedEntity(Entity));
    }

    public void CaptureEntities(int MinX, int MinY, int MinZ, int MaxX,
                                int MaxY, int MaxZ) {

        AxisAlignedBB EntityCaptureBox = AxisAlignedBB.getBoundingBox(MinX - 5,
                MinY - 5, MinZ - 5, MaxX + 5, MaxY + 5, MaxZ + 5);

        List EntitiesFound = worldObj.getEntitiesWithinAABB(Entity.class,
                EntityCaptureBox);

        for (Object EntityObject : EntitiesFound) {
            Entity entity = (Entity) EntityObject;

            BlockRecord PositionCheck = new BlockRecord(
                    (int) Math.floor(entity.posX),
                    (int) Math.floor(entity.posY),
                    (int) Math.floor(entity.posZ));

            if (!body.contains(PositionCheck)) {
                PositionCheck.Y--;

                if (!body.contains(PositionCheck)) {
                    PositionCheck.Y--;

                    if (!body.contains(PositionCheck)) {
                        entity = null;
                    }
                }
            }

            if (entity == null) {
                continue;
            }

            if (ShouldCaptureEntity(entity)) {
                try {
                    ProcessCapturedEntity(entity);
                } catch (Throwable Throwable) {
                    Throwable.printStackTrace();
                }
            }
        }
    }

    public void Absorb(CarriagePackage Package) {
        motionDirection = Package.MotionDirection;

        body = Package.Body;

        spectersToDestroy = Package.spectersToDestroy;

        renderCacheKey = Package.RenderCacheKey;

        pendingBlockUpdates = Package.PendingBlockUpdates;

        driveRecord = new BlockRecord(Package.driveRecord);

        if (!Package.DriveIsAnchored) {
            driveRecord.Shift(Package.MotionDirection);
        }

        if (Package.MotionDirection != null) {
            CaptureEntities(Package.MinX, Package.MinY, Package.MinZ,
                    Package.MaxX, Package.MaxY, Package.MaxZ);
        }
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return (INFINITE_EXTENT_AABB);
    }

    @Override
    public boolean shouldRenderInPass(int Pass) {
        return (true);
    }

    @Override
    public boolean canUpdate() {
        return true;
    }

    public int getLightValue()
    {
        Block b = null;
        NBTTagCompound nbt = null;
        if(body == null) return RIMBlocks.Spectre.getLightValue();
        for(BlockRecord temp : body){
            if(temp.X == xCoord && temp.Y == yCoord && temp.Z == zCoord)
            {
                b = temp.block;
                nbt = temp.entityRecord;
            }
        }
        if(b instanceof  BlockCamouflageable) {
            if(nbt != null) {
                Block b2 = Block.getBlockById(nbt.getInteger("DecorationId"));
                if(b2 != null){
                    return b2.getLightValue();
                }
            }
        }
        if(b != null) return b.getLightValue();
        return RIMBlocks.Spectre.getLightValue();
    }

    public int getLightOpacity()
    {
        Block b = null;
        NBTTagCompound nbt = null;
        if(body == null) return RIMBlocks.Spectre.getLightOpacity();
        for(BlockRecord temp : body){
            if(temp.X == xCoord && temp.Y == yCoord && temp.Z == zCoord)
            {
                b = temp.block;
                nbt = temp.entityRecord;
                break;
            }
        }
        if(b instanceof  BlockCamouflageable) {
            if(nbt != null) {
                Block b2 = Block.getBlockById(nbt.getInteger("DecorationId"));
                if(b2 != null){
                    return b2.getLightOpacity();
                }
            }
        }
        if(b != null) return b.getLightOpacity();
        return RIMBlocks.Spectre.getLightOpacity();
    }

    public class CapturedEntity {
        public Entity entity;

        public double InitialX;
        public double InitialY;
        public double InitialZ;
        
        public Matrix startingPosition;

        public double netMotionX = 0;
        public double netMotionY = 0;
        public double netMotionZ = 0;

        boolean WasOnGround;

        boolean WasAirBorne;

        public CapturedEntity(Entity entity) {
            this(entity, entity.posX, entity.posY, entity.posZ, 0, 0, 0);
        }

        public CapturedEntity(Entity entity, double InitialX, double InitialY,
                              double InitialZ, double netMotionX, double netMotionY, double netMotionZ) {
            this.entity = entity;

            this.InitialX = InitialX;
            this.InitialY = InitialY;
            this.InitialZ = InitialZ;

            this.netMotionX = netMotionX;
            this.netMotionY = netMotionY;
            this.netMotionZ = netMotionZ;

            WasOnGround = entity.onGround;

            WasAirBorne = entity.isAirBorne;

            Update();
        }

        public void SetPosition(double OffsetX, double OffsetY, double OffsetZ) {
            entity.setPosition(entity.posX, entity.posY, entity.posZ);
        }

        public void SetYPosition(double OffsetY) {
            entity.setPosition(entity.posX, InitialY + entity.yOffset + OffsetY, entity.posZ);
        }

        public void Update() {
            doPerSpectreEntityUpdate(this, entity);
        }

        public void stop() {
            entity.lastTickPosX = entity.prevPosX = entity.posX;
            entity.lastTickPosY = entity.prevPosY = entity.posY;
            entity.lastTickPosZ = entity.prevPosZ = entity.posZ;
            
            entity.motionX=entity.motionY=entity.motionZ=0;
        }
    }


}
