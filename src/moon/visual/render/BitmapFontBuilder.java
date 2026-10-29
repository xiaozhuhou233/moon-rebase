package moon.visual.render;

import moon.visual.shader.ShaderUniform.MarkerHooks;

/** hJ and its sole bm.BITMAP variant; the environment is supplied by the migration host. */
public final class BitmapFontBuilder {
    public enum Type { BITMAP }
    private final String name;
    private final float size,scale;
    private BitmapFontBuilder(String name,float size,float scale) {
        this.name=name; this.size=size; this.scale=scale;
    }
    public static BitmapFontBuilder create(String name,float size,float scale) {
        return new BitmapFontBuilder(name,size,scale);
    }
    public BitmapFontFace build(Type type,BitmapAtlasLoader loader,BitmapAtlasFont.Host host,MarkerHooks markers) {
        switch(type) {
            case BITMAP: return new BitmapAtlasFont(name,size,scale,loader,host,markers);
            default: throw new IncompatibleClassChangeError();
        }
    }
    public static BitmapFontFamily.Factory factory(BitmapAtlasLoader loader,BitmapAtlasFont.Host host,MarkerHooks markers) {
        return (name,size,scale)->create(name,size,scale).build(Type.BITMAP,loader,host,markers);
    }
}
