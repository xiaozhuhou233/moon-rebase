package moon.client;

/** RenderManager nv's five independent capture policies. These flags are only
 * set on the client thread by the original fF flow, never by a worker. */
public final class McpGlowRenderPolicy {
    static boolean captureColor,glint=true,shadowFire=true,names=true,cameraBypass;
    private McpGlowRenderPolicy(){}
    public static boolean captureColor(){return captureColor;}
    public static boolean glint(){return glint;}
    public static boolean shadowFire(){return shadowFire;}
    public static boolean names(){return names;}
    public static boolean cameraBypass(){return cameraBypass;}
    public static boolean restored(){return !captureColor&&glint&&shadowFire&&names&&!cameraBypass;}
    static void reset(){captureColor=false;glint=true;shadowFire=true;names=true;cameraBypass=false;}
}
