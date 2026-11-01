package moon.port.myau.util;
public final class ServerPing {public static long getPing(){var mc=net.minecraft.client.Minecraft.getMinecraft();if(mc.thePlayer==null||mc.getNetHandler()==null)return 0;var info=mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());return info==null?0:info.getResponseTime();}}
