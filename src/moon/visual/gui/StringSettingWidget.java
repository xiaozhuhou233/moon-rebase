package moon.visual.gui;

import java.util.function.LongSupplier;
import moon.visual.render.*;

/** ko per-method captured integer-sentinel paths. Key codes/blink period remain explicit unresolved parameters.
 * Selection is a whole-string flag, with no caret index, paste/copy or mouse positioning.
 */
public final class StringSettingWidget extends SettingWidget {
    public interface Host extends SettingWidget.Host {
        void enableRepeatEvents(boolean enabled);
        boolean allowedCharacter(char character); // moom.CG.a(char)
        boolean primaryModifier();               // moom.gD.b(), OS-dependent control/command
        boolean secondaryModifier();             // moom.gD.c()
        boolean thirdModifier();                 // moom.gD.d()
    }
    public static final class Parameters {
        public final int backspaceKey,selectAllKey;
        public final long blinkMillis;
        public Parameters(int backspaceKey,int selectAllKey,long blinkMillis) {
            this.backspaceKey=backspaceKey;this.selectAllKey=selectAllKey;this.blinkMillis=blinkMillis;
        }
    }
    private final StringSetting setting;
    private final Host textHost;
    private final Parameters parameters;
    private final LongSupplier clock;
    private boolean focused,caretVisible,allSelected;
    private long lastBlink;
    public StringSettingWidget(Host host,StringSetting setting,float x,float y,float width,float height,
                               Parameters parameters,LongSupplier clock) {
        super(host);textHost=host;this.setting=setting;this.parameters=parameters;this.clock=clock;
        this.x=x;targetY=y;this.width=width;this.height=height;
    }
    @Override public void update(int mouseX,int mouseY) {
        ensureConfig();drawTitle();drawBox();drawValue();fullHeight=32F;super.update(mouseX,mouseY);
    }
    @Override public boolean mouseClicked(int mouseX,int mouseY,int button) {
        focused=hitTest(mouseX,mouseY,x+padding,getY()+font.height(7.065F)+2F,width-padding*2F,20F);
        return super.mouseClicked(mouseX,mouseY,button);
    }
    public boolean isSelectAllShortcut(int key) {
        int captured=getComponentSentinel();
        if(captured!=0) return (key&1)!=0;
        return key==parameters.selectAllKey&&textHost.primaryModifier()&&!textHost.secondaryModifier()&&!textHost.thirdModifier();
    }
    @Override public void keyTyped(char character,int key) {
        int captured=getComponentSentinel();
        if(captured!=0) { super.keyTyped(character,key);return; }
        if(focused) {
            if(isSelectAllShortcut(key)) { textHost.enableRepeatEvents(false);allSelected=true;return; }
            if(key==parameters.backspaceKey) {
                if(!setting.get().isEmpty()) {
                    textHost.enableRepeatEvents(true);
                    setting.set(allSelected?"":setting.get().substring(0,Math.max(setting.get().length()-1,0)));
                    allSelected=false;
                }
                return;
            }
            if(textHost.allowedCharacter(character)) {
                if(allSelected) { setting.set("");allSelected=false; }
                setting.set(setting.get()+character);
            }
            textHost.enableRepeatEvents(false);
        }
        super.keyTyped(character,key);
    }
    private void drawTitle() {
        Drawing drawing=host.drawing();DrawTaskState.Command clip=drawing.clip(clipX,clipY,clipWidth,clipHeight);
        DrawTask task=drawing.msdfTask(font);
        font.drawSinglePass(task,setting.getDisplayName(),7.065F,getX()+padding+1F,getY(),theme.text());
        task.add(clip);task.draw();
    }
    private void drawBox() {
        Drawing drawing=host.drawing();DrawTaskState.Command clip=drawing.clip(clipX,clipY,clipWidth,clipHeight);
        // The original captures after clip creation, before task creation.
        int captured=getComponentSentinel();
        DrawTask task=drawing.roundedTask();float titleHeight=font.height(7.065F),textWidth=font.width(setting.get(),8.039999F);
        task.add(drawing.rounded(x+padding,getY()+titleHeight+2F,width-padding*2F,20F,5F,theme.track()));
        boolean selected=allSelected;
        if(captured==0&&selected) {
            float limit=width-padding*2F-2F-1.5F,selectedWidth=Math.min(textWidth+4F,limit)-2F;
            task.add(drawing.rounded(x+padding+2F,getY()+titleHeight+6.5F,selectedWidth,font.height(8.039999F)+.5F,0F,new ArgbColor(theme.text()).withAlpha(.2F).getRGB()));
        }
        // Nondefault branches carry the previously read selection flag into the clock test.
        if(captured!=0||focused) {
            if(captured!=0?selected:clock.getAsLong()-lastBlink>parameters.blinkMillis) {
                caretVisible=captured!=0?caretVisible:!caretVisible;
                lastBlink=clock.getAsLong();
            }
            float caretOffset=textWidth+3F,limit=width-padding*2F-8F-1.5F,overflow=textWidth-limit;
            if(captured==0&&overflow>0F) caretOffset-=overflow;
            // fcmpl yields -1 for NaN, so nondefault also draws for NaN/negative overflow.
            if(captured!=0?overflow!=0F:caretVisible) task.add(drawing.rounded(x+padding+caretOffset,getY()+titleHeight+6.5F,2F,font.height(8.039999F)+.5F,0F,theme.text()));
        }
        task.add(clip);task.draw();
    }
    private void drawValue() {
        Drawing drawing=host.drawing();
        DrawTaskState.Command clip=drawing.clip(Math.max(clipX,x+padding+4.5F),clipY,clipWidth,clipHeight);
        // Capture inverse after the text-specific clip, before the text task.
        int captured=inverseComponentSentinel();
        DrawTask task=drawing.msdfTask(font);float limit=width-padding*2F-8F-1.5F,overflow=font.width(setting.get(),8.039999F)-limit;
        font.drawSinglePass(task,setting.get(),8.039999F,getX()+padding+4.5F+(captured==0?overflow:overflow>0F?-overflow:0F),getY()+font.height(8F)+6.5F,theme.text());
        task.add(clip);task.draw();
    }
    @Override public boolean isVisible() { return setting.isVisible(); }
    public StringSetting getSetting() { return setting; }
    public boolean isFocused() { return focused; }
    public boolean isCaretVisible() { return caretVisible; }
    public boolean isAllSelected() { return allSelected; }
    public long getLastBlink() { return lastBlink; }
}
