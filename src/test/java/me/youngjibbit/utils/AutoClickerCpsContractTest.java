package me.youngjibbit.utils;

import me.youngjibbit.module.impl.ghost.AutoClicker2;
import org.junit.Test;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

/** Exercises the module's actual setting consumer without enabling it, clicking or starting a game. */
public class AutoClickerCpsContractTest {
    @Test public void secureCpsRetainsEqualAscendingAndReversedBoundContracts() throws Exception {
        AutoClicker2 module=new AutoClicker2();module.getModeSetting("Randomization Mode").setValue("Secure");
        Method resolve=AutoClicker2.class.getDeclaredMethod("resolveCps");resolve.setAccessible(true);
        module.getNumberSetting("Min CPS").setValue(10.0);module.getNumberSetting("Max CPS").setValue(10.0);
        assertEquals(10,(Double)resolve.invoke(module),0);
        for(boolean reversed:new boolean[]{false,true}) {
            module.getNumberSetting("Min CPS").setValue(reversed?20.0:1.0);
            module.getNumberSetting("Max CPS").setValue(reversed?1.0:20.0);
            for(int i=0;i<100;i++) {
                double value=(Double)resolve.invoke(module);assertTrue(Double.isFinite(value));
                if(reversed)assertTrue(value>1 && value<=20);else assertTrue(value>=1 && value<20);
            }
        }
    }
}
