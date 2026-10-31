package moon.module.visual;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import moon.visual.gui.*;
import moon.visual.render.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.*;
import net.minecraft.util.*;

/** jL: custom scoreboard and chat, with shared rounded shadow/blur masks.
 * Numeric constants are decoded from supplied runtime D=72494844460774.
 * The original 20 Hz worker is adapted to the render thread; no worker calls GL.
 * Camera hides only score text; panel width retains the original score measurement.
 */
public final class CustomMinecraftModule implements ModuleRowWidget.Module {
    public interface Rendering {
        DrawServices drawing(); ShaderDispatcher rounded(); ShaderDispatcher text();
        MsdfFontBase font(); void invalidate(); void isolated(Runnable drawing);
    }
    public record Panel(float x,float y,float width,float height,boolean scoreboard) {}
    public record Text(String value,float x,float y,boolean scoreboard) {}
    private static final float SIZE=9, SCORE_INSET=6, PADDING=3;
    private static final int BACKGROUND=0x80000000;
    private final Minecraft mc;
    private final Rendering rendering;
    public final BooleanSetting scoreboard,chat;
    
    public final ShadowStyle scoreboardShadow,chatShadow;
    private final List<SettingValue<?>> settings;
    private final List<Panel> panels=new ArrayList<>();
    private final List<Text> texts=new ArrayList<>();
    private boolean enabled,previousChat;
    private int previousChatWidth=-1;
    private float previousChatScale=Float.NaN;
    private int scoreRows,chatRows;

    public CustomMinecraftModule(Minecraft mc,MarkerHooks hooks,Rendering rendering) {
        this.mc=Objects.requireNonNull(mc);this.rendering=Objects.requireNonNull(rendering);
        scoreboard=BooleanSetting.builder("scoreboard",hooks).configure("Scoreboard",false).build();
        chat=BooleanSetting.builder("chat",hooks).configure("Chat",false).build();
        scoreboardShadow=new ShadowStyle("scoreboard-shadow-color","Shadow Color",hooks);
        chatShadow=new ShadowStyle("chat-shadow-color","Shadow Color",hooks);
        settings=List.of(scoreboard,chat,scoreboardShadow.group,chatShadow.group);
    }
    public String displayName(){return "CustomMinecraft";}
    public boolean enabled(){return enabled;}
    public void toggle(){setEnabled(!enabled);}
    public void setEnabled(boolean value){
        if(enabled==value)return;
        enabled=value;panels.clear();texts.clear();scoreRows=chatRows=0;
        refreshChat();rendering.invalidate();
    }
    public List<? extends SettingValue<?>> settings(){return settings;}
    public boolean replacesScoreboard(){return enabled&&scoreboard.get()&&mc.theWorld!=null&&mc.thePlayer!=null;}
    public boolean replacesChat(){return enabled&&chat.get()&&mc.theWorld!=null&&mc.thePlayer!=null;}
    public int scoreRows(){return scoreRows;}
    public int chatRows(){return chatRows;}
    public List<Panel> panels(){return List.copyOf(panels);}
    public List<Text> texts(){return List.copyOf(texts);}

