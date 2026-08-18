import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Test-only Alpaca substitute. It never proxies and exposes only local control endpoints. */
public final class FakeAlpaca {
  static final List<String> requests = new CopyOnWriteArrayList<>(), subscriptions = new CopyOnWriteArrayList<>();
  static final List<Socket> news = new CopyOnWriteArrayList<>(), market = new CopyOnWriteArrayList<>(), trade = new CopyOnWriteArrayList<>();
  static final UUID accountId = UUID.fromString("11111111-1111-1111-1111-111111111111");
  static KeyPair pair;
  public static void main(String[] a) throws Exception {
    pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    HttpServer h=HttpServer.create(new InetSocketAddress("127.0.0.1",18080),0);
    h.createContext("/health", e->reply(e,200,"{\"status\":\"UP\"}"));
    h.createContext("/jwks", e->reply(e,200,jwks()));
    h.createContext("/control/token", e->{try{reply(e,200,"{\"token\":\""+jwt()+"\"}");}catch(Exception x){reply(e,500,"{\"error\":\"token\"}");}});
    h.createContext("/control/reset", e->{requests.clear();subscriptions.clear();reply(e,200,"{}");});
    h.createContext("/control/requests", e->reply(e,200,"["+String.join(",",requests)+"]"));
    h.createContext("/control/subscriptions", e->reply(e,200,"["+String.join(",",subscriptions)+"]"));
    h.createContext("/control/send", e->{String q=e.getRequestURI().getQuery(); String target=param(q,"target"); String data=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8); broadcast(target,data);reply(e,200,"{}");});
    h.createContext("/oauth/token", e->{record(e);reply(e,200,"{\"access_token\":\"fake-access-token\",\"scope\":\"account:write\"}");});
    h.createContext("/v2/account", e->{record(e);reply(e,200,"{\"id\":\""+accountId+"\",\"account_number\":\"IT-001\",\"status\":\"ACTIVE\",\"currency\":\"USD\",\"cash\":\"10000\",\"buying_power\":\"20000\",\"portfolio_value\":\"10000\",\"equity\":\"10000\",\"last_equity\":\"10000\",\"long_market_value\":\"0\",\"non_marginable_buying_power\":\"10000\",\"account_blocked\":false,\"trading_blocked\":false,\"trade_suspended_by_user\":false,\"shorting_enabled\":true}");});
    h.createContext("/v2/watchlists", e->{record(e); if("GET".equals(e.getRequestMethod())) reply(e,200,"[]"); else reply(e,200,"{\"id\":\"22222222-2222-2222-2222-222222222222\",\"name\":\"IT\",\"assets\":[]}");});
    h.createContext("/v2/orders", e->{record(e);reply(e,200,"{\"client_order_id\":\"fake-client-order\"}");});
    h.setExecutor(Executors.newCachedThreadPool()); h.start();
    new Thread(()->ws(18081,news,"news"),"fake-news").start(); new Thread(()->ws(18082,market,"market")).start(); new Thread(()->ws(18083,trade,"trade")).start();
  }
  static void ws(int port,List<Socket> clients,String name){try(ServerSocket s=new ServerSocket(port,50,InetAddress.getByName("127.0.0.1"))){while(true){Socket c=s.accept(); Map<String,String> hs=headers(c.getInputStream()); String key=hs.get("sec-websocket-key"); OutputStream out=c.getOutputStream(); String accept=Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII))); out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "+accept+"\r\n\r\n").getBytes());out.flush();clients.add(c); new Thread(()->{try{String sub=readFrame(c.getInputStream()); if(sub!=null) subscriptions.add(json("stream",name,"payload",sub));}catch(Exception ignored){} }).start();}}catch(Exception x){x.printStackTrace();}}
  static Map<String,String> headers(InputStream in)throws IOException{Map<String,String>m=new HashMap<>();String l;while(!(l=line(in)).isEmpty()){int i=l.indexOf(':');if(i>0)m.put(l.substring(0,i).toLowerCase(),l.substring(i+1).trim());}return m;} static String line(InputStream i)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();int x;while((x=i.read())>=0&&x!='\n')if(x!='\r')b.write(x);return b.toString();}
  static String readFrame(InputStream in)throws IOException{int a=in.read(),b=in.read();if(a<0||b<0)return null;int n=b&127;if(n==126)n=(in.read()<<8)|in.read();byte[] mask=new byte[4];if((b&128)!=0)in.read(mask);byte[] p=in.readNBytes(n);for(int i=0;i<p.length;i++)p[i]^=mask[i%4];return new String(p,StandardCharsets.UTF_8);}
  static void broadcast(String t,String body){List<Socket> cs=switch(t){case "news"->news;case "market"->market;default->trade;}; boolean binary="trade".equals(t); for(Socket s:cs)try{byte[] p=body.getBytes(StandardCharsets.UTF_8);OutputStream o=s.getOutputStream();o.write(binary?130:129);if(p.length<126)o.write(p.length);else{o.write(126);o.write(p.length>>8);o.write(p.length);}o.write(p);o.flush();}catch(Exception e){cs.remove(s);}}
  static void record(HttpExchange e)throws IOException{String body=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);requests.add("{\"method\":\""+e.getRequestMethod()+"\",\"path\":\""+e.getRequestURI().getPath()+"\",\"body\":"+quote(body)+",\"authorization\":"+quote(e.getRequestHeaders().getFirst("Authorization"))+",\"apiKey\":"+quote(e.getRequestHeaders().getFirst("APCA-API-KEY-ID"))+"}");}
  static String quote(String s){return "\""+(s==null?"":s.replace("\\","\\\\").replace("\"","\\\""))+"\"";} static String json(String...v){return "{\""+v[0]+"\":"+quote(v[1])+",\""+v[2]+"\":"+quote(v[3])+"}";}
  static String param(String q,String k){if(q==null)return "";for(String p:q.split("&")){String[]x=p.split("=",2);if(x[0].equals(k))return URLDecoder.decode(x.length>1?x[1]:"",StandardCharsets.UTF_8);}return "";} static void reply(HttpExchange e,int c,String s)throws IOException{e.getResponseHeaders().set("Content-Type","application/json");byte[]b=s.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(c,b.length);e.getResponseBody().write(b);e.close();}
  static String jwks(){RSAPublicKey k=(RSAPublicKey)pair.getPublic();return "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"it-key\",\"alg\":\"RS256\",\"n\":\""+b64(k.getModulus().toByteArray())+"\",\"e\":\""+b64(k.getPublicExponent().toByteArray())+"\"}]}";} static String b64(byte[]b){if(b.length>1&&b[0]==0)b=Arrays.copyOfRange(b,1,b.length);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);} static String jwt()throws Exception{String h=b64("{\"alg\":\"RS256\",\"kid\":\"it-key\"}".getBytes()),p=b64(("{\"iss\":\"http://127.0.0.1:18080/issuer\",\"sub\":\"it-user\",\"email\":\"it@example.invalid\",\"preferred_username\":\"integration-user\",\"exp\":"+(Instant.now().getEpochSecond()+3600)+"}").getBytes());Signature s=Signature.getInstance("SHA256withRSA");s.initSign(pair.getPrivate());s.update((h+"."+p).getBytes());return h+"."+p+"."+b64(s.sign());}
}
