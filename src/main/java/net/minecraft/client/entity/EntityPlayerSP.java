package net.minecraft.client.entity;

import me.youngjibbit.Pulsar;
import me.youngjibbit.command.CommandManager;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.event.motion.SlowdownEvent;
import me.youngjibbit.event.packet.LivingEvent;
import me.youngjibbit.event.update.EventUpdate;
import me.zero.alpine.event.EventPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.MovingSoundMinecartRiding;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiCommandBlock;
import net.minecraft.client.gui.GuiEnchantment;
import net.minecraft.client.gui.GuiHopper;
import net.minecraft.client.gui.GuiMerchant;
import net.minecraft.client.gui.GuiRepair;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.client.gui.inventory.GuiBeacon;
import net.minecraft.client.gui.inventory.GuiBrewingStand;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiCrafting;
import net.minecraft.client.gui.inventory.GuiDispenser;
import net.minecraft.client.gui.inventory.GuiEditSign;
import net.minecraft.client.gui.inventory.GuiFurnace;
import net.minecraft.client.gui.inventory.GuiScreenHorseInventory;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.command.server.CommandBlockLogic;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IMerchant;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0CPacketInput;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C13PacketPlayerAbilities;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.potion.Potion;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatFileWriter;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.*;
import net.minecraft.world.IInteractionObject;
import net.minecraft.world.World;


public class EntityPlayerSP extends AbstractClientPlayer
{
    private Vec3 acceptedMovementPosition;

    /** Observe accepted movement, including replays emitted by packet listeners. */
    public void observeAcceptedPosition(double x, double y, double z) {
        this.acceptedMovementPosition = new Vec3(x, y, z);
    }

    public Vec3 getLastReportedPosition() {
        return this.acceptedMovementPosition != null ? this.acceptedMovementPosition
                : new Vec3(this.lastReportedPosX, this.lastReportedPosY, this.lastReportedPosZ);
    }

    /** The position a normal movement packet for this plan will actually report.
     * Sub-threshold local displacement must not become a fictitious server raycast origin. */
    public Vec3 getPlannedMovementPosition(double x, double y, double z) {
        return ridingEntity == null && shouldReportPosition(x, y, z)
                ? new Vec3(x, y, z) : getLastReportedPosition();
    }

    private boolean shouldReportPosition(double x, double y, double z) {
        double dx=x-lastReportedPosX, dy=y-lastReportedPosY, dz=z-lastReportedPosZ;
        return dx*dx+dy*dy+dz*dz > 9.0E-4D || positionUpdateTicks >= 20;
    }

    public final NetHandlerPlayClient sendQueue;
    private final StatFileWriter statWriter;
    private GuiScreen serverInv;
    public int ticksSinceLastSwing;
    private Vec3 spawnPos;
    public int rotIncrement;
    private double lastReportedPosX;
    private double lastReportedPosY;
    private double lastReportedPosZ;
    private float lastReportedYaw;
    private float lastReportedPitch;
    private boolean serverSneakState;
    private boolean serverSprintState;
    private int positionUpdateTicks;
    private boolean hasValidHealth;
    private String clientBrand;
    public MovementInput movementInput;
    protected Minecraft mc;
    protected int sprintToggleTimer;
    public int sprintingTicksLeft;
    public float renderArmYaw;
    public float renderArmPitch;
    public float prevRenderArmYaw;
    public float prevRenderArmPitch;
    private int horseJumpPowerCounter;
    private float horseJumpPower;
    public float timeInPortal;
    public float prevTimeInPortal;
    private boolean prevOnGround;
    private boolean shouldGetSpawnPos = false;
    public int reSprint;
    private boolean startServerSprintState;
    private int counter;
    private float forward = 0.0f;
    private float strafe = 0.0f;
    private Vec3 severPosition = new Vec3(0.0, 0.0, 0.0);
    private Vec3 lastServerPosition = new Vec3(0.0, 0.0, 0.0);

    public float serverYaw, prevServerYaw, serverPitch, prevServerPitch;



    public EntityPlayerSP(Minecraft mcIn, World worldIn, NetHandlerPlayClient netHandler, StatFileWriter statFile)
    {
        super(worldIn, netHandler.getGameProfile());
        this.sendQueue = netHandler;
        this.statWriter = statFile;
        this.mc = mcIn;
        this.dimension = 0;
    }

    /**
     * Called when the entity is attacked.
     */
    public boolean attackEntityFrom(DamageSource source, float amount)
    {
        return false;
    }

    /**
     * Heal living entity (param: amount of half-hearts)
     */
    public void heal(float healAmount)
    {
    }

    /**
     * Called when a player mounts an entity. e.g. mounts a pig, mounts a boat.
     */
    public void mountEntity(Entity entityIn)
    {
        super.mountEntity(entityIn);

        if (entityIn instanceof EntityMinecart)
        {
            this.mc.getSoundHandler().playSound(new MovingSoundMinecartRiding(this, (EntityMinecart)entityIn));
        }
    }

