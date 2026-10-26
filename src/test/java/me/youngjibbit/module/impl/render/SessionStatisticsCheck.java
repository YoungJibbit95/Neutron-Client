package me.youngjibbit.module.impl.render;

import java.util.concurrent.atomic.AtomicLong;

/** Controlled monotonic clock + synthetic lifecycle/chat evidence; no game or account data. */
public final class SessionStatisticsCheck {
    public static void main(String[] args) {
        AtomicLong clock=new AtomicLong();
        SessionStatistics stats=new SessionStatistics(clock::get);
        Object first=new Object(),second=new Object();
        require(stats.snapshot().milliseconds()==0,"No session before joining");
        stats.observe(first,false);
        clock.set(2_000_000_000L);
        require(!stats.snapshot().active,"Connecting is not a play session");
        stats.observe(first,true);
        long generation=stats.generation();
        clock.addAndGet(3_723_000_000_000L);
        SessionStatistics.Snapshot before=stats.snapshot();
        require(before.time().equals("1h 2m 3s") && before.clock().equals("1:02:03"),"Hour formatting");
        for(int i=0;i<10000;i++) stats.snapshot();
        require(stats.snapshot().elapsedNanos==before.elapsedNanos,"Read/render must not mutate time");
        stats.observe(first,false); stats.observe(first,true); // null player/world during respawn, same connection
        require(stats.generation()==generation && stats.snapshot().elapsedNanos==before.elapsedNanos,"Dimension/respawn must retain session");
        String[] negatives={"Tester wants to fight!","Won a reward by Tester","Someone walks by Tester",
                "Tester was killed by", "Other was killed by TesterElse", "<Other> Victim was killed by Tester",
                "[Party] Victim was killed by Tester", "Victim was killed by Tester and Others", "Tester: Victim was slain by Tester"};
        for(String text:negatives) stats.chat(text,"Tester",generation);
        require(stats.snapshot().kills==0 && stats.snapshot().deaths==0,"False-positive chat examples");
        stats.chat("Victim was slain by Tester","Tester",generation);
        stats.chat("Victim was shot by Tester using [Bow]","Tester",generation);
        stats.chat("§cTester was killed by Enemy","Tester",generation);
        stats.chat("Tester was killed by Tester","Tester",generation);
        require(stats.snapshot().kills==2 && stats.snapshot().deaths==2,"Supported kill/death evidence");
        stats.observe(null,false);
        long finished=stats.snapshot().elapsedNanos;
        clock.addAndGet(100_000_000_000L);
        stats.chat("Victim was killed by Tester","Tester",generation);
        require(stats.snapshot().elapsedNanos==finished && stats.snapshot().kills==2,"End freezes time and rejects chat");
        stats.observe(second,true);
        require(stats.snapshot().milliseconds()==0 && stats.snapshot().kills==0 && stats.snapshot().deaths==0,"New session resets statistics");
        stats.chat("Victim was killed by Tester","Tester",generation);
        require(stats.snapshot().kills==0,"Late previous-connection chat rejected");
        clock.addAndGet(100L*3600*1_000_000_000L);
        require(stats.snapshot().time().startsWith("100h "),"Hours do not wrap at 24");
        System.out.println("PASS: controlled monotonic session clock; read-only snapshots; lifecycle/respawn/dimensions; hours; narrow chat evidence; stale-generation rejection");
    }
    static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
