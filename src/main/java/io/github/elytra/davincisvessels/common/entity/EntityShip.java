package io.github.elytra.davincisvessels.common.entity;

import io.github.elytra.davincisvessels.DavincisVesselsMod;
import io.github.elytra.davincisvessels.client.control.ShipControllerClient;
import io.github.elytra.davincisvessels.common.DavincisVesselsConfig;
import io.github.elytra.davincisvessels.common.api.tileentity.ITileEngineModifier;
import io.github.elytra.davincisvessels.common.control.ShipControllerCommon;
import io.github.elytra.davincisvessels.common.object.DavincisVesselsObjects;
import io.github.elytra.davincisvessels.common.tileentity.TileEntityHelm;
import io.github.elytra.movingworld.common.chunk.LocatedBlock;
import io.github.elytra.movingworld.common.chunk.MovingWorldAssemblyInteractor;
import io.github.elytra.movingworld.common.chunk.assembly.AssembleResult;
import io.github.elytra.movingworld.common.chunk.assembly.ChunkDisassembler;
import io.github.elytra.movingworld.common.entity.EntityMovingWorld;
import io.github.elytra.movingworld.common.entity.MovingWorldCapabilities;
import io.github.elytra.movingworld.common.entity.MovingWorldHandlerCommon;
import io.github.elytra.movingworld.common.util.MathHelperMod;
import io.github.elytra.movingworld.common.util.Vec3dMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.Set;

public class EntityShip extends EntityMovingWorld {

    public static final DataParameter<Float> ENGINE_POWER = EntityDataManager.createKey(EntityShip.class, DataSerializers.FLOAT);
    public static final DataParameter<Byte> HAS_ENGINES = EntityDataManager.createKey(EntityShip.class, DataSerializers.BYTE);
    public static final DataParameter<Byte> CAN_SUBMERGE = EntityDataManager.createKey(EntityShip.class, DataSerializers.BYTE);
    public static final DataParameter<Byte> IS_SUBMERGED = EntityDataManager.createKey(EntityShip.class, DataSerializers.BYTE);

    public static final float BASE_FORWARD_SPEED = 0.005F, BASE_TURN_SPEED = 0.5F, BASE_LIFT_SPEED = 0.004F;
    public ShipCapabilities capabilities;
    private ShipControllerCommon controller;
    private MovingWorldHandlerCommon handler;
    private ShipAssemblyInteractor shipAssemblyInteractor;
    private boolean submerge;

    public EntityShip(World world) {
        super(world);
        capabilities = new ShipCapabilities(this, true);
    }

    @Override
    public void assembleResultEntity() {
        super.assembleResultEntity();
    }

    @Override
    public void onEntityUpdate() {
        super.onEntityUpdate();

        if (worldObj != null) {
            if (!worldObj.isRemote) {
                boolean hasEngines = false;
                if (capabilities.getEngines() != null) {
                    if (capabilities.getEngines().isEmpty())
                        hasEngines = false;
                    else {
                        hasEngines = capabilities.getEnginePower() > 0;
                    }
                }
                if (DavincisVesselsMod.instance.getNetworkConfig().getShared().enginesMandatory)
                    getDataManager().set(HAS_ENGINES, new Byte(hasEngines ? (byte) 1 : (byte) 0));
                else
                    getDataManager().set(HAS_ENGINES, new Byte((byte) 1));
            }
            if (worldObj.isRemote) {
                if (dataManager != null && !dataManager.isEmpty() && dataManager.isDirty()) {
                    submerge = dataManager.get(IS_SUBMERGED) == new Byte((byte) 1);
                }
            }
        }
    }

    public boolean getSubmerge() {
        return !getDataManager().isEmpty() ? (getDataManager().get(IS_SUBMERGED) == (byte) 1) : false;
    }

    public void setSubmerge(boolean submerge) {
        this.submerge = submerge;
        if (worldObj != null && !worldObj.isRemote) {
            getDataManager().set(IS_SUBMERGED, submerge ? new Byte((byte) 1) : new Byte((byte) 0));
            if (getMobileChunk().marker != null && getMobileChunk().marker.tileEntity != null && getMobileChunk().marker.tileEntity instanceof TileEntityHelm) {
                TileEntityHelm helm = (TileEntityHelm) getMobileChunk().marker.tileEntity;

                helm.submerge = submerge;
            }
        }
    }