    /**
     * Called to update the entity's position/logic.
     */
    public void onUpdate()
    {
        if (this.worldObj.isBlockLoaded(new BlockPos(this.posX, 0.0D, this.posZ)))
        {
            me.youngjibbit.module.impl.exploit.Tickbase.runPlayerStep(this, this::runLocalPlayerStep);
        }
    }

    private boolean runLocalPlayerStep() {
        EventUpdate eventUpdate = new EventUpdate();
        Pulsar.BUS.post(eventUpdate);

        if (eventUpdate.isCancelled())
        {
            return false;
        }

        this.prevServerYaw = this.serverYaw;
        this.prevServerPitch = this.serverPitch;

        super.onUpdate();

        if (this.isRiding())
        {
            this.sendQueue.addToSendQueue(new C03PacketPlayer.C05PacketPlayerLook(this.rotationYaw, this.rotationPitch, this.onGround));
            this.sendQueue.addToSendQueue(new C0CPacketInput(this.moveStrafing, this.moveForward, this.movementInput.jump, this.movementInput.sneak));
        }
        else
        {
            this.onUpdateWalkingPlayer();
        }

        return true;
    }

    /**
     * called every tick when the player is on foot. Performs all the things that normally happen during movement.
     */
    public void onUpdateWalkingPlayer()
    {
        final Minecraft client = this.mc;
        final World world = this.worldObj;
        final NetHandlerPlayClient connection = this.sendQueue;
        final me.youngjibbit.core.network.PacketPipeline packets = connection.getNetworkManager().getClientPackets();
        final long contextVersion = packets == null ? -1 : packets.getContextVersion();
        if (!walkingContextValid(client, world, connection, packets, contextVersion)) return;
        EventMotion motionEvent = null;
        RotationManager rotations = null;
        long motionSequence = -1;
        if (this.isCurrentViewEntity()) {
            motionEvent = new EventMotion(this.posX, this.getEntityBoundingBox().minY, this.posZ, this.rotationYaw, this.rotationPitch, this.onGround);
            rotations = RuntimeServices.rotation();
            rotations.bindConnection(this.sendQueue.getNetworkManager().getClientPackets());
            rotations.beginMotion(this, this.worldObj, this.rotationYaw, this.rotationPitch);
            motionSequence = rotations.getMotionSequence();
            motionEvent.setEventPhase(EventPhase.PRE);
            Pulsar.BUS.post(motionEvent);
        }
        if (!walkingContextValid(client, world, connection, packets, contextVersion)) {
            if (rotations != null && rotations.getMotionSequence() == motionSequence) rotations.reset();
            return;
        }

        // PRE movement owners finalize sprint before its action packet, even if motion is cancelled.
        final boolean flag = this.isSprinting();

        if (flag != serverSprintState)
        {
            C0BPacketEntityAction sprintAction = new C0BPacketEntityAction(this, flag
                    ? C0BPacketEntityAction.Action.START_SPRINTING : C0BPacketEntityAction.Action.STOP_SPRINTING);
            if (connection.addToSendQueueGuarded(sprintAction,
                    () -> walkingContextValid(client, world, connection, packets, contextVersion) && this.isSprinting() == flag) == sprintAction)
                serverSprintState = flag;
        }

        final boolean flag1 = this.isSneaking();

        if (flag1 != this.serverSneakState)
        {
            C0BPacketEntityAction sneakAction = new C0BPacketEntityAction(this, flag1
                    ? C0BPacketEntityAction.Action.START_SNEAKING : C0BPacketEntityAction.Action.STOP_SNEAKING);
            if (connection.addToSendQueueGuarded(sneakAction,
                    () -> walkingContextValid(client, world, connection, packets, contextVersion) && this.isSneaking() == flag1) == sneakAction)
                this.serverSneakState = flag1;
        }
        if (!walkingContextValid(client, world, connection, packets, contextVersion)) {
            if (rotations != null && rotations.getMotionSequence() == motionSequence) rotations.reset();
            return;
        }

        if (motionEvent != null)
        {
            if (motionEvent.isCancelled())
            {
                rotations.finishMotion(this.rotationYaw, this.rotationPitch, false);
                return;
            }
            rotations.resolve(motionEvent);
            final RotationManager.ResolvedC03SendTicket sendTicket =
                    rotations.captureResolvedC03SendTicket(this.lastReportedYaw, this.lastReportedPitch);

            double posX = motionEvent.getX(), posY = motionEvent.getY(), posZ = motionEvent.getZ();
            float rotationYaw = motionEvent.getYaw(), rotationPitch = motionEvent.getPitch();
            boolean onGround = motionEvent.isOnGround();

            double d3 = rotationYaw - this.lastReportedYaw;
            double d4 = rotationPitch - this.lastReportedPitch;
            boolean flag2 = shouldReportPosition(posX, posY, posZ);
            boolean flag3 = d3 != 0.0D || d4 != 0.0D;

            C03PacketPlayer movementPacket;
            if (this.ridingEntity == null)
            {
                if (flag2 && flag3)
                {
                    movementPacket = new C03PacketPlayer.C06PacketPlayerPosLook(posX, posY, posZ, rotationYaw, rotationPitch, onGround);
                } else if (flag2)
                {
                    movementPacket = new C03PacketPlayer.C04PacketPlayerPosition(posX, posY, posZ, onGround);
                } else if (flag3)
                {
                    movementPacket = new C03PacketPlayer.C05PacketPlayerLook(rotationYaw, rotationPitch, onGround);
                } else
                {
                    movementPacket = new C03PacketPlayer(onGround);
                }
            } else
            {
                movementPacket = new C03PacketPlayer.C06PacketPlayerPosLook(this.motionX, -999.0D, this.motionZ, rotationYaw, rotationPitch, onGround);
                flag2 = false;
            }

            net.minecraft.network.Packet<?> acceptedPacket;
            try {
                acceptedPacket = sendTicket == null ? this.sendQueue.addToSendQueueTracked(movementPacket)
                        : connection.addToSendQueueTracked(movementPacket, sendTicket.guard(movementPacket));
            } catch (RuntimeException | Error error) {
                if (sendTicket != null && rotations.getMotionSequence() == motionSequence && RuntimeServices.rotation() == rotations)
                    rotations.finishMotion(rotationYaw, rotationPitch, false);
                throw error;
            }
            // A nested walking frame owns its own finish/report/POST, even if this older packet was denied.
            if (sendTicket != null && (rotations.getMotionSequence() != motionSequence || RuntimeServices.rotation() != rotations)) return;
            boolean accepted = acceptedPacket instanceof C03PacketPlayer && this.sendQueue.getNetworkManager().isChannelOpen();
            C03PacketPlayer sent = accepted ? (C03PacketPlayer) acceptedPacket : null;
            if (accepted) {
                rotationYaw = rotations.hasEffectiveRotation() ? rotations.getEffectiveYaw() : this.lastReportedYaw;
                rotationPitch = rotations.hasEffectiveRotation() ? rotations.getEffectivePitch() : this.lastReportedPitch;
            }
            rotations.finishMotion(rotationYaw, rotationPitch, accepted);

            ++this.positionUpdateTicks;

            if (flag2 && accepted && sent.isMoving())
            {
                this.lastReportedPosX = sent.getPositionX();
                this.lastReportedPosY = sent.getPositionY();
                this.lastReportedPosZ = sent.getPositionZ();
                this.positionUpdateTicks = 0;
            }

            if (accepted)
            {
                this.lastReportedYaw = rotationYaw;
                this.lastReportedPitch = rotationPitch;
            }

            if (accepted) {
                // Keep confirmed wire angles separate. The renderer owns cosmetic head/body angles.
                this.serverYaw = rotationYaw;
                this.serverPitch = rotationPitch;
            }

            EventMotion postMotionEvent = new EventMotion(posX, posY, posZ, rotationYaw, rotationPitch, onGround);
            postMotionEvent.setAccepted(accepted);
            postMotionEvent.setEventPhase(EventPhase.POST);
            Pulsar.BUS.post(postMotionEvent);
        }
    }

