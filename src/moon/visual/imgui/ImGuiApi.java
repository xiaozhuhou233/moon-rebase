package moon.visual.imgui;

import imgui.ImGui;
import imgui.type.ImInt;
import imgui.type.ImString;

/** Injectable calls preserve the original ImGui API/return-value distinctions.
* Native delegates to the supplied 1.86.11 library, not a replacement backend. */
public interface ImGuiApi {
    void pushId(long value);
    void pushId(String value);
    void popId();
    boolean checkbox(String label,boolean value);
    boolean itemClicked();
    boolean itemEdited();
    boolean itemActive();
    boolean colorEdit4(String label,float[] value,int flags);
    boolean combo(String label,ImInt selected,String[] labels);
    boolean sliderFloat(String label,float[] value,float minimum,float maximum);
    boolean sliderInt(String label,int[] value,int minimum,int maximum);
    boolean inputText(String label,ImString value);
    boolean treeNode(String label);
    void treePop();
    boolean collapsingHeader(String label);
    float itemWidth();
    boolean beginListBox(String label,float width,float height);
    boolean selectable(String label,boolean selected);
    void endListBox();
    boolean begin(String label);
    void end();
    boolean beginTabBar(String label);
    void endTabBar();
    boolean beginTabItem(String label,int flags);
    void endTabItem();
    void newFrame();
    void render();
    void style();
    final class Native implements ImGuiApi {
        public void pushId(long v) {
            ImGui.pushID(v);
        }
        public void pushId(String v) {
            ImGui.pushID(v);
        }
        public void popId() {
            ImGui.popID();
        }
        public boolean checkbox(String s,boolean v) {
            return ImGui.checkbox(s,v);
        }
        public boolean itemClicked() {
            return ImGui.isItemClicked();
        }
        public boolean itemEdited() {
            return ImGui.isItemEdited();
        }
        public boolean itemActive() {
            return ImGui.isItemActive();
        }
        public boolean colorEdit4(String s,float[] v,int flags) {
            return ImGui.colorEdit4(s,v,flags);
        }
        public boolean combo(String s,ImInt i,String[] labels) {
            return ImGui.combo(s,i,labels);
        }
        public boolean sliderFloat(String s,float[] v,float lo,float hi) {
            return ImGui.sliderFloat(s,v,lo,hi);
        }
        public boolean sliderInt(String s,int[] v,int lo,int hi) {
            return ImGui.sliderInt(s,v,lo,hi);
        }
        public boolean inputText(String s,ImString v) {
            return ImGui.inputText(s,v);
        }
        public boolean treeNode(String s) {
            return ImGui.treeNode(s);
        }
        public void treePop() {
            ImGui.treePop();
        }
        public boolean collapsingHeader(String s) {
            return ImGui.collapsingHeader(s);
        }
        public float itemWidth() {
            return ImGui.calcItemWidth();
        }
        public boolean beginListBox(String s,float w,float h) {
            return ImGui.beginListBox(s,w,h);
        }
        public boolean selectable(String s,boolean v) {
            return ImGui.selectable(s,v);
        }
        public void endListBox() {
            ImGui.endListBox();
        }
        public boolean begin(String s) {
            return ImGui.begin(s);
        }
        public void end() {
            ImGui.end();
        }
        public boolean beginTabBar(String s) {
            return ImGui.beginTabBar(s);
        }
        public void endTabBar() {
            ImGui.endTabBar();
        }
        public boolean beginTabItem(String s,int flags) {
            return ImGui.beginTabItem(s,flags);
        }
        public void endTabItem() {
            ImGui.endTabItem();
        }
        public void newFrame() {
            ImGui.newFrame();
        }
        public void render() {
            ImGui.render();
        }
        public void style() {
            var style=ImGui.getStyle();
            style.setWindowMinSize(550,600);
            style.setFrameRounding(2);
            style.setWindowRounding(8);
            style.setWindowBorderSize(0);
            style.setPopupBorderSize(0);
            style.setAntiAliasedFill(true);
            style.setAntiAliasedLines(true);
        }
    }
}
