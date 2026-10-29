package moon.visual.render;

import java.util.Arrays;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** hR and hS. applyColorCodes selects the final draw color; it does not disable parsing. */
public final class FontRenderContext {
    private static final String FORMATS="0123456789abcdefklmnor";
    private static final int[] PALETTE=new int[32];
    static {
        for(int i=0;i<32;i++) {
            int base=(i>>3&1)*85,r=(i>>2&1)*170+base,g=(i>>1&1)*170+base,b=(i&1)*170+base;
            if(i==6) r+=85;
            if(i>=16) { r/=4;g/=4;b/=4; }
            PALETTE[i]=r<<16|g<<8|b;
        }
    }
    private final FontColorState styles;
    private final char[] characters;
    private final int initialColor;
    private int renderedColor;
    private boolean applyColorCodes;
    private FontRenderContext(char[] chars,int initial,int rendered,boolean apply,MarkerHooks markers) {
        characters=chars;initialColor=initial;renderedColor=rendered;applyColorCodes=apply;
        styles=new FontColorState(markers);
    }
    public boolean parse(int index) {
        // At hR 0x035/0x1f2 the nondefault path returns the color as a JVM boolean.
        if(MsdfFontBase.inverse()==0) return (renderedColor&1)!=0;
        if(renderedColor==0) renderedColor=initialColor;
        if(characters[index]!=167||index+1>=characters.length) return false;
        int format=FORMATS.indexOf(characters[index+1]);
        if(format<16) {
            styles.reset();
            if(format<0) format=15;
            renderedColor=PALETTE[format]+((initialColor>>24&255)<<24);
        } else switch(format) {
            case 16: styles.setObfuscated(true);break;
            case 17: styles.setBold(true);break;
            case 18: styles.setStrikethrough(true);break;
            case 19: styles.setUnderlined(true);break;
            case 20: styles.setItalic(true);break;
            case 21: renderedColor=initialColor;styles.reset();break;
            default: break;
        }
        return true;
    }
    public FontColorState getStyles() { return styles; }
    public char[] getCharacters() { return characters; }
    public int getInitialColor() { return initialColor; }
    public int getRenderedColor() { return renderedColor; }
    public boolean isApplyColorCodes() { return applyColorCodes; }
    public FontRenderContext setApplyColorCodes(boolean value) { applyColorCodes=value;return this; }
    public static Builder builder(MarkerHooks markers) { return new Builder(markers); }
    public static final class Builder {
        private char[] characters;
        private int initialColor,renderedColor;
        private boolean applyColorCodes;
        private final MarkerHooks markers;
        private Builder(MarkerHooks markers) { this.markers=markers; }
        public Builder characters(char[] value) { characters=value;return this; }
        public Builder initialColor(int value) { initialColor=value;return this; }
        public Builder renderedColor(int value) { renderedColor=value;return this; }
        public Builder applyColorCodes(boolean value) { applyColorCodes=value;return this; }
        public FontRenderContext build() { return new FontRenderContext(characters,initialColor,renderedColor,applyColorCodes,markers); }
        @Override public String toString() {
            return "FontRenderContext.FontRenderContextBuilder(characters="+Arrays.toString(characters)
                +", initialColor="+initialColor+", renderedColor="+renderedColor+", applyColorCodes="+applyColorCodes+")";
        }
    }
}