    private boolean walkingContextValid(Minecraft client, World world, NetHandlerPlayClient connection,
                                        me.youngjibbit.core.network.PacketPipeline packets, long contextVersion) {
        return client != null && this.mc == client && client.thePlayer == this && client.theWorld == world
                && this.worldObj == world && this.sendQueue == connection && packets != null && packets.isActive()
                && connection.getNetworkManager().getClientPackets() == packets && packets.getContextVersion() == contextVersion;
    }


    /**
     * Called when player presses the drop item key
     */
    public EntityItem dropOneItem(boolean dropAll)
    {
        C07PacketPlayerDigging.Action c07packetplayerdigging$action = dropAll ? C07PacketPlayerDigging.Action.DROP_ALL_ITEMS : C07PacketPlayerDigging.Action.DROP_ITEM;
        this.sendQueue.addToSendQueue(new C07PacketPlayerDigging(c07packetplayerdigging$action, BlockPos.ORIGIN, EnumFacing.DOWN));
        return null;
    }

    /**
     * Joins the passed in entity item with the world. Args: entityItem
     */
    protected void joinEntityItemWithWorld(EntityItem itemIn)
    {
    }

    /**
     * Sends a chat message from the player. Args: chatMessage
     *  
     * @param message used on EntityPlayerSP.sendChatMessage - as inbound message
     */
    public void sendChatMessage(String message)
    {
        if(message.startsWith(CommandManager.COMMAND_PREFIX) && Pulsar.INSTANCE.getCm().handleCommand(message))
            return;
        this.sendQueue.addToSendQueue(new C01PacketChatMessage(message));
    }

