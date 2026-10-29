package moon.client.account;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;

/** bA/bC/bD/bJ: readable account storage and Microsoft OAuth -> Xbox -> Minecraft.
 * All sessions and list mutations belong to the client thread; HTTP runs on a worker.
 * Browser credentials never pass through the game. Secrets are encrypted on disk.
 */
public final class AccountManager implements AutoCloseable {
    public enum Kind { OFFLINE,MICROSOFT }
    public record Account(String name,String uuid,Kind kind,String refreshToken){
        @Override public String toString(){return name+" ("+kind+")";}
    }
    record Login(Account account,String token){@Override public String toString(){return account.toString();}}
    public static final String ORIGINAL_CLIENT_ID="54fd49e4-2103-4044-9603-2b028c814ec3";
    private static final String SCOPE="XboxLive.signin XboxLive.offline_access";
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static AccountManager instance;
    private final Minecraft mc;
    private final Path file,keyFile;
    private final Auth auth;
    private final Consumer<URI> browser;
    private final List<Account> accounts=new ArrayList<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(Thread.ofVirtual().name("Moon account login-",0).factory());
    private volatile Future<?> task;
    private volatile HttpServer callback;
    private volatile boolean closed;
    private volatile long generation;
    private boolean writable=true,busy;
    private String status="Ready";
    private URI authorization;

