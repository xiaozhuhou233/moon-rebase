package moon.client.account;

import java.awt.Desktop;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.*;
import moon.client.MoonMenuButton;
import org.lwjglx.input.Keyboard;
import org.lwjglx.input.Mouse;

/** l5/l6/l7 destination screens. Keep account operations in the account owner. */
public final class AccountManagerScreen extends GuiScreen implements GuiYesNoCallback {
    private final GuiScreen parent;
    private AccountManager manager;
    private AccountManager.Account selected;
    private int scroll;
    private long lastClick;
    public AccountManagerScreen(GuiScreen parent){this.parent=parent;}
    @Override public void initGui(){
        manager=AccountManager.get(mc);int x=width/2-152,y=height-68;
        buttonList.add(new MoonMenuButton(1,x,y,100,20,"Add"));buttonList.add(new MoonMenuButton(2,x+102,y,100,20,"Direct Login"));buttonList.add(new MoonMenuButton(3,x+204,y,100,20,"Login"));
        buttonList.add(new MoonMenuButton(4,x,y+24,100,20,"Remove"));buttonList.add(new MoonMenuButton(5,x+102,y+24,100,20,"Clear List"));buttonList.add(new MoonMenuButton(6,x+204,y+24,100,20,"Random Alt"));
        buttonList.add(new MoonMenuButton(0,8,8,56,20,"Back"));
    }
    private int visible(){return Math.max(1,(height-126)/36);}
    private void clamp(){scroll=Math.max(0,Math.min(scroll,Math.max(0,manager.accounts().size()-visible())));}
    @Override public void drawScreen(int mouseX,int mouseY,float ticks){
        drawGradientRect(0,0,width,height,0xff121724,0xff080b12);clamp();
        drawCenteredString(fontRendererObj,"Account Manager",width/2,12,0xffe8eefb);
        drawCenteredString(fontRendererObj,"Logged in to: "+mc.getSession().getUsername(),width/2,29,0xff8ec9ff);
        List<AccountManager.Account> accounts=manager.accounts();int left=width/2-152,right=width/2+152;
        drawRect(left,49,right,height-76,0x60000000);
        if(accounts.isEmpty())drawCenteredString(fontRendererObj,"No saved accounts. Click Add to get started.",width/2,68,0xffa5b0c4);
        for(int i=scroll;i<Math.min(accounts.size(),scroll+visible());i++){
            var account=accounts.get(i);int y=52+(i-scroll)*36;boolean active=account.equals(selected);
            drawRect(left+3,y,right-3,y+32,active?0xff253d5b:0xff192130);
            if(active)drawRect(left+3,y,left+5,y+32,0xff70bcff);
            drawString(fontRendererObj,account.name(),left+12,y+5,0xffecf3ff);
            drawString(fontRendererObj,account.kind()==AccountManager.Kind.OFFLINE?"Offline":"Microsoft",left+12,y+19,0xff9baac1);
            if(account.uuid().equalsIgnoreCase(mc.getSession().getPlayerID().replace("-","")))drawString(fontRendererObj,"Current",right-52,y+12,0xff88dcb1);
        }
        if(accounts.size()>visible()){
            int track=Math.max(1,height-132),thumb=Math.max(12,track*visible()/accounts.size());int y=52+(track-thumb)*scroll/Math.max(1,accounts.size()-visible());drawRect(right-2,y,right,y+thumb,0xff70bcff);
        }
        for(var b:buttonList){b.enabled=!manager.busy();if(b.id==3||b.id==4)b.enabled&=selected!=null&&accounts.contains(selected);if(b.id==1||b.id==4||b.id==5)b.enabled&=manager.writable();if(b.id==5)b.enabled&=!accounts.isEmpty();if(b.id==0){b.enabled=true;b.displayString=manager.busy()?"Cancel":"Back";}}
        String status=manager.status();drawCenteredString(fontRendererObj,fontRendererObj.trimStringToWidth(status,width-16),width/2,height-15,0xffa7bbd3);
        super.drawScreen(mouseX,mouseY,ticks);
    }
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{
        super.mouseClicked(x,y,button);if(button!=0||manager.busy()||y<52||y>=height-76||x<width/2-152||x>=width/2+152)return;
        int index=scroll+(y-52)/36;var accounts=manager.accounts();if(index>=accounts.size())return;
        var next=accounts.get(index);long now=System.currentTimeMillis();if(next.equals(selected)&&now-lastClick<250)manager.login(next);selected=next;lastClick=now;
    }
    @Override public void handleMouseInput()throws IOException{super.handleMouseInput();int wheel=Mouse.getEventDWheel();if(wheel!=0){scroll+=wheel>0?-1:1;clamp();}}
    @Override protected void actionPerformed(GuiButton button)throws IOException{
        switch(button.id){
            case 0->{if(manager.busy())manager.cancel();else mc.displayGuiScreen(parent);}
            case 1->mc.displayGuiScreen(new Editor(this,true));
            case 2->mc.displayGuiScreen(new Editor(this,false));
            case 3->{if(selected!=null)manager.login(selected);}
            case 4->{if(selected!=null){manager.remove(selected);selected=null;clamp();}}
            case 5->mc.displayGuiScreen(new ClearConfirmation(this,"Clear saved account list?","The current session will stay logged in.",5));
            case 6->manager.randomOffline(false);
        }
    }
    @Override public void confirmClicked(boolean result,int id){if(result&&id==5){manager.clear();selected=null;scroll=0;}mc.displayGuiScreen(this);}
    @Override protected void keyTyped(char typed,int key)throws IOException{if(key==Keyboard.KEY_ESCAPE){if(manager.busy())manager.cancel();else mc.displayGuiScreen(parent);}else super.keyTyped(typed,key);}
    @Override public void onGuiClosed(){if(manager!=null&&manager.busy())manager.cancel();}

