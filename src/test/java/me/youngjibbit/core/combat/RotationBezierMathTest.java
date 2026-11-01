package me.youngjibbit.core.combat;

import org.junit.Test;
import java.lang.reflect.*;
import java.util.Random;
import static org.junit.Assert.*;

/** Reflection keeps the old source tree compilable: missing feature API is an explicit RED. */
public class RotationBezierMathTest {
    private Class<?> type() {
        try{return Class.forName("me.youngjibbit.core.combat.BezierSpeedEnvelope");}
        catch(ClassNotFoundException absent){throw new AssertionError("Missing BezierSpeedEnvelope feature",absent);}
    }
    private Object curve(double... v) throws Exception {
        return type().getConstructor(double.class,double.class,double.class,double.class).newInstance(v[0],v[1],v[2],v[3]);
    }
    private double eval(Object curve,String name,double... args) throws Exception {
        Class<?>[] parameters=new Class<?>[args.length];Object[] values=new Object[args.length];
        for(int i=0;i<args.length;i++){parameters[i]=double.class;values[i]=args[i];}
        return ((Number)type().getMethod(name,parameters).invoke(curve,values)).doubleValue();
    }
    // Independent de Casteljau oracle: no production evaluator, Bernstein coefficients or integral reused.
    private double casteljau(double[] controls,double t) {
        double[] a=controls.clone();for(int length=3;length>0;length--)for(int i=0;i<length;i++)a[i]=a[i]*(1-t)+a[i+1]*t;
        return a[0];
    }
    // Three-node Gauss-Legendre quadrature, exact for cubics; independent of production Simpson averaging.
    private double quadrature(double[] controls,double a,double b) {
        double mid=(a+b)/2,half=(b-a)/2,node=Math.sqrt(3.0/5.0);
        return half*(5.0/9*casteljau(controls,mid-half*node)+8.0/9*casteljau(controls,mid)+5.0/9*casteljau(controls,mid+half*node));
    }
    @Test public void moreThanTwoThousandSamplesMatchIndependentQuadratureAndStayBounded() throws Exception {
        Random random=new Random(0x5be21eL);
        for(int sample=0;sample<2048;sample++) {
            double[] v={random.nextDouble(),random.nextDouble(),random.nextDouble(),random.nextDouble()};Object c=curve(v);
            assertEquals(v[0],eval(c,"value",0),0);assertEquals(v[3],eval(c,"value",1),0);
            double a=random.nextDouble(),b=a+(1-a)*random.nextDouble();
            double value=eval(c,"value",a),average=eval(c,"average",a,b);
            assertEquals(casteljau(v,a),value,2e-14);
            assertEquals(quadrature(v,0,a),eval(c,"integral",a),2e-13);
            assertEquals(quadrature(v,a,b)/(b-a),average,2e-12);
            assertTrue(Double.isFinite(average)&&average>=0&&average<=1);
            double prior=0,total=(v[0]+v[1]+v[2]+v[3])/4;
            for(int i=0;i<=10;i++){double progress=eval(c,"integral",i/10.0)/total;assertTrue(progress+2e-13>=prior);prior=progress;}
            assertEquals(1,prior,2e-13);
            double tiny=1-1e-12;
            assertEquals(casteljau(v,(tiny+1)/2),eval(c,"average",tiny,1),2e-12);
        }
    }
    @Test public void constantZeroAndCompletedCurvesHaveDefinedFiniteResults() throws Exception {
        for(double value:new double[]{0,.1,.7,1}) {
            Object c=curve(value,value,value,value);
            assertEquals(value,eval(c,"average",.3,.3),1e-14);
            assertEquals(value,eval(c,"average",.2,.9),1e-14);
            assertEquals(value*.4,eval(c,"integral",.4),1e-14);
        }
        Object c=curve(.2,.4,.7,1);
        assertEquals(1,eval(c,"average",1,4),0);
        double expected=(quadrature(new double[]{.2,.4,.7,1},.8,1)+.2)/.4;
        assertEquals(expected,eval(c,"average",.8,1.2),2e-13);
    }
    @Test public void invalidControlsAndTimeCannotCreateNonfiniteOutput() throws Exception {
        for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-.1,1.1}) {
            try{curve(.1,invalid,.4,1);fail("invalid control accepted: "+invalid);}
            catch(InvocationTargetException expected){assertTrue(expected.getCause() instanceof IllegalArgumentException);}
        }
        Object c=curve(.1,.3,.6,1);
        for(String method:new String[]{"value","integral"})for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY}) {
            try{eval(c,method,invalid);fail("invalid time accepted");}
            catch(InvocationTargetException expected){assertTrue(expected.getCause() instanceof IllegalArgumentException);}
        }
    }
}
