package me.youngjibbit.core.network;

import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.util.Vec3;
import java.util.function.LongSupplier;

/** Received fixed-point coordinates, separate from the interpolated/applied entity position. */
public final class ReceivedEntityPosition {
    private final LongSupplier clock;
    private Object entity,world,connection;
    private int id,x,y,z;
    private long updated;
    public ReceivedEntityPosition() { this(System::nanoTime); }
    public ReceivedEntityPosition(LongSupplier clock) { this.clock=clock; }
    public boolean bind(Object entity,Object world,Object connection,int id,int x,int y,int z) {
        if(this.entity==entity && this.world==world && this.connection==connection && this.id==id)return false;
        this.entity=entity;this.world=world;this.connection=connection;this.id=id;
        this.x=x;this.y=y;this.z=z;updated=clock.getAsLong();return true;
    }
    public boolean receive(Packet<?> packet) {
        if(entity==null)return false;
        if(packet instanceof S14PacketEntity) {
            S14PacketEntity move=(S14PacketEntity)packet;
            if(move.getEntityId()!=id)return false;
            x+=move.getPosX();y+=move.getPosY();z+=move.getPosZ();
        } else if(packet instanceof S18PacketEntityTeleport) {
            S18PacketEntityTeleport teleport=(S18PacketEntityTeleport)packet;
            if(teleport.getEntityId()!=id)return false;
            x=teleport.getX();y=teleport.getY();z=teleport.getZ();
        } else return false;
        updated=clock.getAsLong();return true;
    }
    public boolean isFresh(long maxAgeMs) {return entity!=null && clock.getAsLong()-updated<=maxAgeMs*1000000L;}
    public Vec3 position() {return entity==null?null:new Vec3(x/32.0,y/32.0,z/32.0);}
    public void clear() {entity=null;world=null;connection=null;}
}
