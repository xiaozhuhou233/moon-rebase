package moon.port.myau.util;
import net.minecraft.client.Minecraft;import net.minecraft.network.*;
public final class PacketUtil {
 public static NetworkManager getConnection(){return Minecraft.getMinecraft().getNetHandler()==null?null:Minecraft.getMinecraft().getNetHandler().getNetworkManager();}
 public static void sendPacket(Packet<?> packet){var handler=Minecraft.getMinecraft().getNetHandler();if(handler!=null)handler.addToSendQueue(packet);}
 public static void sendPacketNoEvent(Packet<?> packet){NetworkManager connection=getConnection();if(connection!=null)connection.sendPacket(packet,null);}
}
