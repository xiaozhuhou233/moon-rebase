package moon.client;

import moon.visual.gui.*;
import moon.visual.hud.*;
import moon.visual.render.*;
import moon.visual.notification.Notification;
import moon.visual.color.*;

/** Explicit destination policy and finite offline original-table candidates.
 * Original runtime seed identity is unverified. Keep policy outside restored classes.
 */
public final class McpVisualProfile {
    public static final String ID="mcp-java24-material-gui-offline-candidate-v3";
    // Explicit destination selection. The underlying c/e/y keys are unique only
    // within the searched original data-node candidates, not runtime captures.
    // GUI role mappings/math are restored; original runtime seed equivalence is
    // still unverified. Keep this provenance visible when porting this profile.
    public final HctColor.ColorScience materialColorScience=OfflineMaterialProfile.colorScience();
    public final NativeClickGuiScreen.Parameters screen = new NativeClickGuiScreen.Parameters(200,250,200,200,250,200,770,771);
    // GUI_NUMERIC_CANDIDATES.json + independent class-pool/CBC cross-check:
    // kA seed22201025122179, ke seed88321056566741, kr seed67106739708862.
    // kA bytecode order ends positive/negative; Parameters takes negative/positive.
    // These are finite offline candidates, not supplied runtime captures.
    public final CategoryPanel.Parameters category = new CategoryPanel.Parameters(20,25,20,-12,12);
    public final BooleanSettingWidget.Parameters toggle = new BooleanSettingWidget.Parameters(100,100,15);
    // ko text constants remain a host policy; kl independently has runtime evidence for these codes.
    public final StringSettingWidget.Parameters text = new StringSettingWidget.Parameters(14,30,500);
    public final ModalOverlay.Parameters modal = new ModalOverlay.Parameters(0x80000000,100);
    public final BezierEditor.Parameters curve = BezierEditor.Parameters.unverifiedTableCandidates();
    public final int fpsFloor=60,gradientEstimate=272,outlineEstimate=224;
    // jN runtime settings snapshot supplies radius16; the encrypted maximum is
    // not recovered. These GL enums/bounds are destination policy, not fK/fG decrypts.
    public final int glowRadius=16,glowRadiusMaximum=64,glowMaskFilter=9728;
    public final GlowOutlinePass.Constants glowOutline=new GlowOutlinePass.Constants(9728,9728,33984,33984,33986,2,33986,33985);
    public final GlowCompositePass.Constants glowComposite=new GlowCompositePass.Constants(516,770,771,7,516);
    public final GlowBlurPass.Parameters glowBlur=GlowBlurPass.Parameters.freshStateDecodeCandidate();
    public final GlowMaskColorEnvironment.Parameters glowColorEnvironment=GlowMaskColorEnvironment.Parameters.freshStateDecodeCandidate();
    public final GlowEntityFilter.ExcludedCodes glowExcludedCodes=GlowEntityFilter.ExcludedCodes.freshStateDecodeCandidate();
    // fH/fI encrypted constants remain unresolved. These are destination GL,
    // geometry/cache/label policies, deliberately separate from recovered values.
    public final int entityOutlineRadius=16,entityOutlineRadiusMaximum=64;
    public final EntityOutlinePass.Constants entityOutline=new EntityOutlinePass.Constants(101,9728,9728,33984,33984,
        33984,33986,2,33986,33985,new GlowCompositePass.Constants(516,770,771,7,516));
    public final EntityEspRenderer.Constants esp=new EntityEspRenderer.Constants(516,770,771,0x80000000,770,771,519,515);
    public final LegacyColorRectangle.Constants espItemRectangle=new LegacyColorRectangle.Constants(772,773,7,16,255,8,255,24);
    public final EntityEspBoxComponent.Constants espBox=new EntityEspBoxComponent.Constants(0x80000000,768,32);
    public final EntityEspBarComponents.Constants espBars=new EntityEspBarComponents.Constants(2,0x80000000,0x80000000,0xff000000,272);
    public final EntityEspTextComponents.Labels espLabels=new EntityEspTextComponents.Labels("BLOCK","SNEAK","LOW HP");
    public final HudColors.Parameters hudColors = new HudColors.Parameters(7,6,-100,7,6,-100);
    // fw is missing: these are explicit semantic mappings for this destination, not recovered switch arms.
    public final HudRenderPipeline.Parameters hud = new HudRenderPipeline.Parameters(
        mode->mode==HudRenderPipeline.TextColorMode.GRADIENT?1:mode==HudRenderPipeline.TextColorMode.PULSING?2:0,
        mode->mode==HudRenderPipeline.SidebarColorMode.GRADIENT?1:mode==HudRenderPipeline.SidebarColorMode.PULSING?2:0);
    // gc/ge encrypted values and k_ mapping are not decoded: explicit target policy.
    public final Notification.Parameters notification=new Notification.Parameters(0xff20252d,200,0);
    public final int notificationBlendCapability=3042;
    public final long notificationRecentChangeWindow=100;
    public Palette palette(int argb){return new Palette(MaterialScheme.dark(argb,materialColorScience));}
    /** Original dark Scheme and consumer roles, explicit offline numeric policy above. */
    public static final class Palette extends MaterialGuiScheme {
        Palette(MaterialScheme scheme){super(scheme);}
    }
}