    @Override
    public AxisAlignedBB getCollisionBox(Entity entity) {
        if (entity != null) {
            if (entity instanceof EntityMovingWorld) {
                EntityMovingWorld entityMovingWorld = (EntityMovingWorld) entity;
                return entityMovingWorld.getEntityBoundingBox();
            }
            if (entity instanceof EntitySeat || entity.getRidingEntity() instanceof EntitySeat || entity instanceof EntityLiving)
                return new AxisAlignedBB(0, 0, 0, 0, 0, 0);
        }
        return new AxisAlignedBB(0, 0, 0, 0, 0, 0);
    }

    @Override
    public MovingWorldHandlerCommon getHandler() {
        if (handler == null) {
            if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
                handler = new ShipHandlerClient(this);
                handler.setMovingWorld(this);
            } else {
                handler = new ShipHandlerServer(this);
                handler.setMovingWorld(this);
            }
        }
        return handler;
    }

    @Override
    public void initMovingWorld() {
        dataManager.register(ENGINE_POWER, 0F);
        dataManager.register(HAS_ENGINES, (byte) 0);
        dataManager.register(CAN_SUBMERGE, (byte) 0);
        dataManager.register(IS_SUBMERGED, (byte) 0);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void initMovingWorldClient() {
        handler = new ShipHandlerClient(this);
        controller = new ShipControllerClient();
    }

    @Override
    public void initMovingWorldCommon() {
        handler = new ShipHandlerServer(this);
        controller = new ShipControllerCommon();
    }

    @Override
    public MovingWorldCapabilities getCapabilities() {
        return this.capabilities == null ? new ShipCapabilities(this, true) : this.capabilities;
    }

    @Override
    public void setCapabilities(MovingWorldCapabilities capabilities) {
        if (capabilities != null && capabilities instanceof ShipCapabilities) {
            this.capabilities = (ShipCapabilities) capabilities;
        }
    }

    /**
     * Aligns to the closest anchor within the radius specified in the configuration.
     */
    public boolean alignToAnchor() {
        ImmutablePair<LocatedBlock, LocatedBlock> closestRelation = capabilities.findClosestValidAnchor(DavincisVesselsMod.instance.getNetworkConfig().anchorRadius);
        if (!closestRelation.getLeft().equals(LocatedBlock.AIR) && !closestRelation.getRight().equals(LocatedBlock.AIR)) {
            super.alignToGrid(true);
            float ox = -getMobileChunk().getCenterX();
            float oy = -getMobileChunk().minY(); //Created the normal way, through a VehicleFiller, this value will always be 0.
            float oz = -getMobileChunk().getCenterZ();

            Vec3dMod vec = new Vec3dMod(closestRelation.getLeft().blockPos.getX() + ox,
                    closestRelation.getLeft().blockPos.getY() + oy,
                    closestRelation.getLeft().blockPos.getZ() + oz);
            vec = vec.rotateAroundY(rotationYaw);
            BlockPos pos = new BlockPos(MathHelperMod.round_double(vec.xCoord + closestRelation.getRight().blockPos.getX()),
                    MathHelperMod.round_double(vec.yCoord + closestRelation.getRight().blockPos.getY()),
                    MathHelperMod.round_double(vec.zCoord + closestRelation.getRight().blockPos.getZ()));

            setPositionAndUpdate(pos.getX(), pos.getY(), pos.getZ());
            super.alignToGrid(true);

            return true;
        }
        return false;
    }

    @Override
    public void alignToGrid(boolean doPosAdjustment) {
        if (!alignToAnchor()) super.alignToGrid(true);
    }

    @Override
    public boolean isBraking() {
        return controller.getShipControl() == 3;
    }

    @Override
    public MovingWorldAssemblyInteractor getNewAssemblyInteractor() {
        return new ShipAssemblyInteractor();
    }

    @Override
    public void writeMovingWorldNBT(NBTTagCompound tag) {
        tag.setBoolean("submerge", submerge);
    }

    @Override
    public void readMovingWorldNBT(NBTTagCompound tag) {
        setSubmerge(tag.getBoolean("submerge"));
    }

    @Override
    public void writeMovingWorldSpawnData(ByteBuf data) {
    }

    @Override
    public void handleControl(double horizontalVelocity) {
        capabilities.updateEngines();

        if (getControllingPassenger() == null) {
            if (prevRiddenByEntity != null) {
                if (DavincisVesselsMod.instance.getNetworkConfig().getShared().disassembleOnDismount) {
                    alignToGrid(true);
                    updatePassengerPosition(prevRiddenByEntity, riderDestination, 1);
                    disassemble(false);
                } else {
                    if (!worldObj.isRemote && isFlying()) {
                        EntityParachute parachute = new EntityParachute(worldObj, this, riderDestination);
                        if (worldObj.spawnEntityInWorld(parachute)) {
                            prevRiddenByEntity.startRiding(parachute);
                            prevRiddenByEntity.setSneaking(false);
                        }
                    }
                }
                prevRiddenByEntity = null;
            }
        }

        if (getControllingPassenger() == null || !capabilities.canMove()) {
            if (isFlying()) {
                motionY -= BASE_LIFT_SPEED * 0.2F;
            }
        } else {
            handlePlayerControl();
            prevRiddenByEntity = getControllingPassenger();
        }
    }

    @Override
    public void updatePassengerPosition(Entity passenger, BlockPos riderDestination, int flags) {
        super.updatePassengerPosition(passenger, riderDestination, flags);

        if (submerge && passenger != null && passenger instanceof EntityLivingBase && worldObj != null && !worldObj.isRemote) {
            Potion waterBreathing = Potion.REGISTRY.getObject(new ResourceLocation("water_breathing"));
            if (!((EntityLivingBase) passenger).isPotionActive(waterBreathing))
                ((EntityLivingBase) passenger).addPotionEffect(new PotionEffect(waterBreathing, 20, 1));
        }
    }

    /**
     * Currently overridden for future combat system.
     */

    protected String getHurtSound() {
        return "mob.irongolem.hit";
    }

    /**
     * Currently overridden for future combat system.
     */
    protected String getDeathSound() {
        return "mob.irongolem.death";
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void spawnParticles(double horvel) {
        if (capabilities.getEngines() != null && !capabilities.getEngines().isEmpty()) {
            Vec3dMod vec = Vec3dMod.getOrigin();
            float yaw = (float) Math.toRadians(rotationYaw);
            for (ITileEngineModifier engine : capabilities.getEngines()) {
                if (engine.getPowerIncrement(capabilities) != 0F) {
                    vec = vec.setX(((TileEntity) engine).getPos().getX() - getMobileChunk().getCenterX() + 0.5f);
                    vec = vec.setY(((TileEntity) engine).getPos().getY());
                    vec = vec.setZ(((TileEntity) engine).getPos().getZ() - getMobileChunk().getCenterZ() + 0.5f);
                    vec = vec.rotateAroundY(yaw);
                    worldObj.spawnParticle(EnumParticleTypes.SMOKE_LARGE, posX + vec.xCoord, posY + vec.yCoord + 1d, posZ + vec.zCoord, 0d, 0d, 0d);
                }
            }
        }
    }

    public int getBelowWater() {
        byte b0 = 5;
        int blocksPerMeter = (int) (b0 * (getEntityBoundingBox().maxY - getEntityBoundingBox().minY));
        AxisAlignedBB axisalignedbb = new AxisAlignedBB(0D, 0D, 0D, 0D, 0D, 0D);
        int belowWater = 0;
        for (; belowWater < blocksPerMeter; belowWater++) {
            double d1 = getEntityBoundingBox().minY + (getEntityBoundingBox().maxY - getEntityBoundingBox().minY) * belowWater / blocksPerMeter;
            double d2 = getEntityBoundingBox().minY + (getEntityBoundingBox().maxY - getEntityBoundingBox().minY) * (belowWater + 1) / blocksPerMeter;
            axisalignedbb = new AxisAlignedBB(getEntityBoundingBox().minX, d1, getEntityBoundingBox().minZ, getEntityBoundingBox().maxX, d2, getEntityBoundingBox().maxZ);

            if (!isAABBInLiquidNotFall(worldObj, axisalignedbb)) {
                break;
            }
        }

        return belowWater;
    }

    @Override
    public void handleServerUpdate(double horizontalVelocity) {
        boolean submergeMode = getSubmerge();

        byte b0 = 5;
        int blocksPerMeter = (int) (b0 * (getEntityBoundingBox().maxY - getEntityBoundingBox().minY));
        float waterVolume = 0F;
        AxisAlignedBB axisalignedbb = new AxisAlignedBB(0D, 0D, 0D, 0D, 0D, 0D);
        int belowWater = 0;
        for (; belowWater < blocksPerMeter; belowWater++) {
            double d1 = getEntityBoundingBox().minY + (getEntityBoundingBox().maxY - getEntityBoundingBox().minY) * belowWater / blocksPerMeter;
            double d2 = getEntityBoundingBox().minY + (getEntityBoundingBox().maxY - getEntityBoundingBox().minY) * (belowWater + 1) / blocksPerMeter;
            axisalignedbb = new AxisAlignedBB(getEntityBoundingBox().minX, d1, getEntityBoundingBox().minZ, getEntityBoundingBox().maxX, d2, getEntityBoundingBox().maxZ);

            if (!isAABBInLiquidNotFall(worldObj, axisalignedbb)) {
                break;
            }
        }
        if (belowWater > 0 && layeredBlockVolumeCount != null) {
            int k = belowWater / b0;
            for (int y = 0; y <= k && y < layeredBlockVolumeCount.length; y++) {
                if (y == k) {
                    waterVolume += layeredBlockVolumeCount[y] * (belowWater % b0) * 1F / b0;
                } else {
                    waterVolume += layeredBlockVolumeCount[y] * 1F;
                }
            }
        }

        if (onGround) {
            isFlying = false;
        }

        float gravity = 0.05F;
        if (waterVolume > 0F && !submergeMode) {
            isFlying = false;
            float buoyancyforce = 1F * waterVolume * gravity; //F = rho * V * g (Archimedes' principle)
            float mass = getCapabilities().getMass();
            motionY += buoyancyforce / mass;
        }

        if (DavincisVesselsMod.instance.getNetworkConfig().getShared().enableShipDownfall) {
            if (!isFlying() || (submergeMode && belowWater <= (getMobileChunk().maxY() * 5 / 3 * 2)))
                motionY -= gravity;
        } else {
            if (!capabilities.canFly() && !capabilities.canSubmerge())
                motionY -= gravity;
        }

        super.handleServerUpdate(horizontalVelocity);
    }

    @Override
    public void handleServerUpdatePreRotation() {
        if (DavincisVesselsMod.instance.getNetworkConfig().getShared().shipControlType == DavincisVesselsConfig.CONTROL_TYPE_VANILLA) {
            double newYaw = rotationYaw;
            double dx = prevPosX - posX;
            double dz = prevPosZ - posZ;

            if (getControllingPassenger() != null && !isBraking() && dx * dx + dz * dz > 0.01D) {
                newYaw = 270F - Math.toDegrees(Math.atan2(dz, dx)) + frontDirection.getHorizontalIndex() * 90F;
            }

            double deltayaw = MathHelper.wrapDegrees(newYaw - rotationYaw);
            double maxyawspeed = 2D;
            if (deltayaw > maxyawspeed) {
                deltayaw = maxyawspeed;
            }
            if (deltayaw < -maxyawspeed) {
                deltayaw = -maxyawspeed;
            }

            rotationYaw = (float) (rotationYaw + deltayaw);
        }
    }

    @Override
    public boolean disassemble(boolean overwrite) {
        if (worldObj.isRemote) return true;

        updatePassenger(getControllingPassenger());

        ChunkDisassembler disassembler = getDisassembler();
        disassembler.overwrite = overwrite;

        if (!disassembler.canDisassemble(getNewAssemblyInteractor())) {
            if (prevRiddenByEntity instanceof EntityPlayer) {
                TextComponentString testMessage = new TextComponentString("Cannot disassemble ship here");
                prevRiddenByEntity.addChatMessage(testMessage);
            }
            return false;
        }

        AssembleResult result = disassembler.doDisassemble(getNewAssemblyInteractor());
        if (result.getShipMarker() != null) {
            TileEntity te = result.getShipMarker().tileEntity;
            if (te instanceof TileEntityHelm) {
                ((TileEntityHelm) te).setAssembleResult(result);
                ((TileEntityHelm) te).setInfo(getInfo());
            }
        }

        return true;
    }

    private void handlePlayerControl() {
        if (getControllingPassenger() instanceof EntityLivingBase && ((ShipCapabilities) getCapabilities()).canMove()) {
            double throttle = ((EntityLivingBase) getControllingPassenger()).moveForward;
            if (isFlying()) {
                throttle *= 0.5D;
            }

            if (DavincisVesselsMod.instance.getNetworkConfig().getShared().shipControlType == DavincisVesselsConfig.CONTROL_TYPE_DAVINCI) {
                Vec3dMod vec = new Vec3dMod(getControllingPassenger().motionX, 0D, getControllingPassenger().motionZ);
                vec.rotateAroundY((float) Math.toRadians(getControllingPassenger().rotationYaw));

                double steer = ((EntityLivingBase) getControllingPassenger()).moveStrafing;

                motionYaw += steer * BASE_TURN_SPEED * capabilities.getRotationMult() * DavincisVesselsMod.instance.getNetworkConfig().getShared().turnSpeed;

                float yaw = (float) Math.toRadians(180F - rotationYaw + frontDirection.getHorizontalIndex() * 90F);
                vec = vec.setX(motionX);
                vec = vec.setZ(motionZ);
                vec = vec.rotateAroundY(yaw);
                vec = vec.setX(vec.xCoord * 0.9D);
                vec = vec.setZ(vec.zCoord - throttle * BASE_FORWARD_SPEED * capabilities.getSpeedMult());
                vec = vec.rotateAroundY(-yaw);

                motionX = vec.xCoord;
                motionZ = vec.zCoord;
            } else if (DavincisVesselsMod.instance.getNetworkConfig().getShared().shipControlType == DavincisVesselsConfig.CONTROL_TYPE_VANILLA) {
                if (throttle > 0.0D) {
                    double dsin = -Math.sin(Math.toRadians(getControllingPassenger().rotationYaw));
                    double dcos = Math.cos(Math.toRadians(getControllingPassenger().rotationYaw));
                    motionX += dsin * BASE_FORWARD_SPEED * capabilities.speedMultiplier;
                    motionZ += dcos * BASE_FORWARD_SPEED * capabilities.speedMultiplier;
                }
            }
        }

        if (controller.getShipControl() != 0) {
            if (controller.getShipControl() == 4) {
                alignToGrid(true);
            } else if (isBraking()) {
                motionX *= capabilities.brakeMult;
                motionZ *= capabilities.brakeMult;
                if (isFlying()) {
                    motionY *= capabilities.brakeMult;
                }
            } else if (controller.getShipControl() < 3 && capabilities.canFly()) {
                int i;
                if (controller.getShipControl() == 2) {
                    isFlying = true;
                    i = 1;
                } else {
                    i = -1;
                }
                motionY += i * BASE_LIFT_SPEED * capabilities.getLiftMult();
                if (getControllingPassenger() != null && getControllingPassenger() instanceof EntityPlayer
                        && !((EntityPlayer) getControllingPassenger()).hasAchievement(DavincisVesselsObjects.achievementFlyShip))
                    ((EntityPlayer) getControllingPassenger()).addStat(DavincisVesselsObjects.achievementFlyShip);
            }
        }
    }

    @Override
    public boolean canBePushed() {
        return !isDead && getControllingPassenger() == null;
    }

    @Override
    public boolean isFlying() {
        return (capabilities.canFly() && (isFlying || controller.getShipControl() == 2)) || getSubmerge();
    }

    public boolean areSubmerged() {
        int belowWater = getBelowWater();

        return getSubmerge() && belowWater > 0;
    }

    @Override
    public void readMovingWorldSpawnData(ByteBuf data) {
    }

    @Override
    public float getXRenderScale() {
        return 1F;
    }

    @Override
    public float getYRenderScale() {
        return 1F;
    }

    @Override
    public float getZRenderScale() {
        return 1F;
    }

    @Override
    public MovingWorldAssemblyInteractor getAssemblyInteractor() {
        if (shipAssemblyInteractor == null)
            shipAssemblyInteractor = (ShipAssemblyInteractor) getNewAssemblyInteractor();

        return shipAssemblyInteractor;
    }

    @Override
    public void setAssemblyInteractor(MovingWorldAssemblyInteractor interactor) {
        //shipAssemblyInteractor = (ShipAssemblyInteractor) interactor;
        //interactor.transferToCapabilities(getCapabilities());
    }

    public void fillAirBlocks(Set<BlockPos> set, BlockPos pos) {
        super.fillAirBlocks(set, pos);
    }

    public ShipControllerCommon getController() {
        return controller;
    }

    public boolean canSubmerge() {
        return !dataManager.isEmpty() ? dataManager.get(CAN_SUBMERGE) == new Byte((byte) 1) : false;
    }
}