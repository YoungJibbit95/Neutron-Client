package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.gui.iosUi.iOS;
import me.youngjibbit.gui.iosUi.component.iOSComponent;
import me.youngjibbit.gui.iosUi.component.impl.*;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.utils.animations.Animation;
import me.youngjibbit.utils.animations.Direction;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Actual iOS container and child handlers, with real rotation settings and no renderer/session. */
public class IosSettingsVisibilityTest {
    private Field field(Class<?> type,String name) throws Exception { Field f=type.getDeclaredField(name);f.setAccessible(true);return f; }
    @Test public void presetsHideExpertControlsFromHeightAndClicksAndReleaseHiddenSliderCapture() throws Exception {
        sun.misc.Unsafe unsafe=(sun.misc.Unsafe)field(sun.misc.Unsafe.class,"theUnsafe").get(null);
        iOS shell=(iOS)unsafe.allocateInstance(iOS.class);
        Field gui=field(Pulsar.class,"iOS");Object previous=gui.get(Pulsar.INSTANCE);
        try {
            gui.set(Pulsar.INSTANCE,shell);Aura aura=new Aura();
            iOSModule row=new iOSModule(aura,10,10,220,24);field(iOSModule.class,"open").set(row,true);
            Animation opening=(Animation)field(iOSModule.class,"openAnimation").get(row);
            opening.setDirection(Direction.FORWARDS);opening.timerUtil.setTime(System.currentTimeMillis()-1000);
            List<iOSComponent.Sub<?>> controls=(List<iOSComponent.Sub<?>>)field(iOSModule.class,"VALUES").get(row);
            iOSCheckbox randomization=null;iOSSlider frequency=null;
            for(iOSComponent.Sub<?> control:controls) {
                if(control instanceof iOSCombo) {
                    Animation dropdown=(Animation)field(iOSCombo.class,"openAnimation").get(control);
                    dropdown.timerUtil.setTime(System.currentTimeMillis()-1000);
                }
                if(control instanceof iOSCheckbox && ((iOSCheckbox)control).getValue().getId().equals("randomization"))randomization=(iOSCheckbox)control;
                if(control instanceof iOSSlider && ((iOSSlider)control).getValue().getId().equals("frequency"))frequency=(iOSSlider)control;
            }
            assertNotNull(randomization);assertNotNull(frequency);
            randomization.updateComponent(10,70);frequency.updateComponent(10,105);
            AtomicInteger callbacks=new AtomicInteger();aura.getBooleanSetting("Randomization").setOnChange(value->callbacks.incrementAndGet());
            float presetHeight=row.getHeight();assertFalse(randomization.getValue().isVisible());
            row.mouseClicked(200,80,0);assertTrue(randomization.getValue().getValue());assertEquals(0,callbacks.get());
            aura.getModeSetting("Randomization Style").setValue("Benutzerdefiniert");
            assertTrue(randomization.getValue().isVisible());assertTrue(row.getHeight()>presetHeight);
            row.mouseClicked(200,80,0);assertFalse(randomization.getValue().getValue());assertEquals(1,callbacks.get());
            frequency.updateComponent(10,70); // Stale hidden geometry deliberately overlaps the checkbox.
            row.mouseClicked(200,80,0);assertTrue(frequency.getValue().isVisible());
            assertFalse("The click revealing a slider must not start dragging it",field(iOSSlider.class,"dragging").getBoolean(frequency));
            randomization.updateComponent(10,40);
            row.mouseClicked(200,80,0);assertTrue(field(iOSSlider.class,"dragging").getBoolean(frequency));
            aura.getModeSetting("Randomization Style").setValue("Sanft");
            row.mouseClicked(200,80,0);assertFalse(field(iOSSlider.class,"dragging").getBoolean(frequency));
            assertEquals(presetHeight,row.getHeight(),.001);assertEquals(2,callbacks.get());assertFalse(aura.isToggled());
        } finally { gui.set(Pulsar.INSTANCE,previous); }
    }
}
