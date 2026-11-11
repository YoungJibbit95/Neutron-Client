package net.minecraft.client.renderer.chunk;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.VertexBufferUploader;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.src.Config;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.util.MathHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

public class ChunkRenderDispatcher
{
    private static final Logger logger = LogManager.getLogger();
    private static final ThreadFactory threadFactory = (new ThreadFactoryBuilder()).setNameFormat("Chunk Batcher %d").setDaemon(true).build();
    private final List<ChunkRenderWorker> listThreadedWorkers;
    private final BlockingQueue<ChunkCompileTaskGenerator> queueChunkUpdates;
    private final BlockingQueue<RegionRenderCacheBuilder> queueFreeRenderBuilders;
    private final WorldVertexBufferUploader worldVertexUploader;
    private final VertexBufferUploader vertexUploader;
    private final Queue<PendingUpload> queueChunkUploads;
    private final ChunkRenderWorker renderWorker;
    private final int countRenderBuilders;
    private List<RegionRenderCacheBuilder> listPausedBuilders;

    public ChunkRenderDispatcher()
    {
        this(-1);
    }

    public ChunkRenderDispatcher(int p_i4_1_)
    {
        this.listThreadedWorkers = Lists.<ChunkRenderWorker>newArrayList();
        this.queueChunkUpdates = new ChunkCompileQueue(100);
        this.worldVertexUploader = new WorldVertexBufferUploader();
        this.vertexUploader = new VertexBufferUploader();
        this.queueChunkUploads = Queues.<PendingUpload>newArrayDeque();
        this.listPausedBuilders = new ArrayList();
        int i = Math.max(1, (int)((double)Runtime.getRuntime().maxMemory() * 0.3D) / 10485760);
        int j = Math.max(1, MathHelper.clamp_int(Runtime.getRuntime().availableProcessors() - 2, 1, i / 5));

        if (p_i4_1_ < 0)
        {
            this.countRenderBuilders = MathHelper.clamp_int(j * 8, 1, i);
        }
        else
        {
            this.countRenderBuilders = p_i4_1_;
        }

        for (int k = 0; k < j; ++k)
        {
            ChunkRenderWorker chunkrenderworker = new ChunkRenderWorker(this);
            Thread thread = threadFactory.newThread(chunkrenderworker);
            thread.start();
            this.listThreadedWorkers.add(chunkrenderworker);
        }

        this.queueFreeRenderBuilders = Queues.<RegionRenderCacheBuilder>newArrayBlockingQueue(this.countRenderBuilders);

        for (int l = 0; l < this.countRenderBuilders; ++l)
        {
            this.queueFreeRenderBuilders.add(new RegionRenderCacheBuilder());
        }

        this.renderWorker = new ChunkRenderWorker(this, new RegionRenderCacheBuilder());
    }

    public String getDebugInfo()
    {
        return String.format("pC: %03d, pU: %1d, aB: %1d", new Object[] {Integer.valueOf(this.queueChunkUpdates.size()), Integer.valueOf(this.queueChunkUploads.size()), Integer.valueOf(this.queueFreeRenderBuilders.size())});
    }

    public boolean runChunkUploads(long finishTimeNano)
    {
        return this.runChunkUploads(finishTimeNano, Long.MAX_VALUE);
    }

    /** Soft limits between complete revisions. One group may overrun for progress. */
    public boolean runChunkUploads(long finishTimeNano, long maxUploadBytes)
    {
        boolean uploaded = false;
        long bytes = 0L;
        while (true)
        {
            PendingUpload upload;
            synchronized (this.queueChunkUploads)
            {
                upload = this.queueChunkUploads.poll();
            }
            if (upload == null) break;
            if (upload.task.isCancelled()) continue;
            upload.task.run();
            uploaded = true;
            bytes += upload.bytes;
            if (finishTimeNano == 0L || bytes >= maxUploadBytes
                    || (finishTimeNano != Long.MAX_VALUE
                        && System.nanoTime() - finishTimeNano >= 0L)) break;
        }
        return uploaded;
    }

