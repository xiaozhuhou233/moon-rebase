package moon.port.myau.inject;
import java.lang.reflect.*;
/** Direct MCP names only: no injector, remapper or native bootstrap. */
public final class MappingBridge {
 public static Field field(String owner,String name,Class<?> type){try{Field f=Class.forName(owner).getDeclaredField(name);f.setAccessible(true);return f;}catch(ReflectiveOperationException e){throw new IllegalStateException(owner+"."+name,e);}}
 public static Method method(String owner,String name,Class<?>...types){try{Method m=Class.forName(owner).getDeclaredMethod(name,types);m.setAccessible(true);return m;}catch(ReflectiveOperationException e){throw new IllegalStateException(owner+"."+name,e);}}
}
