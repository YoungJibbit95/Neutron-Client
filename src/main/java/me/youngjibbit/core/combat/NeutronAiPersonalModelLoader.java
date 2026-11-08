package me.youngjibbit.core.combat;

import me.youngjibbit.core.combat.ai.NeutronAiCheckpoint;
import me.youngjibbit.core.combat.ai.NeutronAiTrainer;
import me.youngjibbit.core.network.PacketPipeline;
import net.minecraft.client.Minecraft;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Explicit personal NAI1 checkpoint provenance/consent gate, never an importer
 * or automatic startup model loader. Stores only fixed local files; a manifest
 * MAC authenticates these files against the locally generated private key, NOT
 * against a compromised machine and NOT as proof of human mouse provenance.
 */
public final class NeutronAiPersonalModelLoader {
    private static final String PARENT="neutron-ai", FOLDER="personal-v1";
    private static final String MODEL="model-v1.nai", PROOF="origin-v1.bin", KEY="local-key-v1.bin";
    private static final String STAGED_MODEL=".model.pending", STAGED_PROOF=".origin.pending";
    private static final int MAGIC=0x4e415031, VERSION=1;
    private static final int PROOF_BYTES=8*4+3*4+32+32;
    private static final int PROOF_SIGNED_BYTES=PROOF_BYTES-32;
    private final Path root;
    private final RotationManager rotation;
    private final Minecraft minecraft;
    private final Thread owningThread=Thread.currentThread();
    private Scope consentScope;

    /** Contains only RAM identities. No credentials, session name or ID is serialized. */
    public static final class Scope {
        public final Object account,world,player,connection;
        public final long epoch;
        private Scope(Object account,Object world,Object player,Object connection,long epoch) {
            this.account=account;this.world=world;this.player=player;
            this.connection=connection;this.epoch=epoch;
        }
        public static Scope capture(Minecraft mc) {
            if(mc==null || mc.getSession()==null || mc.theWorld==null || mc.thePlayer==null
                    || mc.thePlayer.sendQueue==null
                    || mc.thePlayer.sendQueue.getNetworkManager()==null)return null;
            PacketPipeline pipe=mc.thePlayer.sendQueue.getNetworkManager().getClientPackets();
            if(pipe==null || !pipe.isActive() || pipe.getContextVersion()<0)return null;
            return new Scope(mc.getSession(),mc.theWorld,mc.thePlayer,pipe,pipe.getContextVersion());
        }
        boolean same(Scope other) {
            return other!=null && account==other.account && world==other.world
                    && player==other.player && connection==other.connection && epoch==other.epoch;
        }
        boolean live(Minecraft mc) {
            Scope active=capture(mc);
            return active!=null && same(active);
        }
    }

    /** Does not create files or grant consent by constructing the service. */
    public NeutronAiPersonalModelLoader(Path trustedGameDataDirectory,RotationManager rotation) {
        if(trustedGameDataDirectory==null || rotation==null)
            throw new IllegalArgumentException("missing trusted local runtime");
        root=trustedGameDataDirectory.toAbsolutePath().normalize();
        this.rotation=rotation;this.minecraft=Minecraft.getMinecraft();
    }

    /** Separate, visible UI action. Consent is NEVER persisted in disk or config. */
    public boolean allowSession(boolean explicitUserConsent,Scope scope) {
        if(!ownThread())return false;
        withdraw();
        if(!explicitUserConsent || scope==null || !scope.live(minecraft))return false;
        consentScope=scope;return true;
    }
    public boolean isConsented() {
        return ownThread() && consentScope!=null && consentScope.live(minecraft);
    }
    public boolean isLoaded() {
        return isConsented() && rotation.hasAiPersonalCheckpoint();
    }

    /** Must be invoked on any observed world/account/connection/epoch transition. */
    public void onScopeChange(Scope live) {
        if(ownThread() && consentScope!=null
                && (!consentScope.same(live) || !consentScope.live(minecraft)))withdraw();
    }
    /** Explicit revoke; also eliminates any C03/AI owner state from the manager. */
    public void withdraw() {
        if(!ownThread())return;
        consentScope=null;
        rotation.clearAiPersonalCheckpoint();
    }

