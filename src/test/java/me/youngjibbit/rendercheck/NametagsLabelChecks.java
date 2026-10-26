package me.youngjibbit.rendercheck;

import me.youngjibbit.module.impl.render.Nametags;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleManager;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.scoreboard.*;
import net.minecraft.util.*;
import javax.vecmath.Vector4d;
import java.lang.reflect.Field;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;

/** Executes native label decisions; only frustum/projection and the final glyph draw are substituted. */
public class NametagsLabelChecks {
    @me.youngjibbit.module.ModuleInfo(name="Nametags",category=me.youngjibbit.module.Category.Render)
    static class Names extends Nametags {
        boolean inView=true, projected=true;
        Vector4d realProject(Entity e) { return super.projectLabel(e); }
        @Override public boolean isInViewFrustrum(Entity e) { return inView; }
        @Override protected Vector4d projectLabel(Entity e) { return projected?new Vector4d(1,2,3,4):null; }
    }
    static class Player extends RotationBehaviorHookTest.Player {
        boolean invisible,sneak;
        Team team;
        @Override public boolean isInvisible() { return invisible; }
        @Override public boolean isInvisibleToPlayer(net.minecraft.entity.player.EntityPlayer p) { return invisible; }
        @Override public boolean isSneaking() { return sneak; }
        @Override public Team getTeam() { return team; }
        @Override public IChatComponent getDisplayName() { return new ChatComponentText("remote"); }
    }
    static class Pet extends EntityWolf {
        boolean custom=true,always=true,invisible;
        Pet() { super(null); }
        @Override public boolean hasCustomName() { return custom; }
        @Override public boolean getAlwaysRenderNameTagForRender() { return always; }
        @Override public boolean isInvisibleToPlayer(net.minecraft.entity.player.EntityPlayer p) { return invisible; }
    }
    static class Glyph extends RuntimeException { }
    static class General extends Render<Entity> {
        General() { super(null); }
        @Override protected ResourceLocation getEntityTexture(Entity e) { return null; }
        @Override public FontRenderer getFontRendererFromRenderManager() { throw new Glyph(); }
        void label(Entity e,String text) { renderLivingLabel(e,text,0,0,0,64); }
    }
    static class Living extends RenderLiving<Pet> {
        Living() { super(null,null,0); }
        @Override protected ResourceLocation getEntityTexture(Pet e) { return null; }
        boolean eligible(Pet e) { return canRenderName(e); }
    }
    static class Players extends RendererLivingEntity<Player> {
        Players() { super(null,null,0); }
        @Override public FontRenderer getFontRendererFromRenderManager() { throw new AssertionError("Vanilla sneaking glyph draw executed despite replacement"); }
        @Override protected ResourceLocation getEntityTexture(Player e) { return null; }
    }
    static me.youngjibbit.rendercheck.PbufferTestContext gl;
    static RotationBehaviorHookTest.Fixture f;
    static Names names;
    static Player player;
    static RenderManager manager;
    static General general;
    static Living living;
    static Players players;
    static void field(Class<?> c,Object o,String name,Object value) throws Exception { Field x=c.getDeclaredField(name);x.setAccessible(true);x.set(o,value); }
    @BeforeClass public static void setup() throws Exception {
        System.setProperty("org.lwjgl.librarypath",java.nio.file.Paths.get(System.getProperty("user.dir")).getParent().resolve("test_natives/windows").toString());
        gl=new me.youngjibbit.rendercheck.PbufferTestContext();
        f=new RotationBehaviorHookTest.Fixture();f.mc.displayWidth=1280;f.mc.displayHeight=960;
        f.mc.gameSettings=RotationBehaviorHookTest.bare(GameSettings.class);
        field(net.minecraft.src.Config.class,null,"gameSettings",f.mc.gameSettings);
        field(net.minecraft.client.Minecraft.class,f.mc,"mcLanguageManager",new net.minecraft.client.resources.LanguageManager(null,"en_US"));
        f.mc.timer=new net.minecraft.util.Timer(20,()->0);f.mc.entityRenderer=RotationBehaviorHookTest.bare(EntityRenderer.class);
        manager=RotationBehaviorHookTest.bare(RenderManager.class);manager.livingPlayer=f.player;
        field(net.minecraft.client.Minecraft.class,f.mc,"renderManager",manager);
        field(net.minecraft.world.World.class,f.world,"loadedEntityList",new ArrayList<Entity>());
        names=new Names();field(Module.class,names,"toggled",true);
        Field byClass=ModuleManager.class.getDeclaredField("modulesByClass");byClass.setAccessible(true);
        ((Map)byClass.get(Pulsar.INSTANCE.getMm())).put(Nametags.class,names);
        player=RotationBehaviorHookTest.bare(Player.class);player.worldObj=f.world;player.posZ=3;
        f.world.loadedEntityList.add(player);
        general=RotationBehaviorHookTest.bare(General.class);living=RotationBehaviorHookTest.bare(Living.class);players=RotationBehaviorHookTest.bare(Players.class);
        for(Render<?> r:Arrays.asList(general,living,players))field(Render.class,r,"renderManager",manager);
    }
    @AfterClass public static void close() throws Exception { try { f.close(); } finally { gl.close(); } }
    @Before public void reset() throws Exception { field(Module.class,names,"toggled",true);names.inView=names.projected=true;player.invisible=player.sneak=false;player.team=null;player.posZ=3;player.riddenByEntity=null;f.mc.gameSettings.hideGUI=false; }
    @Test public void namedPetsKeepVanillaAlwaysPointedAndVisibilityRulesWithModuleOnAndOff() throws Exception {
        Pet pet=RotationBehaviorHookTest.bare(Pet.class);pet.custom=pet.always=true;
        for(boolean enabled:new boolean[]{false,true}) {
            field(Module.class,names,"toggled",enabled);
            assertTrue(living.eligible(pet));
            pet.always=false;assertFalse(living.eligible(pet));manager.pointedEntity=pet;assertTrue(living.eligible(pet));
            pet.custom=false;assertFalse(living.eligible(pet));pet.custom=true;
            pet.invisible=true;assertFalse(living.eligible(pet));pet.invisible=false;
            f.mc.gameSettings.hideGUI=true;assertFalse(living.eligible(pet));f.mc.gameSettings.hideGUI=false;
            manager.pointedEntity=null;pet.always=true;
            try { general.label(pet,"pet");fail("non-player label must reach the vanilla glyph draw"); } catch(Glyph expected) { }
        }
    }
    @Test public void exactRemoteNameIsReplacedButOtherLabelsAndProjectionFallbackSurvive() throws Exception {
        assertTrue(Nametags.replacesVanillaLabel(player,manager));
        general.label(player,"remote"); // returns before any native glyph draw
        try { general.label(player,"20 objective");fail(); } catch(Glyph expected) { }
        names.projected=false;assertFalse(Nametags.replacesVanillaLabel(player,manager));
        try { general.label(player,"remote");fail(); } catch(Glyph expected) { }
        names.projected=true;names.inView=false;assertFalse(Nametags.replacesVanillaLabel(player,manager));
        names.inView=true;assertFalse(Nametags.replacesVanillaLabel(f.player,manager));
        player.invisible=true;assertFalse(Nametags.replacesVanillaLabel(player,manager));player.invisible=false;
        f.mc.gameSettings.hideGUI=true;assertFalse(Nametags.replacesVanillaLabel(player,manager));f.mc.gameSettings.hideGUI=false;
        player.posZ=64;assertTrue(Nametags.replacesVanillaLabel(player,manager));
        player.posZ=33;player.sneak=true;assertTrue(Nametags.replacesVanillaLabel(player,manager));
        field(Module.class,names,"toggled",false);assertFalse(Nametags.replacesVanillaLabel(player,manager));
        try { general.label(player,"remote");fail(); } catch(Glyph expected) { }
    }
    @Test public void sneakingNameTakesTheSameReplacementDecisionBeforeItsDirectDraw() {
        player.sneak=true;assertTrue(Nametags.replacesVanillaLabel(player,manager));
        players.renderName(player,0,0,0); // old direct sneaking path attempted native GL despite replacement
    }
    @Test public void actualProjectionRejectsClippedBoundsAndLeavesCameraMatricesIntact() {
        org.lwjgl.opengl.GL11.glViewport(0,0,1280,960);
        org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);
        org.lwjgl.opengl.GL11.glLoadIdentity();org.lwjgl.opengl.GL11.glOrtho(-10,10,-10,10,-10,10);
        org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);org.lwjgl.opengl.GL11.glLoadIdentity();
        player.lastTickPosZ=player.posZ=3;player.width=.6f;player.height=1.8f;
        assertNotNull(names.realProject(player));
        org.lwjgl.opengl.GL11.glMatrixMode(org.lwjgl.opengl.GL11.GL_PROJECTION);
        org.lwjgl.opengl.GL11.glLoadIdentity();org.lwjgl.opengl.GL11.glOrtho(-10,10,-10,10,10,20);
        java.nio.FloatBuffer before=org.lwjgl.BufferUtils.createFloatBuffer(16),after=org.lwjgl.BufferUtils.createFloatBuffer(16);
        org.lwjgl.opengl.GL11.glGetFloat(org.lwjgl.opengl.GL11.GL_PROJECTION_MATRIX,before);
        assertNull(names.realProject(player));
        org.lwjgl.opengl.GL11.glGetFloat(org.lwjgl.opengl.GL11.GL_PROJECTION_MATRIX,after);
        for(int index=0;index<16;index++)assertEquals(before.get(index),after.get(index),0);
        assertEquals(org.lwjgl.opengl.GL11.GL_NO_ERROR,org.lwjgl.opengl.GL11.glGetError());
    }
    private static Vector4d includeProjected(Vector4d bounds,double x,double y) throws Exception {
        java.lang.reflect.Method method=Nametags.class.getDeclaredMethod("includeProjected",Vector4d.class,double.class,double.class);
        method.setAccessible(true);
        return (Vector4d)method.invoke(null,bounds,x,y);
    }
    @Test public void projectedBoundsKeepBothScreenQuadrantsWithoutDepthOrZeroInTheirSeed() throws Exception {
        for(double sign:new double[]{-1,1}) {
            Vector4d bounds=includeProjected(null,sign*100,sign*200);
            assertEquals(sign*100,bounds.z,0);assertEquals(sign*200,bounds.w,0);
            bounds=includeProjected(bounds,sign*120,sign*240);
            assertEquals(Math.min(sign*100,sign*120),bounds.x,0);
            assertEquals(Math.max(sign*100,sign*120),bounds.z,0);
            assertEquals(Math.min(sign*200,sign*240),bounds.y,0);
            assertEquals(Math.max(sign*200,sign*240),bounds.w,0);
        }
    }
    @Test public void replacementHonorsEveryVanillaTeamVisibilityPolicy() throws Exception {
        Scoreboard board=new Scoreboard();ScorePlayerTeam team=new ScorePlayerTeam(board,"team"),other=new ScorePlayerTeam(board,"other");player.team=team;
        Player local=RotationBehaviorHookTest.bare(Player.class);f.mc.thePlayer=local;
        try {
            for(Team localTeam:Arrays.asList(null,team,other))for(Team.EnumVisible policy:Team.EnumVisible.values()) {
                local.team=localTeam;team.setNameTagVisibility(policy);
                boolean expected=policy==Team.EnumVisible.ALWAYS
                        || policy==Team.EnumVisible.HIDE_FOR_OTHER_TEAMS && (localTeam==null || localTeam==team)
                        || policy==Team.EnumVisible.HIDE_FOR_OWN_TEAM && (localTeam==null || localTeam!=team);
                assertEquals(policy+" / "+localTeam,expected,RendererLivingEntity.canRenderVanillaName(player,local,manager.livingPlayer,true));
                assertEquals(expected,names.replacementCandidate(player,manager));
            }
        } finally {f.mc.thePlayer=f.player;}
        team.setNameTagVisibility(Team.EnumVisible.NEVER);assertFalse(Nametags.replacesVanillaLabel(player,manager));
        team.setNameTagVisibility(Team.EnumVisible.ALWAYS);f.mc.gameSettings.hideGUI=true;
        assertFalse(Nametags.replacesVanillaLabel(player,manager));
        try { general.label(player,"remote");fail(); } catch(Glyph expected) { }
    }
}
