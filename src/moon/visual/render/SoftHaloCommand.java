package moon.visual.render;

/** One continuous Gaussian falloff around a rounded source; shares the rounded dispatcher. */
public final class SoftHaloCommand extends DrawCommand {
    private final DrawServices services;
    private static final long epoch=System.nanoTime();
    private int linked=-1;
    private final int[] locations=new int[12];
    private float x,y,width,height,radius,sigma,strength;
    private int first,second;
    public SoftHaloCommand(DrawServices services){this.services=services;}
    public SoftHaloCommand configure(float x,float y,float width,float height,float radius,float sigma,float strength,int first,int second){
        this.x=x;this.y=y;this.width=width;this.height=height;this.radius=radius;this.sigma=sigma;this.strength=strength;this.first=first;this.second=second;return this;
    }
    public void before(float sw,float sh){
        if(linked!=program.getProgram()){
            linked=program.getProgram();String[] names={"halo.required","halo.bounds","halo.shape","gradient.required","gradient.color1","gradient.color2","gradient.alpha","gradient.time","stencil.required","halo.screenSize","gradient.factor","gradient.rainbow"};
            for(int i=0;i<names.length;i++)locations[i]=services.backend.getUniformLocation(linked,names[i]);
        }
        var b=services.backend;b.uniform1i(locations[0],1);b.uniform4f(locations[1],x,y,width,height);b.uniform4f(locations[2],radius,sigma,strength*RenderTransformState.getAlpha(),0);
        b.uniform1i(locations[3],1);color(4,first);color(5,second);b.uniform1f(locations[6],1);b.uniform1f(locations[7],(System.nanoTime()-epoch)/1_000_000_000F);b.uniform1i(locations[8],0);
        b.uniform2f(locations[9],sw,sh);b.uniform1i(locations[10],6);b.uniform1i(locations[11],0);
    }
    private void color(int index,int color){services.backend.uniform4f(locations[index],(color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,1);}
    public void after(){program.use();services.backend.uniform1i(locations[0],0);services.backend.uniform1i(locations[3],0);services.backend.useProgram(0);}
}
