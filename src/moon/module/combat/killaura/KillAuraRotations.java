package moon.module.combat.killaura;

import java.util.random.RandomGenerator;
import moon.module.combat.killaura.KillAuraSettings.Randomization;
import moon.module.combat.killaura.KillAuraTargetSelector.*;

/** e1 normal angle math; old/current snapshots and 0.15-degree truncation retained.
 * 360-entry circular table is a destination policy, not a decrypted e1 integer.
 */
public final class KillAuraRotations {
    public record Angles(float yaw, float pitch) { }
    private Angles previous, current;
    private int circularIndex;
    private boolean returning;
    private final float[][] circle = new float[360][2];
    public void reset(Player p, RandomGenerator random) {
        previous=current=p==null?null:new Angles(p.yaw(),p.pitch()); returning=false; circularIndex=0;
        for(int i=0;i<circle.length;i++) {
            circle[i][0]=(float)Math.sin(Math.toRadians(i))*between(random,1.25F,6F);
            circle[i][1]=(float)Math.cos(Math.toRadians(i))*between(random,1.25F,6F);
        }
    }
    public Angles current() { return current; }
    public void update(Player p, Target target, KillAuraSettings s, RandomGenerator random, boolean rayHitsTarget) {
        if(current==null)reset(p,random);
        if(target==null) {
            if(s.preventBackSnap.get()&&returning) {
                Angles next=new Angles(approach(previous.yaw(),p.yaw(),s.rotationSpeed.get()),approach(previous.pitch(),p.pitch(),s.rotationSpeed.get()));
                // The original's second comparison is player.pitch - player.pitch.
                if(next.yaw()==p.yaw())returning=false;
                previous=current;current=next;
            } else previous=current=new Angles(p.yaw(),p.pitch());
            return;
        }
        if(s.onlyRotateWhenNeeded.get()&&rayHitsTarget) { previous=current;return; }
        double eyeY=p.y()+p.eyeHeight();
        if(s.dontRotateInHitbox.get()&&inside(p.x(),eyeY,p.z(),target)) { previous=current;return; }
        double x=clamp(p.x(),target.x()-.4000000059604645,target.x()+.4000000059604645);
        double y=clamp(eyeY,target.y(),target.y()+target.height());
        double z=clamp(p.z(),target.z()-.4000000059604645,target.z()+.4000000059604645);
        if(s.randomization.isSelected(Randomization.ATTACK_POINT)) {
            x+=between(random,-.1F,.1F);y+=between(random,-.25F,.25F);z+=between(random,-.1F,.1F);
        }
        double dx=x-p.x(),dy=y-eyeY,dz=z-p.z();
        float yaw=(float)Math.toDegrees(Math.atan2(dz,dx))-90F;
        float pitch=-(float)Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz)));
        yaw=approach(previous.yaw(),yaw,s.rotationSpeed.get());pitch=approach(previous.pitch(),pitch,s.rotationSpeed.get());
        if(s.randomization.isSelected(Randomization.NORMAL)) {
            float amount=s.randomizationAmount.get();yaw+=between(random,-amount,amount);pitch+=between(random,-amount,amount);
        }
        if(s.randomization.isSelected(Randomization.CIRCULAR)) {
            circularIndex=(int)(circularIndex+36F*s.circularSpeed.get());
            int index=Math.floorMod(circularIndex,circle.length);
            if(circularIndex>360)circularIndex=0;
            yaw+=circle[index][0];pitch+=circle[index][1];
        }
        yaw=currentFromDelta(previous.yaw(),yaw);pitch=currentFromDelta(previous.pitch(),pitch);
        previous=current;current=new Angles(yaw,(float)clamp(pitch,-90,90));returning=true;
    }
    static float currentFromDelta(float previous,float requested) {
        return (float)(previous+(int)(wrap(requested-previous)/.15)*.15);
    }
    public static float approach(float from,float to,float speed) { return from+Math.max(-speed,Math.min(speed,wrap(to-from))); }
    public static float wrap(float angle) { angle%=360;if(angle>=180)angle-=360;if(angle< -180)angle+=360;return angle; }
    static float between(RandomGenerator r,float min,float max) { return min+r.nextFloat()*(max-min); }
    static double clamp(double value,double min,double max) { return Math.max(min,Math.min(max,value)); }
    private static boolean inside(double x,double y,double z,Target t) {
        return x>t.x()-.4&&x<t.x()+.4&&z>t.z()-.4&&z<t.z()+.4&&y>t.y()&&y<t.y()+t.height();
    }
}