    /**
     * Swings the item the player is holding.
     */
    public void swingItem()
    {
        super.swingItem();
        this.sendQueue.addToSendQueue(new C0APacketAnimation());
    }

    public void swingItemNoPacket() {
        super.swingItem();
    }

    public void respawnPlayer()
    {
        this.sendQueue.addToSendQueue(new C16PacketClientStatus(C16PacketClientStatus.EnumState.PERFORM_RESPAWN));
    }

    /**
     * Deals damage to the entity. If its a EntityPlayer then will take damage from the armor first and then health
     * second with the reduced value. Args: damageAmount
     */
    protected void damageEntity(DamageSource damageSrc, float damageAmount)
    {
        if (!this.isEntityInvulnerable(damageSrc))
        {
            this.setHealth(this.getHealth() - damageAmount);
        }
    }

    /**
     * set current crafting inventory back to the 2x2 square
     */
    public void closeScreen()
    {
        this.sendQueue.addToSendQueue(new C0DPacketCloseWindow(this.openContainer.windowId));
        this.closeScreenAndDropStack();
    }

    public void closeScreenAndDropStack()
    {
        this.inventory.setItemStack((ItemStack)null);
        super.closeScreen();
        this.mc.displayGuiScreen((GuiScreen)null);
    }

    /**
     * Updates health locally.
     */
    public void setPlayerSPHealth(float health)
    {
        if (this.hasValidHealth)
        {
            float f = this.getHealth() - health;

            if (f <= 0.0F)
            {
                this.setHealth(health);

                if (f < 0.0F)
                {
                    this.hurtResistantTime = this.maxHurtResistantTime / 2;
                }
            }
            else
            {
                this.lastDamage = f;
                this.setHealth(this.getHealth());
                this.hurtResistantTime = this.maxHurtResistantTime;
                this.damageEntity(DamageSource.generic, f);
                this.hurtTime = this.maxHurtTime = 10;
            }
        }
        else
        {
            this.setHealth(health);
            this.hasValidHealth = true;
        }
    }

    /**
     * Adds a value to a statistic field.
     */
    public void addStat(StatBase stat, int amount)
    {
        if (stat != null)
        {
            if (stat.isIndependent)
            {
                super.addStat(stat, amount);
            }
        }
    }

    /**
     * Sends the player's abilities to the server (if there is one).
     */
    public void sendPlayerAbilities()
    {
        this.sendQueue.addToSendQueue(new C13PacketPlayerAbilities(this.capabilities));
    }

    /**
     * returns true if this is an EntityPlayerSP, or the logged in player.
     */
    public boolean isUser()
    {
        return true;
    }

    protected void sendHorseJump()
    {
        this.sendQueue.addToSendQueue(new C0BPacketEntityAction(this, C0BPacketEntityAction.Action.RIDING_JUMP, (int)(this.getHorseJumpPower() * 100.0F)));
    }

    public void sendHorseInventory()
    {
        this.sendQueue.addToSendQueue(new C0BPacketEntityAction(this, C0BPacketEntityAction.Action.OPEN_INVENTORY));
    }

    public void setClientBrand(String brand)
    {
        this.clientBrand = brand;
    }

    public String getClientBrand()
    {
        return this.clientBrand;
    }

    public StatFileWriter getStatFileWriter()
    {
        return this.statWriter;
    }

    public void addChatComponentMessage(IChatComponent chatComponent)
    {
        this.mc.ingameGUI.getChatGUI().printChatMessage(chatComponent);
    }

    protected boolean pushOutOfBlocks(double x, double y, double z)
    {
        if (this.noClip)
        {
            return false;
        }
        else
        {
            BlockPos blockpos = new BlockPos(x, y, z);
            double d0 = x - (double)blockpos.getX();
            double d1 = z - (double)blockpos.getZ();

            if (!this.isOpenBlockSpace(blockpos))
            {
                int i = -1;
                double d2 = 9999.0D;

                if (this.isOpenBlockSpace(blockpos.west()) && d0 < d2)
                {
                    d2 = d0;
                    i = 0;
                }

                if (this.isOpenBlockSpace(blockpos.east()) && 1.0D - d0 < d2)
                {
                    d2 = 1.0D - d0;
                    i = 1;
                }

                if (this.isOpenBlockSpace(blockpos.north()) && d1 < d2)
                {
                    d2 = d1;
                    i = 4;
                }

                if (this.isOpenBlockSpace(blockpos.south()) && 1.0D - d1 < d2)
                {
                    d2 = 1.0D - d1;
                    i = 5;
                }

                float f = 0.1F;

                if (i == 0)
                {
                    this.motionX = (double)(-f);
                }

                if (i == 1)
                {
                    this.motionX = (double)f;
                }

                if (i == 4)
                {
                    this.motionZ = (double)(-f);
                }

                if (i == 5)
                {
                    this.motionZ = (double)f;
                }
            }

            return false;
        }
    }

