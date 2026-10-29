package moon.visual.render;

/** hP. The second metric is max glyph plane height plus descender, not line height. */
public interface MsdfMetrics {
    float relativeWidth(String text);
    float relativeHeight(String text);
    float lineHeight();
}
