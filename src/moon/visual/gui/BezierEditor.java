package moon.visual.gui;

import moon.visual.render.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** kv captured-sentinel control flow; numeric constants and host ownership remain adapters.
 * Background, direct g4 line/points, then drag update.
 * Tasks skipped by the captured branch are released by the destination without submitting geometry.
 * No point-size/capability restoration occurs in kv. The destination screen must own that state.
 */
public final class BezierEditor extends ModalContent {
    public interface Drawing {
        DrawTask roundedTask();
        DrawTask rectangleTask(int primitive);
        DrawTaskState.Geometry rounded(float x,float y,float width,float height,float radius,int color);
        void enable(int capability);
        void pointSize(float value);
    }
    /** kv seed lacks supplied runtime evidence. These four candidates remain explicit inputs. */
    public static final class Parameters {
        public final int lineCapability,samples,lineVertexCount,pointCapability;
        public Parameters(int lineCapability,int samples,int lineVertexCount,int pointCapability) {
            this.lineCapability=lineCapability;this.samples=samples;this.lineVertexCount=lineVertexCount;this.pointCapability=pointCapability;
        }
        public static Parameters unverifiedTableCandidates() { return new Parameters(2848,101,101,2832); }
    }
    private final CurveHandle[] handles=new CurveHandle[4];
    private final BezierSetting setting;
    private CurveHandle dragged;
    private final Drawing drawing;
    private final Parameters parameters;
    private final MarkerHooks markers;
    private static Object[] sentinel=new Object[5];
    public BezierEditor(BezierSetting setting,Drawing drawing,Parameters parameters,MarkerHooks markers) {
        this.setting=setting;this.drawing=drawing;this.parameters=parameters;this.markers=markers;
    }
    @Override public void initialize() {
        width=150F;height=150F; // Size is known before the modal computes its first layout.
        getEditorSentinel(); // Original getter precedes handle creation; captured value is unused here.
        handles[0]=new CurveHandle(p->setting.setBegin(p.y),false,markers);
        handles[1]=new CurveHandle(p->setting.setPoints(new float[]{p.x,p.y},setting.getPoints()[1]),true,markers);
        handles[2]=new CurveHandle(p->setting.setPoints(setting.getPoints()[0],new float[]{p.x,p.y}),true,markers);
        handles[3]=new CurveHandle(p->setting.setEnd(p.y),false,markers);
        super.initialize();if(markers.getMarker()!=null) sentinel=new Object[2];
    }
    @Override public void update(int mouseX,int mouseY) {
        height=150F;width=150F;
        DrawTask background=drawing.roundedTask();background.add(drawing.rounded(x,y,width,height,6,scheme.background()));background.draw();
        float left=x+8F,top=y+8F,graphWidth=width-16F,graphHeight=height-16F,bottom=top+graphHeight;
        handles[0].setX(left);
        Object[] capturedSentinel=getEditorSentinel();
        handles[0].setY(bottom-setting.getBegin()*graphHeight);
        handles[1].setX(left+setting.getPoints()[0][0]*graphWidth).setY(bottom-setting.getPoints()[0][1]*graphHeight);
        handles[2].setX(left+setting.getPoints()[1][0]*graphWidth).setY(bottom-setting.getPoints()[1][1]*graphHeight);
        handles[3].setX(left+graphWidth).setY(bottom-setting.getEnd()*graphHeight);
        drawing.enable(parameters.lineCapability);
        DrawTask line=drawing.rectangleTask(3);DrawTaskState state=line.getState();NativeVertexWriter writer=state.getWriter();
        float screenWidth=state.getWidth(),screenHeight=state.getHeight();
        boolean lineUnsubmitted=false;
        for(int i=0;i<parameters.samples;i++) {
            float[] point=setting.sample(i/100F);
            writer.append((left+point[0]*graphWidth)/screenWidth,(bottom-point[1]*graphHeight)/screenHeight).appendColor(scheme.curve());
            if(capturedSentinel==null) { lineUnsubmitted=true;break; }
        }
        if(!lineUnsubmitted) {
            state.setVertexCount(state.getVertexCount()+parameters.lineVertexCount);line.draw();
            drawing.enable(parameters.pointCapability);drawing.pointSize(10F);
        } else state.release();
        DrawTask dots=drawing.rectangleTask(0);NativeVertexWriter dotWriter=dots.getState().getWriter();
        boolean dotsUnsubmitted=false;
        for(CurveHandle handle:handles) {
            dotWriter.append(handle.getX()/screenWidth,handle.getY()/screenHeight).appendColor(scheme.accent());
            if(capturedSentinel==null) { dotsUnsubmitted=true;break; }
        }
        if(!dotsUnsubmitted) { dots.getState().setVertexCount(dots.getState().getVertexCount()+4);dots.draw(); }
        else dots.getState().release();
        if(dragged!=null) {
            float normalizedY=1F-(mouseY-top)/graphHeight;
            float normalizedX=NumericSettingMath.clamp((mouseX-left)/graphWidth,0F,1F);
            // Null carries dot-loop counter 1 into offset749; no movable-X read/X write.
            boolean movableX=capturedSentinel!=null&&dragged.isMovableX();
            if(movableX) dragged.setX(normalizedX);
            dragged.setY(capturedSentinel==null||movableX?normalizedY:NumericSettingMath.clamp(normalizedY,0F,1F));
            dragged.apply();
        }
    }
    @Override public void mouseClicked(int mouseX,int mouseY,int button) {
        Object[] capturedSentinel=getEditorSentinel();
        if(capturedSentinel==null) return;
        if(button==0) for(CurveHandle handle:handles) if(handle.hit(mouseX,mouseY)) { dragged=handle;break; }
        super.mouseClicked(mouseX,mouseY,button);
    }
    @Override public void mouseReleased(int mouseX,int mouseY,int button) { dragged=null;super.mouseReleased(mouseX,mouseY,button); }
    public CurveHandle[] getHandles() { return handles; }
    public CurveHandle getDragged() { return dragged; }
    public BezierSetting getSetting() { return setting; }
    public static Object[] getEditorSentinel() { return sentinel; }
    public static void setEditorSentinel(Object[] value) { sentinel=value; }
}
