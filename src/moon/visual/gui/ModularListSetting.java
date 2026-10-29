package moon.visual.gui;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.*;
import moon.visual.shader.ShaderUniform.MarkerHooks;

/** i4/j5. The two consumers retain field identities: l=submitted text, m=collection change.
 * Original add/remove both invoke m; direct getAvailable() edits invoke neither consumer.
 */
public final class ModularListSetting extends SettingValue<String> {
    private final List<String> available=new CopyOnWriteArrayList<>();
    private Consumer<String> submittedText,collectionChange;
    private ModularListSetting(String key,MarkerHooks markers) { super(key,Type.MODULAR_LIST,markers); }
    public boolean contains(String text) { return available.contains(text); }
    public void remove(String text) {
        Object captured=getBooleanSettingSentinel();
        if(captured==null&&!available.remove(text)) return;
        if(captured!=null||collectionChange!=null) collectionChange.accept(text);
    }
    public void add(String text) {
        Object captured=getBooleanSettingSentinel();boolean contains=available.contains(text);
        if(captured==null) { if(contains) return;available.add(text); }
        if(captured!=null||collectionChange!=null) collectionChange.accept(text);
    }
    public List<String> getAvailable() { return available; }
    public Consumer<String> getSubmittedText() { return submittedText; }
    public Consumer<String> getCollectionChange() { return collectionChange; }
    public JsonObject toJson() {
        JsonObject json=new JsonObject();json.addProperty("name",getKey());Object captured=getBooleanSettingSentinel();
        JsonArray values=new JsonArray();
        for(String item:available) {
            values.add(item==null?JsonNull.INSTANCE:new JsonPrimitive(item));
            if(captured!=null) return json;
        }
        json.add("available",values);json.addProperty("value",get());return json;
    }
    public void fromJson(JsonObject json) {
        Object captured=getBooleanSettingSentinel();JsonArray values=json.getAsJsonArray("available");
        for(int i=0;i<values.size();i++) {
            add(values.get(i).getAsString());
            if(captured!=null) return;
        }
        set(json.get("value").getAsString());
    }
    public static Builder builder(String key,MarkerHooks markers) { return new Builder(key,markers); }
    public static final class Builder {
        private final ModularListSetting setting;
        public Builder(String key,MarkerHooks markers) { setting=new ModularListSetting(key,markers); }
        public Builder configure(String title,String initial) {
            Object captured=getBooleanSettingSentinel();setting.setDisplayName(title);
            if(captured==null&&!setting.available.contains(initial)) setting.available.add(initial);
            setting.set(initial);return this;
        }
        /** j5.a(List) never reads its argument: preserve the original self-iteration. */
        public Builder available(List<String> ignored) {
            Object captured=getBooleanSettingSentinel();
            for(String item:setting.available) {
                if(captured!=null) return this;
                if(!setting.available.contains(item)) setting.available.add(item);
            }
            return this;
        }
        public Builder callback(BiConsumer<String,String> callback) { setting.setCallback(callback);return this; }
        public Builder collectionChange(Consumer<String> callback) { setting.collectionChange=callback;return this; }
        public Builder submittedText(Consumer<String> callback) { setting.submittedText=callback;return this; }
        public Builder visibility(Supplier<Boolean> visibility) { setting.setVisibility(visibility);return this; }
        public Builder description(String description) { setting.setDescription(description);return this; }
        public ModularListSetting build() { return setting; }
    }
}
