package moon.visual.render;

import java.awt.Color;
import java.io.IOException;
import java.util.function.Supplier;
import moon.visual.shader.ShaderProgram;
import org.lwjgl.opengl.*;

/** Final glow tint. Cached coverage/blur radius remain unchanged as the colors flow each frame. */
public final class FlowingGlow {
    private final ShaderProgram program;
    private final Supplier<int[]> colors;
    private final DrawServices services;
    private final long epoch=System.nanoTime();
    public FlowingGlow(DrawServices services,Supplier<int[]> colors)throws IOException{
        this.services=services;this.colors=colors;
        program=services.program("flowing_glow.vert","flowing_glow.frag",false);
        if(GL20.glGetProgrami(program.getProgram(),GL20.GL_LINK_STATUS)!=1)throw new IOException("Flowing glow shader did not link");
    }
    /** Equal theme colors still produce a visible two-tone gradient. */
    public static int[] palette(int first,int second){
        int distance=Math.abs((first>>16&255)-(second>>16&255))+Math.abs((first>>8&255)-(second>>8&255))+Math.abs((first&255)-(second&255));
        if(distance<60){
            float[] hsv=Color.RGBtoHSB(first>>16&255,first>>8&255,first&255,null);
            if(hsv[1]<.12F){first=0xff8b6cff;second=0xff51d8ef;}
            else second=Color.HSBtoRGB((hsv[0]+.16F)%1,Math.max(.4F,hsv[1]),Math.max(.65F,hsv[2]));
        }
        return new int[]{first|0xff000000,second|0xff000000};
    }
    private int uniform(String name){return GL20.glGetUniformLocation(program.getProgram(),name);}
    private void color(String name,int color){GL20.glUniform3f(uniform(name),(color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F);}
    public void draw(Runnable geometry){drawAt((System.nanoTime()-epoch)/1_000_000_000F,geometry);}
    public void drawAt(float seconds,Runnable geometry){
        int old=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        try{
            program.use();int[] pair=colors.get();
            color("colorOne",pair[0]);color("colorTwo",pair[1]);
            GL20.glUniform1i(uniform("glowTexture"),0);GL20.glUniform1f(uniform("time"),seconds);
            GL20.glUniform2f(uniform("screenSize"),(float)services.scaledWidth.getAsDouble(),(float)services.scaledHeight.getAsDouble());
            geometry.run();
        }finally{services.backend.useProgram(old);}
    }
}