    /** Keep vanilla confirmation lifecycle while using the same Moon menu controls. */
    private static final class ClearConfirmation extends GuiYesNo {
        ClearConfirmation(AccountManagerScreen parent,String title,String detail,int id){super(parent,title,detail,id);}
        @Override public void initGui(){
            super.initGui();
            for(int i=0;i<buttonList.size();i++){
                GuiButton b=buttonList.get(i);
                buttonList.set(i,new MoonMenuButton(b.id,b.xPosition,b.yPosition,b.getButtonWidth(),20,b.displayString));
            }
        }
        @Override protected void keyTyped(char typed,int key)throws IOException{
            if(key==Keyboard.KEY_ESCAPE)parentScreen.confirmClicked(false,parentButtonClickedId);
            else super.keyTyped(typed,key);
        }
    }

    /** Shared Add/Direct Login form: one implementation with an explicit persistence choice. */
    public static final class Editor extends GuiScreen {
        private final AccountManagerScreen parent;private final boolean save;
        private AccountManager manager;private GuiTextField username;private String text="";
        Editor(AccountManagerScreen parent,boolean save){this.parent=parent;this.save=save;}
        @Override public void initGui(){
            manager=AccountManager.get(mc);Keyboard.enableRepeatEvents(true);int x=width/2-130,y=Math.max(56,height/2-60);
            username=new GuiTextField(0,fontRendererObj,x,y+18,260,20);username.setMaxStringLength(16);username.setText(text);username.setFocused(true);
            buttonList.add(new MoonMenuButton(1,x,y+48,260,20,save?"Add Offline":"Login Offline"));
            buttonList.add(new MoonMenuButton(2,x,y+74,260,20,save?"Add Microsoft":"Login with Microsoft"));
            buttonList.add(new MoonMenuButton(3,x,y+100,128,20,"Open Browser"));buttonList.add(new MoonMenuButton(0,x+132,y+100,128,20,"Back"));
        }
        @Override public void drawScreen(int x,int y,float ticks){
            drawGradientRect(0,0,width,height,0xff121724,0xff080b12);int top=Math.max(56,height/2-60);
            drawCenteredString(fontRendererObj,save?"Add Account":"Direct Login",width/2,20,0xffecf3ff);
            drawString(fontRendererObj,"Offline username",width/2-130,top+4,0xffadbed4);username.drawTextBox();
            drawCenteredString(fontRendererObj,"Microsoft login opens your browser.",width/2,top+135,0xffa7bbd3);
            drawCenteredString(fontRendererObj,fontRendererObj.trimStringToWidth(manager.status(),width-16),width/2,height-20,0xff8ec9ff);
            for(var b:buttonList){b.enabled=!manager.busy();if(b.id==3)b.enabled=manager.busy()&&manager.authorization()!=null;if(b.id==0){b.enabled=true;b.displayString=manager.busy()?"Cancel":"Back";}}
            super.drawScreen(x,y,ticks);
        }
        @Override public void updateScreen(){username.updateCursorCounter();}
        @Override protected void actionPerformed(GuiButton button){
            switch(button.id){
                case 0->{if(manager.busy())manager.cancel();else mc.displayGuiScreen(parent);}
                case 1->{if(manager.addOffline(username.getText(),!save,save))mc.displayGuiScreen(parent);}
                case 2->manager.microsoft(!save,save);
                case 3->{try{if(manager.authorization()!=null&&Desktop.isDesktopSupported())Desktop.getDesktop().browse(manager.authorization());}catch(Exception ignored){}}
            }
        }
        @Override protected void mouseClicked(int x,int y,int b)throws IOException{super.mouseClicked(x,y,b);if(!manager.busy())username.mouseClicked(x,y,b);}
        @Override protected void keyTyped(char typed,int key)throws IOException{
            if(key==Keyboard.KEY_ESCAPE){if(manager.busy())manager.cancel();else mc.displayGuiScreen(parent);return;}
            if(manager.busy())return;if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER){actionPerformed(buttonList.getFirst());return;}username.textboxKeyTyped(typed,key);text=username.getText();
        }
        @Override public void onGuiClosed(){text=username.getText();Keyboard.enableRepeatEvents(false);if(manager.busy())manager.cancel();}
    }
}
