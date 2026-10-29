package moon.visual.render;

import moon.visual.shader.ShaderUniform.MarkerHooks;

/** hQ. Captured hT guard controls equality, including the original cast/NPE path.
 * Five independent flags; color and reset codes clear all five. Labels remain a host policy.
 */
public class FontColorState {
    private boolean bold,italic,obfuscated,strikethrough,underlined;
    private final MarkerHooks markers;
    public FontColorState(MarkerHooks markers) { this.markers=markers; }
    public void reset() { bold=italic=obfuscated=strikethrough=underlined=false; }
    public boolean isBold() { return bold; }
    public boolean isItalic() { return italic; }
    public boolean isObfuscated() { return obfuscated; }
    public boolean isStrikethrough() { return strikethrough; }
    public boolean isUnderlined() { return underlined; }
    public FontColorState setBold(boolean value) { bold=value; return this; }
    public FontColorState setItalic(boolean value) { italic=value; return this; }
    public FontColorState setObfuscated(boolean value) { obfuscated=value; return this; }
    public FontColorState setStrikethrough(boolean value) { strikethrough=value; return this; }
    public FontColorState setUnderlined(boolean value) { underlined=value; return this; }
    protected boolean canEqual(Object value) { return value instanceof FontColorState; }
    @Override public boolean equals(Object value) {
        int captured=MsdfFontBase.getSentinel();
        if(captured!=0) {
            FontColorState other=(FontColorState)value;
            // hQ 73->98->133->163->193->235 carries canEqual and other.bold.
            return other.canEqual(this)==other.isBold();
        }
        if(value==this) return true;
        if(!(value instanceof FontColorState)) return false;
        FontColorState other=(FontColorState)value;
        return other.canEqual(this)&&isBold()==other.isBold()&&isItalic()==other.isItalic()
            &&isObfuscated()==other.isObfuscated()&&isStrikethrough()==other.isStrikethrough()
            &&isUnderlined()==other.isUnderlined();
    }
    @Override public int hashCode() {
        int captured=MsdfFontBase.getSentinel(),result=1;
        // hQ's recovered numeric table uses 79/97, including for the first flag.
        result=result*59+(captured!=0?(isBold()?1:0):(isBold()?79:97));
        result=result*59+(captured!=0?(isItalic()?1:0):(isItalic()?79:97));
        result=result*59+(captured!=0?(isObfuscated()?1:0):(isObfuscated()?79:97));
        result=result*59+(captured!=0?(isStrikethrough()?1:0):(isStrikethrough()?79:97));
        result=result*59+(captured!=0?(isUnderlined()?1:0):(isUnderlined()?79:97));
        if(markers.getMarker()!=null) MsdfFontBase.setSentinel(captured+1);
        return result;
    }
    @Override public String toString() {
        return "FontColorState(bold="+isBold()+", italic="+isItalic()+", obfuscated="+isObfuscated()
            +", strikethrough="+isStrikethrough()+", underlined="+isUnderlined()+")";
    }
}
