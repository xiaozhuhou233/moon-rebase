package moon.port.myau.module;
public abstract class Module {
 protected final String name; protected boolean enabled;
 public Module(String name,boolean enabled){this.name=name;this.enabled=enabled;}
 public String getName(){return name;} public String[] getLegacyNames(){return new String[0];}
 public boolean isEnabled(){return enabled;} public String[] getSuffix(){return new String[0];}
 public boolean worksWithGuiOpen(){return false;}
 public void setEnabled(boolean value){if(enabled==value)return;enabled=value;if(value)onEnabled();else onDisabled();}
 public void onEnabled(){} public void onDisabled(){} public void verifyValue(String key){}
}
