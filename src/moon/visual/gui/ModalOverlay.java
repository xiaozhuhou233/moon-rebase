package moon.visual.gui;

import java.util.function.LongSupplier;
import moon.visual.render.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** kr. Layout before content update, raw mouse coordinates, original transform/reset order.
 * Deliberately no finally: an exception or nonnull ku first-button branch leaves transforms/tasks.
 * Unverified raw kr constants and the incomplete k9 dependency keep kr PARTIAL.
 */
public final class ModalOverlay {
    public interface Scheme extends ModalContent.Scheme {
        int panel();int onPanel();
        int success();int onSuccess();int error();int onError();int onAccent();
    }
    public interface Drawing {
        DrawTask roundedTask();
        DrawTask msdfTask(MsdfFontBase font);
        DrawTaskState.Geometry rounded(float x,float y,float width,float height,float radius,int color);
        default DrawTask outlineTask(){throw new UnsupportedOperationException("Modern modal outline");}
        default DrawTaskState.Geometry outline(float x,float y,float width,float height,float radius,float thickness,int color){throw new UnsupportedOperationException("Modern modal outline");}
    }
    public interface Host {
        MsdfFontBase robotoMedium();
        int scaledWidth();int scaledHeight();
        MarkerHooks markers();
        Drawing modalDrawing();
    }
    public static final class Parameters {
        public final int backdropColor;
        public final long durationMillis;
        public Parameters(int backdropColor,long durationMillis) { this.backdropColor=backdropColor;this.durationMillis=durationMillis; }
    }
    private TimedAnimation animation;
    private final LongSupplier clock;
    private boolean modern;
    private final ModalConfiguration configuration;
    private Scheme scheme;
    private float x,y,width,height;
    private final Host host;
    private final Parameters parameters;
    public ModalOverlay(Host host,ModalConfiguration configuration,Parameters parameters,LongSupplier clock) {
        this.host=host;this.parameters=parameters;this.clock=clock;
        animation=new TimedAnimation(parameters.durationMillis,0,clock,host.markers());this.configuration=configuration;
    }
    /** Nightly nk uses a 100 ms linear reveal; keep the user's downward motion. */
    public void setModern(boolean value){
        if(modern==value)return;modern=value;
        animation=new TimedAnimation(modern?100L:parameters.durationMillis,0,clock,host.markers());
    }
    public boolean isModern(){return modern;}
    public void initialize() {
        scheme=configuration.getScreen().modalScheme();configuration.getContent().initialize();
        configuration.getButtons().forEach(ModalButton::initialize);
    }
    private void layout() {
        MsdfFontBase font=host.robotoMedium();
        float padding=modern?20F:16F;
        float w=Math.max(configuration.getContent().getWidth()+padding,200F);
        float h=configuration.getContent().getHeight()+36F+padding+font.height(7F)+font.height(12F);
        width=w;height=h;x=configuration.getX().get()-w/2F;y=configuration.getY().get()-h/2F;
    }
    public void update(int mouseX,int mouseY) {
        int[] captured=ModalContent.getSentinel();animation.update(0F,1F,true);layout();
        float progress=Math.max(0,Math.min(1,animation.get()));
        // Keep X and size fixed; descend a few GUI pixels into the final layout.
        y-=Math.min(12F,Math.max(0,y))*(1F-progress);
        float previousAlpha=RenderTransformState.getAlpha();
        RenderTransformState.setAlpha(previousAlpha*progress);
        try{
        MsdfFontBase font=host.robotoMedium();ModalContent content=configuration.getContent();Drawing drawing=host.modalDrawing();
        DrawTask background=drawing.roundedTask();
        background.add(drawing.rounded(-20000F,-20000F,40000F,40000F,0F,modern?0x80000000:parameters.backdropColor));
        background.add(drawing.rounded(x,y,width,height,modern?16F:12F,modern?0xff111111:scheme.panel()));
        String title=configuration.getTitle(),description=configuration.getDescription();
        float inset=modern?10F:8F;
        float titleX=x+inset,titleY=y+inset,descriptionX=x+inset,descriptionY=y+inset+font.height(12F);
        DrawTask text=drawing.msdfTask(font);
        font.drawSinglePass(text,modern?"\u00a7l"+title:title,12F,titleX,titleY,modern?-1:scheme.onPanel());
        font.drawSinglePass(text,description,7F,descriptionX,descriptionY,modern?0xffaaaaaa:scheme.onPanel());
        float offset=0F;
        for(ModalButton button:configuration.getButtons()) {
            button.setX(x+200F-inset-button.getWidth()-offset);
            button.setY(y+height-inset-button.getHeight());
            button.draw(background,text,scheme,drawing);offset+=button.getWidth()+inset;
            if(captured!=null) return;
        }
        background.draw();
        if(modern){DrawTask outline=drawing.outlineTask();outline.add(drawing.outline(x-1F,y-1F,width+2F,height+2F,17F,.25F,0xff5b5b5b));outline.draw();}
        text.draw();
        content.setX(x+100F-content.getWidth()/2F);content.setY(descriptionY+font.height(7F)+12F);content.setScheme(scheme);
        content.update(mouseX,mouseY);
        }finally{RenderTransformState.setAlpha(previousAlpha);}
    }
    public void mouseClicked(int mouseX,int mouseY,int button) {
        int[] captured=ModalContent.getSentinel();
        for(ModalButton action:configuration.getButtons()) {
            if(captured!=null) return;
            if(action.hit((float)mouseX,(float)mouseY)) { action.getCallback().accept(null);return; }
        }
        configuration.getContent().mouseClicked(mouseX,mouseY,button);
    }
    public void mouseReleased(int mouseX,int mouseY,int button) { configuration.getContent().mouseReleased(mouseX,mouseY,button); }
    public void keyTyped(char character,int key) { configuration.getContent().keyTyped(character,key); }
    public ModalConfiguration getConfiguration() { return configuration; }
    public TimedAnimation getAnimation() { return animation; }
    public Scheme getScheme() { return scheme; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }
}
