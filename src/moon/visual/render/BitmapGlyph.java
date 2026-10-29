package moon.visual.render;

import com.google.gson.annotations.SerializedName;

/** hM. Atlas coordinates and dimensions are integer pixels, not scaled GUI units. */
public final class BitmapGlyph {
    @SerializedName("id") private int id;
    @SerializedName("x") private int x;
    @SerializedName("y") private int y;
    @SerializedName("w") private int width;
    @SerializedName("h") private int height;

    public int getId() { return id; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public BitmapGlyph setId(int value) { id=value; return this; }
    public BitmapGlyph setX(int value) { x=value; return this; }
    public BitmapGlyph setY(int value) { y=value; return this; }
    public BitmapGlyph setWidth(int value) { width=value; return this; }
    public BitmapGlyph setHeight(int value) { height=value; return this; }
}