    public static AccountManager get(Minecraft mc){
        if(instance==null)instance=new AccountManager(mc,mc.mcDataDir.toPath().resolve("moon/accounts"),Auth.production());
        return instance;
    }
    public static void shutdown(){if(instance!=null){instance.close();instance=null;}}
    AccountManager(Minecraft mc,Path directory,Auth auth){
        this(mc,directory,auth,uri->{try{if(Desktop.isDesktopSupported())Desktop.getDesktop().browse(uri);}catch(Exception ignored){}});
    }
    AccountManager(Minecraft mc,Path directory,Auth auth,Consumer<URI> browser){
        this.mc=mc;this.file=directory.resolve("accounts.json");this.keyFile=directory.resolve("accounts.key");this.auth=auth;
        this.browser=browser;
        try{load();}catch(Exception failure){writable=false;status="Account file could not be read; original file preserved.";}
    }
    private void check(){if(closed||!mc.isCallingFromMinecraftThread())throw new IllegalStateException("Account manager requires the live client thread");}
    public List<Account> accounts(){check();return List.copyOf(accounts);}
    public String status(){return status;}
    public boolean busy(){return busy;}
    public boolean writable(){return writable;}
    public URI authorization(){return authorization;}
    private boolean canLogin(){
        if(mc.theWorld!=null||mc.thePlayer!=null||mc.isIntegratedServerRunning()){status="Disconnect from the world before changing accounts.";return false;}
        return true;
    }
    static Account offline(String name){
        if(name==null||!name.matches("[A-Za-z0-9_]{3,16}"))throw new IllegalArgumentException("Use 3-16 letters, numbers or underscores.");
        return new Account(name,UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(StandardCharsets.UTF_8)).toString().replace("-",""),Kind.OFFLINE,"");
    }
    public boolean addOffline(String name,boolean login,boolean save){
        check();if(busy)return false;if(login&&!canLogin())return false;
        try{
            Account account=offline(name.trim());
            if(save)put(account);
            if(login)apply(new Login(account,"0"));
            status=(login?"Logged in to: ":"Added account: ")+account.name;return true;
        }catch(IOException|IllegalArgumentException failure){status=failure.getMessage();return false;}
    }
    public void randomOffline(boolean save){addOffline("Moon_"+UUID.randomUUID().toString().replace("-","").substring(0,10),true,save);}
    public void remove(Account account){
        check();if(busy)return;
        try{List<Account> next=new ArrayList<>(accounts);next.remove(account);store(next);accounts.clear();accounts.addAll(next);status="Removed account.";}
        catch(IOException failure){status=failure.getMessage();}
    }
    public void clear(){
        check();if(busy)return;
        try{store(List.of());accounts.clear();status="Account list cleared.";}catch(IOException failure){status=failure.getMessage();}
    }
    private void put(Account account)throws IOException{
        List<Account> next=new ArrayList<>(accounts);next.removeIf(a->a.kind==account.kind&&a.uuid.equalsIgnoreCase(account.uuid));next.add(account);
        store(next);accounts.clear();accounts.addAll(next);
    }
    private void apply(Login login){
        check();if(!canLogin())throw new IllegalStateException("Disconnect from the world before changing accounts.");
        mc.setAccountSession(new Session(login.account.name,login.account.uuid,login.token,login.account.kind==Kind.OFFLINE?"legacy":"mojang"));
    }
    public void login(Account account){
        check();if(busy||!canLogin())return;
        if(account.kind==Kind.OFFLINE){try{apply(new Login(account,"0"));status="Logged in to: "+account.name;}catch(RuntimeException failure){status=failure.getMessage();}return;}
        start(()->auth.refresh(account.refreshToken),true,true);
    }
    public void microsoft(boolean login,boolean save){
        check();if(busy||!canLogin())return;
        start(()->browserLogin(),login,save);
    }
    private interface LoginTask {Login run()throws Exception;}
    private void start(LoginTask login,boolean apply,boolean save){
        long request=++generation;busy=true;authorization=null;status="Signing in...";
        task=worker.submit(()->{
            try{
                Login result=login.run();
                mc.addScheduledTask(()->{
                    if(closed||request!=generation)return;
                    try{if(apply&&!canLogin())return;if(save)put(result.account);if(apply)apply(result);status=(apply?"Logged in to: ":"Added account: ")+result.account.name;}
                    catch(IOException|RuntimeException failure){status="Could not save or switch account.";}
                    finally{busy=false;authorization=null;}
                });
            }catch(Exception failure){
                mc.addScheduledTask(()->{if(!closed&&request==generation){busy=false;authorization=null;status=message(failure);}});
            }finally{stopCallback();}
        });
    }
    private static String message(Exception failure){
        if(failure instanceof AuthFailure)return failure.getMessage();
        if(failure instanceof TimeoutException)return "Login timed out. Please try again.";
        if(failure instanceof BindException)return "Login callback port 42000 is in use.";
        if(failure instanceof InterruptedException||failure instanceof CancellationException)return "Login cancelled.";
        return "Login failed. Check your connection and try again.";
    }
    public void cancel(){check();generation++;busy=false;authorization=null;status="Login cancelled.";if(task!=null)task.cancel(true);stopCallback();}
    private synchronized void stopCallback(){HttpServer server=callback;callback=null;if(server!=null)server.stop(0);}
    private Login browserLogin()throws Exception{
        long request=generation;
        String state=random(24),verifier=random(48);
        String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        CompletableFuture<String> code=new CompletableFuture<>();
        HttpServer server=HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),42000),0);
        synchronized(this){if(closed||Thread.currentThread().isInterrupted()){server.stop(0);throw new InterruptedException();}callback=server;}
        server.createContext("/",exchange->{
            String response="Invalid login response.";int status=400;
            try{
                Map<String,String> query=query(exchange.getRequestURI().getRawQuery());
                if(exchange.getRequestMethod().equals("GET")&&exchange.getRequestURI().getPath().equals("/")&&state.equals(query.get("state"))){
                    if(query.containsKey("error")){code.completeExceptionally(new AuthFailure("Microsoft login was declined."));response="Login cancelled. You can return to Moon.";status=200;}
                    else if(query.getOrDefault("code","").length()>0){code.complete(query.get("code"));response="Login received. You can return to Moon.";status=200;}
                }
                byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","text/plain; charset=utf-8");exchange.getResponseHeaders().set("Cache-Control","no-store");exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);
            }catch(IllegalArgumentException invalid){exchange.sendResponseHeaders(400,-1);}finally{exchange.close();}
        });
        server.setExecutor(workerForCallback());server.start();
        URI uri=auth.authorize(state,challenge);authorization=uri;
        browser.accept(uri);
        mc.addScheduledTask(()->{if(!closed&&busy&&request==generation)status="Complete Microsoft login in your browser. Use Open Browser if needed.";});
        try{return auth.code(code.get(3,TimeUnit.MINUTES),verifier);}catch(ExecutionException failure){if(failure.getCause() instanceof Exception e)throw e;throw failure;}
    }
    private static Executor workerForCallback(){return command->Thread.ofVirtual().name("Moon OAuth callback").start(command);}
    static Map<String,String> query(String query){
        Map<String,String> values=new HashMap<>();if(query!=null)for(String part:query.split("&")){String[] p=part.split("=",2);String key=URLDecoder.decode(p[0],StandardCharsets.UTF_8);if(values.putIfAbsent(key,p.length==2?URLDecoder.decode(p[1],StandardCharsets.UTF_8):"")!=null)throw new IllegalArgumentException("Duplicate query parameter");}return values;
    }
    private static String random(int size){byte[] bytes=new byte[size];RANDOM.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    private byte[] key(boolean create)throws IOException{
        if(!Files.exists(keyFile)){
            if(!create)throw new IOException("Account key is missing; original file preserved.");
            Files.createDirectories(keyFile.getParent());byte[] key=new byte[32];RANDOM.nextBytes(key);Path temporary=Files.createTempFile(keyFile.getParent(),"account-key-",".tmp");
            try{restrict(temporary);Files.write(temporary,key);Files.move(temporary,keyFile);}finally{Files.deleteIfExists(temporary);}
        }
        byte[] key=Files.readAllBytes(keyFile);if(key.length!=32)throw new IOException("Invalid account key; original file preserved.");restrict(keyFile);return key;
    }
    /** Owner-only ACL on Windows, owner read/write on POSIX; no shell or extra dependencies. */
    private static void restrict(Path path)throws IOException{
        var acl=Files.getFileAttributeView(path,AclFileAttributeView.class);
        if(acl!=null){var entry=AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(path)).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build();acl.setAcl(List.of(entry));}
        else if(Files.getFileAttributeView(path,PosixFileAttributeView.class)!=null)Files.setPosixFilePermissions(path,PosixFilePermissions.fromString("rw-------"));
    }
    private String crypt(String value,boolean encrypt,byte[] key)throws GeneralSecurityException{
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        if(encrypt){byte[] nonce=new byte[12];RANDOM.nextBytes(nonce);cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD("Moon accounts v1".getBytes(StandardCharsets.UTF_8));byte[] data=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));return Base64.getEncoder().encodeToString(nonce)+":"+Base64.getEncoder().encodeToString(data);}
        String[] parts=value.split(":",-1);if(parts.length!=2)throw new GeneralSecurityException("Invalid encrypted token");byte[] nonce=Base64.getDecoder().decode(parts[0]);if(nonce.length!=12)throw new GeneralSecurityException("Invalid nonce");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));cipher.updateAAD("Moon accounts v1".getBytes(StandardCharsets.UTF_8));return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])),StandardCharsets.UTF_8);
    }
    private void load()throws Exception{
        if(!Files.exists(file))return;if(Files.size(file)>4*1024*1024)throw new IOException("Account file too large");
        JsonObject root=new JsonParser().parse(Files.readString(file,StandardCharsets.UTF_8)).getAsJsonObject();if(root.get("version").getAsInt()!=1)throw new IOException("Unsupported account file");
        List<Account> parsed=new ArrayList<>();byte[] key=null;
        for(JsonElement element:root.getAsJsonArray("accounts")){
            JsonObject a=element.getAsJsonObject();String name=a.get("name").getAsString(),uuid=a.get("uuid").getAsString();Kind kind=Kind.valueOf(a.get("kind").getAsString());offline(name);
            if(!uuid.matches("[a-fA-F0-9]{32}"))throw new IOException("Invalid UUID");String refresh="";
            if(kind==Kind.MICROSOFT){if(key==null)key=key(false);refresh=crypt(a.get("refresh").getAsString(),false,key);if(refresh.isBlank())throw new IOException("Missing refresh token");}
            parsed.add(new Account(name,uuid,kind,refresh));
        }
        accounts.addAll(parsed);
    }
    private void store(List<Account> next)throws IOException{
        if(!writable)throw new IOException("Account file is unreadable; original file preserved.");
        JsonObject root=new JsonObject();root.addProperty("version",1);JsonArray saved=new JsonArray();byte[] key=null;
        try{
            for(Account account:next){JsonObject a=new JsonObject();a.addProperty("name",account.name);a.addProperty("uuid",account.uuid);a.addProperty("kind",account.kind.name());
                if(account.kind==Kind.MICROSOFT){if(key==null)key=key(true);a.addProperty("refresh",crypt(account.refreshToken,true,key));}saved.add(a);}
        }catch(GeneralSecurityException failure){throw new IOException("Could not protect account token.");}
        root.add("accounts",saved);Files.createDirectories(file.getParent());Path temporary=Files.createTempFile(file.getParent(),"accounts-",".tmp");
        try{restrict(temporary);Files.writeString(temporary,JSON.toJson(root)+"\n",StandardCharsets.UTF_8);try{Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException failure){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}}
        finally{Files.deleteIfExists(temporary);}
    }
    @Override public void close(){closed=true;generation++;if(task!=null)task.cancel(true);stopCallback();worker.shutdownNow();}

    static final class AuthFailure extends IOException {AuthFailure(String message){super(message);}}
    /** Endpoint bundle is package-private for deterministic protocol fixtures; production uses HTTPS only. */
    static final class Auth {
        final String clientId;final URI authorize,token,xbl,xsts,minecraft,profile;
        final HttpClient http;
        Auth(String clientId,URI authorize,URI token,URI xbl,URI xsts,URI minecraft,URI profile){
            this.clientId=clientId;this.authorize=authorize;this.token=token;this.xbl=xbl;this.xsts=xsts;this.minecraft=minecraft;this.profile=profile;
            http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
        }
        static Auth production(){return new Auth(System.getProperty("moon.microsoft.clientId",ORIGINAL_CLIENT_ID),URI.create("https://login.live.com/oauth20_authorize.srf"),URI.create("https://login.live.com/oauth20_token.srf"),URI.create("https://user.auth.xboxlive.com/user/authenticate"),URI.create("https://xsts.auth.xboxlive.com/xsts/authorize"),URI.create("https://api.minecraftservices.com/authentication/login_with_xbox"),URI.create("https://api.minecraftservices.com/minecraft/profile"));}
        URI authorize(String state,String challenge){return URI.create(authorize+"?"+form(Map.of("client_id",clientId,"response_type","code","redirect_uri","http://localhost:42000","scope",SCOPE,"state",state,"code_challenge",challenge,"code_challenge_method","S256","prompt","select_account")));}
        Login code(String code,String verifier)throws Exception{return exchange(Map.of("grant_type","authorization_code","code",code,"code_verifier",verifier));}
        Login refresh(String refresh)throws Exception{if(refresh.isBlank())throw new AuthFailure("Sign in with Microsoft again.");return exchange(Map.of("grant_type","refresh_token","refresh_token",refresh));}
        private Login exchange(Map<String,String> grant)throws Exception{
            Map<String,String> body=new HashMap<>(grant);body.put("client_id",clientId);body.put("redirect_uri","http://localhost:42000");body.put("scope",SCOPE);
            JsonObject oauth=send(token,"application/x-www-form-urlencoded",form(body),null,"Microsoft authorization");String access=string(oauth,"access_token");
            String refresh=oauth.has("refresh_token")?string(oauth,"refresh_token"):grant.getOrDefault("refresh_token","");if(refresh.isBlank())throw new AuthFailure("Microsoft did not provide a refresh token. Sign in again.");
            JsonObject properties=new JsonObject();properties.addProperty("AuthMethod","RPS");properties.addProperty("SiteName","user.auth.xboxlive.com");properties.addProperty("RpsTicket","d="+access);
            JsonObject request=new JsonObject();request.add("Properties",properties);request.addProperty("RelyingParty","http://auth.xboxlive.com");request.addProperty("TokenType","JWT");
            JsonObject xbox=send(xbl,"application/json",request.toString(),null,"Xbox Live authentication");String xboxToken=string(xbox,"Token");
            properties=new JsonObject();properties.addProperty("SandboxId","RETAIL");JsonArray tokens=new JsonArray();tokens.add(new JsonPrimitive(xboxToken));properties.add("UserTokens",tokens);
            request=new JsonObject();request.add("Properties",properties);request.addProperty("RelyingParty","rp://api.minecraftservices.com/");request.addProperty("TokenType","JWT");
            JsonObject authorized=send(xsts,"application/json",request.toString(),null,"Xbox authorization");String xstsToken=string(authorized,"Token");String uhs;
            try{uhs=authorized.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();if(uhs.isBlank())throw new IllegalArgumentException();}catch(RuntimeException e){throw new AuthFailure("Xbox authorization returned no user identity.");}
            request=new JsonObject();request.addProperty("identityToken","XBL3.0 x="+uhs+";"+xstsToken);
            JsonObject result=send(minecraft,"application/json",request.toString(),null,"Minecraft authentication");String mcToken=string(result,"access_token");
            JsonObject player=send(profile,null,null,mcToken,"Minecraft profile");String name=string(player,"name"),uuid=string(player,"id");
            if(!name.matches("[A-Za-z0-9_]{3,16}")||!uuid.matches("[a-fA-F0-9]{32}"))throw new AuthFailure("Minecraft profile is invalid or has no username.");
            return new Login(new Account(name,uuid,Kind.MICROSOFT,refresh),mcToken);
        }
        private static String string(JsonObject object,String key)throws AuthFailure{
            try{String value=object.get(key).getAsString();if(value.isBlank())throw new IllegalArgumentException();return value;}catch(RuntimeException failure){throw new AuthFailure("Login service returned an incomplete response.");}
        }
        private JsonObject send(URI endpoint,String contentType,String body,String bearer,String stage)throws Exception{
            var builder=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(25)).header("Accept","application/json");
            if(body==null)builder.GET();else builder.header("Content-Type",contentType).POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8));
            if(bearer!=null)builder.header("Authorization","Bearer "+bearer);
            HttpResponse<String> response=http.send(builder.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));int status=response.statusCode();
            if(status<200||status>=300){
                if(endpoint.equals(profile)&&status==404)throw new AuthFailure("No Minecraft Java profile. Check ownership and set a username first.");
                if(endpoint.equals(xsts))throw new AuthFailure("Xbox authorization failed. Check your Xbox profile, family permissions and region.");
                throw new AuthFailure(stage+" failed (HTTP "+status+"). Please sign in again.");
            }
            try{if(response.body().length()>1024*1024)throw new IllegalArgumentException();return new JsonParser().parse(response.body()).getAsJsonObject();}catch(RuntimeException failure){throw new AuthFailure(stage+" returned an invalid response.");}
        }
        private static String form(Map<String,String> values){return String.join("&",values.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).toList());}
    }
}
