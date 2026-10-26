package me.youngjibbit.module;

import org.lwjgl.input.Keyboard;
import me.youngjibbit.Pulsar;

public class KeyListener {

    public static void onKeyPress(int key) {
        if (Pulsar.INSTANCE.getMm() != null) {
            Pulsar.INSTANCE.getMm().toggleByKey(key);
        }
    }
}
