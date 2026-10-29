package moon.visual.render;

import java.util.*;
import moon.visual.gui.*;
import moon.visual.shader.*;

/** Color only. Every shadow uses the diffusion configured by Effects. */
public final class ShadowStyle {
    public enum Mode implements DisplayNamed {
        STATIC,GRADIENT;
        public String displayName(){return this==STATIC?"Static":"Gradient";}
    }
    public final EnumSetting<Mode> mode;
    public final ColorSetting first,second;
    public final SettingGroup group;
    private final Map<ShaderProgram,int[]> uniforms=new IdentityHashMap<>();
    private static final ThreadLocal<ShadowStyle> active=new ThreadLocal<>();
    private static final long epoch=System.nanoTime();
    public ShadowStyle(String key,String title,ShaderUniform.MarkerHooks hooks){
        mode=EnumSetting.builder(key+"-mode",Mode.class,hooks).configure("Color Mode",Mode.GRADIENT).build();
        first=ColorSetting.builder(key+"-first",hooks).configure("Color",0xff8b6cff).build();
        second=ColorSetting.builder(key+"-second",hooks).configure("Second Color",0xff51d8ef).visibility(()->mode.get()==Mode.GRADIENT).build();
        group=SettingGroup.builder(hooks).configure(title,mode,first,second).build();
    }
    public int[] colors(){return new int[]{first.get(),mode.get()==Mode.STATIC?first.get():second.get()};}
    public void render(Runnable draw){ShadowStyle previous=active.get();active.set(this);try{draw.run();}finally{if(previous==null)active.remove();else active.set(previous);}}
    /** Called after a task's own commands, only while producing a shadow mask. */
    static int apply(DrawServices services,ShaderProgram program){
        ShadowStyle style=active.get();if(style==null)return -1;
        var b=services.backend;int[] u=style.uniforms.computeIfAbsent(program,p->{
            String[] names={"gradient.required","gradient.color1","gradient.color2","gradient.alpha","gradient.factor","gradient.rainbow","gradient.time"};
            int[] locations=new int[names.length];for(int i=0;i<names.length;i++)locations[i]=b.getUniformLocation(p.getProgram(),names[i]);return locations;
        });
        if(u[0]<0)return -1;
        int[] colors=style.colors();for(int i=0;i<2;i++){int c=colors[i];b.uniform4f(u[i+1],(c>>16&255)/255F,(c>>8&255)/255F,(c&255)/255F,1);}
        b.uniform1f(u[3],1);b.uniform1i(u[4],6);b.uniform1i(u[5],0);b.uniform1f(u[6],(System.nanoTime()-epoch)/1_000_000_000F);b.uniform1i(u[0],1);return u[0];
    }
}
