package net.minecraft.client.renderer.chunk;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.CancellationException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.crash.CrashReport;
import net.minecraft.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ChunkRenderWorker implements Runnable
{
    private static final Logger LOGGER = LogManager.getLogger();
    private final ChunkRenderDispatcher chunkRenderDispatcher;
    private final RegionRenderCacheBuilder regionRenderCacheBuilder;

    public ChunkRenderWorker(ChunkRenderDispatcher p_i46201_1_)
    {
        this(p_i46201_1_, (RegionRenderCacheBuilder)null);
    }

    public ChunkRenderWorker(ChunkRenderDispatcher chunkRenderDispatcherIn, RegionRenderCacheBuilder regionRenderCacheBuilderIn)
    {
        this.chunkRenderDispatcher = chunkRenderDispatcherIn;
        this.regionRenderCacheBuilder = regionRenderCacheBuilderIn;
    }

    public void run()
    {
        while (true)
        {
            try
            {
                this.processTask(this.chunkRenderDispatcher.getNextChunkUpdate());
            }
            catch (InterruptedException var3)
            {
                LOGGER.debug("Stopping due to interrupt");
                return;
            }
            catch (Throwable throwable)
            {
                CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Batching chunks");
                Minecraft.getMinecraft().crashed(Minecraft.getMinecraft().addGraphicsAndWorldToCrashReport(crashreport));
                return;
            }
        }
    }

    protected void processTask(final ChunkCompileTaskGenerator generator) throws InterruptedException
    {
        generator.getLock().lock();
        try
        {
            if (generator.getStatus() != ChunkCompileTaskGenerator.Status.PENDING) return;
            generator.setStatus(ChunkCompileTaskGenerator.Status.COMPILING);
        }
        finally
        {
            generator.getLock().unlock();
        }

        boolean uploadOwnsBuilder = false;
        try
        {
            Entity camera = Minecraft.getMinecraft().getRenderViewEntity();
            if (camera == null) return;
            generator.setRegionRenderCacheBuilder(this.getRegionRenderCacheBuilder());
            if (generator.getTrace() != null) generator.getTrace().meshStarted();
            float x = (float)camera.posX;
            float y = (float)camera.posY + camera.getEyeHeight();
            float z = (float)camera.posZ;
            if (generator.getType() == ChunkCompileTaskGenerator.Type.REBUILD_CHUNK)
                generator.getRenderChunk().rebuildChunk(x, y, z, generator);
            else
                generator.getRenderChunk().resortTransparency(x, y, z, generator);
            if (generator.getTrace() != null) generator.getTrace().meshed();

            generator.getLock().lock();
            try
            {
                if (generator.getStatus() != ChunkCompileTaskGenerator.Status.COMPILING) return;
                generator.setStatus(ChunkCompileTaskGenerator.Status.UPLOADING);
            }
            finally
            {
                generator.getLock().unlock();
            }

            final ListenableFuture<Object> upload = this.chunkRenderDispatcher.uploadChunk(generator);
            generator.addFinishRunnable(new Runnable()
            {
                public void run() { upload.cancel(false); }
            });
            Futures.addCallback(upload, new FutureCallback<Object>()
            {
                public void onSuccess(Object ignored)
                {
                    ChunkRenderWorker.this.freeRenderBuilder(generator);
                }
                public void onFailure(Throwable error)
                {
                    if (!(error instanceof CancellationException) && generator.getTrace() != null)
                        generator.getTrace().failed(error.getClass().getSimpleName());
                    generator.finish("upload-failed-or-cancelled");
                    ChunkRenderWorker.this.freeRenderBuilder(generator);
                    if (!(error instanceof CancellationException) && !(error instanceof InterruptedException))
                        Minecraft.getMinecraft().crashed(CrashReport.makeCrashReport(error, "Rendering chunk"));
                }
            });
            uploadOwnsBuilder = true;
        }
        catch (Throwable error)
        {
            if (generator.getTrace() != null) generator.getTrace().failed(error.getClass().getSimpleName());
            throw error;
        }
        finally
        {
            if (!uploadOwnsBuilder)
            {
                generator.finish();
                this.freeRenderBuilder(generator);
            }
        }
    }

    private RegionRenderCacheBuilder getRegionRenderCacheBuilder() throws InterruptedException
    {
        return this.regionRenderCacheBuilder != null ? this.regionRenderCacheBuilder : this.chunkRenderDispatcher.allocateRenderBuilder();
    }

    private void freeRenderBuilder(ChunkCompileTaskGenerator taskGenerator)
    {
        RegionRenderCacheBuilder builder = taskGenerator.takeRegionRenderCacheBuilder();
        if (builder == null) return;
        if (this.regionRenderCacheBuilder == null)
            this.chunkRenderDispatcher.freeRenderBuilder(builder);
        else
            ChunkRenderDispatcher.resetRenderBuilder(builder);
    }
}
