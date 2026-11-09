package me.neutron.testserver;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.v1_8_R3.DamageSource;
import net.minecraft.server.v1_8_R3.Entity;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.Items;
import net.minecraft.server.v1_8_R3.MinecraftServer;
import net.minecraft.server.v1_8_R3.PlayerInteractManager;
import net.minecraft.server.v1_8_R3.WorldServer;

/**
 * Server-side player NPC.  It is a real EntityPlayer for the client (correct
 * player hitbox, player metadata and player targeting), but it has no socket
 * and never loses health or moves.
 */
public final class FakePlayer extends EntityPlayer {

    private final BotDefinition definition;

    public FakePlayer(MinecraftServer server, WorldServer world, GameProfile profile, BotDefinition definition) {
        super(server, world, profile, new PlayerInteractManager(world));
        this.definition = definition;

        // No socket.  The fake connection swallows every outbound packet.
        this.playerConnection = new FakePlayerConnection(server, this);

        this.noclip = true;
        this.collidesWithEntities = false;
        this.onGround = true;
        this.setHealth(this.getMaxHealth());
        this.setCustomName(definition.displayName());
        this.setCustomNameVisible(true);
        this.setSneaking(false);
        this.setSprinting(false);
        this.setInvisible(false);
        this.setAirTicks(300);
        this.fireTicks = 0;
        this.setLocation(definition.x, definition.y, definition.z, definition.yaw, definition.pitch);
        this.aI = definition.yaw;
        this.aJ = definition.yaw;
        this.aK = definition.yaw;
        applyEquipment();
    }

    public BotDefinition getDefinition() {
        return definition;
    }

    /** Apply the configured held item/armour through NMS. */
    public void applyEquipment() {
        for (int slot = 0; slot <= 4; slot++) {
            setEquipment(slot, null);
        }
        String equipment = definition.equipment == null ? "NONE" : definition.equipment.toUpperCase(java.util.Locale.ROOT);
        if ("SWORD".equals(equipment)) {
            setEquipment(0, new ItemStack(Items.DIAMOND_SWORD));
        } else if ("ARMOR".equals(equipment)) {
            applyArmor(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
        } else if ("FULL".equals(equipment)) {
            setEquipment(0, new ItemStack(Items.DIAMOND_SWORD));
            applyArmor(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
        } else if ("IRON_FULL".equals(equipment)) {
            setEquipment(0, new ItemStack(Items.IRON_SWORD));
            applyArmor(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
        }
    }

    private void applyArmor(net.minecraft.server.v1_8_R3.Item helmet,
                            net.minecraft.server.v1_8_R3.Item chest,
                            net.minecraft.server.v1_8_R3.Item legs,
                            net.minecraft.server.v1_8_R3.Item boots) {
        setEquipment(1, new ItemStack(boots));
        setEquipment(2, new ItemStack(legs));
        setEquipment(3, new ItemStack(chest));
        setEquipment(4, new ItemStack(helmet));
    }

    /** Called once per tick by the bot manager. */
    public void keepStatic() {
        setPosition(definition.x, definition.y, definition.z);
        this.motX = 0.0D;
        this.motY = 0.0D;
        this.motZ = 0.0D;
        this.onGround = true;
        this.noclip = true;
        this.collidesWithEntities = false;
        this.fireTicks = 0;
        this.setAirTicks(300);

        // Combat bots must accept every hit for CPS/Aura tests.  Vanilla's
        // damage cooldown would otherwise ignore rapid follow-up hits.
        this.noDamageTicks = 0;
        this.lastDamage = 0.0F;

        if (this.getHealth() < this.getMaxHealth()) {
            this.setHealth(this.getMaxHealth());
        }
    }

    public void teleportToDefinition() {
        setLocation(definition.x, definition.y, definition.z, definition.yaw, definition.pitch);
        this.aI = definition.yaw;
        this.aJ = definition.yaw;
        this.aK = definition.yaw;
        this.motX = 0.0D;
        this.motY = 0.0D;
        this.motZ = 0.0D;
        this.onGround = true;
    }

    /** Rotate the body and head toward a world position. */
    public void faceTowards(double targetX, double targetZ) {
        double dx = targetX - this.locX;
        double dz = targetZ - this.locZ;
        float yaw = (float) (Math.atan2(-dx, dz) * 180.0D / Math.PI);
        this.yaw = yaw;
        this.aI = yaw;
        this.aJ = yaw;
        this.aK = yaw;
    }

    /** Immortal combat dummy: keep the hurt animation, never subtract health. */
    @Override
    public void damageEntity(DamageSource source, float amount) {
        // Intentionally empty.
    }

    /** Static dummy: no knockback from player hits. */
    @Override
    public void knockBack(Entity entity, float strength, double xRatio, double zRatio) {
        this.motX = 0.0D;
        this.motY = 0.0D;
        this.motZ = 0.0D;
    }

    @Override
    public void die(DamageSource source) {
        // Intentionally empty: the bot is removed only by the manager.
    }

    @Override
    public boolean isEntityInvulnerable(DamageSource source) {
        return false;
    }
}
