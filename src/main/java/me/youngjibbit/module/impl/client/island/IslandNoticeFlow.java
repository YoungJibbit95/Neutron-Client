package me.youngjibbit.module.impl.client.island;

/** Consumer cursor only: no queue and no replay when the Island or an individual notice is hidden. */
public final class IslandNoticeFlow {
    private long startedAt,lastModuleSequence,lastWarningRun=Long.MIN_VALUE,lastWarningSequence;
    public void reset(long now,long moduleSequence){
        startedAt=now;lastModuleSequence=moduleSequence;lastWarningRun=Long.MIN_VALUE;lastWarningSequence=0;
    }
    public void update(IslandStateMachine model,IslandNotice module,IslandNotice warning,
                       boolean showModule,boolean showWarning,long epoch,long now){
        model.retainNotices(module,warning,showModule,showWarning,now);
        // A warning wins the same-frame collision. The lower-priority change is consumed, never queued.
        if(warning!=null&&(warning.sourceRun!=lastWarningRun||warning.sequence!=lastWarningSequence)){
            lastWarningRun=warning.sourceRun;lastWarningSequence=warning.sequence;
            if(showWarning&&warning.observedAtNanos-startedAt>=0)model.offerNotice(warning,epoch,now);
        }
        if(module!=null&&module.sequence>lastModuleSequence){
            lastModuleSequence=module.sequence;
            if(showModule&&module.observedAtNanos-startedAt>=0)model.offerNotice(module,epoch,now);
        }
    }
}
