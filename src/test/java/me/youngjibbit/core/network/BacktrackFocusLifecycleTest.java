package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.module.impl.exploit.Backtrack;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.network.play.server.S14PacketEntity;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

/** Actual Backtrack listeners, shared FIFO and original local entity packet consumer. */
public class BacktrackFocusLifecycleTest {
    private static final String[] MODES={"Distance","Timed","Pulse","Intave","Receipt Safe"};
    private static Backtrack enable(ExploitHookFixture f,String mode)throws Exception{
        AtomicLong clock=new AtomicLong();NetworkTestSupport.clock(f,clock);
        Backtrack module=new Backtrack(clock::get,f.client,(min,max)->max);
        module.getBooleanSetting("Only Aura").setValue(false);
        module.getModeSetting("Backtrack Mode").setValue(mode);
        module.getModeSetting("Delay Mode").setValue("Fixed");
        module.getNumberSetting("Delay").setValue(100D);
        module.getNumberSetting("Cooldown Ticks").setValue(0D);
        module.setToggled(true);return module;
    }
    private static void move(ExploitHookFixture f){
        f.receive(new S14PacketEntity.S15PacketEntityRelMove(7,(byte)4,(byte)0,(byte)0,true));
    }
    private static ExploitHookFixture.Enemy acquireAndHold(ExploitHookFixture f)throws Exception{
        ExploitHookFixture.Enemy enemy=f.enemy(7,3);
        assertTrue(f.controller.tryAttackEntity(f.player,enemy,()->true));
        f.drainOutbound();move(f);
        assertEquals("positive control: a real received S14 is held",1,f.pipe().delays().size());
        assertEquals(96,enemy.serverPosX);return enemy;
    }
    @Test public void focusLossReleasesEveryModeWithoutWaitingForAnotherPacket()throws Exception{
        for(String mode:MODES)try(ExploitHookFixture f=new ExploitHookFixture()){
            Backtrack module=enable(f,mode);
            try{
                ExploitHookFixture.Enemy enemy=acquireAndHold(f);
                f.client.inGameHasFocus=false;Pulsar.BUS.post(new TickEvent());
                assertEquals(mode+": focus loss must drain the original FIFO",0,f.pipe().delays().size());
                assertEquals(100,enemy.serverPosX);
                f.client.inGameHasFocus=true;Pulsar.BUS.post(new TickEvent());move(f);
                assertEquals(mode+": old acquisition cannot resume after refocus",0,f.pipe().delays().size());
                assertEquals(104,enemy.serverPosX);
                assertTrue(f.controller.tryAttackEntity(f.player,enemy,()->true));move(f);
                assertEquals(mode+": a fresh accepted acquisition is usable",1,f.pipe().delays().size());
            }finally{module.setToggled(false);}
        }
    }
    @Test public void incomingPacketWhileUnfocusedDrainsEarlierMovementInFifoOrder()throws Exception{
        try(ExploitHookFixture f=new ExploitHookFixture()){
            Backtrack module=enable(f,"Receipt Safe");
            try{
                ExploitHookFixture.Enemy enemy=acquireAndHold(f);
                f.client.inGameHasFocus=false;move(f);
                assertEquals(0,f.pipe().delays().size());
                assertEquals("both actual relative movements applied exactly once",104,enemy.serverPosX);
                f.client.inGameHasFocus=true;move(f);
                assertEquals(0,f.pipe().delays().size());assertEquals(108,enemy.serverPosX);
            }finally{module.setToggled(false);}
        }
    }
    @Test public void openingAndClosingGuiCannotReviveAPreviousAcquisition()throws Exception{
        try(ExploitHookFixture f=new ExploitHookFixture()){
            Backtrack module=enable(f,"Receipt Safe");
            try{
                ExploitHookFixture.Enemy enemy=acquireAndHold(f);
                f.client.currentScreen=new GuiScreen(){};Pulsar.BUS.post(new TickEvent());
                assertEquals(0,f.pipe().delays().size());assertEquals(100,enemy.serverPosX);
                f.client.currentScreen=null;move(f);
                assertEquals("closing a GUI is not a fresh target acquisition",0,f.pipe().delays().size());
                assertEquals(104,enemy.serverPosX);
            }finally{module.setToggled(false);}
        }
    }
}
