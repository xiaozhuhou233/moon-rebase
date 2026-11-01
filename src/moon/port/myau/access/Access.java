package moon.port.myau.access;
public final class Access {public static void report(String owner,String name,Throwable failure){throw new IllegalStateException(owner+"."+name,failure);}}
