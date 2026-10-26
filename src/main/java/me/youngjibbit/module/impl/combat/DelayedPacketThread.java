package me.youngjibbit.module.impl.combat;

import lombok.AllArgsConstructor;
import me.youngjibbit.utils.PacketUtils;
import net.minecraft.network.Packet;

@AllArgsConstructor
public class DelayedPacketThread extends Thread {

    private Packet packet;
    private long delay;

    @Override
    public void run() {
        try {
            this.sleep(delay);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            PacketUtils.sendPacketNoEvent(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
