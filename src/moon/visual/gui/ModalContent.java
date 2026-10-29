package moon.visual.gui;

/** ku: modal content lifecycle, independently of the still-unrestored kr screen wrapper. */
public class ModalContent {
    public interface Scheme { int background();int curve();int accent(); }
    protected float x,y,width,height;
    protected Scheme scheme;
    private static int[] sentinel;
    public void initialize() { }
    public void update(int mouseX,int mouseY) { }
    public void mouseClicked(int mouseX,int mouseY,int button) { }
    public void mouseReleased(int mouseX,int mouseY,int button) { }
    public void keyTyped(char character,int key) { }
    public ModalContent setX(float value) { x=value;return this; }
    public ModalContent setY(float value) { y=value;return this; }
    public ModalContent setWidth(float value) { width=value;return this; }
    public ModalContent setHeight(float value) { height=value;return this; }
    public ModalContent setScheme(Scheme value) { scheme=value;return this; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }
    public Scheme getScheme() { return scheme; }
    public static int[] getSentinel() { return sentinel; }
    public static void setSentinel(int[] value) { sentinel=value; }
}
