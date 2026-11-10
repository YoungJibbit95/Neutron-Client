package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.activity.ExploitActivitySnapshot;
import me.youngjibbit.core.activity.WidgetIncident;
import me.youngjibbit.core.activity.WidgetIncidentJournal;
import me.youngjibbit.core.world.Wave2IsolatedCase;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.module.impl.exploit.Backtrack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S14PacketEntity;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.Collections;
import static org.junit.Assert.*;

/** Real Backtrack hold and successful/cancelled native S08 application, without rendering. */
public class WidgetIncidentFlowTest {
    @Test public void incidentRequiresSuccessfulNativeCorrectionAndNeverChangesItsAcknowledgement()throws Exception{
        Wave2IsolatedCase.run(Checks.class,"incidentRequiresSuccessfulNativeCorrectionAndNeverChangesItsAcknowledgement");
    }
    public static final class Checks {
        @Test public void incidentRequiresSuccessfulNativeCorrectionAndNeverChangesItsAcknowledgement()throws Exception{
            run(false);run(true);
        }
        private static void run(boolean cancel)throws Exception{
            WidgetIncidentJournal journal=WidgetIncidentJournal.global();journal.clear();
            try(ExploitHookFixture f=new ExploitHookFixture()){
                Field minecraft=field(Minecraft.class,"theMinecraft"),config=field(net.minecraft.src.Config.class,"minecraft");
                Object previousMinecraft=minecraft.get(null),previousConfig=config.get(null);
                minecraft.set(null,f.client);config.set(null,f.client);
                field(NetHandlerPlayClient.class,"doneLoadingTerrain").setBoolean(f.connection.play,true);
                f.player.ticksExisted=200;
                f.client.renderGlobal=NetworkTestSupport.bare(net.minecraft.client.renderer.RenderGlobal.class);
                Backtrack module=new Backtrack(System::nanoTime,f.client);
                module.getBooleanSetting("Only Aura").setValue(false);
                module.getModeSetting("Target Source").setValue("Attack / Cursor");
                module.getModeSetting("Backtrack Mode").setValue("Timed");
                module.getModeSetting("Delay Mode").setValue("Fixed");
                module.getNumberSetting("Delay").setValue(500D);
                NetworkTestSupport.Observer observer=new NetworkTestSupport.Observer();
                observer.action=e->{if(cancel&&e.isInbound()&&e.isPre()&&e.getPacket() instanceof S08PacketPlayerPosLook)e.cancel();};
                Pulsar.BUS.subscribe(observer);module.setToggled(true);
                try{
                    f.cursor(f.enemy(7,3));Pulsar.BUS.post(new TickEvent());
                    f.receive(new S14PacketEntity.S15PacketEntityRelMove(7,(byte)4,(byte)0,(byte)0,true));
                    ExploitActivitySnapshot activity=module.getActivitySnapshot();
                    assertNotNull("positive control: the module really owns held movement",activity);
                    journal.context(f.world,f.pipe(),f.pipe().getContextVersion());
                    journal.observeActivity(activity,System.nanoTime());
                    f.drainOutbound();
                    f.receive(new S08PacketPlayerPosLook(1,0,0,20,0,Collections.emptySet()));
                    WidgetIncident incident=journal.latest(WidgetIncident.Kind.EXPLOIT,System.nanoTime());
                    if(cancel){
                        assertNull(incident);assertEquals(0,f.player.posX,0);assertNull(f.connection.channel.readOutbound());
                    }else{
                        assertEquals(1,f.player.posX,0);assertNotNull(incident);
                        assertTrue(incident.detail.contains("Backtrack"));assertTrue(incident.detail.contains("Cause unconfirmed"));
                        assertEquals("1.0 blocks / 20.0°",incident.value);
                        assertTrue(f.connection.channel.readOutbound() instanceof C03PacketPlayer.C06PacketPlayerPosLook);
                        journal.context(f.world,f.pipe(),f.pipe().getContextVersion());
                        assertSame("the applied correction owns its new packet epoch",incident,journal.latest(WidgetIncident.Kind.EXPLOIT,System.nanoTime()));
                    }
                }finally{
                    module.setToggled(false);Pulsar.BUS.unsubscribe(observer);
                    minecraft.set(null,previousMinecraft);config.set(null,previousConfig);journal.clear();
                }
            }
        }
        private static Field field(Class<?> owner,String name)throws Exception{
            Field field=owner.getDeclaredField(name);field.setAccessible(true);return field;
        }
    }
}