    /**
     * Returns true if the block at the given BlockPos and the block above it are NOT full cubes.
     */
    private boolean isOpenBlockSpace(BlockPos pos)
    {
        return !this.worldObj.getBlockState(pos).getBlock().isNormalCube() && !this.worldObj.getBlockState(pos.up()).getBlock().isNormalCube();
    }

    /**
     * Set sprinting switch for Entity.
     */
    public void setSprinting(boolean sprinting)
    {
        super.setSprinting(sprinting);
        this.sprintingTicksLeft = sprinting ? 600 : 0;
    }

    /**
     * Sets the current XP, total XP, and level number.
     */
    public void setXPStats(float currentXP, int maxXP, int level)
    {
        this.experience = currentXP;
        this.experienceTotal = maxXP;
        this.experienceLevel = level;
    }

    /**
     * Send a chat message to the CommandSender
     *  
     * @param component The ChatComponent to send
     */
    public void addChatMessage(IChatComponent component)
    {
        this.mc.ingameGUI.getChatGUI().printChatMessage(component);
    }

    /**
     * Returns {@code true} if the CommandSender is allowed to execute the command, {@code false} if not
     *  
     * @param permLevel The permission level required to execute the command
     * @param commandName The name of the command
     */
    public boolean canCommandSenderUseCommand(int permLevel, String commandName)
    {
        return permLevel <= 0;
    }

    /**
     * Get the position in the world. <b>{@code null} is not allowed!</b> If you are not an entity in the world, return
     * the coordinates 0, 0, 0
     */
    public BlockPos getPosition()
    {
        return new BlockPos(this.posX + 0.5D, this.posY + 0.5D, this.posZ + 0.5D);
    }

    public void playSound(String name, float volume, float pitch)
    {
        this.worldObj.playSound(this.posX, this.posY, this.posZ, name, volume, pitch, false);
    }

    /**
     * Returns whether the entity is in a server world
     */
    public boolean isServerWorld()
    {
        return true;
    }

    public boolean isRidingHorse()
    {
        return this.ridingEntity != null && this.ridingEntity instanceof EntityHorse && ((EntityHorse)this.ridingEntity).isHorseSaddled();
    }

    public float getHorseJumpPower()
    {
        return this.horseJumpPower;
    }

    public void openEditSign(TileEntitySign signTile)
    {
        this.mc.displayGuiScreen(new GuiEditSign(signTile));
    }

    public void openEditCommandBlock(CommandBlockLogic cmdBlockLogic)
    {
        this.mc.displayGuiScreen(new GuiCommandBlock(cmdBlockLogic));
    }

    /**
     * Displays the GUI for interacting with a book.
     */
    public void displayGUIBook(ItemStack bookStack)
    {
        Item item = bookStack.getItem();

        if (item == Items.writable_book)
        {
            this.mc.displayGuiScreen(new GuiScreenBook(this, bookStack, true));
        }
    }

    /**
     * Displays the GUI for interacting with a chest inventory. Args: chestInventory
     */
    public void displayGUIChest(IInventory chestInventory)
    {
        String s = chestInventory instanceof IInteractionObject ? ((IInteractionObject)chestInventory).getGuiID() : "minecraft:container";

        if ("minecraft:chest".equals(s))
        {
            this.mc.displayGuiScreen(new GuiChest(this.inventory, chestInventory));
        }
        else if ("minecraft:hopper".equals(s))
        {
            this.mc.displayGuiScreen(new GuiHopper(this.inventory, chestInventory));
        }
        else if ("minecraft:furnace".equals(s))
        {
            this.mc.displayGuiScreen(new GuiFurnace(this.inventory, chestInventory));
        }
        else if ("minecraft:brewing_stand".equals(s))
        {
            this.mc.displayGuiScreen(new GuiBrewingStand(this.inventory, chestInventory));
        }
        else if ("minecraft:beacon".equals(s))
        {
            this.mc.displayGuiScreen(new GuiBeacon(this.inventory, chestInventory));
        }
        else if (!"minecraft:dispenser".equals(s) && !"minecraft:dropper".equals(s))
        {
            this.mc.displayGuiScreen(new GuiChest(this.inventory, chestInventory));
        }
        else
        {
            this.mc.displayGuiScreen(new GuiDispenser(this.inventory, chestInventory));
        }
    }

    public void displayGUIHorse(EntityHorse horse, IInventory horseInventory)
    {
        this.mc.displayGuiScreen(new GuiScreenHorseInventory(this.inventory, horseInventory, horse));
    }