    public MsdfFontBase chatFont(){return rendering.font();}
    private void refreshChat(){
        previousChat=enabled&&chat.get();
        if(mc.ingameGUI!=null){
            GuiNewChat gui=mc.ingameGUI.getChatGUI();
            previousChatWidth=gui.getChatWidth();previousChatScale=gui.getChatScale();
            gui.refreshChat();
        }
    }
    /** Same team-colored sidebar selection as GuiIngame; slot 0 remains the tab list. */
    public ScoreObjective sidebarObjective(){
        if(mc.theWorld==null||mc.thePlayer==null)return null;
        Scoreboard board=mc.theWorld.getScoreboard();
        ScorePlayerTeam team=board.getPlayersTeam(mc.thePlayer.getName());
        ScoreObjective objective=null;
        if(team!=null&&team.getChatFormat().getColorIndex()>=0)
            objective=board.getObjectiveInDisplaySlot(3+team.getChatFormat().getColorIndex());
        return objective!=null?objective:board.getObjectiveInDisplaySlot(1);
    }
    /** Before POST effects, build one CPU snapshot shared by masks and visible drawing. */
    public void prepare(){
        GuiNewChat gui=mc.ingameGUI.getChatGUI();
        if(previousChat!=(enabled&&chat.get()) || (enabled&&chat.get()&&
            (previousChatWidth!=gui.getChatWidth()||Float.compare(previousChatScale,gui.getChatScale())!=0)))refreshChat();
        List<Panel> old=List.copyOf(panels);
        panels.clear();texts.clear();scoreRows=chatRows=0;
        if(mc.theWorld!=null&&mc.thePlayer!=null&&enabled){
            ScaledResolution screen=new ScaledResolution(mc);
            if(scoreboard.get()){
                ScoreObjective objective=sidebarObjective();
                if(objective!=null)prepareScoreboard(objective,screen);
            }
            if(chat.get())prepareChat(screen);
        }
        if(!old.equals(panels))rendering.invalidate();
    }
    private void prepareScoreboard(ScoreObjective objective,ScaledResolution screen){
        Scoreboard board=objective.getScoreboard();
        Collection<Score> raw=board.getSortedScores(objective);
        List<Score> rows=raw.stream().filter(s->s.getPlayerName()!=null&&!s.getPlayerName().startsWith("#")).toList();
        // jL uses the original collection size in this skip, matching vanilla 1.8.
        if(rows.size()>15)rows=rows.subList(Math.min(rows.size(),raw.size()-15),rows.size());
        MsdfFontBase font=rendering.font();
        float width=font.width(objective.getDisplayName(),SIZE),line=font.height(SIZE);
        for(Score score:rows){
            String label=ScorePlayerTeam.formatPlayerName(board.getPlayersTeam(score.getPlayerName()),score.getPlayerName());
            width=Math.max(width,font.width(label+": "+EnumChatFormatting.RED+score.getScorePoints(),SIZE));
        }
        int rowHeight=(int)Math.ceil(rows.size()*line);
        int baseline=(int)Math.ceil(screen.getScaledHeight()/2F+rowHeight/3F+100F);
        int left=(int)Math.ceil(screen.getScaledWidth()-width-3-SCORE_INSET);
        int right=(int)Math.ceil(screen.getScaledWidth()-3+2-SCORE_INSET);
        int index=0;
        for(Score score:rows){
            int y=(int)Math.ceil(baseline-++index*line);
            String label=ScorePlayerTeam.formatPlayerName(board.getPlayersTeam(score.getPlayerName()),score.getPlayerName());
            String number=EnumChatFormatting.RED.toString()+score.getScorePoints();
            texts.add(new Text(label,left,y,true));
            if(!moon.client.McpVisualServices.cameraFlag("score"))texts.add(new Text(number,right-font.width(number,SIZE),y,true));
            if(index==rows.size()){
                texts.add(new Text(objective.getDisplayName(),left+width/2-font.width(objective.getDisplayName(),SIZE)/2,y-line,true));
            }
        }
        float x=left-PADDING,y=baseline-index*line-line-PADDING;
        panels.add(new Panel(x,y,screen.getScaledWidth()-3+2-x-SCORE_INSET+PADDING,(rows.size()+1)*line+PADDING*2,true));
        scoreRows=rows.size();
    }
    private void prepareChat(ScaledResolution screen){
        if(mc.gameSettings.chatVisibility==EntityPlayer.EnumChatVisibility.HIDDEN)return;
        GuiNewChat gui=mc.ingameGUI.getChatGUI();
        List<ChatLine> lines=gui.moonDrawnChatLines();
        MsdfFontBase font=rendering.font();float lineHeight=font.height(SIZE);
        boolean open=gui.getChatOpen();
        float opacity=mc.gameSettings.chatOpacity*.9F+.1F;
        for(int i=0;i+gui.moonScrollPosition()<lines.size()&&i<gui.getLineCount();i++){
            ChatLine line=lines.get(i+gui.moonScrollPosition());
            if(line==null)continue;
            int age=mc.ingameGUI.getUpdateCounter()-line.getUpdatedCounter();
            if(age>=200&&!open)continue;
            double fade=MathHelper.clamp_double((1-age/200D)*10,0,1);
            int alpha=(int)((open?255:(int)(255*fade*fade))*opacity);
            chatRows++;
            float textY=screen.getScaledHeight()-i*lineHeight-8-27;
            // Original alpha gates visibility; the text itself is opaque white.
            if(alpha>3)texts.add(new Text(line.getChatComponent().getFormattedText(),5,textY,false));
        }
        if(chatRows>0){
            float height=chatRows*lineHeight+5;
            panels.add(new Panel(2,screen.getScaledHeight()-27-height+4.5F,gui.getChatWidth()+3,height,false));
        }
    }
    public void draw(){if(!panels.isEmpty())rendering.isolated(()->render(false,null));}
    public void mask(boolean shadow){if(!panels.isEmpty())rendering.isolated(()->{
        if(shadow){scoreboardShadow.render(()->render(true,true));chatShadow.render(()->render(true,false));}
        else render(true,null);
    });}
    private void render(boolean mask,Boolean scoreboardOnly){
        DrawTask background=DrawTask.builder(rendering.drawing()).dispatcher(rendering.rounded()).retained().build();
        try{
            for(Panel p:panels)if(scoreboardOnly==null||p.scoreboard()==scoreboardOnly)
                background.add(new UiGeometry.RoundedRectangle(p.x,p.y,p.width,p.height,4,
                    mask?0xff000000:BACKGROUND,rendering.drawing().markers));
            background.draw();
        }finally{background.getState().release();}
        if(mask)return;
        MsdfFontBase font=rendering.font();
        DrawTask text=DrawTask.builder(rendering.drawing()).dispatcher(rendering.text()).texture(font::bind).retained().build();
        try{
            for(Text value:texts){
                // Scoreboard rows keep team/objective formatting codes: §l bolds,
                // §o skews, §k obfuscates, §m/§n decorate, and §r resets glyph style.
                if(value.scoreboard)font.draw(text,value.value,SIZE,value.x,value.y,-1,true);
                else font.draw(text,value.value,SIZE,value.x,value.y,-1);
            }
            text.draw();
        }finally{text.getState().release();}
    }
    /** Custom chat uses unscaled MSDF positions; vanilla's hit test uses a different font/scale. */
    public IChatComponent chatComponentAt(int mouseX,int mouseY){
        if(!replacesChat()||!mc.ingameGUI.getChatGUI().getChatOpen())return null;
        GuiNewChat gui=mc.ingameGUI.getChatGUI();ScaledResolution screen=new ScaledResolution(mc);
        float x=mouseX/(float)screen.getScaleFactor()-5;
        float bottom=mouseY/(float)screen.getScaleFactor();
        float height=rendering.font().height(SIZE);
        if(x<0||x>gui.getChatWidth()||bottom<35-height)return null;
        int row=(int)Math.floor((bottom-(35-height))/height);
        if(row<0||row>=gui.getLineCount())return null;
        int index=row+gui.moonScrollPosition();
        if(index>=gui.moonDrawnChatLines().size())return null;
        ChatLine line=gui.moonDrawnChatLines().get(index);
        if(line==null)return null;
        float used=0;
        for(IChatComponent component:line.getChatComponent()){
            if(component instanceof ChatComponentText part){
                used+=rendering.font().width(part.getChatStyle().getFormattingCode()+part.getChatComponentText_TextValue(),SIZE);
                if(used>x)return component;
            }
        }
        return null;
    }
}