    public boolean updateChunkLater(RenderChunk chunkRenderer)
    {
        return this.updateChunkLater(chunkRenderer, false);
    }

    public boolean updateChunkLater(RenderChunk chunkRenderer, boolean urgent)
    {
        chunkRenderer.getLockCompileTask().lock();
        boolean flag;

        try
        {
            // Dirty invalidations remain pending while the current build progresses.
            if (chunkRenderer.hasPendingCompileTask()) return false;
            final ChunkCompileTaskGenerator chunkcompiletaskgenerator = chunkRenderer.makeCompileTaskChunk();
            chunkcompiletaskgenerator.setUrgent(urgent);
            chunkcompiletaskgenerator.beginTrace(this.queueChunkUpdates.size());
            chunkcompiletaskgenerator.addFinishRunnable(new Runnable()
            {
                public void run()
                {
                    ChunkRenderDispatcher.this.queueChunkUpdates.remove(chunkcompiletaskgenerator);
                }
            });
            boolean flag1 = this.queueChunkUpdates.offer(chunkcompiletaskgenerator);

            if (!flag1)
            {
                chunkcompiletaskgenerator.finish("compile-queue-full");
            }

            flag = flag1;
        }
        finally
        {
            chunkRenderer.getLockCompileTask().unlock();
        }

        return flag;
    }

    public boolean updateChunkNow(RenderChunk chunkRenderer)
    {
        chunkRenderer.getLockCompileTask().lock();
        boolean flag;

        try
        {
            ChunkCompileTaskGenerator chunkcompiletaskgenerator = chunkRenderer.makeCompileTaskChunk();
            chunkcompiletaskgenerator.beginTrace(this.queueChunkUpdates.size());

            try
            {
                this.renderWorker.processTask(chunkcompiletaskgenerator);
            }
            catch (InterruptedException var8)
            {
                ;
            }

            flag = true;
        }
        finally
        {
            chunkRenderer.getLockCompileTask().unlock();
        }

        return flag;
    }

    public void stopChunkUpdates()
    {
        this.clearChunkUpdates();

        while (this.runChunkUploads(0L))
        {
            ;
        }

        List<RegionRenderCacheBuilder> list = Lists.<RegionRenderCacheBuilder>newArrayList();

        while (((List)list).size() != this.countRenderBuilders)
        {
            try
            {
                list.add(this.allocateRenderBuilder());
            }
            catch (InterruptedException var3)
            {
                ;
            }
        }

        this.queueFreeRenderBuilders.addAll(list);
    }

    public void freeRenderBuilder(RegionRenderCacheBuilder builder)
    {
        resetRenderBuilder(builder);
        this.queueFreeRenderBuilders.add(builder);
    }

    static void resetRenderBuilder(RegionRenderCacheBuilder builder)
    {
        for (EnumWorldBlockLayer layer : RenderChunk.ENUM_WORLD_BLOCK_LAYERS)
        {
            WorldRenderer renderer = builder.getWorldRendererByLayer(layer);
            if (renderer.isDrawing()) renderer.finishDrawing();
            renderer.reset();
            renderer.setTranslation(0.0D, 0.0D, 0.0D);
        }
    }

    public RegionRenderCacheBuilder allocateRenderBuilder() throws InterruptedException
    {
        return (RegionRenderCacheBuilder)this.queueFreeRenderBuilders.take();
    }

    public ChunkCompileTaskGenerator getNextChunkUpdate() throws InterruptedException
    {
        return (ChunkCompileTaskGenerator)this.queueChunkUpdates.take();
    }

    public boolean updateTransparencyLater(RenderChunk chunkRenderer)
    {
        chunkRenderer.getLockCompileTask().lock();
        boolean flag1;

        try
        {
            final ChunkCompileTaskGenerator chunkcompiletaskgenerator = chunkRenderer.makeCompileTaskTransparency();

            if (chunkcompiletaskgenerator != null)
            {
                chunkcompiletaskgenerator.beginTrace(this.queueChunkUpdates.size());
                chunkcompiletaskgenerator.addFinishRunnable(new Runnable()
                {
                    public void run()
                    {
                        ChunkRenderDispatcher.this.queueChunkUpdates.remove(chunkcompiletaskgenerator);
                    }
                });
                boolean flag2 = this.queueChunkUpdates.offer(chunkcompiletaskgenerator);
                if (!flag2) chunkcompiletaskgenerator.finish("compile-queue-full");
                return flag2;
            }

            // Keep the later sort request when another task owns this renderer.
            flag1 = false;
        }
        finally
        {
            chunkRenderer.getLockCompileTask().unlock();
        }

        return flag1;
    }