    public void displayGui(IInteractionObject guiOwner)
    {
        String s = guiOwner.getGuiID();

        if ("minecraft:crafting_table".equals(s))
        {
            this.mc.displayGuiScreen(new GuiCrafting(this.inventory, this.worldObj));
        }
        else if ("minecraft:enchanting_table".equals(s))
        {
            this.mc.displayGuiScreen(new GuiEnchantment(this.inventory, this.worldObj, guiOwner));
        }
        else if ("minecraft:anvil".equals(s))
        {
            this.mc.displayGuiScreen(new GuiRepair(this.inventory, this.worldObj));
        }
    }

    public void displayVillagerTradeGui(IMerchant villager)
    {
        this.mc.displayGuiScreen(new GuiMerchant(this.inventory, villager, this.worldObj));
    }

    /**
     * Called when the player performs a critical hit on the Entity. Args: entity that was hit critically
     */
    public void onCriticalHit(Entity entityHit)
    {
        this.mc.effectRenderer.emitParticleAtEntity(entityHit, EnumParticleTypes.CRIT);
    }

    public void onEnchantmentCritical(Entity entityHit)
    {
        this.mc.effectRenderer.emitParticleAtEntity(entityHit, EnumParticleTypes.CRIT_MAGIC);
    }

    /**
     * Returns if this entity is sneaking.
     */
    public boolean isSneaking()
    {
        boolean flag = this.movementInput != null ? this.movementInput.sneak : false;
        return flag && !this.sleeping;
    }

    public void updateEntityActionState()
    {
        super.updateEntityActionState();

        if (this.isCurrentViewEntity())
        {
            this.moveStrafing = this.movementInput.moveStrafe;
            this.moveForward = this.movementInput.moveForward;
            this.isJumping = this.movementInput.jump;
            this.prevRenderArmYaw = this.renderArmYaw;
            this.prevRenderArmPitch = this.renderArmPitch;
            this.renderArmPitch = (float)((double)this.renderArmPitch + (double)(this.rotationPitch - this.renderArmPitch) * 0.5D);
            this.renderArmYaw = (float)((double)this.renderArmYaw + (double)(this.rotationYaw - this.renderArmYaw) * 0.5D);
        }
    }

    protected boolean isCurrentViewEntity()
    {
        return this.mc.getRenderViewEntity() == this;
    }

    /**
     * Called frequently so the entity can update its state every tick as required. For example, zombies and skeletons
     * use this to react to sunlight and start to burn.
     */
    public void onLivingUpdate()
    {
        Pulsar.INSTANCE.BUS.post(new LivingEvent());

        if (this.sprintingTicksLeft > 0)
        {
            --this.sprintingTicksLeft;

            if (this.sprintingTicksLeft == 0)
            {
                this.setSprinting(false);
            }
        }

        if (this.sprintToggleTimer > 0)
        {
            --this.sprintToggleTimer;
        }

        this.prevTimeInPortal = this.timeInPortal;

        if (this.inPortal)
        {
            if (this.mc.currentScreen != null && !this.mc.currentScreen.doesGuiPauseGame())
            {
                this.mc.displayGuiScreen((GuiScreen)null);
            }

            if (this.timeInPortal == 0.0F)
            {
                this.mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("portal.trigger"), this.rand.nextFloat() * 0.4F + 0.8F));
            }

            this.timeInPortal += 0.0125F;

            if (this.timeInPortal >= 1.0F)
            {
                this.timeInPortal = 1.0F;
            }

