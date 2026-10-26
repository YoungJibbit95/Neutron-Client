package me.youngjibbit.utils;

import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.event.EventFlow;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S14PacketEntity;
import java.util.function.BiPredicate;

/** Small ownership facade over the current connection's bounded delay queue. */
public final class PingSpoofComponent implements Eierlecken {
    private static final Owner LEGACY = new Owner();
    private PingSpoofComponent() { }
    public static void initialize() { }
    public static void shutdown() { LEGACY.release(); }

    public static PacketPipeline currentPipeline() {
        return mc == null || mc.getNetHandler() == null ? null : mc.getNetHandler().getNetworkManager().getClientPackets();
    }
    public static final class Owner {
        private PacketPipeline pipeline;
        public void request(int delayMs, BiPredicate<Packet<?>,EventFlow> matches) {
            request(currentPipeline(),delayMs,matches);
        }
        public void request(PacketPipeline selected,int delayMs,BiPredicate<Packet<?>,EventFlow> matches) {
            if(pipeline!=selected) { release();pipeline=selected; }
            if(pipeline!=null && pipeline.isActive())pipeline.delays().request(this,delayMs,matches);
        }
        public void release() {
            PacketPipeline previous=pipeline;pipeline=null;
            if(previous!=null)previous.delays().release(this);
        }
        public boolean isActive() { return pipeline!=null && pipeline.isActive() && pipeline.delays().hasOwner(this); }
        public me.youngjibbit.core.network.PacketDelays.Snapshot snapshot() {
            return pipeline == null ? null : pipeline.delays().snapshot(this);
        }
    }
    /** Legacy callers share only this legacy owner, never Backtrack's or LagRange's holds. */
    public static void dispatch() { LEGACY.release(); }
    public static void disable() { LEGACY.release(); }
    public static void spoof(int ms,boolean regular,boolean velocity,boolean teleports,boolean players) {
        spoof(ms,regular,velocity,teleports,players,false,false);
    }
    public static void spoof(int ms,boolean regular,boolean velocity,boolean teleports,boolean players,boolean blink) {
        spoof(ms,regular,velocity,teleports,players,blink,false);
    }
    public static void spoof(int ms,boolean regular,boolean velocity,boolean teleports,boolean players,boolean blink,boolean movement) {
        // Only audited movement packets are delayable. Connection, correction, velocity and action packets are barriers.
        LEGACY.request(ms,(packet,flow)->(players && flow==EventFlow.INBOUND && packet instanceof S14PacketEntity)
                || ((movement||blink) && flow==EventFlow.OUTBOUND && packet instanceof C03PacketPlayer));
    }
    public static void blink() { spoof(500,false,false,false,false,true,true); }
}
