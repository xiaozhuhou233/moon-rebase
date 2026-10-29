package moon.visual.render;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.lwjglx.util.vector.Vector4f;

/** fQ/fS/fR and fL/fT/fU/fV. Uses the actual restored font APIs; game getters
 * and type casts are kept live through Host, rather than captured snapshots. */
public final class EntityEspTextComponents {
    private EntityEspTextComponents() {}
    public enum Flag { BLOCK,SNEAK,LOW_HP }
    public interface Settings {
        boolean statusEnabled();boolean flag(Flag flag);
        boolean nameEnabled();boolean unformattedName();int nameColor();
        boolean itemEnabled();boolean itemNameEnabled();boolean armorEnabled();
    }
    public interface Friend {
        String alias();String originalName();
    }
    public interface Item { String displayName(); }
    public interface Host<E> extends EntityEspComponent.Host<E> {
        BitmapFontFamily bitmapFont();MsdfFontBase verdana();
        boolean isLiving(E entity);boolean isPlayer(E entity);
        /** Correspond to checkcast; null is returned unchanged, incompatible types throw. */
        E requireLiving(E entity);E requirePlayer(E entity);
        boolean blocking(E player);boolean sneaking(E player);
        float health(E living);float maxHealth(E living);
        String rawName(E living);String formattedName(E living);
        Friend friend(E player);Object target(E player);Item heldItem(E player);
    }
    /** fL. The raw endpoint retains the noncanonical dcmpg return of fV. */
    public interface Label<E> {
        int acceptsRaw(E entity);String text(E entity);
        default boolean accepts(E entity) { return acceptsRaw(entity)!=0; }
    }
    public static final class Labels {
        public final String blocking,sneaking,lowHealth;
        /** fT/fU offline candidates are not runtime evidence; fV remains unresolved. */
        public Labels(String blocking,String sneaking,String lowHealth) {
            this.blocking=blocking;this.sneaking=sneaking;this.lowHealth=lowHealth;
        }
    }
    public static final class Status<E> extends EntityEspComponent<E,Settings> {
        private final EntityEspTextComponents.Host<E> textHost;
        private final List<Label<E>> labels;
        public Status(Settings settings,EntityEspTextComponents.Host<E> host,Labels values) {
            super(settings,host);textHost=host;
            labels=Collections.unmodifiableList(Arrays.asList(new StateLabel(Flag.BLOCK,values.blocking),
                    new StateLabel(Flag.SNEAK,values.sneaking),new StateLabel(Flag.LOW_HP,values.lowHealth)));
        }
        public boolean accepts(E entity) { return getSettings().statusEnabled(); }
        public List<Label<E>> getLabels() { return labels; }
        public void draw(E entity) {
            int marker=host.componentMarker();
            BitmapFontFamily font=textHost.bitmapFont();
            Vector4f bounds=host.projection(entity);
            float l=bounds.x,t=bounds.y,r=bounds.z,b=bounds.w,width=r-l,height=b-t;
            float x=l+width+spacing*2,y=t+spacing,offset=0;
            for(Label<E> label:labels) {
                if(marker==0||label.accepts(entity)) {
                    String text=label.text(entity);
                    font.drawOutline(bitmap,text,x,y+offset,1,-1);
                    offset+=font.height()+2;
                }
                if(marker==0)break;
            }
            if(host.getMarker()!=null)host.setInverseComponentMarker(marker+1);
        }
        private final class StateLabel implements Label<E> {
            private final Flag flag;private final String text;
            StateLabel(Flag flag,String text) { this.flag=flag;this.text=text; }
            public String text(E entity) { return text; }
            public int acceptsRaw(E entity) {
                if(flag==Flag.LOW_HP) {
                    int marker=host.componentMarker();
                    if(marker!=0&&!textHost.isPlayer(entity))return 0;
                    E player=textHost.requirePlayer(entity);
                    double ratio=(double)(textHost.health(player)/textHost.maxHealth(player));
                    // dcmpg returns +1 for NaN. In the abnormal branch that raw value is returned.
                    int comparison=ratio<.25D?-1:ratio==.25D?0:1;
                    if(marker==0)return comparison;
                    if(comparison>=0)return 0;
                } else {
                    int marker=host.inverseComponentMarker();
                    if(marker==0&&!textHost.isPlayer(entity))return 0;
                    E player=textHost.requirePlayer(entity);
                    boolean value=flag==Flag.BLOCK?textHost.blocking(player):textHost.sneaking(player);
                    if(marker!=0)return value?1:0;
                    if(!value)return 0;
                }
                return getSettings().flag(flag)?1:0;
            }
        }
    }
    public static final class Name<E> extends EntityEspComponent<E,Settings> {
        private final EntityEspTextComponents.Host<E> textHost;
        public Name(Settings settings,EntityEspTextComponents.Host<E> host) { super(settings,host);textHost=host; }
        public boolean accepts(E entity) {
            int marker=host.componentMarker();boolean enabled=getSettings().nameEnabled();
            return marker==0?enabled:enabled&&textHost.isLiving(entity);
        }
        public void draw(E entity) {
            MsdfFontBase font=textHost.verdana();
            Vector4f bounds=host.projection(entity);
            float l=bounds.x,t=bounds.y;
            int marker=host.componentMarker();
            float r=bounds.z,b=bounds.w,width=r-l,height=b-t;
            if(marker!=0&&!textHost.isLiving(entity))return;
            E living=textHost.requireLiving(entity);
            String text=textHost.rawName(living);
            if(marker!=0) {
                if(text==null)return;
                text=textHost.formattedName(living);
            }
            boolean raw=getSettings().unformattedName();
            if(marker!=0&&raw)text=textHost.rawName(living);
            if(marker==0?raw:textHost.isPlayer(living)) {
                E player=textHost.requirePlayer(living);
                Friend friend=textHost.friend(player);
                Object target=textHost.target(player);
                if(marker==0)text="\u00a7d\u00a7l"+friend.alias();
                else if(friend!=null) {
                    text=friend.alias().isEmpty()?"\u00a7d\u00a7l"+friend.originalName():"\u00a7d\u00a7l"+friend.alias();
                }
                if(target!=null)text=text+" \u00a7c\u00a7l[Target]";
            }
            float size=6,widthText=font.width(text,size);
            float x=l+width/2-widthText/2,y=t-font.secondMetric(text,size)-spacing;
            boolean plain=getSettings().unformattedName();
            int color=marker==0?(plain?1:0):plain?getSettings().nameColor():-1;
            font.draw(msdf,text,size,x,y,color);
        }
    }
    public static final class HeldItem<E> extends EntityEspComponent<E,Settings> {
        private final EntityEspTextComponents.Host<E> textHost;
        public HeldItem(Settings settings,EntityEspTextComponents.Host<E> host) { super(settings,host);textHost=host; }
        public boolean accepts(E entity) {
            int marker=host.componentMarker();boolean enabled=getSettings().itemEnabled();
            return marker==0?enabled:enabled&&textHost.isPlayer(entity);
        }
        public void draw(E entity) {
            int marker=host.componentMarker();
            BitmapFontFamily font=textHost.bitmapFont();
            Vector4f bounds=host.projection(entity);
            if(marker!=0&&bounds==null)return;
            float l=bounds.x,t=bounds.y,r=bounds.z,b=bounds.w,width=r-l,height=b-t;
            boolean enabled=getSettings().itemNameEnabled();
            if(!enabled)return;
            if(marker!=0&&!textHost.isPlayer(entity))return;
            E player=textHost.requirePlayer(entity);
            Item item=textHost.heldItem(player);
            if(marker!=0) {
                if(item==null)return;
                item=textHost.heldItem(player);
            }
            String text=item.displayName().toUpperCase(); // Original default-locale overload.
            float x=l+width/2-font.width(text)/2;
            float y=t+height+spacing*2;
            if(marker!=0)y+=getSettings().armorEnabled()?2.5F:0;
            font.drawOutline(bitmap,text,x,y,1,-1);
        }
    }
}
