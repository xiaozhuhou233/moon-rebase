package moon.client;

import moon.visual.render.DrawTask;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/** Readable com.moon.kM menu button, with Minecraft input/sound dispatch only. */
public final class MoonMenuButton extends GuiButton {
    public MoonMenuButton(int id,int x,int y,int width,int height,String label) {
        super(id,x,y,width,height,label);
    }
    private boolean contains(int x,int y) {
        return x>=xPosition&&x<=xPosition+width&&y>=yPosition&&y<=yPosition+height;
    }
    @Override public boolean mousePressed(Minecraft mc,int x,int y) {
        return visible&&enabled&&contains(x,y);
    }
    @Override public void drawButton(Minecraft mc,int mouseX,int mouseY) {
        if(!visible)return;
        hovered=contains(mouseX,mouseY);
        McpVisualServices visual=McpVisualServices.current();
        if(visual==null)return; // Visual services are created before the main menu.
        visual.renderFrame(()->McpLuaRenderer.renderHud(()->{
            var scheme=visual.uiColor(visual.clickGuiConfig().colorArgb());
            int fill=scheme.accent(),ink=scheme.onPrimary();
            // kM/Y replace alpha with 0.6, retaining RGB; hover leaves text opaque.
            if(hovered||!enabled)fill=(fill&0x00ffffff)|0x99000000;
            if(!enabled)ink=(ink&0x00ffffff)|0x99000000;
            var drawing=visual.drawing();
            DrawTask shape=drawing.roundedTask();
            shape.add(drawing.rounded(xPosition,yPosition,width,height,height/2F-1,fill));
            shape.draw();
            var font=visual.robotoMedium();float size=8.25F;
            DrawTask text=drawing.msdfTask(font);
            font.draw(text,displayString,size,xPosition+width/2F-font.width(displayString,size)/2,
                    yPosition+height/2F-font.height(size)/2+.5F,ink,false);
            text.draw();
        }));
    }
}
