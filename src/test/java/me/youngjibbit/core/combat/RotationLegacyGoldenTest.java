package me.youngjibbit.core.combat;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

/** Freeze these signatures on the old implementation before touching production sources. */
public class RotationLegacyGoldenTest {
    private static final String PURE_EXPECTED="b1554771927979f60c19602df67ea374ff6d8abddf9a0c7098ce713999a1dc84";
    static final String CONSUMERS_EXPECTED="44c8af34b9eb0bbe4b3a24df070c0de5ee9568a9dda646a4a53923bc4a0d6bf4";
    static void bits(StringBuilder trace,float... values) {
        for(float value:values)trace.append(Integer.toHexString(Float.floatToIntBits(value))).append(',');
        trace.append('\n');
    }
    static String digest(String text) throws Exception {
        byte[] bytes=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return out.toString();
    }
    static void verify(String label,String expected,String trace) throws Exception {
        String actual=digest(trace);System.out.println("BEZIER_LEGACY_"+label+"="+actual);
        if(Boolean.getBoolean("neutron.bezier.captureLegacy"))return;
        assertNotEquals("Capture and freeze the OLD implementation first", "CAPTURE_BEFORE_IMPLEMENTATION",expected);
        assertEquals(label+" legacy float-bit trace changed",expected,actual);
    }
    @Test public void allLegacyModesPatternsAndPresetsHaveFrozenFloatBitTraces() throws Exception {
        StringBuilder trace=new StringBuilder();
        for(RotationProfile.Mode mode:RotationProfile.Mode.values())for(RotationProfile.Pattern pattern:RotationProfile.Pattern.values())
            for(String preset:new String[]{"Aus","Sanft","Organisch","Dynamisch","Benutzerdefiniert"}) {
                AtomicLong clock=new AtomicLong();RotationTrajectory path=new RotationTrajectory(clock::get,76421);
                RotationProfile p=new RotationProfile();p.mode=mode;p.pattern=pattern;p.yawSpeed=7;p.pitchSpeed=9;
                p.preset(preset);Object target=new Object();float yaw=179.3f,pitch=-87.1f;
                trace.append(mode).append('/').append(pattern).append('/').append(preset).append('\n');
                for(int step=0;step<64;step++) {
                    clock.addAndGet(new long[]{0,17_000_000,50_000_000,113_000_000,37_000_000}[step%5]);
                    if(step==19||step==43)target=new Object();
                    if(step==11||step==12||step==31){path.pause(target);bits(trace,yaw,pitch);continue;}
                    Vec3 point=path.point(target,new AxisAlignedBB(-1,0,-1,1,2,1),new Vec3(.2,1.3,-.1),p);
                    bits(trace,(float)point.xCoord,(float)point.yCoord,(float)point.zCoord,path.sample(target,p,2));
                    float[] next=path.advance(target,yaw,pitch,step%2==0?-176.2f:91.7f,step%3==0?89.7f:-72.4f,1+step%4,p);
                    bits(trace,next);yaw=next[0];pitch=next[1];
                    bits(trace,path.advance(target,yaw,pitch,-120,80,2,p));
                }
            }
        verify("PURE",PURE_EXPECTED,trace.toString());
    }
    @Test public void legacyConsumersHaveFrozenPacketAndCameraTraces() throws Exception {
        RotationBezierIsolatedCase.run("legacyConsumersGolden");
    }
}
