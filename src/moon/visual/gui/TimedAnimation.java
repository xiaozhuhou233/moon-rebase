package moon.visual.gui;

import java.util.function.LongSupplier;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** k9 with both captured k8 guards, original time reads and float/double order.
 * easingCase is the original k_ switch result, not an inferred enum ordinal.
 * Encrypted k_ enum mapping and mo/mp durations remain separate boundaries.
 */
public final class TimedAnimation {
    private final int easingCase;
    private final long duration;
    private final LongSupplier clock;
    private final MarkerHooks markers;
    private boolean forward,started,shortened,blocked;
    private float upper,lower,value;
    private long start,shortDuration;
    public TimedAnimation(long duration,int easingCase,LongSupplier clock,MarkerHooks markers) {
        this.duration=duration;this.clock=clock;start=clock.getAsLong();this.easingCase=easingCase;this.markers=markers;
    }
    public void resetTo(float value) { this.value=value;start=clock.getAsLong();blocked=false; }
    public void restart() {
        boolean normal=FrameAnimation.inverseSentinel();shortDuration=elapsed();
        if(normal) {
            if(shortDuration<duration) shortened=true;
            start=clock.getAsLong();
        }
        blocked=false;
        if(markers.getMarker()!=null) FrameAnimation.setSentinel(!normal);
    }
    public long elapsed() { return Math.max(Math.min(duration-(start+duration-clock.getAsLong()),duration),0L); }
    public void update(float lower,float upper,boolean forward) {
        boolean normal=FrameAnimation.inverseSentinel();
        if(normal) {
            if(!started) { start=clock.getAsLong();started=true; }
            if(isFinished()||blocked) return;
            if(forward) value=lower+(value+(upper-lower-value))*easedProgress();
            else value=upper-(value+(upper-lower-value))*easedProgress();
        } else {
            // The carried started flag selects entry99, not the forward input.
            // Entering the forward arm also falls through to the reverse arm.
            if(started) value=lower+(value+(upper-lower-value))*easedProgress();
            value=upper-(value+(upper-lower-value))*easedProgress();
        }
        this.forward=forward;this.upper=upper;this.lower=lower;
    }
    public boolean isFinished() {
        boolean normal=FrameAnimation.inverseSentinel();
        long limit=normal&&shortened?shortDuration:duration;
        int comparison=Long.compare(clock.getAsLong(),start+limit);
        if(!normal) {
            markers.setMarker("BE7DHc");
            return comparison!=0;
        }
        if(comparison>=0) shortened=false;
        return clock.getAsLong()>=start+limit;
    }
    private float progress() {
        boolean normal=FrameAnimation.inverseSentinel();
        long limit=normal&&shortened?shortDuration:duration;
        return (float)((double)(limit-(start+limit-clock.getAsLong()))/(double)limit);
    }
    private float easedProgress() {
        boolean normal=FrameAnimation.inverseSentinel();
        float p=progress();
        if(!normal) {
            // The raw switch result goes directly to ifge117. No switch dispatch
            // or second LINEAR clock read occurs on this path.
            return easingCase<0?4*p*p*p:(float)(1-Math.pow(-2*p+2,3)/2);
        }
        switch(easingCase) {
            case 1:return p<.5D?4*p*p*p:(float)(1-Math.pow(-2*p+2,3)/2);
            case 2:return p==1?1:(float)(1-Math.pow(2,-10*p));
            case 3:{double c1=1.70158D,c3=c1+1;return (float)(c3*p*p*p-c1*p*p);}
            case 4:{double c1=1.7015800476074219D,c3=c1+1;return (float)(1+c3*Math.pow(p-1,3)+c1*Math.pow(p-1,2));}
            case 5:{float c4=2.0943952F;return p==0?0:p==1?1:(float)(Math.pow(2,-10*p)*Math.sin((p*10-.75D)*c4)+1);}
            case 6:return (float)(-(Math.cos(Math.PI*p)-1)/2);
            case 7:return (float)(1-Math.pow(1-p,5));
            case 8:return 1-(1-p)*(1-p);
            case 9:return (float)Math.sqrt(1-Math.pow(p-1,2));
            case 10:{float c1=1.70158F,c2=(float)(c1*1.525D);
                return (float)(p<.5D?Math.pow(2*p,2)*((c2+1)*2*p-c2)/2:
                    (Math.pow(2*p-2,2)*((c2+1)*(p*2-2)+c2)+2)/2);}
            default:return progress(); // Original LINEAR fallback reads the clock again.
        }
    }
    public float get() {
        boolean normal=FrameAnimation.inverseSentinel();
        if(normal&&isFinished()&&!blocked) return forward?upper:lower;
        return value;
    }
    public void set(float value) { this.value=value; }
    public long getDuration() { return duration; }
    public int getEasingCase() { return easingCase; }
    public void block() { blocked=true; }
    public long getStartTime() { return start; }
    public TimedAnimation setStartTime(long time) { start=time;return this; }
}
