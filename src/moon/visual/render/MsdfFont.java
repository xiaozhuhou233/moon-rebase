package moon.visual.render;

import moon.visual.shader.ShaderUniform.MarkerHooks;

/** hU's captured draw/bind branches. Unresolved hU/hY constants are mandatory explicit inputs.
 * migrationDefaults is a host policy, not evidence that those constants were decrypted.
 * The original shared glyph size mutations and per-character shadow ordering are preserved.
 * The supplied loader belongs to this font; sharing the mutable h5 loader across font names
 * replaces its provider/resource and changes existing fonts. Share its texture Host instead.
 */
public final class MsdfFont extends MsdfFontBase {
    public static final class Parameters {
        public final char newline,carriageReturn,tab;
        public final int noPlaneShadow,planeShadow,strikeShadow,underlineShadow;
        public final int textureTarget,minFilter,magFilter;
        public Parameters(char newline,char carriageReturn,char tab,int noPlaneShadow,int planeShadow,
                          int strikeShadow,int underlineShadow,int textureTarget,int minFilter,int magFilter) {
            this.newline=newline;this.carriageReturn=carriageReturn;this.tab=tab;
            this.noPlaneShadow=noPlaneShadow;this.planeShadow=planeShadow;
            this.strikeShadow=strikeShadow;this.underlineShadow=underlineShadow;
            this.textureTarget=textureTarget;this.minFilter=minFilter;this.magFilter=magFilter;
        }
        /** Explicit migration policy until the original encrypted values are recovered. */
        public static Parameters migrationDefaults() {
            return new Parameters('\n','\r','\t',0x80000000,0x80000000,0x80000000,0x80000000,3553,10241,10240);
        }
    }
    private final MsdfMetrics metrics;
    private final MsdfAtlasLoader loader;
    private final float smooth,boldWeight,weight;
    private final MarkerHooks markers;
    private final Parameters parameters;
    public boolean filterDirty;
    public MsdfFont(String name,float smooth,float boldWeight,float weight,MsdfAtlasLoader loader,
                    Parameters parameters,MarkerHooks markers) {
        super(markers);this.loader=loader;this.smooth=smooth;this.boldWeight=boldWeight;this.weight=weight;
        this.markers=markers;this.parameters=parameters;
        loader.load(name);metrics=new CachedMsdfMetrics(loader.getProvider(),MsdfGlyphShape::getSentinel);
    }
    @Override public void draw(DrawTask task,String text,float size,float x,float y,int color,
                               boolean applyColorCodes,boolean shadow,float offset) {
        draw(task,text,size,x,y,color,applyColorCodes,shadow,offset,0F);
    }
    @Override public void draw(DrawTask task,String text,float size,float x,float y,int color,
                               boolean applyColorCodes,boolean shadow,float offset,float smoothingAdjustment) {
        char[] chars=text.toCharArray();
        boolean captured=MsdfGlyphShape.inverse();
        MsdfGlyphProvider provider=loader.getProvider();
        FontRenderContext context=FontRenderContext.builder(markers).characters(chars).initialColor(color)
            .applyColorCodes(applyColorCodes).build();
        MsdfFontData.Metrics data=loader.getData().getMetrics();
        float lineHeight=data.getLineHeight()*size,pen=0;char previous=0;
        // hU 121->1117 returns after setup, without glyph/parse/task operations.
        if(captured) return;
        for(int i=0;i<chars.length;i++) {
            char ch=chars[i];boolean hasPrevious=previous!=0;
            if(ch==parameters.newline||ch==parameters.carriageReturn||ch==parameters.tab) continue;
            if(context.parse(i)) { i++;continue; }
            FontColorState styles=context.getStyles();MsdfGlyphShape glyph=provider.glyph(ch);
            if(glyph==null) continue;
            glyph.setFontSize(size);
            float kern=hasPrevious?provider.kerning(previous,ch):0;
            if(styles.isObfuscated()&&glyph.getGlyph().getPlaneBounds()!=null) {
                MsdfGlyphShape original=glyph;
                do { glyph=provider.randomGlyph(original); } while(glyph==null);
                kern=hasPrevious?provider.kerning(previous,(char)glyph.getGlyph().getUnicode()):0;
                glyph.setFontSize(size);
            }
            if(shadow&&context.isApplyColorCodes()) {
                context.setApplyColorCodes(false);
                task.add(glyph.quad(x+pen+kern*size+.5F,y+.5F,smooth+smoothingAdjustment,boldWeight,offset,weight,true,context,
                    parameters.noPlaneShadow,parameters.planeShadow,markers));
                context.setApplyColorCodes(true);
            }
            task.add(glyph.quad(x+pen+kern*size,y,smooth+smoothingAdjustment,boldWeight,offset,weight,false,context,
                parameters.noPlaneShadow,parameters.planeShadow,markers));
            float advance=i==chars.length-1?glyph.advance()*size:(glyph.advance()+kern)*size;
            if(glyph.getGlyph().getPlaneBounds()!=null) {
                float thickness=data.getUnderlineThickness()*size;
                if(styles.isStrikethrough()) {
                    decoration(task,x+pen,y+lineHeight/2,advance,thickness,shadow,context,parameters.strikeShadow);
                } else if(styles.isUnderlined()) {
                    float underlineOffset=data.getUnderlineY()*size;
                    decoration(task,x+pen,y+lineHeight+underlineOffset*2,advance,thickness,shadow,context,parameters.underlineShadow);
                }
            }
            pen+=advance;previous=ch;
        }
    }
    private void decoration(DrawTask task,float x,float y,float advance,float thickness,boolean shadow,
                            FontRenderContext context,int shadowColor) {
        if(shadow&&context.isApplyColorCodes()) {
            context.setApplyColorCodes(false);
            // Original offsets only left/top; right/bottom are not shifted by 0.5.
            task.add(new UiGeometry.PlaceholderGlyph(x+.5F,y+.5F,x+advance,y+thickness,shadowColor,markers));
            context.setApplyColorCodes(true);
        }
        // Original decorations always use renderedColor, even with applyColorCodes=false.
        task.add(new UiGeometry.PlaceholderGlyph(x,y,x+advance,y+thickness,context.getRenderedColor(),markers));
    }
    @Override public void bind() {
        boolean captured=MsdfGlyphShape.getSentinel();
        loader.getHost().bindResource(loader.getTextureResource());
        if(!captured) {
            // hU 37->101 carries dirty as the target, skips the min-filter call.
            loader.getHost().textureParameter(filterDirty?1:0,parameters.magFilter,9729F);
            filterDirty=false;
            return;
        }
        if(filterDirty) {
            loader.getHost().textureParameter(parameters.textureTarget,parameters.minFilter,9729F);
            loader.getHost().textureParameter(parameters.textureTarget,parameters.magFilter,9729F);
            filterDirty=false;
        }
    }
    public float width(String text,float size) { return metrics.relativeWidth(text)*size; }
    public float secondMetric(String text,float size) { return metrics.relativeHeight(text)*size; }
    public float height(float size) { return metrics.lineHeight()*size; }
    public MsdfMetrics getMetrics() { return metrics; }
    public MsdfAtlasLoader getLoader() { return loader; }
    public float getSmooth() { return smooth; }
    public float getBoldWeight() { return boldWeight; }
    public float getWeight() { return weight; }
    public boolean isFilterDirty() { return filterDirty; }
}
