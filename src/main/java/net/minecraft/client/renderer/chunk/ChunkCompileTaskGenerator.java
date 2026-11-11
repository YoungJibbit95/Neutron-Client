package net.minecraft.client.renderer.chunk;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import me.youngjibbit.core.diagnostics.ChunkPipelineMetrics;

public class ChunkCompileTaskGenerator
{
    private final RenderChunk renderChunk;
    private final ReentrantLock lock = new ReentrantLock();
    private final List<Runnable> listFinishRunnables = Lists.<Runnable>newArrayList();
    private final ChunkCompileTaskGenerator.Type type;
    private RegionRenderCacheBuilder regionRenderCacheBuilder;
    private CompiledChunk compiledChunk;
    private volatile ChunkCompileTaskGenerator.Status status = ChunkCompileTaskGenerator.Status.PENDING;
    private volatile boolean finished;
    private Set<TileEntity> globalTileEntities = Collections.emptySet();
    private boolean urgent;
    private ChunkPipelineMetrics.Trace trace;

    public ChunkCompileTaskGenerator(RenderChunk renderChunkIn, ChunkCompileTaskGenerator.Type typeIn)
    {
        this.renderChunk = renderChunkIn;
        this.type = typeIn;
    }

    public ChunkCompileTaskGenerator.Status getStatus()
    {
        return this.status;
    }

    public RenderChunk getRenderChunk()
    {
        return this.renderChunk;
    }

    public CompiledChunk getCompiledChunk()
    {
        return this.compiledChunk;
    }

    public void setCompiledChunk(CompiledChunk compiledChunkIn)
    {
        this.compiledChunk = compiledChunkIn;
    }

    public RegionRenderCacheBuilder getRegionRenderCacheBuilder()
    {
        return this.regionRenderCacheBuilder;
    }

    public void setRegionRenderCacheBuilder(RegionRenderCacheBuilder regionRenderCacheBuilderIn)
    {
        this.regionRenderCacheBuilder = regionRenderCacheBuilderIn;
    }

    /** Transfers the builder lease once, including cancellation/error races. */
    public RegionRenderCacheBuilder takeRegionRenderCacheBuilder()
    {
        this.lock.lock();
        try
        {
            RegionRenderCacheBuilder builder = this.regionRenderCacheBuilder;
            this.regionRenderCacheBuilder = null;
            return builder;
        }
        finally
        {
            this.lock.unlock();
        }
    }

    public Set<TileEntity> getGlobalTileEntities()
    {
        return this.globalTileEntities;
    }

    public void setGlobalTileEntities(Set<TileEntity> tileEntities)
    {
        this.globalTileEntities = tileEntities;
    }

    public boolean isUrgent() { return this.urgent; }
    public void setUrgent(boolean urgent) { this.urgent = urgent; }
    public ChunkPipelineMetrics.Trace getTrace() { return this.trace; }

    public void beginTrace(int queueDepth)
    {
        BlockPos position = this.renderChunk.getPosition();
        if (position != null)
            this.trace = ChunkPipelineMetrics.INSTANCE.beginTask(position.getX() >> 4,
                    position.getY() >> 4, position.getZ() >> 4, this.type.name(), queueDepth);
    }

    public void setStatus(ChunkCompileTaskGenerator.Status statusIn)
    {
        this.lock.lock();

        try
        {
            this.status = statusIn;
        }
        finally
        {
            this.lock.unlock();
        }
    }

    public void finish()
    {
        this.finish("cancelled-or-unloaded");
    }

    public void finish(String reason)
    {
        this.lock.lock();

        try
        {
            if (this.finished)
            {
                return;
            }
            if (this.type == ChunkCompileTaskGenerator.Type.REBUILD_CHUNK && this.status != ChunkCompileTaskGenerator.Status.DONE)
            {
                this.renderChunk.setNeedsUpdate(true);
            }

            this.finished = true;
            if (this.trace != null) this.trace.cancelled(reason);
            this.status = ChunkCompileTaskGenerator.Status.DONE;

            for (Runnable runnable : this.listFinishRunnables)
            {
                runnable.run();
            }
        }
        finally
        {
            this.lock.unlock();
        }
    }

    public void addFinishRunnable(Runnable p_178539_1_)
    {
        this.lock.lock();

        try
        {
            this.listFinishRunnables.add(p_178539_1_);

            if (this.finished)
            {
                p_178539_1_.run();
            }
        }
        finally
        {
            this.lock.unlock();
        }
    }

    public ReentrantLock getLock()
    {
        return this.lock;
    }

    public ChunkCompileTaskGenerator.Type getType()
    {
        return this.type;
    }

    public boolean isFinished()
    {
        return this.finished;
    }

    public static enum Status
    {
        PENDING,
        COMPILING,
        UPLOADING,
        DONE;
    }

    public static enum Type
    {
        REBUILD_CHUNK,
        RESORT_TRANSPARENCY;
    }
}
