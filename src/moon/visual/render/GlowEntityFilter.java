package moon.visual.render;

import java.util.function.Predicate;

/** fD.a(entity), separated from the abstract pass lifecycle. Entity identities,
 * projection data and instanceof checks come from the destination game. */
public final class GlowEntityFilter<E> implements Predicate<E> {
    public enum Target { INVISIBLE,MOB,ANIMAL,PLAYER }
    /** Distinct checks preserve sJ || sU || tk short-circuit order. */
    public enum Type { LIVING,MOB,ANIMAL_A,ANIMAL_B,ANIMAL_C,PLAYER }
    public interface Targets { boolean contains(Target target); }
    public interface Entities<E> { boolean is(E entity,Type type); }
    public interface Host<E> extends Entities<E> {
        Object marker();Targets targets();boolean isLocalPlayer(E entity);int thirdPersonView();
        Object projection(E entity);int entityCode(E entity);boolean invisible(E entity);
    }
    public static final class ExcludedCodes {
        public final int first,second;
        public ExcludedCodes(int first,int second){this.first=first;this.second=second;}
        /** Offline table decodes both calls. No supplied runtime seed. */
        public static ExcludedCodes freshStateDecodeCandidate(){return new ExcludedCodes(-84,-42);}
    }
    private final Host<E> host;private final ExcludedCodes codes;
    public GlowEntityFilter(Host<E> host,ExcludedCodes codes){this.host=host;this.codes=codes;}
    /** Original nonnull sI path ireturns entity.e() directly, a noncanonical Z.
     * Keep the integer endpoint for callers inspecting the original stack value. */
    public int testRaw(E entity){return testRaw(entity,true);}
    /** jN selection runs before gs produces projection; fD additionally requires it. */
    public boolean selectedForProjection(E entity){return testRaw(entity,false)!=0;}
    private int testRaw(E entity,boolean requireProjection){
        Object marker=host.marker();Targets targets=host.targets();
        if(marker!=null)return host.entityCode(entity);
        if(host.isLocalPlayer(entity))return host.thirdPersonView()!=0?1:0;
        if(requireProjection&&host.projection(entity)==null)return 0;
        if(host.entityCode(entity)==codes.first||host.entityCode(entity)==codes.second)return 0;
        if(host.invisible(entity)&&!targets.contains(Target.INVISIBLE))return 0;
        if(host.is(entity,Type.MOB)&&targets.contains(Target.MOB))return 1;
        if((host.is(entity,Type.ANIMAL_A)||host.is(entity,Type.ANIMAL_B)||host.is(entity,Type.ANIMAL_C))&&targets.contains(Target.ANIMAL))return 1;
        return host.is(entity,Type.PLAYER)&&targets.contains(Target.PLAYER)?1:0;
    }
    public boolean test(E entity){return testRaw(entity)!=0;}
}
