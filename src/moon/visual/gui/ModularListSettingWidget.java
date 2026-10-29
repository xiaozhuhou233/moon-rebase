package moon.visual.gui;

import java.util.*;
import java.util.function.LongSupplier;
import moon.visual.render.*;

/** kl with independently captured ko guards, including carried operands in private drawing helpers. Runtime seed confirms keys14/30, blink500 and error duration2000.
 * Submit/cancel poll left-button level during drawing; row deletion bypasses setting.remove.
 */
public final class ModularListSettingWidget extends SettingWidget {
    public interface Scheme extends ChoiceSettingWidget.Scheme {
        int success();int onSuccess();int error();int onError();
    }
    public interface Host extends StringSettingWidget.Host,ChoiceSettingWidget.Host {
        @Override Scheme uiColor(int argb);
        boolean leftButtonDown();
        /** Original notification builder title/message/duration/ERROR, published once. */
        void errorNotification(String title,String message,long durationMillis);
    }
    private static final String ADD="\ue145",DELETE="\ue14c",CHECK="\ue5ca",CANCEL="\ue5cd";
    private final ModularListSetting setting;
    private final Host listHost;
    private final LongSupplier clock;
    private final FrameAnimation heightAnimation;
    private final Map<String,FrameAnimation> selectionAnimations,hoverAnimations;
    private boolean expanded,editing,caretVisible,allSelected,suppressNextClick;
    private int editIndex;
    private String buffer="";
    private long lastBlink;
    public ModularListSettingWidget(Host host,ModularListSetting setting,float x,float y,float width,float height,LongSupplier clock) {
        super(host);listHost=host;this.clock=clock;heightAnimation=newAnimation();
        selectionAnimations=new HashMap<>();int captured=inverseComponentSentinel();hoverAnimations=new HashMap<>();
        this.setting=setting;this.x=x;targetY=y;this.width=width;this.height=height;
        for(String item:setting.getAvailable()) {
            selectionAnimations.put(item,newAnimation());if(captured==0) break;
        }
    }
    private ChoiceSettingWidget.Drawing drawing() { return listHost.drawing(); }
    private Scheme colors() { return (Scheme)theme; }
    private DrawTaskState.Command parentClip() { return drawing().clip(clipX,clipY,clipWidth,clipHeight); }
    private float rowsClipHeight() { return Math.min(clipHeight-(clipY+clipHeight-(getY()+height)),clipHeight); }
    private DrawTaskState.Command rowsClip() { return drawing().clip(clipX,clipY,clipWidth,rowsClipHeight()); }
    @Override public void update(int mouseX,int mouseY) {
        int captured=getComponentSentinel();ensureConfig();drawBackground(mouseX,mouseY);drawTitle(mouseX,mouseY);
        if(captured!=0||expanded||heightAnimation.get()>19.1D) drawRows(mouseX,mouseY);
        super.update(mouseX,mouseY);
    }
    private void drawBackground(int mouseX,int mouseY) {
        DrawTaskState.Command clip=parentClip();int captured=inverseComponentSentinel();
        float measureX=x+padding+5F,bx=0F;boolean carriedX=false;
        for(String item:setting.getAvailable()) {
            float textWidth=font.width(item,7.7F);
            if(captured==0) {
                // kl:123 carries both float operands to fadd at186, replacing bx.
                bx=(measureX+textWidth)+(x+width-(padding*2F+10F));carriedX=true;break;
            }
            if(measureX+textWidth>x+width-(padding*2F+10F)) measureX=x+padding+5F;
            measureX+=textWidth+15F;
        }
        if(!carriedX) bx=x+padding;
        float by=getY(),bw=width-padding*2F; // original unused decoded local=19
        boolean hover=hitTest(mouseX,mouseY,bx+bw-19F,by,19F,19F);
        DrawTask task=drawing().roundedTask();
        task.add(drawing().rounded(bx+bw-19F,by,19F,19F,5F,captured==0?(hover?1:0):(hover?colors().accent():colors().success()),true,true,true,true,false));
        task.add(drawing().rounded(bx,by,bw-19F-1F,19F,5F,theme.track(),true,true,true,true,false));
        task.add(clip);task.draw();
        fullHeight=expanded?21.5F*setting.getAvailable().size()+20F:19F;
        if(captured!=0&&editing) fullHeight+=21.5F;
        visibilityAnimation.update(isVisible()?1F:0F,.30000001192092896D);
        heightAnimation.update(fullHeight*visibilityAnimation.get(),.30000001192092896D);setHeight(heightAnimation.get());
    }
    private void drawTitle(int mouseX,int mouseY) {
        DrawTaskState.Command clip=parentClip();MsdfFontBase icons=listHost.materialIcons();
        DrawTask text=drawing().msdfTask(font),symbols=drawing().msdfTask(icons);
        float bx=x+padding,by=getY(),bw=width-padding*2F;
        int captured=getComponentSentinel();
        float iconHeight=icons.secondMetric(ADD,9.8F),iconWidth=icons.width(ADD,9.8F);
        float ix=bx+bw-19F+(19F-iconWidth)/2F,iy=by+19F/2F-iconHeight/2F;
        boolean hover=hitTest(mouseX,mouseY,ix,iy,19F,19F);
        icons.drawSinglePass(symbols,ADD,9.8F,ix,iy+2.5F,captured!=0?(hover?1:0):(hover?colors().onPrimary():colors().onSuccess()));
        font.draw(text,setting.getDisplayName(),8.039999F,getX()+padding+5F,getY()+5.5F,theme.text(),false,false,.01F);
        text.add(clip);symbols.add(clip);text.draw();symbols.draw();
    }
    private void drawRows(int mouseX,int mouseY) {
        DrawTaskState.Command clip=rowsClip();MsdfFontBase icons=listHost.materialIcons();
        DrawTask backgrounds=drawing().roundedTask(),text=drawing().msdfTask(font),symbols=drawing().msdfTask(icons);
        int captured=inverseComponentSentinel();
        float rx=x+padding,ry=getY()+19F+3F-1F,rw=width-padding*2F,rh=21F;
        List<String> available=setting.getAvailable();
        for(int index=0;index<available.size();index++) {
            String item=available.get(index);boolean selected=item.equals(setting.get());
            boolean hovered=hitTest(mouseX,mouseY,rx,ry,rw,rh);
            // Original creates the argument animation even when the key is already present.
            selectionAnimations.putIfAbsent(item,newAnimation());hoverAnimations.putIfAbsent(item,newAnimation());
            FrameAnimation selection=selectionAnimations.get(item),hover=hoverAnimations.get(item);
            if(captured==0) return; // No task submission, including clip commands.
            selection.update(selected?1F:0F,.4000000059604645D);hover.update(hovered?1F:0F,.4000000059604645D);
            // Both palette callbacks precede the live list reads used for corner flags.
            int backgroundColor=theme.track(),selectedColor=new ArgbColor(theme.accent()).withAlpha(selection.get()).getRGB();
            boolean g=true,h=true,i=true,j=true;
            if(available.size()>1) {
                if(index==0) {i=false;j=false;}
                else if(index<available.size()-1) {g=false;h=false;i=false;j=false;}
                else {h=false;g=false;}
            }
            backgrounds.add(drawing().rounded(rx,ry,rw,rh,5F,backgroundColor,g,h,i,j,false));
            backgrounds.add(drawing().rounded(rx,ry,rw,rh,5F,selectedColor,g,h,i,j,false));
            font.draw(text,item,8F,rx+5F,ry+rh/2F-font.height(8F)/2F+1F,new ArgbColor(theme.text()).withAlpha(1F-selection.get()).getRGB(),false);
            font.draw(text,item,8F,rx+5F,ry+rh/2F-font.height(8F)/2F+1F,new ArgbColor(colors().onPrimary()).withAlpha(selection.get()).getRGB(),false);
            if(selected) icons.draw(symbols,CHECK,8.5F,rx+rw-5F-icons.width(CHECK,8.5F),ry+rh/2F-icons.height(8.5F)/2F+1F,
                new ArgbColor(colors().onPrimary()).withAlpha(Math.min(selection.get(),1F-hover.get())).getRGB(),false);
            backgrounds.add(drawing().rounded(rx+rw-5F-icons.width(DELETE,9F)-2F,ry+rh/2F-icons.height(9F)/2F-2F,
                icons.width(DELETE,9F)+4F,icons.height(9F)+4F,5F,new ArgbColor(colors().error()).withAlpha(hover.get()).getRGB()));
            icons.draw(symbols,DELETE,9F,rx+rw-5F-icons.width(DELETE,9F),ry+rh/2F-icons.height(9F)/2F+1.5F,
                new ArgbColor(colors().onError()).withAlpha(hover.get()).getRGB(),false);
            ry+=rh+1.5F-1F;
        }
        if(captured==0||editing) drawInput(mouseX,mouseY,editIndex);
        backgrounds.add(clip);text.add(clip);symbols.add(clip);backgrounds.draw();text.draw();symbols.draw();
    }
    private void drawInput(int mouseX,int mouseY,int index) {
        DrawTaskState.Command clip=rowsClip();
        DrawTaskState.Command textClip=drawing().clip(Math.max(clipX,x+padding+4.5F),clipY,clipWidth,rowsClipHeight());
        int captured=inverseComponentSentinel();
        MsdfFontBase icons=listHost.materialIcons();float rx=x+padding,ry=getY()+19F+3F,rw=width-padding*2F,rh=21F;
        ry+=(rh+1.5F-1F)*index;rh-=1F;
        DrawTask backgrounds=drawing().roundedTask(),symbols=drawing().msdfTask(icons),text=drawing().msdfTask(font);
        backgrounds.add(drawing().rounded(rx+.5F,ry+.5F,rw-rh*2F-1F,rh-1F,5F,theme.track()));
        backgrounds.add(drawing().rounded(rx+rw-rh*2F+.5F,ry+.5F,rh-1F,rh-1F,5F,colors().error()));
        backgrounds.add(drawing().rounded(rx+rw-rh+.5F,ry+.5F,rh-1F,rh-1F,5F,theme.accent()));
        icons.draw(symbols,CANCEL,9.8F,rx+rw-rh*2F+rh/2F-icons.width(CANCEL,9.8F)/2F,ry+rh/2F-icons.height(8.5F)/2F+1F,colors().onError(),false);
        icons.draw(symbols,CHECK,9.8F,rx+rw-rh+rh/2F-icons.width(CHECK,9.8F)/2F,ry+rh/2F-icons.height(8.5F)/2F+1F,colors().onPrimary(),false);
        if(clock.getAsLong()-lastBlink>500L) {if(captured!=0) caretVisible=!caretVisible;lastBlink=clock.getAsLong();}
        float limit=rw-rh*2F-8F,overflow=font.width(buffer,8.039999F)-limit,caretOffset=font.width(buffer,8.039999F)+5F;
        if(captured!=0&&overflow>0F) caretOffset-=overflow;
        if(captured==0?overflow!=0F:caretVisible) backgrounds.add(drawing().rounded(x+padding+caretOffset,ry+5F,2F,font.height(8.039999F)+.5F,0F,theme.text()));
        font.drawSinglePass(text,buffer,8.039999F,getX()+padding+4.5F+(captured==0?overflow:(overflow>0F?-overflow:0F)),ry+6F,theme.text());
        if(captured==0) return; // Built geometry/text remain unsubmitted; no mouse polling.
        if(listHost.leftButtonDown()) {
            if(hitTest(mouseX,mouseY,rx+rw-rh*2F,ry,rh,rh)) editing=false;
            if(hitTest(mouseX,mouseY,rx+rw-rh,ry,rh,rh)) {
                setting.add(buffer);editing=false;suppressNextClick=true;
            }
        }
        backgrounds.add(clip);text.add(textClip);symbols.add(clip);backgrounds.draw();symbols.draw();text.draw();
    }
    @Override public boolean mouseClicked(int mouseX,int mouseY,int button) {
        int captured=getComponentSentinel();float bx=x+padding,bw=width-padding*2F,headerEnd=getY()+3F+23F;
        if(captured!=0) return suppressNextClick;
        if(suppressNextClick) {suppressNextClick=false;return false;}
        if(hitTest(mouseX,mouseY,bx,headerEnd-23F-6F,bw-20F,23F)) {
            if(!editing) expanded=!expanded;
        } else if(hitTest(mouseX,mouseY,bx+bw-19F,headerEnd-23F-6F,23F,23F)) {
            if(!expanded) expanded=true;
            editIndex=setting.getAvailable().size();editing=!editing;buffer="";
        } else if(expanded) {
            float rx=x+padding,ry=getY()+19F+3F-1F,rw=width-padding*2F,rh=21F;
            List<String> available=setting.getAvailable();
            for(int index=0;index<available.size();index++) {
                String item=available.get(index);
                if(hitTest(mouseX,mouseY,rx+rw-19F,ry,rh,rh)) {
                    boolean selected=item.equals(setting.get());
                    if(setting.getAvailable().size()==1) {
                        listHost.errorNotification("Settings","You cannot delete the last list element!",2000L);return false;
                    }
                    selectionAnimations.remove(item);hoverAnimations.remove(item);setting.getAvailable().remove(index);
                    if(selected) setting.set(available.get(0));return false;
                }
                if(hitTest(mouseX,mouseY,rx,ry,rw,rh)) setting.set(item);
                ry+=rh+1.5F-1F;
            }
        }
        return super.mouseClicked(mouseX,mouseY,button);
    }
    public boolean isSelectAllShortcut(int key) {
        int captured=inverseComponentSentinel();if(captured==0) return (key&1)!=0;
        return key==30&&listHost.primaryModifier()&&!listHost.secondaryModifier()&&!listHost.thirdModifier();
    }
    @Override public void keyTyped(char character,int key) {
        int captured=inverseComponentSentinel();
        if(captured==0) {listHost.enableRepeatEvents(editing);return;}
        if(editing) {
            if(isSelectAllShortcut(key)) {listHost.enableRepeatEvents(false);allSelected=true;return;}
            if(key==14) {
                if(!setting.get().isEmpty()) {
                    listHost.enableRepeatEvents(true);buffer=allSelected?"":buffer.substring(0,Math.max(buffer.length()-1,0));allSelected=false;
                }
                return;
            }
            if(listHost.allowedCharacter(character)) {
                if(allSelected) {buffer="";allSelected=false;}buffer=buffer+character;
            }
            listHost.enableRepeatEvents(false);
        }
    }
    @Override public boolean isVisible() { return setting.isVisible(); }
    @Override public boolean usesVisibilityAnimation() { return false; }
    public ModularListSetting getSetting() { return setting; }
    public FrameAnimation getHeightAnimation() { return heightAnimation; }
    public FrameAnimation getSelectionAnimation(String item) { return selectionAnimations.get(item); }
    public FrameAnimation getHoverAnimation(String item) { return hoverAnimations.get(item); }
    public boolean isExpanded() { return expanded; }
    public boolean isEditing() { return editing; }
    public int getEditIndex() { return editIndex; }
    public String getBuffer() { return buffer; }
    public boolean isAllSelected() { return allSelected; }
    public boolean isCaretVisible() { return caretVisible; }
    public long getLastBlink() { return lastBlink; }
    public boolean isSuppressingNextClick() { return suppressNextClick; }
}
