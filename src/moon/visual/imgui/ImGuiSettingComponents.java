package moon.visual.imgui;

import java.awt.Color;
import java.util.*;
import java.util.function.DoubleSupplier;
import imgui.type.ImInt;
import imgui.type.ImString;
import moon.visual.gui.*;

/** kC..kI/kK/kL normal nonnull-kL-sentinel paths, using existing restored settings.
* Text capacity is an explicit unresolved kL.f input. Original random IDs,
* no-finally ID/tree stacks and Color(int)'s discarded alpha are preserved. */
public final class ImGuiSettingComponents {
    private static String sentinel="xIHhn";
    public static String sentinel() {
        return sentinel;
    }
    public static void sentinel(String value) {
        sentinel=value;
    }
    static void normal() {
        if(sentinel==null)throw new UnsupportedOperationException("ImGui kL null-sentinel path not restored");
    }
    public static final class Parameters {
        public final int textCapacity;
        public final String enabledLabel;
        public Parameters(int textCapacity,String enabledLabel) {
            this.textCapacity=textCapacity;
            this.enabledLabel=enabledLabel;
        }
    }
    final ImGuiApi api;
    final Parameters parameters;
    final DoubleSupplier random;
    private final java.util.function.Function<EnumSetting<?>,String[]> choiceLabels;
    /** Default uses the original random source; injection is for deterministic checks. */
    public ImGuiSettingComponents(ImGuiApi api,Parameters parameters) {
        this(api,parameters,Math::random);
    }
    public ImGuiSettingComponents(ImGuiApi api,Parameters parameters,DoubleSupplier random) {
        this(api,parameters,random,s->labels(s.values()));
    }
    /** Destination-only enum label policy; default constructors retain the original behavior. */
    public ImGuiSettingComponents(ImGuiApi api,Parameters parameters,DoubleSupplier random,
            java.util.function.Function<EnumSetting<?>,String[]> choiceLabels) {
        this.api=api;
        this.parameters=parameters;
        this.random=random;
        this.choiceLabels=java.util.Objects.requireNonNull(choiceLabels);
    }
    long randomId() {
        return Math.round(random.getAsDouble()*1.0E9);
    }
    public ImGuiComponent create(SettingValue<?> setting) {
        normal();
        if(setting instanceof SettingGroup s)return new Group(s);
        if(setting instanceof BooleanSetting s)return new Toggle(s);
        if(setting instanceof IntegerSetting s)return new IntegerSlider(s);
        if(setting instanceof FloatSetting s)return new FloatSlider(s);
        if(setting instanceof ColorSetting s)return new ColorEditor(s);
        if(setting instanceof EnumSetting<?> s)return new Choice(s);
        if(setting instanceof ModeSetting<?> s)return new Mode(s);
        if(setting instanceof MultiChoiceSetting<?> s)return new Multiple(s);
        if(setting instanceof StringSetting s)return new Text(s);
        return null;
        // Original factory has no button/list/Bezier fallback.
    }
    public abstract class Leaf implements ImGuiComponent {
        public final SettingValue<?> setting;
        long id;
        Leaf(SettingValue<?> setting) {
            this.setting=setting;
        }
        public long id() {
            return id;
        }
        public void initialize() {
            normal();
            id=randomId();
        }
        boolean visible() {
            normal();
            return setting.isVisible();
        }
    }
    public final class Toggle extends Leaf {
        private final BooleanSetting value;
        Toggle(BooleanSetting s) {
            super(s);
            value=s;
        }
        public void render() {
            if(!visible())return;
            api.pushId(id);
            api.checkbox(value.getDisplayName(),value.get());
            if(api.itemClicked())value.set(!value.get());
            api.popId();
        }
    }
    public final class ColorEditor extends Leaf {
        private final ColorSetting value;
        private final float[] rgba=new float[4];
        ColorEditor(ColorSetting s) {
            super(s);
            value=s;
        }
        private void sync() {
            Color c=new Color(value.get());
            rgba[0]=c.getRed()/255F;
            rgba[1]=c.getGreen()/255F;
            rgba[2]=c.getBlue()/255F;
            rgba[3]=c.getAlpha()/255F;
        }
        public void initialize() {
            normal();
            sync();
            id=randomId();
        }
        public void render() {
            if(!visible())return;
            api.pushId(id);
            sync();
            api.colorEdit4(value.getDisplayName(),rgba,2);
            if(api.itemEdited())value.set(new Color(rgba[0],rgba[1],rgba[2],rgba[3]).getRGB());
            api.popId();
        }
    }
    public final class FloatSlider extends Leaf {
        private final FloatSetting value;
        private final float[] buffer=new float[1];
        FloatSlider(FloatSetting s) {
            super(s);
            value=s;
        }
        public void initialize() {
            normal();
            buffer[0]=value.get();
            id=randomId();
        }
        public void render() {
            if(!visible())return;
            api.pushId(id);
            buffer[0]=value.get();
            api.sliderFloat(value.getDisplayName(),buffer,value.getMinimum(),value.getMaximum());
            if(api.itemEdited())value.set(buffer[0]);
            api.popId();
        }
    }
    public final class IntegerSlider extends Leaf {
        private final IntegerSetting value;
        private final int[] buffer=new int[1];
        IntegerSlider(IntegerSetting s) {
            super(s);
            value=s;
        }
        public void initialize() {
            normal();
            buffer[0]=value.get();
            id=randomId();
        }
        public void render() {
            if(!visible())return;
            api.pushId(id);
            buffer[0]=value.get();
            api.sliderInt(value.getDisplayName(),buffer,value.getMinimum(),value.getMaximum());
            if(api.itemEdited())value.setFromDrag((float)buffer[0]);
            api.popId();
        }
    }
    private static String[] labels(Enum<?>[] values) {
        List<String> labels=new ArrayList<>();
        for(Enum<?> value:values)if(value instanceof DisplayNamed named)labels.add(named.displayName());
        return labels.toArray(new String[0]);
    }
    public final class Choice extends Leaf {
        private final EnumSetting<?> value;
        private final ImInt selected=new ImInt();
        private String[] labels;
        Choice(EnumSetting<?> s) {
            super(s);
            value=s;
        }
        public void initialize() {
            normal();
            labels=choiceLabels.apply(value);
            id=randomId();
        }
        public void render() {
            if(!visible())return;
            api.pushId(id);
            selected.set(value.get().ordinal());
            api.combo(value.getDisplayName(),selected,labels);
            if(api.itemEdited())assign(value,value.values()[selected.get()]);
            api.popId();
        }
    }
    public final class Mode extends Leaf {
        private final ModeSetting<?> value;
        private final ImInt selected=new ImInt();
        private String[] labels;
        Mode(ModeSetting<?> s) {
            super(s);
            value=s;
        }
        public void initialize() {
            normal();
            labels=labels(value.values());
            id=randomId();
        }
        public void render() {
            if(!visible())return;
            selected.set(value.get().ordinal());
            api.pushId(id);
            api.combo(value.getDisplayName(),selected,labels);
            if(api.itemEdited())assign(value,value.values()[selected.get()]);
            api.popId();
        }
    }
    @SuppressWarnings({"unchecked","rawtypes"})private static void assign(SettingValue value,Object next) {
        value.set(next);
    }
    public final class Multiple extends Leaf {
        private final MultiChoiceSetting<?> value;
        private final ImInt unusedSelected=new ImInt();
        private String[] labels;
        Multiple(MultiChoiceSetting<?> s) {
            super(s);
            value=s;
        }
        public void initialize() {
            normal();
            labels=labels(value.values());
            id=randomId();
        }
        @SuppressWarnings({"rawtypes","unchecked"})public void render() {
            if(!visible())return;
            api.pushId(id);
            if(api.beginListBox(value.getDisplayName(),api.itemWidth(),85F)) {
                for(int i=0;i<labels.length;i++) {
                    Enum option=value.values()[i];
                    MultiChoiceSetting raw=value;
                    if(api.selectable(labels[i],raw.isSelected(option)))raw.toggle(option);
                }
                api.endListBox();
            }
            api.popId();
        }
    }
    public final class Text extends Leaf {
        private final StringSetting value;
        private final ImString buffer;
        Text(StringSetting s) {
            super(s);
            buffer=new ImString(parameters.textCapacity);
            value=s;
        }
        public void initialize() {
            normal();
            buffer.set(value.get());
            id=randomId();
        }
        public void render() {
            if(!visible())return;
            api.pushId(id);
            buffer.set(value.get());
            api.inputText(value.getDisplayName(),buffer);
            if(api.itemEdited())value.set(buffer.get());
            api.popId();
        }
    }
    public final class Group extends Leaf {
        private final SettingGroup value;
        private final List<ImGuiComponent> children=new ArrayList<>();
        Group(SettingGroup s) {
            super(s);
            value=s;
        }
        public void initialize() {
            normal();
            for(SettingValue<?> child:value.get()) {
                ImGuiComponent component=create(child);
                if(component!=null)children.add(component);
            }
            for(ImGuiComponent child:children)child.initialize();
            id=randomId();
        }
        // No clear; unused group ID is original.
        public void render() {
            if(!visible())return;
            if(api.treeNode(value.getDisplayName())) {
                for(ImGuiComponent child:children)child.render();
                api.treePop();
            }
        }
        public List<ImGuiComponent> children() {
            return children;
        }
    }
}
