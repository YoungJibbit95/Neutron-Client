package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.impl.player.BedBreaker;
import me.youngjibbit.module.impl.render.BedESP;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Real module ownership/controller progress with loaded chunk reads; no renderer or live-server assertion. */
public class BedBreakerEstimateFlowTest {
    @Test public void activeEstimateReadsProgressWithoutProducingActions() throws Exception {
        Wave2IsolatedCase.run(Checks.class,"activeEstimateReadsProgressWithoutProducingActions");
    }
    @Test public void loadedChunkAndActionOwnershipAreRequiredEvenForCachedEstimates() throws Exception {
        Wave2IsolatedCase.run(Checks.class,"loadedChunkAndActionOwnershipAreRequiredEvenForCachedEstimates");
    }
    @Test public void cosmeticChangesAndCosmeticOnlyBatchesPreserveMiningAndRotationOwnership() throws Exception {
        Wave2IsolatedCase.run(Checks.class,"cosmeticChangesAndCosmeticOnlyBatchesPreserveMiningAndRotationOwnership");
    }
    @Test public void mixedSettingBatchStillInvalidatesTheActionAndItsPresentationLease() throws Exception {
        Wave2IsolatedCase.run(Checks.class,"mixedSettingBatchStillInvalidatesTheActionAndItsPresentationLease");
    }
    @Test public void overlayCosmeticsRetainObservationsAndUnloadDiscardsThemImmediately() throws Exception {
        Wave2IsolatedCase.run(Checks.class,"overlayCosmeticsRetainObservationsAndUnloadDiscardsThemImmediately");
    }
    @Test public void nativeProgressAndCooldownChangesInvalidateAnEstimateWithinTheSameTick() throws Exception {
        Wave2IsolatedCase.run(Checks.class,"nativeProgressAndCooldownChangesInvalidateAnEstimateWithinTheSameTick");
    }
    public static class Checks {
        @Test public void activeEstimateReadsProgressWithoutProducingActions() throws Exception {
            try(BedBreakerTestSupport.Fixture f=new BedBreakerTestSupport.Fixture()) {
                loadedProvider(f); f.module.legit.setValue(false); f.aim(); f.input(false); f.digs();
                long revision=f.controller.getMiningRevision(); int calls=f.controller.automaticCalls; float damage=f.progress();
                float yaw=f.f.player.rotationYaw,pitch=f.f.player.rotationPitch;
                BedBreakEstimate first=f.module.getBreakEstimate();
                assertTrue(first.state.toString(),first.hasEstimate()); assertEquals(BedBreakEstimate.Basis.DIRECT,first.basis);
                assertEquals(.2,first.totalSeconds,1e-9);
                for(int frame=0;frame<20;frame++) assertSame(first,f.module.getBreakEstimate());
                assertEquals(revision,f.controller.getMiningRevision()); assertEquals(calls,f.controller.automaticCalls);assertEquals(damage,f.progress(),0);
                assertEquals(yaw,f.f.player.rotationYaw,0); assertEquals(pitch,f.f.player.rotationPitch,0);assertTrue(f.digs().isEmpty());
                f.next(); f.rotation(); f.input(false);
                BedBreakEstimate advanced=f.module.getBreakEstimate();assertTrue(advanced.hasEstimate());assertEquals(.25,advanced.blockProgress,0);assertEquals(.15,advanced.totalSeconds,1e-9);
                Pulsar.BUS.post(new WorldEvent.Unload(f.world));assertFalse(f.module.getBreakEstimate().hasEstimate());
            }
        }
        @Test public void loadedChunkAndActionOwnershipAreRequiredEvenForCachedEstimates() throws Exception {
            try(BedBreakerTestSupport.Fixture f=new BedBreakerTestSupport.Fixture()) {
                ChunkProviderClient provider=loadedProvider(f);f.module.legit.setValue(false);f.aim();f.input(false);
                assertTrue(f.module.getBreakEstimate().hasEstimate());
                f.controller.onPlayerDamageBlock(f.pos,EnumFacing.UP);f.digs();
                assertFalse(f.module.getBreakEstimate().hasEstimate());assertTrue(f.digs().isEmpty());
                assertTrue("the fixture intentionally exposes the old weak loaded predicate",f.world.isBlockLoaded(f.pos));
                provider.unloadChunk(0,0);
                assertNull(BedWorldTracker.stateIfLoaded(f.world,f.pos));assertEquals(0,provider.getLoadedChunkCount());
                assertFalse(f.module.getBreakEstimate().hasEstimate());
            }
        }
        @Test public void cosmeticChangesAndCosmeticOnlyBatchesPreserveMiningAndRotationOwnership() throws Exception {
            try(BedBreakerTestSupport.Fixture f=new BedBreakerTestSupport.Fixture()) {
                loadedProvider(f);f.module.legit.setValue(false);f.aim();f.input(false);f.digs();
                long revision=f.controller.getMiningRevision(), action=f.module.getBreakEstimateRevision();
                int calls=f.controller.automaticCalls;float damage=f.progress();float[] rotation=f.module.rotationEngine.rotation();
                Object owner=BedBreakerTestSupport.field(BedBreaker.class,"owned").get(f.module);
                BedBreakEstimate estimate=f.module.getBreakEstimate();assertTrue(estimate.hasEstimate());
                f.module.getNumberSetting("Indicator X").setValue(23.5D);f.module.getNumberSetting("Indicator Y").setValue(81D);
                f.module.getNumberSetting("Indicator Scale").setValue(1.25D);f.module.getBooleanSetting("Break Indicator").setValue(false);
                f.module.getBooleanSetting("Break Indicator").setValue(true);
                assertSame(owner,BedBreakerTestSupport.field(BedBreaker.class,"owned").get(f.module));
                assertEquals(revision,f.controller.getMiningRevision());assertEquals(action,f.module.getBreakEstimateRevision());
                assertSame(estimate,f.module.getBreakEstimate());assertTrue(f.digs().isEmpty());
                f.module.beginSettingsUpdate();
                f.module.getNumberSetting("Indicator X").setValue(75D);f.module.getNumberSetting("Indicator Y").setValue(40D);
                f.module.endConfigUpdate();
                assertSame(owner,BedBreakerTestSupport.field(BedBreaker.class,"owned").get(f.module));
                assertEquals(revision,f.controller.getMiningRevision());assertEquals(action,f.module.getBreakEstimateRevision());
                assertEquals(calls,f.controller.automaticCalls);assertEquals(damage,f.progress(),0);
                assertArrayEquals(rotation,f.module.rotationEngine.rotation(),0);assertFalse(f.module.rotationEngine.returning());
                assertEquals(BedBreaker.Status.MINING,f.module.getStatus());assertSame(estimate,f.module.getBreakEstimate());assertTrue(f.digs().isEmpty());
            }
        }
        @Test public void mixedSettingBatchStillInvalidatesTheActionAndItsPresentationLease() throws Exception {
            try(BedBreakerTestSupport.Fixture f=new BedBreakerTestSupport.Fixture()) {
                loadedProvider(f);f.module.legit.setValue(false);f.aim();f.input(false);f.digs();
                assertTrue(f.module.getBreakEstimate().hasEstimate());long action=f.module.getBreakEstimateRevision();
                f.module.beginSettingsUpdate();f.module.getNumberSetting("Indicator X").setValue(25D);
                f.module.getBooleanSetting("Silent").setValue(false);f.module.endConfigUpdate();
                assertEquals(action+1,f.module.getBreakEstimateRevision());assertFalse(f.controller.isMiningBlock(f.pos));
                assertFalse(f.module.getBreakEstimate().hasEstimate());assertEquals(BedBreaker.Status.WAITING,f.module.getStatus());
                List<net.minecraft.network.play.client.C07PacketPlayerDigging> packets=f.digs();assertEquals(1,packets.size());
                assertEquals(net.minecraft.network.play.client.C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK,packets.get(0).getStatus());
            }
        }
        @Test public void overlayCosmeticsRetainObservationsAndUnloadDiscardsThemImmediately() throws Exception {
            try(BedBreakerTestSupport.Fixture f=new BedBreakerTestSupport.Fixture()) {
                loadedProvider(f);f.module.legit.setValue(false);f.aim();f.input(false);f.digs();
                BedESP overlay=new BedESP();overlay.setToggled(true);
                try {
                    Pulsar.BUS.post(new TickEvent());
                    BedWorldTracker tracker=(BedWorldTracker)BedBreakerTestSupport.field(BedESP.class,"tracker").get(overlay);
                    List<BedObservation> before=tracker.snapshot(f.f.mc);assertEquals(1,before.size());
                    long action=f.module.getBreakEstimateRevision(),revision=f.controller.getMiningRevision();
                    int calls=f.controller.automaticCalls;float progress=f.progress();
                    overlay.mode.setValue("Flame");overlay.widgets.setValue(false);overlay.colors.setValue("Team hint");
                    overlay.beginSettingsUpdate();overlay.glowStrength.setValue(1.2D);overlay.lineWidth.setValue(2D);overlay.endConfigUpdate();
                    assertSame(before.get(0),tracker.snapshot(f.f.mc).get(0));
                    assertEquals(action,f.module.getBreakEstimateRevision());assertEquals(revision,f.controller.getMiningRevision());
                    assertEquals(calls,f.controller.automaticCalls);assertEquals(progress,f.progress(),0);assertTrue(f.digs().isEmpty());
                    Pulsar.BUS.post(new WorldEvent.Unload(f.world));
                    assertTrue(tracker.snapshot(f.f.mc).isEmpty());assertNull(BedBreakerTestSupport.field(BedESP.class,"world").get(overlay));
                    Pulsar.BUS.post(new TickEvent());assertTrue(tracker.snapshot(f.f.mc).isEmpty());
                    overlay.setToggled(false);assertNull(BedBreakerTestSupport.field(BedESP.class,"connection").get(overlay));
                } finally { if(overlay.isToggled())overlay.setToggled(false); }
            }
        }
        @Test public void nativeProgressAndCooldownChangesInvalidateAnEstimateWithinTheSameTick() throws Exception {
            try(BedBreakerTestSupport.Fixture f=new BedBreakerTestSupport.Fixture()) {
                loadedProvider(f);f.module.legit.setValue(false);f.aim();f.input(false);f.digs();
                int tick=f.f.player.ticksExisted,calls=f.controller.automaticCalls;long revision=f.controller.getMiningRevision();
                BedBreakEstimate first=f.module.getBreakEstimate();assertTrue(first.hasEstimate());assertEquals(.2,first.totalSeconds,1e-9);
                // Model native damage/cooldown updates without granting another module an action revision.
                BedBreakerTestSupport.field(PlayerControllerMP.class,"curBlockDamageMP").setFloat(f.controller,.5f);
                BedBreakEstimate damage=f.module.getBreakEstimate();assertNotSame(first,damage);assertEquals(.1,damage.totalSeconds,1e-9);
                BedBreakerTestSupport.field(PlayerControllerMP.class,"blockHitDelay").setInt(f.controller,3);
                BedBreakEstimate delay=f.module.getBreakEstimate();assertNotSame(damage,delay);assertEquals(.25,delay.totalSeconds,1e-9);
                assertSame(delay,f.module.getBreakEstimate());assertEquals(.5,f.progress(),0);assertEquals(3,f.controller.getBlockHitDelay());
                assertEquals(tick,f.f.player.ticksExisted);assertEquals(revision,f.controller.getMiningRevision());
                assertEquals(calls,f.controller.automaticCalls);assertTrue(f.digs().isEmpty());
            }
        }
        private static ChunkProviderClient loadedProvider(BedBreakerTestSupport.Fixture f) throws Exception {
            // The tiny world bypasses the normal World constructor; native chunk unload still needs this host list.
            BedBreakerTestSupport.field(World.class,"unloadedEntityList").set(f.world,new ArrayList<Entity>());
            ChunkProviderClient provider=new ChunkProviderClient(f.world);
            BedBreakerTestSupport.field(World.class,"chunkProvider").set(f.world,provider);
            Chunk chunk=provider.loadChunk(0,0);
            chunk.getBlockStorageArray()[4]=new ExtendedBlockStorage(64,false) {
                @Override public IBlockState get(int x,int y,int z) { return f.world.getBlockState(new BlockPos(x,64+y,z)); }
                @Override public boolean isEmpty() { return false; }
            };
            return provider;
        }
    }
}
