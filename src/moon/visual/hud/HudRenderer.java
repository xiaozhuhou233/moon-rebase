package moon.visual.hud;

import moon.visual.render.DrawTask;

/** f1's five rendering callbacks. Its color helper is extracted as HudColors,
 * whose unconfirmed numeric parameters remain explicit at the host boundary. */
public interface HudRenderer {
    void prepareRectangles(DrawTask task); // a(gv)
    void prepareBackground(DrawTask task); // b(gv)
    void prepareMask(DrawTask task);       // c(gv)
    void prepareText(DrawTask task);       // d(gv)
    void renderAfterTasks();               // a()
}