            this.inPortal = false;
        }
        else if (this.isPotionActive(Potion.confusion) && this.getActivePotionEffect(Potion.confusion).getDuration() > 60)
        {
            this.timeInPortal += 0.006666667F;

            if (this.timeInPortal > 1.0F)
            {
                this.timeInPortal = 1.0F;
            }
        }
        else
        {
            if (this.timeInPortal > 0.0F)
            {
                this.timeInPortal -= 0.05F;
            }

            if (this.timeInPortal < 0.0F)
            {
                this.timeInPortal = 0.0F;
            }
        }

        if (this.timeUntilPortal > 0)
        {
            --this.timeUntilPortal;
        }

        boolean flag = this.movementInput.jump;
        boolean flag1 = this.movementInput.sneak;
        float f = 0.8F;
        boolean flag2 = this.movementInput.moveForward >= f;
        this.movementInput.updatePlayerMoveState();
        me.youngjibbit.module.impl.world.Scaffold.applyDownwardsInput(this.movementInput);
        boolean noslowAllowSprint = false;

        if (this.isUsingItem() && !this.isRiding()) {
            SlowdownEvent event = new SlowdownEvent(0.2F, 0.2F, false);
            Pulsar.BUS.post(event);
            MovementInput var10000 = this.movementInput;
            var10000.moveStrafe *= event.getStrafe();
            var10000 = this.movementInput;
            var10000.moveForward *= event.getForward();
            if (event.isAllowedSprinting()) {
                noslowAllowSprint = true;
            } else {
                this.sprintToggleTimer = 0;
            }
        }

        this.pushOutOfBlocks(this.posX - (double)this.width * 0.35D, this.getEntityBoundingBox().minY + 0.5D, this.posZ + (double)this.width * 0.35D);
        this.pushOutOfBlocks(this.posX - (double)this.width * 0.35D, this.getEntityBoundingBox().minY + 0.5D, this.posZ - (double)this.width * 0.35D);
        this.pushOutOfBlocks(this.posX + (double)this.width * 0.35D, this.getEntityBoundingBox().minY + 0.5D, this.posZ - (double)this.width * 0.35D);
        this.pushOutOfBlocks(this.posX + (double)this.width * 0.35D, this.getEntityBoundingBox().minY + 0.5D, this.posZ + (double)this.width * 0.35D);
        boolean flag3 = (float)this.getFoodStats().getFoodLevel() > 6.0F || this.capabilities.allowFlying;

        if (this.onGround && !flag1 && !flag2 && this.movementInput.moveForward >= f && !this.isSprinting() && flag3 && (!this.isUsingItem() || noslowAllowSprint) && !this.isPotionActive(Potion.blindness))
        {
            if (this.sprintToggleTimer <= 0 && !this.mc.gameSettings.keyBindSprint.isKeyDown())
            {
                this.sprintToggleTimer = 7;
            }
            else
            {
                this.setSprinting(true);
            }
        }

        if (!this.isSprinting() && this.movementInput.moveForward >= f && flag3 && (!this.isUsingItem() || noslowAllowSprint) && !this.isPotionActive(Potion.blindness) && this.mc.gameSettings.keyBindSprint.isKeyDown())
        {
            this.setSprinting(true);
        }

        if (this.isSprinting() && (this.movementInput.moveForward < f || this.isCollidedHorizontally || !flag3))
        {
            this.setSprinting(false);
        }

        if (this.capabilities.allowFlying)
        {
            if (this.mc.playerController.isSpectatorMode())
            {
                if (!this.capabilities.isFlying)
                {
                    this.capabilities.isFlying = true;
                    this.sendPlayerAbilities();
                }
            }
            else if (!flag && this.movementInput.jump)
            {
                if (this.flyToggleTimer == 0)
                {
                    this.flyToggleTimer = 7;
                }
                else
                {
                    this.capabilities.isFlying = !this.capabilities.isFlying;
                    this.sendPlayerAbilities();
                    this.flyToggleTimer = 0;
                }
            }
        }

        if (this.capabilities.isFlying && this.isCurrentViewEntity())
        {
            if (this.movementInput.sneak)
            {
                this.motionY -= (double)(this.capabilities.getFlySpeed() * 3.0F);
            }

            if (this.movementInput.jump)
            {
                this.motionY += (double)(this.capabilities.getFlySpeed() * 3.0F);
            }
        }

        if (this.isRidingHorse())
        {
            if (this.horseJumpPowerCounter < 0)
            {
                ++this.horseJumpPowerCounter;

                if (this.horseJumpPowerCounter == 0)
                {
                    this.horseJumpPower = 0.0F;
                }
            }

            if (flag && !this.movementInput.jump)
            {
                this.horseJumpPowerCounter = -10;
                this.sendHorseJump();
            }
            else if (!flag && this.movementInput.jump)
            {
                this.horseJumpPowerCounter = 0;
                this.horseJumpPower = 0.0F;
            }
            else if (flag)
            {
                ++this.horseJumpPowerCounter;

                if (this.horseJumpPowerCounter < 10)
                {
                    this.horseJumpPower = (float)this.horseJumpPowerCounter * 0.1F;
                }
                else
                {
                    this.horseJumpPower = 0.8F + 2.0F / (float)(this.horseJumpPowerCounter - 9) * 0.1F;
                }
            }
        }
        else
        {
            this.horseJumpPower = 0.0F;
        }

        super.onLivingUpdate();

        if (this.onGround && this.capabilities.isFlying && !this.mc.playerController.isSpectatorMode())
        {
            this.capabilities.isFlying = false;
            this.sendPlayerAbilities();
        }
    }
    @Override
    public MovingObjectPosition customRayTrace(double blockReachDistance, float partialTicks, float yaw, float pitch) {
        Vec3 vec3 = this.getPositionEyes(partialTicks);
        Vec3 vec31 = this.customGetLook(partialTicks, yaw, pitch);
        Vec3 vec32 = vec3.addVector(vec31.xCoord * blockReachDistance, vec31.yCoord * blockReachDistance, vec31.zCoord * blockReachDistance);
        return this.worldObj.rayTraceBlocks(vec3, vec32, false, false, true);
    }

    public MovingObjectPosition customRayTrace(double blockReachDistance, float partialTicks, float yaw, float pitch, boolean pitchPredict) {
        Vec3 vec3 = this.getPositionEyes(partialTicks);
        Vec3 vec31 = this.customGetLook(partialTicks, yaw, pitch, pitchPredict);
        Vec3 vec32 = vec3.addVector(vec31.xCoord * blockReachDistance, vec31.yCoord * blockReachDistance, vec31.zCoord * blockReachDistance);
        return this.worldObj.rayTraceBlocks(vec3, vec32, false, false, true);
    }

    public MovingObjectPosition customRayTrace(double blockReachDistance, float partialTicks, float yaw, float pitch, float lastYaw, float lastPitch) {
        Vec3 vec3 = this.getPositionEyes(partialTicks);
        Vec3 vec31 = this.customGetLook(partialTicks, yaw, pitch, lastYaw, lastPitch);
        Vec3 vec32 = vec3.addVector(vec31.xCoord * blockReachDistance, vec31.yCoord * blockReachDistance, vec31.zCoord * blockReachDistance);
        return this.worldObj.rayTraceBlocks(vec3, vec32, false, false, true);
    }

    public MovingObjectPosition customRayTrace(double blockReachDistance, float partialTicks, float yaw, float pitch, double[] xyz, double[] lastXYZ) {
        Vec3 vec3 = this.getCustomPositionEyes(partialTicks, xyz, lastXYZ);
        Vec3 vec31 = this.customGetLook(partialTicks, yaw, pitch);
        Vec3 vec32 = vec3.addVector(vec31.xCoord * blockReachDistance, vec31.yCoord * blockReachDistance, vec31.zCoord * blockReachDistance);
        return this.worldObj.rayTraceBlocks(vec3, vec32, false, false, true);
    }

    private Vec3 customGetLook(float partialTicks, float yaw, float pitch) {
        if (partialTicks != 1.0f && partialTicks != 2.0f) {
            float f = this.prevRotationPitch + (this.rotationPitch - this.prevRotationPitch) * partialTicks;
            float f1 = this.prevRotationYaw + (this.rotationYaw - this.prevRotationYaw) * partialTicks;
            return this.getVectorForRotation(f, f1);
        }
        return this.getVectorForRotation(pitch, yaw);
    }

    private Vec3 customGetLook(float partialTicks, float yaw, float pitch, boolean pitchPredict) {
        if (partialTicks == 1.0f) {
            return this.getVectorForRotation(pitch, yaw);
        }
        float f = pitchPredict ? this.prevRotationPitch + (this.rotationPitch - this.prevRotationPitch) * partialTicks : pitch;
        float f1 = pitchPredict ? this.prevRotationYaw + (this.rotationYaw - this.prevRotationYaw) * partialTicks : yaw;
        return this.getVectorForRotation(f, f1);
    }

    private Vec3 customGetLook(float partialTicks, float yaw, float pitch, float lastYaw, float lastPitch) {
        if (partialTicks == 1.0f) {
            return this.getVectorForRotation(pitch, yaw);
        }
        float f = lastPitch + (pitch - lastPitch) * partialTicks;
        float f1 = lastYaw + (yaw - lastYaw) * partialTicks;
        return this.getVectorForRotation(f, f1);
    }

    public Vec3 getCustomPositionEyes(float partialTicks, double[] xyz, double[] lastXYZ) {
        if (partialTicks == 1.0f) {
            return new Vec3(xyz[0], xyz[1] + (double)this.getEyeHeight(), xyz[2]);
        }
        double d0 = lastXYZ[0] + (xyz[0] - lastXYZ[0]) * (double)partialTicks;
        double d1 = lastXYZ[1] + (xyz[1] - lastXYZ[1]) * (double)partialTicks + (double)this.getEyeHeight();
        double d2 = lastXYZ[2] + (xyz[2] - lastXYZ[2]) * (double)partialTicks;
        return new Vec3(d0, d1, d2);
    }

    public float getXZDistanceToEntity(Entity entityIn) {
        float f = (float)(this.posX - entityIn.posX);
        float f2 = (float)(this.posZ - entityIn.posZ);
        return MathHelper.sqrt_float(f * f + f2 * f2);
    }

    public void resetCooldown() {
        this.ticksSinceLastSwing = 0;
    }

    public GuiScreen getServerInv() {
        return this.serverInv;
    }

    public void setServerInv(GuiScreen serverInv) {
        this.serverInv = serverInv;
    }

    public Vec3 getSpawnPos() {
        return this.spawnPos;
    }

    public boolean isServerSprintState() {
        return this.serverSprintState;
    }

    public void setServerSprintState(boolean serverSprintState) {
        this.serverSprintState = serverSprintState;
    }

    public Vec3 getLastServerPosition() {
        return this.lastServerPosition;
    }

    public void setLastServerPosition(Vec3 lastServerPosition) {
        this.lastServerPosition = lastServerPosition;
    }

    public Vec3 getSeverPosition() {
        return this.severPosition;
    }

    public void setSeverPosition(Vec3 severPosition) {
        this.severPosition = severPosition;
    }

    public double getX()
    {
        return posX;
    }

    public double getY()
    {
        return posY;
    }

    public double getZ()
    {
        return posZ;
    }

}
