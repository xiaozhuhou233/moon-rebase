package moon.visual.notification;

/** bh: original enum order and Material Icons glyphs, not destination labels. */
public enum NotificationType {
    SUCCESS("\ue876"),ERROR("\ue5cd"),INFO("\ue7f4");
    private final String icon;
    NotificationType(String icon){this.icon=icon;}
    public String icon(){return icon;}
}