    public ListenableFuture<Object> uploadChunk(final EnumWorldBlockLayer player, final WorldRenderer p_178503_2_, final RenderChunk chunkRenderer, final CompiledChunk compiledChunkIn)
    {
        if (Minecraft.getMinecraft().isCallingFromMinecraftThread())
        {
            if (OpenGlHelper.useVbo())
            {
                this.uploadVertexBuffer(p_178503_2_, chunkRenderer.getVertexBufferByLayer(player.ordinal()));
            }
            else
            {
                this.uploadDisplayList(p_178503_2_, ((ListedRenderChunk)chunkRenderer).getDisplayList(player, compiledChunkIn), chunkRenderer);
            }

            p_178503_2_.setTranslation(0.0D, 0.0D, 0.0D);
            return Futures.<Object>immediateFuture((Object)null);
        }
        else
        {
            ListenableFutureTask<Object> listenablefuturetask = ListenableFutureTask.<Object>create(new Runnable()
            {
                public void run()
                {
                    ChunkRenderDispatcher.this.uploadChunk(player, p_178503_2_, chunkRenderer, compiledChunkIn);
                }
            }, (Object)null);

            synchronized (this.queueChunkUploads)
            {
                this.queueChunkUploads.add(new PendingUpload(listenablefuturetask, getUploadSize(p_178503_2_)));
                return listenablefuturetask;
            }
        }
    }

    /** A complete revision, including empty meshes, is consumed on the GL thread. */
    public ListenableFuture<Object> uploadChunk(final ChunkCompileTaskGenerator generator)
    {
        final CompiledChunk compiled = generator.getCompiledChunk();
        final RegionRenderCacheBuilder builder = generator.getRegionRenderCacheBuilder();
        long bytes = 0L;
        for (EnumWorldBlockLayer layer : RenderChunk.ENUM_WORLD_BLOCK_LAYERS)
        {
            if (shouldUploadLayer(generator, compiled, layer))
                bytes += getUploadSize(builder.getWorldRendererByLayer(layer));
        }
        final long uploadBytes = bytes;
        ListenableFutureTask<Object> task = ListenableFutureTask.create(new Runnable()
        {
            public void run()
            {
                if (!Minecraft.getMinecraft().isCallingFromMinecraftThread())
                    throw new IllegalStateException("Chunk upload requires the client thread");
                RenderChunk chunk = generator.getRenderChunk();
                chunk.getLockCompileTask().lock();
                generator.getLock().lock();
                try
                {
                    if (!chunk.isCompileTaskCurrent(generator)
                            || generator.getStatus() != ChunkCompileTaskGenerator.Status.UPLOADING)
                    {
                        if (generator.getTrace() != null) generator.getTrace().cancelled("stale-upload");
                        generator.setStatus(ChunkCompileTaskGenerator.Status.DONE);
                        return;
                    }
                    if (generator.getTrace() != null) generator.getTrace().uploadStarted();
                    // No frame can draw a mixture of new and old layer buffers.
                    for (EnumWorldBlockLayer layer : RenderChunk.ENUM_WORLD_BLOCK_LAYERS)
                    {
                        if (shouldUploadLayer(generator, compiled, layer))
                            ChunkRenderDispatcher.this.uploadChunk(layer,
                                    builder.getWorldRendererByLayer(layer), chunk, compiled);
                    }
                    if (chunk.publishCompiledChunk(generator) && generator.getTrace() != null)
                        generator.getTrace().uploaded(uploadBytes);
                }
                finally
                {
                    generator.getLock().unlock();
                    chunk.getLockCompileTask().unlock();
                }
            }
        }, null);
        if (Minecraft.getMinecraft().isCallingFromMinecraftThread()) task.run();
        else
        {
            synchronized (this.queueChunkUploads)
            {
                this.queueChunkUploads.add(new PendingUpload(task, bytes));
            }
        }
        return task;
    }

