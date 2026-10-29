package moon.visual.hud;

/** fZ/f0: background and title text are final; only disableTitle is mutable. */
public class DraggableComponentProperties {
    private static Object[] sentinel;
    private final boolean disableBackground;
    private boolean disableTitle;
    private final String title;

    DraggableComponentProperties(boolean disableBackground,boolean disableTitle,String title) {
        this.disableBackground=disableBackground;this.disableTitle=disableTitle;this.title=title;
    }
    public boolean isBackgroundDisabled(){return disableBackground;}
    public boolean isTitleDisabled(){return disableTitle;}
    public String getTitle(){return title;}
    public DraggableComponentProperties setTitleDisabled(boolean value){disableTitle=value;return this;}
    public static Object[] getSentinel(){return sentinel;}
    public static void setSentinel(Object[] value){sentinel=value;}
    public static Builder builder(){return new Builder();}

    public static class Builder {
        private boolean disableBackground,disableTitle;
        private String title;
        Builder(){}
        public Builder disableBackground(boolean value){disableBackground=value;return this;}
        public Builder disableTitle(boolean value){disableTitle=value;return this;}
        public Builder title(String value){title=value;return this;}
        public DraggableComponentProperties build(){return new DraggableComponentProperties(disableBackground,disableTitle,title);}
        @Override public String toString(){
            return "DraggableComponentProperties.DraggableComponentPropertiesBuilder(disableBackground="+disableBackground+
                ", disableTitle="+disableTitle+", title="+title+")";
        }
    }
}
