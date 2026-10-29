package moon.client;

import java.util.*;
import moon.visual.render.*;
import net.minecraft.client.renderer.GlStateManager;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.VarArgFunction;
import org.lwjgl.opengl.*;

/** Destination Lua API adapter. Shapes/fonts use the restored g5/gx/gv pipeline.
 * This implements the target's existing template contract, not original Lua glue.
 * Prepared batches own immutable CPU data; GL buffers belong to shared dispatchers.
 * Rendering uses the current framebuffer, including an effects mask framebuffer.
 */
public final class McpLuaRenderer {
    private static final ThreadLocal<List<Shape>> deferred=new ThreadLocal<>();
    private McpLuaRenderer(){}
    public static void renderCallback(Runnable callback){
        if(McpVisualServices.current()==null){callback.run();return;}
        visual();try(DrawState state=new DrawState()){
            List<Shape> previous=deferred.get();boolean owner=previous==null;if(owner)deferred.set(new ArrayList<>());
            GlStateManager.disableDepth();GlStateManager.enableBlend();GlStateManager.enableAlpha();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
            try{callback.run();}finally{if(owner){List<Shape> queued=deferred.get();deferred.remove();drawNow(queued);}}
        }
    }
    /** Shared HUD state scope; preserves the caller framebuffer and GL bindings. */
    public static void renderHud(Runnable callback){
        try(DrawState state=new DrawState()){
            List<Shape> previous=deferred.get();boolean owner=previous==null;if(owner)deferred.set(new ArrayList<>());
            GlStateManager.disableDepth();GlStateManager.disableAlpha();GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,GL11.GL_ONE,GL11.GL_ONE_MINUS_SRC_ALPHA);
            try{callback.run();}finally{if(owner){List<Shape> queued=deferred.get();deferred.remove();drawNow(queued);}}
        }
    }
    private static void flushPending(){List<Shape> queued=deferred.get();if(queued==null||queued.isEmpty())return;List<Shape> batch=List.copyOf(queued);queued.clear();drawNow(batch);}
    private interface Call {Varargs run(Varargs args);}
    private static LuaValue fn(Call call){return new VarArgFunction(){
        @Override public Varargs invoke(Varargs args){
            try{return call.run(args);}catch(LuaError error){throw error;}
            catch(RuntimeException error){throw new LuaError(error);}
        }
    };}
    private static Varargs strip(Varargs args,LuaTable self){return args.arg1()==self?args.subargs(2):args;}
    private static McpVisualServices visual(){
        McpVisualServices visual=McpVisualServices.current();
        if(visual==null)throw new LuaError("Visual renderer is not initialized");
        visual.backend.requireOpen();return visual;
    }
    private static int kind(LuaValue value){int kind=value.checkint();if(kind<0||kind>5)throw new LuaError("Unknown template type: "+kind);return kind;}
    private static float number(LuaTable value,String key){
        double parsed=value.get(key).checkdouble();float result=(float)parsed;
        if(!Float.isFinite(result))throw new LuaError("Nonfinite template field: "+key);return result;
    }
    private static int color(LuaTable value,String key){
        double parsed=value.get(key).checkdouble();
        if(!Double.isFinite(parsed)||parsed< Integer.MIN_VALUE||parsed>0xffffffffL)throw new LuaError("Invalid ARGB field: "+key);
        return (int)(long)parsed;
    }
    // These names follow LuaApi.enums.custom_font_type's existing zero-based order.
    private static String fontName(int font){return switch(font){
        case 0->"Roboto-Medium";case 1->"inter_medium";case 2->"Tahoma";case 3->"Verdana";
        case 4->throw new LuaError("SF font atlas is absent from the recovered assets");
        case 5->"ProductSans";case 6->"MaterialIcons-Regular";
        case 7->"MaterialSymbolsOutlined-Regular";case 8->"Volte-SemiBold";
        default->throw new LuaError("Unknown custom font: "+font);
    };}
    private record Shape(int kind,float[] coordinates,int color,int to,boolean flag,int font,String text){}
    private static Shape parse(int kind,LuaTable source){
        String[] fields=switch(kind){
            case 0,1->new String[]{"x","y","width","height"};
            case 2->new String[]{"x","y","width","height","radius"};
            case 3->new String[]{"x","y","width","height","radius","thickness"};
            case 4->new String[]{"x","y","size"};
            case 5->new String[]{"top_x","top_y","left_x","left_y","right_x","right_y"};
            default->throw new LuaError("Unknown template type");
        };
        float[] coordinates=new float[fields.length];for(int i=0;i<fields.length;i++)coordinates[i]=number(source,fields[i]);
        int font=kind==4?source.get("font_type").checkint():0;
        if(kind==4){fontName(font);if(coordinates[2]<=0)throw new LuaError("Text size must be positive");}
        return new Shape(kind,coordinates,color(source,kind==1?"from":"color"),kind==1?color(source,"to"):0,
            kind==1?source.get("vertical").checkboolean():kind==4&&source.get("shadow").checkboolean(),font,
            kind==4?source.get("text").checkjstring():null);
    }
    private static List<Shape> parseList(int kind,LuaTable source){
        List<Shape> result=new ArrayList<>();for(int i=1;i<=source.length();i++)result.add(parse(kind,source.get(i).checktable()));return result;
    }
    public static LuaTable renderTable(){
        LuaTable render=new LuaTable(),batched=new LuaTable();
        batched.set("render_batch",fn(args->{Varargs a=strip(args,batched);draw(parseList(kind(a.arg1()),a.arg(2).checktable()));return LuaValue.NONE;}));
        batched.set("prepare_batch",fn(args->{
            Varargs a=strip(args,batched);int kind=kind(a.arg1());
            List<Shape> shapes=a.arg(2).isnil()?new ArrayList<>():parseList(kind,a.arg(2).checktable());
            LuaTable batch=new LuaTable();
            batch.set("add",fn(inner->{shapes.add(parse(kind,strip(inner,batch).arg1().checktable()));return batch;}));
            batch.set("render",fn(inner->{draw(List.copyOf(shapes));return LuaValue.NONE;}));
            batch.set("clear",fn(inner->{shapes.clear();return batch;}));
            return batch;
        }));
        render.set("batched",batched);
        LuaTable immediate=new LuaTable();
        String[] names={"rect","gradient_rect","rounded_rect","outline_rounded_rect","text","triangle"};
        String[][] keys={{"x","y","width","height","color"},{"x","y","width","height","from","to","vertical"},
            {"x","y","width","height","radius","color"},{"x","y","width","height","radius","thickness","color"},
            {"font_type","text","size","x","y","color","shadow"},{"top_x","top_y","left_x","left_y","right_x","right_y","color"}};
        for(int i=0;i<names.length;i++){
            int selected=i;immediate.set(names[i],fn(args->{
                Varargs a=strip(args,immediate);LuaTable template=new LuaTable();
                for(int j=0;j<keys[selected].length;j++)template.set(keys[selected][j],a.arg(j+1));
                draw(List.of(parse(selected,template)));return LuaValue.NONE;
            }));
        }
        immediate.set("minecraft_text",fn(args->{
            Varargs a=strip(args,immediate);String text=a.checkjstring(1);float x=(float)a.checkdouble(2),y=(float)a.checkdouble(3);
            if(!Float.isFinite(x)||!Float.isFinite(y))throw new LuaError("Nonfinite text position");
            LuaTable value=new LuaTable();value.set("color",a.arg(4));int color=color(value,"color");
            flushPending();McpVisualServices v=visual();try(DrawState state=new DrawState()){
                v.mc.fontRendererObj.drawString(text,x,y,color,a.arg(5).optboolean(false));
            }return LuaValue.NONE;
        }));
        render.set("immediate",immediate);
        LuaTable state=new LuaTable();
        for(String name:new String[]{"enable_depth","disable_depth","enable_blend","disable_blend","blend_func","blend_func_separate"})
            state.set(name,fn(args->{
                flushPending();visual();Varargs a=strip(args,state);
                switch(name){
                    case "enable_depth"->GlStateManager.enableDepth();case "disable_depth"->GlStateManager.disableDepth();
                    case "enable_blend"->GlStateManager.enableBlend();case "disable_blend"->GlStateManager.disableBlend();
                    case "blend_func"->GlStateManager.blendFunc(a.checkint(1),a.checkint(2));
                    case "blend_func_separate"->GlStateManager.tryBlendFuncSeparate(a.checkint(1),a.checkint(2),a.checkint(3),a.checkint(4));
                }return LuaValue.NONE;
            }));
        render.set("state",state);render.set("implemented",LuaValue.TRUE);
        LuaTable fonts=new LuaTable();for(int i=0;i<=8;i++)fonts.set(i,LuaValue.valueOf(i!=4));render.set("supported_fonts",fonts);
        return render;
    }
    public static LuaTable fontTable(){
        LuaTable table=new LuaTable();
        table.set("width",fn(args->{Varargs a=strip(args,table);float size=(float)a.arg(2).optdouble(9);
            checkSize(size);return LuaValue.valueOf(visual().font(fontName(a.checkint(1))).width(a.arg(3).optjstring(""),size));}));
        table.set("height",fn(args->{Varargs a=strip(args,table);float size=(float)a.arg(2).optdouble(9);
            checkSize(size);return LuaValue.valueOf(visual().font(fontName(a.checkint(1))).height(size));}));
        table.set("minecraft_width",fn(args->LuaValue.valueOf(visual().mc.fontRendererObj.getStringWidth(strip(args,table).checkjstring(1)))));
        table.set("minecraft_height",fn(args->LuaValue.valueOf(visual().mc.fontRendererObj.FONT_HEIGHT)));
        return table;
    }
    private static void checkSize(float value){if(!Float.isFinite(value)||value<=0)throw new LuaError("Text size must be finite and positive");}
    private static void draw(List<Shape> shapes){
        if(shapes.isEmpty())return;List<Shape> queue=deferred.get();if(queue!=null){for(Shape shape:shapes)if(shape.kind==4)visual().font(fontName(shape.font));queue.addAll(shapes);return;}drawNow(shapes);
    }
    private static void drawNow(List<Shape> shapes){
        McpVisualServices v=visual();if(shapes.isEmpty())return;
        try(DrawState state=new DrawState()){
            // Resolve every font before drawing, so a missing font cannot partially draw a batch.
            Map<Integer,MsdfFontBase> fonts=new HashMap<>();
            for(Shape shape:shapes)if(shape.kind==4)fonts.computeIfAbsent(shape.font,key->v.font(fontName(key)));
            for(int first=0;first<shapes.size();){
                Shape head=shapes.get(first);int end=first+1;
                while(end<shapes.size()&&shapes.get(end).kind==head.kind&&(head.kind!=4||shapes.get(end).font==head.font))end++;
                ShaderDispatcher dispatcher=switch(head.kind){case 1->v.gradient;case 2->v.rounded;case 3->v.outline;case 4->v.msdf;default->v.rectangle;};
                DrawTaskBuilder builder=DrawTask.builder(v.draw).dispatcher(dispatcher).retained();
                MsdfFontBase font=fonts.get(head.font);if(head.kind==4)builder.texture(font::bind);
                DrawTask task=builder.build();
                try{
                    if(head.kind==5)task.getState().setPrimitive(GL11.GL_TRIANGLES);
                    for(int i=first;i<end;i++){
                        Shape shape=shapes.get(i);float[] c=shape.coordinates;
                        switch(shape.kind){
                            case 0->task.add(new UiGeometry.Rectangle(c[0],c[1],c[2],c[3],shape.color));
                            case 1->task.add(new UiGeometry.GradientRectangle(c[0],c[1],c[2],c[3],shape.color,shape.to,shape.flag,v.profile.gradientEstimate,v.hooks));
                            case 2->task.add(new UiGeometry.RoundedRectangle(c[0],c[1],c[2],c[3],c[4],shape.color,v.hooks));
                            case 3->task.add(new UiGeometry.OutlineRectangle(c[0],c[1],c[2],c[3],c[4],c[5],shape.color,v.profile.outlineEstimate));
                            case 4->font.draw(task,shape.text,c[2],c[0],c[1],shape.color,true,shape.flag,0);
                            case 5->task.add(UiGeometry.Triangle.create(c[0],c[1],c[2],c[3],c[4],c[5],shape.color,72,v.hooks));
                        }
                    }
                    task.draw();
                }finally{task.getState().release();}
                first=end;
            }
        }
    }
    /** Restore the MCP cache as well as GL state; never bind the main FBO here. */
    static final class DrawState implements AutoCloseable {
        private final boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),blend=GL11.glIsEnabled(GL11.GL_BLEND),alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        private final int src=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dst=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
            srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
            alphaFunc=GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC),program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
            vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),buffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),
            active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        private final float reference=GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        private final float[] rgba=new float[4];
        private final int texture;private final boolean textured;
        DrawState(){
            GL11.glGetFloatv(GL11.GL_CURRENT_COLOR,rgba);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);texture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);textured=GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        }
        @Override public void close(){
            if(depth)GlStateManager.enableDepth();else GlStateManager.disableDepth();
            if(blend)GlStateManager.enableBlend();else GlStateManager.disableBlend();
            if(alpha)GlStateManager.enableAlpha();else GlStateManager.disableAlpha();
            GlStateManager.tryBlendFuncSeparate(src,dst,srcAlpha,dstAlpha);GlStateManager.alphaFunc(alphaFunc,reference);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);GlStateManager.bindTexture(texture);
            if(textured)GlStateManager.enableTexture2D();else GlStateManager.disableTexture2D();
            GlStateManager.setActiveTexture(active);GL20.glUseProgram(program);GL30.glBindVertexArray(vao);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,buffer);
            GlStateManager.color(rgba[0],rgba[1],rgba[2],rgba[3]);
        }
    }
}