    private static boolean shouldUploadLayer(ChunkCompileTaskGenerator generator,
            CompiledChunk compiled, EnumWorldBlockLayer layer)
    {
        return generator.getType() == ChunkCompileTaskGenerator.Type.REBUILD_CHUNK
                ? compiled.isLayerStarted(layer)
                : layer == EnumWorldBlockLayer.TRANSLUCENT && compiled.getState() != null
                        && !compiled.isLayerEmpty(layer);
    }

    private static long getUploadSize(WorldRenderer renderer)
    {
        return getUploadSize(renderer, renderer.getDrawMode() == 7 && Config.isQuadsToTriangles());
    }

    /** Account for the uploader's existing quad-to-triangle expansion without doing it twice. */
    static long getUploadSize(WorldRenderer renderer, boolean triangulate)
    {
        long bytes = renderer.getByteBuffer().remaining();
        return triangulate && renderer.getDrawMode() == 7 ? bytes / 4L * 6L : bytes;
    }

    private static final class PendingUpload
    {
        final ListenableFutureTask<Object> task;
        final long bytes;
        PendingUpload(ListenableFutureTask<Object> task, long bytes)
        {
            this.task = task;
            this.bytes = bytes;
        }
    }

    public int getPendingCompileCount() { return this.queueChunkUpdates.size(); }
    public int getPendingUploadCount()
    {
        synchronized (this.queueChunkUploads) { return this.queueChunkUploads.size(); }
    }
    public int getBuilderCount() { return this.countRenderBuilders; }
    public int getAvailableBuilderCount() { return this.queueFreeRenderBuilders.size(); }
    public int getWorkerCount() { return this.listThreadedWorkers.size(); }

    private void uploadDisplayList(WorldRenderer p_178510_1_, int p_178510_2_, RenderChunk chunkRenderer)
    {
        GL11.glNewList(p_178510_2_, GL11.GL_COMPILE);
        GlStateManager.pushMatrix();
        chunkRenderer.multModelviewMatrix();
        this.worldVertexUploader.func_181679_a(p_178510_1_);
        GlStateManager.popMatrix();
        GL11.glEndList();
    }

    private void uploadVertexBuffer(WorldRenderer p_178506_1_, VertexBuffer vertexBufferIn)
    {
        this.vertexUploader.setVertexBuffer(vertexBufferIn);
        this.vertexUploader.func_181679_a(p_178506_1_);
    }

    public void clearChunkUpdates()
    {
        while (!this.queueChunkUpdates.isEmpty())
        {
            ChunkCompileTaskGenerator chunkcompiletaskgenerator = (ChunkCompileTaskGenerator)this.queueChunkUpdates.poll();

            if (chunkcompiletaskgenerator != null)
            {
                chunkcompiletaskgenerator.finish();
            }
        }
    }

    public boolean hasChunkUpdates()
    {
        return this.queueChunkUpdates.isEmpty() && this.queueChunkUploads.isEmpty();
    }

    public void pauseChunkUpdates()
    {
        while (this.listPausedBuilders.size() != this.countRenderBuilders)
        {
            try
            {
                this.runChunkUploads(Long.MAX_VALUE);
                RegionRenderCacheBuilder regionrendercachebuilder = (RegionRenderCacheBuilder)this.queueFreeRenderBuilders.poll(100L, TimeUnit.MILLISECONDS);

                if (regionrendercachebuilder != null)
                {
                    this.listPausedBuilders.add(regionrendercachebuilder);
                }
            }
            catch (InterruptedException var2)
            {
                ;
            }
        }
    }

    public void resumeChunkUpdates()
    {
        this.queueFreeRenderBuilders.addAll(this.listPausedBuilders);
        this.listPausedBuilders.clear();
    }
}
