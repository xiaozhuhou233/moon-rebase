package moon.module.combat.killaura;

import java.util.random.RandomGenerator;
import moon.module.combat.killaura.KillAuraSettings.ClickPattern;

/** eZ normal tick-counter scheduler and eO fractional delay accumulator.
 * Resample 5000..25000ms, 50 retries, inclusive chance 0..100 and resistance17
 * are finite original-DATA candidates, independently checked against class constants.
 * No original class, native timing, or protection bootstrap is executed.
 */
public final class KillAuraClickScheduler {
    public record Policy(int resampleMinMillis,int resampleMaxMillis,int deviationAttempts,int pauseChanceRange,int velocityHurtThreshold) {
        public static Policy destination() { return new Policy(5000,25000,50,100,17); }
    }
    private final Policy policy;
    private int ticks;
    private boolean suppressed,skipNextCycle;
    private double delay, previousDelay, sampleCps, previousCps, previousRandom=Double.MAX_VALUE;
    private double fraction, reserve;
    private long nextSample;
    public KillAuraClickScheduler(Policy policy) { this.policy=policy; }
    public void reset() { ticks=0;delay=previousDelay=sampleCps=previousCps=fraction=reserve=0;previousRandom=Double.MAX_VALUE;nextSample=0;suppressed=skipNextCycle=false; }
    /** eZ.c: suppress one eligible cycle; clear only when the delay is reached. */
    public void suppressUntilDue() { suppressed=true; }
    /** jc.ds calls eZ.a(0), then eZ.a(true); cadence and accumulator are retained. */
    public void afterTickbaseAttack() { ticks=0;skipNextCycle=true; }
    public int ticks() { return ticks; }
    public double delay() { return delay; }
    /** eZ.a normal control flow: capture eligibility before velocity sync, then drain
     * zero-delay follow-ups. Block callbacks surround the entire batch once.
     */
    public void cycle(KillAuraSettings settings,long now,RandomGenerator random,int hurtResistantTime,double heldItemAttackSpeed,Runnable before,Runnable attack,Runnable after) {
        int cps=settings.cps.get();
        if(now>=nextSample||cps!=previousCps) {
            sampleCps=between(random,Math.max(1,cps-3),Math.min(settings.doubleClicks.get()?23:20,cps+4));
            previousCps=cps;
            nextSample=now+random.nextInt(policy.resampleMinMillis(),policy.resampleMaxMillis()+1);
        }
        boolean eligible=!suppressed&&ticks>=delay;
        if(eligible)before.run();
        if(!skipNextCycle) {
            // Original 425 runs once; the back-edge 619 -> 437 is after this write.
            if(settings.velocityAttackSync.get()&&hurtResistantTime>=policy.velocityHurtThreshold())delay=0;
            while(ticks>=delay) {
                if(!eligible) { suppressed=false;break; }
                attack.run();
                previousDelay=delay;
                delay=nextDelay(settings,random,settings.doubleClicks.get()&&previousDelay>0,heldItemAttackSpeed);
                ticks=0;
            }
        }
        skipNextCycle=false;
        if(eligible)after.run();
        ++ticks;
    }
    private double nextDelay(KillAuraSettings s,RandomGenerator random,boolean doubleClick,double heldItemAttackSpeed) {
        if(s.clickPattern.get()==ClickPattern.BASIC)return Math.max(1,Math.round(20D/s.cps.get()));
        if(s.clickPattern.get()==ClickPattern.LATEST)return Math.max(1,20D/heldItemAttackSpeed);
        double low=Math.max(1,sampleCps-3),high=Math.min(doubleClick?23:20,sampleCps+4);
        double cps=between(random,low,high);
        if(s.balancedDeviation.get()) {
            double margin=Math.min(sampleCps-1,Math.min(23-sampleCps,(low+high)/2))*.6;
            int attempts=0;
            while(Math.abs(cps-previousRandom)<=margin&&++attempts<policy.deviationAttempts())cps=between(random,low,high);
            previousRandom=cps;
        }
        double value=accumulate(20/cps,between(random,.2,.8));
        if(random.nextInt(policy.pauseChanceRange()+1)<=10&&reserve>=6) {
            double taken=Math.min(reserve,between(random,1.5,3.4));
            value+=accumulate(taken,0);reserve-=taken;
        }
        return value;
    }
    /** eO.a(double,double). */
    private double accumulate(double value,double weight) {
        double whole=Math.floor(value),remainder=value-whole;
        reserve+=remainder*weight;remainder-=remainder*weight;
        fraction+=remainder*(1-weight);
        double carried=Math.floor(fraction);fraction-=carried;
        return whole+carried;
    }
    private static double between(RandomGenerator r,double min,double max) { return min+r.nextDouble()*(max-min); }
}
