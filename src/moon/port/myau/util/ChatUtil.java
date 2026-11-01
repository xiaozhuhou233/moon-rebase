package moon.port.myau.util;
public final class ChatUtil {public static void sendFormatted(String format,Object...args){var p=net.minecraft.client.Minecraft.getMinecraft().thePlayer;if(p!=null)p.addChatMessage(new net.minecraft.util.ChatComponentText(String.format(format,args).replace('&','§')));}}