    /**
     * Only accepts Result.CANDIDATE from our local CPU trainer. Structural
     * validation, chronological-quality metrics and consent are rechecked.
     * Saves an authenticated versioned local checkpoint but DOES NOT load it.
     */
    public boolean saveTrainedCandidate(NeutronAiTrainer.Result trained,Scope current)
            throws IOException {
        if(!authorized(current) || trained==null || !trained.isCandidate()
                || trained.frames<NeutronAiTrainer.MIN_FRAMES
                || trained.trainingFrames<NeutronAiTrainer.MIN_FRAMES*4/5
                || trained.validationFrames<80 || trained.epochs<1
                || !Float.isFinite(trained.validationLoss)
                || !Float.isFinite(trained.zeroBaselineLoss)
                || trained.zeroBaselineLoss<=0
                || trained.validationLoss>=trained.zeroBaselineLoss*.98f)return false;
        byte[] bytes=trained.candidateBytes();
        NeutronAiCheckpoint checkpoint=NeutronAiCheckpoint.decode(bytes);
        if(checkpoint==null || checkpoint.recordedFrames()!=trained.frames
                || checkpoint.epochs()!=trained.epochs
                || Float.floatToIntBits(checkpoint.validationLoss())
                    !=Float.floatToIntBits(trained.validationLoss)
                || Float.floatToIntBits(checkpoint.trainingLoss())
                    !=Float.floatToIntBits(trained.trainingLoss))return false;
        Path directory=null;
        try {
            directory=directory(true);
            byte[] secret=key(directory,true);
            if(secret==null)return false;
            byte[] proof=proof(bytes,checkpoint,trained.zeroBaselineLoss,secret);
            Path model=directory.resolve(MODEL),manifest=directory.resolve(PROOF);
            Path stagingModel=directory.resolve(STAGED_MODEL),stagingProof=directory.resolve(STAGED_PROOF);
            requireWritableFile(model);requireWritableFile(manifest);
            // An explicit new training run still must not silently replace an
            // existing personal model: cross-session losses are incomparable.
            // The user must revoke/delete the old checkpoint first.
            if(Files.exists(model,LinkOption.NOFOLLOW_LINKS)
                    || Files.exists(manifest,LinkOption.NOFOLLOW_LINKS))
                throw new IOException("personal model already saved; explicit deletion required");
            if(Files.exists(stagingModel,LinkOption.NOFOLLOW_LINKS)
                    || Files.exists(stagingProof,LinkOption.NOFOLLOW_LINKS))
                throw new IOException("unresolved personal model staging files");
            try {
                Files.write(stagingModel,bytes,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
                Files.write(stagingProof,proof,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
                // Manifest is the final commit marker; partial two-file updates fail
                // verification, rather than silently falling back to stale models.
                Files.move(stagingModel,model,StandardCopyOption.ATOMIC_MOVE);
                Files.move(stagingProof,manifest,StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(stagingModel);
                Files.deleteIfExists(stagingProof);
            }
            return true;
        } catch(IOException error) {
            withdraw();
            throw error;
        }
    }

    /** Explicit second user action. Never auto-scans or loads arbitrary files. */
    public boolean loadExplicit(boolean explicitUserAction,Scope current) throws IOException {
        if(!explicitUserAction || !authorized(current))return false;
        rotation.clearAiPersonalCheckpoint();
        try {
            Path directory=directory(false);
            if(directory==null)return false;
            byte[] secret=key(directory,false);
            byte[] bytes=readFixed(directory.resolve(MODEL),NeutronAiCheckpoint.SERIALIZED_BYTES);
            byte[] manifest=readFixed(directory.resolve(PROOF),PROOF_BYTES);
            if(secret==null || bytes==null || manifest==null)return false;
            NeutronAiCheckpoint checkpoint=NeutronAiCheckpoint.decode(bytes);
            if(checkpoint==null || !validProof(manifest,bytes,checkpoint,secret))return false;
            if(!authorized(current))return false;
            return rotation.installAiPersonalCheckpoint(checkpoint,current.account,current.world,
                    current.player,current.connection,current.epoch);
        } catch(IOException error) {
            withdraw();throw error;
        }
    }

    /** Explicit full local deletion. Never recursively touch unrelated files. */
    public boolean revokeAndDelete() {
        if(!ownThread())return false;
        withdraw();
        try {
            Path directory=directory(false);
            if(directory==null)return true;
            for(String name:new String[]{MODEL,PROOF,KEY,STAGED_MODEL,STAGED_PROOF})
                Files.deleteIfExists(directory.resolve(name));
            return true;
        } catch(IOException failed){return false;}
    }

    private boolean authorized(Scope current) {
        if(!ownThread() || consentScope==null)return false;
        if(!consentScope.same(current) || !consentScope.live(minecraft)) {
            withdraw();return false;
        }
        return true;
    }
    private boolean ownThread() {return Thread.currentThread()==owningThread;}

    private Path directory(boolean create) throws IOException {
        if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root))
            throw new IOException("untrusted Minecraft data root");
        Path first=folder(root.resolve(PARENT),create);
        return first==null?null:folder(first.resolve(FOLDER),create);
    }
    private static Path folder(Path path,boolean create) throws IOException {
        if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS)) {
            if(!create)return null;
            Files.createDirectory(path);
        }
        if(Files.isSymbolicLink(path) || !Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("untrusted personal model directory");
        return path;
    }
    private static void requireWritableFile(Path path) throws IOException {
        if(Files.exists(path,LinkOption.NOFOLLOW_LINKS)
                && !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("untrusted model destination");
    }
    private static byte[] readFixed(Path path,int exact) throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)
                || Files.size(path)!=exact)return null;
        byte[] data=Files.readAllBytes(path);
        return data.length==exact?data:null;
    }
    private static byte[] key(Path directory,boolean create) throws IOException {
        Path path=directory.resolve(KEY);
        if(create && !Files.exists(path,LinkOption.NOFOLLOW_LINKS)) {
            byte[] generated=new byte[32];
            new SecureRandom().nextBytes(generated);
            Files.write(path,generated,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            Arrays.fill(generated,(byte)0);
        }
        return readFixed(path,32);
    }

    private static byte[] proof(byte[] model,NeutronAiCheckpoint checkpoint,
                                float baseline,byte[] secret) {
        byte[] data=new byte[PROOF_BYTES];
        ByteBuffer out=ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        out.putInt(MAGIC);out.putInt(VERSION);
        out.putInt(NeutronAiCheckpoint.VERSION);out.putInt(47);
        out.putInt(checkpoint.recordedFrames());out.putInt(checkpoint.manualClicks());
        out.putInt(checkpoint.reactions());out.putInt(checkpoint.epochs());
        out.putFloat(checkpoint.trainingLoss());out.putFloat(checkpoint.validationLoss());
        out.putFloat(baseline);
        out.put(sha256(model));
        if(out.position()!=PROOF_SIGNED_BYTES)throw new IllegalStateException("provenance layout");
        out.put(mac(secret,data,PROOF_SIGNED_BYTES));
        return data;
    }
    private static boolean validProof(byte[] manifest,byte[] model,
                                      NeutronAiCheckpoint checkpoint,byte[] secret) {
        if(manifest==null || manifest.length!=PROOF_BYTES || model==null
                || secret==null || secret.length!=32)return false;
        byte[] signature=Arrays.copyOfRange(manifest,PROOF_SIGNED_BYTES,PROOF_BYTES);
        if(!MessageDigest.isEqual(signature,mac(secret,manifest,PROOF_SIGNED_BYTES)))
            return false;
        ByteBuffer in=ByteBuffer.wrap(manifest).order(ByteOrder.BIG_ENDIAN);
        if(in.getInt()!=MAGIC || in.getInt()!=VERSION
                || in.getInt()!=NeutronAiCheckpoint.VERSION || in.getInt()!=47
                || in.getInt()!=checkpoint.recordedFrames()
                || in.getInt()!=checkpoint.manualClicks()
                || in.getInt()!=checkpoint.reactions()
                || in.getInt()!=checkpoint.epochs())return false;
        float train=in.getFloat(),validation=in.getFloat(),baseline=in.getFloat();
        if(!Float.isFinite(train) || !Float.isFinite(validation)
                || !Float.isFinite(baseline) || baseline<=0
                || validation>=baseline*.98f
                || Float.floatToIntBits(train)!=Float.floatToIntBits(checkpoint.trainingLoss())
                || Float.floatToIntBits(validation)!=Float.floatToIntBits(checkpoint.validationLoss()))
            return false;
        byte[] digest=new byte[32];in.get(digest);
        return MessageDigest.isEqual(digest,sha256(model));
    }

    private static byte[] sha256(byte[] data) {
        try{return MessageDigest.getInstance("SHA-256").digest(data);}
        catch(NoSuchAlgorithmException unavailable){
            throw new IllegalStateException("Java 8 SHA-256 required",unavailable);
        }
    }
    private static byte[] mac(byte[] secret,byte[] data,int length) {
        try {
            Mac hmac=Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secret,"HmacSHA256"));
            hmac.update(data,0,length);
            return hmac.doFinal();
        } catch(java.security.GeneralSecurityException unavailable){
            throw new IllegalStateException("Java 8 HMAC required",unavailable);
        }
    }
}
