package org.example.socket;
import java.io.*;
import java.net.*;
import java.sql.SQLException;
import java.util.concurrent.*;
import org.example.dto.LoginUser;
import org.example.service.AuthService;
import static org.example.socket.Packet.*;
public final class SocketServer {
    public static void main(String[] args)throws IOException {
        int port=args.length==0?9090:Integer.parseInt(args[0]);
        var pool=new ThreadPoolExecutor(16,16,0,TimeUnit.SECONDS,new SynchronousQueue<>());
        try(var server=new ServerSocket(port,32,InetAddress.getLoopbackAddress())){
            System.out.println("Socket server: "+server.getLocalSocketAddress());
            while(true){var s=server.accept();try{pool.execute(()->serve(s));}catch(RejectedExecutionException e){s.close();}}
        }finally{pool.shutdownNow();}
    }
    private static void serve(Socket s){
        try(s;var in=new DataInputStream(s.getInputStream());var out=new DataOutputStream(s.getOutputStream())){
            s.setSoTimeout(120000); LoginUser user=null;
            while(true){
                Packet p;
                try{p=Packet.read(in);}catch(Packet.Format e){new Packet(e.type,FORMAT_ERROR,"패킷 형식 오류").write(out);return;}
                if(p==null)return;
                int code=SUCCESS; String body;
                try{
                    if(p.code()!=REQUEST||p.type()<1||p.type()>23)throw new Operations.Failure(FORMAT_ERROR,"TYPE 또는 CODE 오류");
                    String[] fields=p.body().isEmpty()?new String[0]:p.body().split("\t",-1);
                    if(p.type()==1){user=null; Operations.arity(fields,2);Operations.text(fields[0]);Operations.text(fields[1]);
                        user=AuthService.login(fields[0],fields[1]);
                        if(user==null)throw new Operations.Failure(PERMISSION_DENIED,"로그인 실패");
                        body=user.getRole()+"\t"+user.getName();
                    }else if(p.type()==11 && user!=null) throw new Operations.Failure(PERMISSION_DENIED,"로그아웃 후 가입하세요.");
                    else body=Operations.run(p.type(),fields,user);
                }catch(Operations.Failure e){code=e.code;body=e.getMessage();}
                catch(IllegalArgumentException|java.time.DateTimeException e){code=INVALID_INPUT;body="입력값을 확인하세요.";}
                catch(SQLException|ExceptionInInitializerError|NoClassDefFoundError e){code=DATABASE_ERROR;body="DB 처리 오류";System.err.println(e.getClass().getSimpleName());}
                catch(Exception e){code=DATABASE_ERROR;body="요청 처리 오류";System.err.println(e.getClass().getSimpleName());}
                try{new Packet(p.type(),code,body).write(out);}catch(Packet.Format e){new Packet(p.type(),INVALID_STATE,"응답 크기 한도 초과").write(out);}
            }
        }catch(IOException ignored){}
    }
}
