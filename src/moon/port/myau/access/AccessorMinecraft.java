package moon.port.myau.access;
public final class AccessorMinecraft {
 private static final java.lang.reflect.Field TIMER=moon.port.myau.inject.MappingBridge.field("net.minecraft.client.Minecraft","timer",net.minecraft.util.Timer.class);
 public static net.minecraft.util.Timer getTimer(net.minecraft.client.Minecraft mc){try{return (net.minecraft.util.Timer)TIMER.get(mc);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
}
