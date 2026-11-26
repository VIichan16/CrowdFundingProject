package org.example.socket;
import java.sql.*;
import java.time.LocalDate;
import java.math.BigDecimal;
import org.example.dto.LoginUser;
import org.example.persistence.DBConnection;
import org.example.service.*;
import org.example.common.BusinessException;
import static org.example.socket.Packet.*;
final class Operations {
    static final class Failure extends Exception {final int code;Failure(int code,String msg){super(msg);this.code=code;}}
    static void require(boolean ok,int code,String msg)throws Failure{if(!ok)throw new Failure(code,msg);}
    static void arity(String[] f,int n)throws Failure{require(f.length==n,FORMAT_ERROR,"필드 개수 오류");}
    static String text(String v)throws Failure{require(!v.isBlank(),INVALID_INPUT,"빈 입력값");return v;}
    static int positive(String v)throws Failure{int x=Integer.parseInt(v);require(x>0,INVALID_INPUT,"양의 정수가 필요합니다.");return x;}
    static BigDecimal money(String v)throws Failure{require(v.matches("[0-9]{1,16}(\\.[0-9]{1,2})?"),INVALID_INPUT,"금액 형식 오류");var x=new BigDecimal(v);require(x.signum()>0,INVALID_INPUT,"금액은 0보다 커야 합니다.");return x;}
    static PreparedStatement stmt(Connection c,String sql,Object... args)throws SQLException{
        var p=c.prepareStatement(sql);try{for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);return p;}catch(SQLException e){p.close();throw e;}
    }
    static int update(Connection c,String sql,Object... args)throws SQLException{try(var p=stmt(c,sql,args)){return p.executeUpdate();}}
    static boolean exists(Connection c,String sql,Object... args)throws SQLException{try(var p=stmt(c,sql,args);var r=p.executeQuery()){return r.next();}}
    static String rows(Connection c,String sql,Object... args)throws SQLException{
        try(var p=stmt(c,sql,args);var r=p.executeQuery()){
            var b=new StringBuilder();int n=r.getMetaData().getColumnCount();
            for(int i=1;i<=n;i++){if(i>1)b.append('\t');b.append(r.getMetaData().getColumnLabel(i));}
            while(r.next()){b.append('\n');for(int i=1;i<=n;i++){if(i>1)b.append('\t');String v=r.getString(i);if(v!=null)b.append(v.replace("\\","\\\\").replace("\t","\\t").replace("\r","\\r").replace("\n","\\n"));}}
            return b.toString();
        }
    }
    static boolean visible(String status){return java.util.Set.of("진행중","성공","종료","실패").contains(status);}
    static String project(Connection c,int id,LoginUser u,boolean owner,boolean lock)throws Exception{
        try(var p=stmt(c,"SELECT maker_id,project_status FROM project WHERE project_id=?"+(lock?" FOR UPDATE":""),id);var r=p.executeQuery()){
            require(r.next(),NOT_FOUND,"프로젝트 없음"); boolean own=r.getLong(1)==u.getId();String status=r.getString(2);
            require(owner?own:(own||visible(status)),PERMISSION_DENIED,"프로젝트 접근 권한 없음");return status;
        }
    }
    static Object[] projectFields(String[] f,int offset)throws Exception{
        var start=LocalDate.parse(f[offset+4]);var end=LocalDate.parse(f[offset+5]);require(!end.isBefore(start),INVALID_INPUT,"종료일이 시작일보다 빠릅니다.");
        return new Object[]{text(f[offset]),text(f[offset+1]),f[offset+2],money(f[offset+3]),Date.valueOf(start),Date.valueOf(end)};
    }
    static String run(int t,String[] f,LoginUser u)throws Exception{
        if(t==11){arity(f,4);
            // 회원가입 실패 원인을 그대로 알려 준다. (SocketServer는 IllegalArgumentException을 "입력값을 확인하세요."로 바꾸므로 여기서 변환)
            try{AuthService.register(f[0],f[1],f[2],f[3],"SUPPORTER");}
            catch(NumberFormatException e){throw e;}
            catch(IllegalArgumentException e){throw new Failure(INVALID_INPUT,e.getMessage()==null||e.getMessage().isBlank()?"입력값을 확인하세요.":e.getMessage());}
            catch(SQLIntegrityConstraintViolationException e){throw new Failure(INVALID_INPUT,"이미 사용 중인 아이디 또는 전화번호입니다.");}
            return "회원가입 완료";}
        if(t==16){
            boolean pending=f.length==1&&f[0].equals("pending");
            if(!pending)arity(f,0);
            if(pending)require(u!=null&&"ADMIN".equals(u.getRole()),PERMISSION_DENIED,"관리자 전용입니다.");
            try(var c=DBConnection.getConnection()){
                String q="SELECT p.project_id,p.project_name,p.category,p.project_status FROM project p WHERE ";
                if(pending)return rows(c,q+"p.project_status='대기' AND (SELECT a.action FROM approval_history a WHERE a.project_id=p.project_id ORDER BY a.history_id DESC LIMIT 1) IN ('신청','재신청') ORDER BY p.project_id DESC");
                return rows(c,q+"p.project_status IN ('진행중','성공','종료','실패') OR p.maker_id=? ORDER BY p.project_id DESC LIMIT 100",u==null?-1:u.getId());
            }
        }
        // 프로젝트 상세 조회는 비로그인 사용자도 허용한다. (목록 조회와 동일하게 공개 상태만)
        if(t==3 && f.length==1 && u==null){
            int id=positive(f[0]);
            try(var c=DBConnection.getConnection()){
                require(exists(c,"SELECT 1 FROM project WHERE project_id=? AND project_status IN ('진행중','성공','종료','실패')",id),NOT_FOUND,"공개된 프로젝트가 아닙니다.");
                String amount="COALESCE((SELECT SUM(f.funding_amount) FROM funding f WHERE f.project_id=project.project_id AND f.funding_status='후원완료'),0)";
                String detail="SELECT project_id,project_name,category,description,target_amount,start_date,end_date,project_status,"+amount+" AS pledged_amount,ROUND(100*"+amount+"/NULLIF(target_amount,0),2) AS achievement_pct FROM project";
                return rows(c,detail+" WHERE project_id=?",id)+"\n\n"+rows(c,"SELECT reward_id,reward_name,description,required_amount,remaining_quantity FROM reward WHERE project_id=? ORDER BY reward_id LIMIT 50",id);
            }
        }
        if(u==null && t!=17 && t!=18)require(false,PERMISSION_DENIED,"로그인이 필요합니다.");
        if(t==2||t==7||t==8||t==9||t==12)require("MAKER".equals(u.getRole()),PERMISSION_DENIED,"메이커 전용입니다.");
        if(t==4||t==5||t==6||t==10||t==20||t==21)require("SUPPORTER".equals(u.getRole()),PERMISSION_DENIED,"후원자 전용입니다.");
        if(t==13||t==14) require("ADMIN".equals(u.getRole()),PERMISSION_DENIED,"관리자 전용입니다.");
        try(var c=DBConnection.getConnection()){
            if(t==4){arity(f,3);int id=positive(f[0]),rid=positive(f[1]),qty=positive(f[2]);project(c,id,u,false,false);
                require(exists(c,"SELECT 1 FROM reward WHERE project_id=? AND reward_id=?",id,rid),NOT_FOUND,"리워드 없음");
                try{return FundingService.applyReward(c,u.getId(),id,rid,qty).toPlainString();}catch(BusinessException e){throw new Failure(INVALID_STATE,e.getMessage());}
            }
            c.setAutoCommit(false);
            try{String result=execute(c,t,f,u);c.commit();return result;}catch(Exception e){try{c.rollback();}catch(SQLException r){e.addSuppressed(r);}throw e;}
        }
    }
    static String summary(Connection c,String where,Object... args)throws SQLException{
        String amount="COALESCE((SELECT SUM(f.funding_amount) FROM funding f WHERE f.project_id=p.project_id AND f.funding_status='후원완료'),0)";
        return rows(c,"SELECT p.project_id,p.project_name,p.project_status,u.name AS maker,p.target_amount,"+amount+" AS pledged_amount,ROUND(100*"+amount+"/NULLIF(p.target_amount,0),2) AS achievement_pct,(SELECT action FROM approval_history a WHERE a.project_id=p.project_id ORDER BY history_id DESC LIMIT 1) AS approval,(SELECT reason FROM approval_history a WHERE a.project_id=p.project_id ORDER BY history_id DESC LIMIT 1) AS reason FROM project p JOIN users u ON u.id=p.maker_id"+where+" ORDER BY p.project_id DESC LIMIT 50",args);
    }
    static String registerRewardImages(Connection c,String[] f,LoginUser u)throws Exception{
        require(f.length>=7&&(f.length-2)%5==0,FORMAT_ERROR,
                "사진 포함 리워드 필드 개수 오류 (수신 필드: "+f.length+", 프로젝트 ID 이후 필드는 5개씩 필요)");
        int id=positive(f[1]);require("대기".equals(project(c,id,u,true,true)),INVALID_STATE,"대기 중인 프로젝트만 처리할 수 있습니다.");
        String last=MakerService.findLastApprovalAction(c,id);
        require(last==null||last.equals("거절"),INVALID_STATE,"이미 신청했거나 처리된 프로젝트입니다.");
        var files=new java.util.ArrayList<java.nio.file.Path>();
        try{
            for(int i=2;i<f.length;i+=5){
                int qty=Integer.parseInt(f[i+3]);require(qty>=0,INVALID_INPUT,"수량은 0 이상이어야 합니다.");Integer q=qty==0?null:qty;
                String path=null;
                if(!f[i+4].isEmpty()){
                    byte[] bytes=java.util.Base64.getDecoder().decode(f[i+4]);
                    require(bytes.length>=8&&bytes.length<=2 * 1024 * 1024,INVALID_INPUT,"리워드 사진은 2 MiB 이하로 선택하세요.");
                    boolean jpeg=(bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255;
                    boolean png=java.util.Arrays.equals(java.util.Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10});
                    require(jpeg||png,INVALID_INPUT,"JPG 또는 PNG 사진만 등록할 수 있습니다.");
                    var dir=java.nio.file.Files.createDirectories(java.nio.file.Path.of("reward-images"));
                    var file=java.nio.file.Files.createTempFile(dir,"reward-",jpeg?".jpg":".png");files.add(file);
                    java.nio.file.Files.write(file,bytes);path="reward-images/"+file.getFileName();
                }
                update(c,"INSERT INTO reward(project_id,reward_name,description,required_amount,total_quantity,remaining_quantity,image_path) VALUES (?,?,?,?,?,?,?)",id,text(f[i]),text(f[i+1]),money(f[i+2]),q,q,path);
            }
            return "리워드 등록 완료";
        }catch(Exception e){for(var file:files)try{java.nio.file.Files.deleteIfExists(file);}catch(java.io.IOException x){e.addSuppressed(x);}throw e;}
    }
    static String execute(Connection c,int t,String[] f,LoginUser u)throws Exception{
        long uid=u==null?-1:u.getId();
        switch(t){
            case 2:{require(f.length==6||f.length==7,FORMAT_ERROR,"필드 개수 오류");var a=projectFields(f,0);
                if(f.length==6) update(c,"INSERT INTO project (project_name,category,description,target_amount,start_date,end_date,maker_id,project_status) VALUES (?,?,?,?,?,?,?,'대기')",a[0],a[1],a[2],a[3],a[4],a[5],uid);
                else {
                    byte[] bytes=java.util.Base64.getDecoder().decode(f[6]);
                    require(bytes.length>=8&&bytes.length<=10 * 1024 * 1024,INVALID_INPUT,"사진은 10 MiB 이하로 선택하세요.");
                    boolean jpeg=(bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255;
                    boolean png=java.util.Arrays.equals(java.util.Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10});
                    require(jpeg||png,INVALID_INPUT,"JPG 또는 PNG 사진만 등록할 수 있습니다.");
                    var dir=java.nio.file.Path.of("project-images");java.nio.file.Files.createDirectories(dir);
                    var file=dir.resolve(java.util.UUID.randomUUID()+(jpeg?".jpg":".png"));
                    try {java.nio.file.Files.write(file,bytes,java.nio.file.StandardOpenOption.CREATE_NEW);
                        update(c,"INSERT INTO project (project_name,category,description,target_amount,start_date,end_date,maker_id,project_status,image_path) VALUES (?,?,?,?,?,?,?,'대기',?)",a[0],a[1],a[2],a[3],a[4],a[5],uid,file.toString().replace('\\','/'));
                    }catch(Exception e){try{java.nio.file.Files.deleteIfExists(file);}catch(java.io.IOException cleanup){e.addSuppressed(cleanup);}throw e;}
                }
                try(var p=c.prepareStatement("SELECT LAST_INSERT_ID()");var r=p.executeQuery()){r.next();return r.getString(1);}
            }
            case 3:{
                String select="SELECT project_id,project_name,category,target_amount,start_date,end_date,project_status FROM project";
                if(f.length==0)return rows(c,select+" WHERE maker_id=? OR project_status IN ('진행중','성공','종료','실패') ORDER BY project_id DESC LIMIT 50",uid);
                if(f.length==2){String column=switch(f[0]){case "제목"->"project_name";case "카테고리"->"category";case "상태"->"project_status";default->throw new Failure(INVALID_INPUT,"검색 조건 오류");};
                    return rows(c,select+" WHERE (maker_id=? OR project_status IN ('진행중','성공','종료','실패')) AND "+column+(column.equals("project_name")?" LIKE ?":" = ?")+" ORDER BY project_id DESC LIMIT 50",uid,column.equals("project_name")?"%"+f[1]+"%":f[1]);}
                arity(f,1);int id=positive(f[0]);if(!"ADMIN".equals(u.getRole()))project(c,id,u,false,false);
                String amount="COALESCE((SELECT SUM(f.funding_amount) FROM funding f WHERE f.project_id=project.project_id AND f.funding_status='후원완료'),0)";
                String detail="SELECT project_id,project_name,category,description,target_amount,start_date,end_date,project_status,"+amount+" AS pledged_amount,ROUND(100*"+amount+"/NULLIF(target_amount,0),2) AS achievement_pct,CASE WHEN EXISTS (SELECT 1 FROM project_like l WHERE l.project_id=project.project_id AND l.supporter_id=?) THEN 1 ELSE 0 END AS liked FROM project";
                return rows(c,detail+" WHERE project_id=?",uid,id)+"\n\n"+rows(c,"SELECT reward_id,reward_name,description,required_amount,remaining_quantity FROM reward WHERE project_id=? ORDER BY reward_id LIMIT 50",id);
            }
            case 5:{arity(f,1);int id=positive(f[0]);project(c,id,u,false,true);
                require(!exists(c,"SELECT 1 FROM project_like WHERE project_id=? AND supporter_id=?",id,uid),INVALID_STATE,"이미 좋아요를 눌렀습니다.");
                update(c,"INSERT INTO project_like(project_id,supporter_id) VALUES (?,?)",id,uid);return "좋아요 등록 완료";
            }
            case 20:{arity(f,0);
                // 로그인한 서포터 본인이 누른 좋아요만 최신순으로 조회한다. 마지막 열 project_id는 GUI의 상세 보기·좋아요 취소용.
                String amount="COALESCE((SELECT SUM(fd.funding_amount) FROM funding fd WHERE fd.project_id=p.project_id AND fd.funding_status='후원완료'),0)";
                return rows(c,"SELECT p.project_name AS 프로젝트명,p.category AS 카테고리,p.project_status AS 상태,ROUND(100*"+amount+"/NULLIF(p.target_amount,0),2) AS 달성률,DATE_FORMAT(l.created_at,'%Y-%m-%d') AS 좋아요날짜,p.project_id AS project_id FROM project_like l JOIN project p ON p.project_id=l.project_id WHERE l.supporter_id=? ORDER BY l.created_at DESC,l.like_id DESC LIMIT 50",uid);
            }
            case 21:{arity(f,1);int id=positive(f[0]);
                int removed=update(c,"DELETE FROM project_like WHERE project_id=? AND supporter_id=?",id,uid);
                require(removed==1,NOT_FOUND,"좋아요하지 않은 프로젝트입니다.");
                return "좋아요 취소 완료";
            }
            case 6:{arity(f,3);int id=positive(f[0]),rating=positive(f[1]);String content=text(f[2]);require(rating<=5,INVALID_INPUT,"별점 범위는 1~5입니다.");String state=project(c,id,u,false,true);
                require((state.equals("성공")||state.equals("종료"))&&exists(c,"SELECT 1 FROM funding WHERE project_id=? AND supporter_id=? AND funding_status='후원완료'",id,uid),INVALID_STATE,"리뷰를 작성할 수 없는 상태입니다.");
                if(exists(c,"SELECT 1 FROM project_review WHERE project_id=? AND supporter_id=?",id,uid))update(c,"UPDATE project_review SET rating=?,content=? WHERE project_id=? AND supporter_id=?",rating,content,id,uid);
                else update(c,"INSERT INTO project_review(rating,content,project_id,supporter_id) VALUES (?,?,?,?)",rating,content,id,uid);return "리뷰 저장 완료";
            }
            case 7: case 8: case 9:{
                if(t==8&&f.length>0&&f[0].equals("images-v1"))return registerRewardImages(c,f,u);
                if(t==8)require(f.length>=5&&(f.length-1)%4==0,FORMAT_ERROR,"리워드 필드 개수 오류");else arity(f,t==7?7:1);
                int id=positive(f[0]);require("대기".equals(project(c,id,u,true,true)),INVALID_STATE,"대기 중인 프로젝트만 처리할 수 있습니다.");
                String last=MakerService.findLastApprovalAction(c,id);
                require(last==null||last.equals("거절"),INVALID_STATE,"이미 신청했거나 처리된 프로젝트입니다.");
                if(t==7){var a=projectFields(f,1);update(c,"UPDATE project SET project_name=?,category=?,description=?,target_amount=?,start_date=?,end_date=? WHERE project_id=?",a[0],a[1],a[2],a[3],a[4],a[5],id);return "프로젝트 수정 완료";}
                if(t==8){for(int i=1;i<f.length;i+=4){int qty=Integer.parseInt(f[i+3]);require(qty>=0,INVALID_INPUT,"수량은 0 이상이어야 합니다.");Integer q=qty==0?null:qty;
                    update(c,"INSERT INTO reward(project_id,reward_name,description,required_amount,total_quantity,remaining_quantity) VALUES (?,?,?,?,?,?)",id,text(f[i]),text(f[i+1]),money(f[i+2]),q,q);}return "리워드 등록 완료";}
                require(exists(c,"SELECT COUNT(*) FROM reward WHERE project_id=? HAVING COUNT(*)>=2 AND SUM(total_quantity>0)>=1",id),REWARD_CONDITION,"리워드 2개 이상, 수량 한정 리워드 1개 이상 필요");
                String action=last==null?"신청":"재신청";update(c,"INSERT INTO approval_history(project_id,actor_id,action,reason) VALUES (?,?,?,NULL)",id,uid,action);return "승인 "+action+" 완료";
            }
            case 10:{arity(f,0);return rows(c,"SELECT p.project_name AS 프로젝트명,r.reward_name AS 리워드명,f.quantity AS 후원수량,f.funding_amount AS 후원금액,f.funding_date AS 후원일시,f.funding_status AS 후원상태,p.project_id AS project_id FROM funding f JOIN project p ON p.project_id=f.project_id LEFT JOIN reward r ON r.reward_id=f.reward_id WHERE f.supporter_id=? ORDER BY f.funding_date DESC LIMIT 50",uid);}
            case 12:{arity(f,1);int id=positive(f[0]);project(c,id,u,true,false);
                return summary(c," WHERE p.project_id=?",id)+"\n\n"+rows(c,"SELECT reward_id,reward_name,total_quantity,remaining_quantity,COALESCE((SELECT SUM(quantity) FROM funding f WHERE f.reward_id=r.reward_id AND f.funding_status='후원완료'),0) AS pledged_quantity FROM reward r WHERE project_id=?",id)+"\n\n"+rows(c,"SELECT funding_id,reward_id,quantity,funding_amount,funding_status FROM funding WHERE project_id=? ORDER BY funding_id DESC LIMIT 50",id);}
            case 13:{arity(f,3);int id=positive(f[0]);String action=f[1];require(action.equals("승인")||action.equals("거절"),INVALID_INPUT,"승인 또는 거절을 입력하세요.");
                if(action.equals("거절"))text(f[2]);
                require(exists(c,"SELECT 1 FROM project WHERE project_id=? AND project_status='대기' FOR UPDATE",id),INVALID_STATE,"승인 대기 프로젝트가 아닙니다.");
                String last=MakerService.findLastApprovalAction(c,id);require("신청".equals(last)||"재신청".equals(last),INVALID_STATE,"미신청 또는 이미 처리된 프로젝트입니다.");
                if(action.equals("승인"))update(c,"UPDATE project SET project_status='진행중' WHERE project_id=?",id);
                update(c,"INSERT INTO approval_history(project_id,actor_id,action,reason) VALUES (?,?,?,?)",id,uid,action,action.equals("거절")?f[2]:null);return action+" 완료";}
            case 14:{if(f.length==0)return summary(c,"",new Object[0]);arity(f,1);int id=positive(f[0]);return summary(c," WHERE p.project_id=?",id)+"\n\n"+rows(c,"SELECT f.funding_id,u.id,u.login_id,u.name,f.quantity,f.funding_amount,f.funding_status FROM funding f JOIN users u ON u.id=f.supporter_id WHERE f.project_id=? ORDER BY f.funding_id DESC LIMIT 50",id);}
            case 15:{arity(f,0);return rows(c,"SELECT id,login_id,name,role,phone_number,bio FROM users WHERE id=?",uid);}
            case 17:{arity(f,2);int id=positive(f[0]), rid=positive(f[1]);
                if(u==null){require(exists(c,"SELECT 1 FROM project WHERE project_id=? AND project_status IN ('진행중','성공','종료','실패')",id),PERMISSION_DENIED,"공개된 프로젝트가 아닙니다.");}
                else if(!"ADMIN".equals(u.getRole()))project(c,id,u,false,false);
                String stored;
                try(var stmt=stmt(c,"SELECT image_path FROM reward WHERE project_id=? AND reward_id=?",id,rid);var rs=stmt.executeQuery()){
                    require(rs.next(),NOT_FOUND,"리워드 없음");stored=rs.getString(1);
                }
                if(stored!=null&&!stored.isBlank()){
                    require(stored.matches("reward-images/[A-Za-z0-9_-]+[.](jpg|png)"),NOT_FOUND,"리워드 사진 경로 오류");
                    var path=java.nio.file.Path.of(stored);
                    require(java.nio.file.Files.isRegularFile(path,java.nio.file.LinkOption.NOFOLLOW_LINKS),NOT_FOUND,"리워드 사진 파일 없음");
                    try(var input=java.nio.file.Files.newInputStream(path,java.nio.file.LinkOption.NOFOLLOW_LINKS)){
                        byte[] bytes=input.readNBytes(2 * 1024 * 1024 + 1);
                        require(bytes.length<=2 * 1024 * 1024,INVALID_INPUT,"리워드 사진 크기 초과");
                        return java.util.Base64.getEncoder().encodeToString(bytes);
                    }
                }
                require(exists(c,"SELECT 1 FROM reward WHERE project_id=? AND reward_id=?",id,rid),NOT_FOUND,"리워드 없음");
                for(String ext:new String[]{".jpg",".png"}){
                    var path=java.nio.file.Path.of("reward-images",rid+ext);
                    if(java.nio.file.Files.isRegularFile(path))try(var image=java.nio.file.Files.newInputStream(path)){
                        byte[] data=image.readNBytes(48001);require(data.length<=48000,INVALID_INPUT,"리워드 사진은 48 KB 이하로 저장하세요.");
                        return java.util.Base64.getEncoder().encodeToString(data);
                    }
                }throw new Failure(NOT_FOUND,"등록된 리워드 사진이 없습니다.");}
            case 18:{arity(f,1);int id=positive(f[0]);
                if(u==null){require(exists(c,"SELECT 1 FROM project WHERE project_id=? AND project_status IN ('진행중','성공','종료','실패')",id),PERMISSION_DENIED,"공개된 프로젝트가 아닙니다.");}
                else if(!"ADMIN".equals(u.getRole()))project(c,id,u,false,false);
                String path;try(var p=stmt(c,"SELECT image_path FROM project WHERE project_id=?",id);var r=p.executeQuery()){require(r.next(),NOT_FOUND,"프로젝트 없음");path=r.getString(1);}
                require(path!=null&&path.matches("project-images/[A-Za-z0-9_-]+[.](jpg|png)"),NOT_FOUND,"등록된 대표 사진이 없습니다.");
                var file=java.nio.file.Path.of(path);require(java.nio.file.Files.isRegularFile(file,java.nio.file.LinkOption.NOFOLLOW_LINKS),NOT_FOUND,"사진 파일 없음");
                try(var in=java.nio.file.Files.newInputStream(file,java.nio.file.LinkOption.NOFOLLOW_LINKS)){byte[] bytes=in.readNBytes(10 * 1024 * 1024 + 1);require(bytes.length<=10 * 1024 * 1024,INVALID_INPUT,"사진 크기 초과");return java.util.Base64.getEncoder().encodeToString(bytes);}
            }
            case 19:{
                arity(f,6);
                String newName=text(f[0]).trim();
                String phone=text(f[1]).trim();
                String bio=f[2].trim();
                String currentPassword=f[3];
                String newPassword=f[4];
                String confirmPassword=f[5];

                require(newName.length()<=50,INVALID_INPUT,"이름은 50자 이하로 입력하세요.");
                String digits=phone.replaceAll("[- ]","");
                require(digits.matches("[0-9]{10,11}"),INVALID_INPUT,"전화번호는 숫자 10~11자리로 입력하세요.");
                if(digits.length()==11) phone=digits.substring(0,3)+"-"+digits.substring(3,7)+"-"+digits.substring(7);
                else phone=digits.substring(0,3)+"-"+digits.substring(3,6)+"-"+digits.substring(6);
                require(bio.length()<=500,INVALID_INPUT,"자기소개는 500자 이하로 입력하세요.");
                require(!exists(c,"SELECT 1 FROM users WHERE REPLACE(REPLACE(phone_number,'-',''),' ','')=? AND id<>?",digits,uid),INVALID_INPUT,"이미 사용 중인 전화번호입니다.");

                boolean changingPassword=!currentPassword.isEmpty()||!newPassword.isEmpty()||!confirmPassword.isEmpty();
                if(changingPassword){
                    require(!currentPassword.isBlank()&&!newPassword.isBlank()&&!confirmPassword.isBlank(),INVALID_INPUT,"비밀번호를 변경하려면 현재 비밀번호, 새 비밀번호, 새 비밀번호 확인을 모두 입력하세요.");
                    require(newPassword.equals(confirmPassword),INVALID_INPUT,"새 비밀번호와 비밀번호 확인이 일치하지 않습니다.");
                    require(newPassword.length()>=8&&newPassword.length()<=72,INVALID_INPUT,"새 비밀번호는 8~72자로 입력하세요.");
                    AuthService.changePassword(c,uid,currentPassword,newPassword);
                }

                int changed=update(c,"UPDATE users SET name=?, phone_number=?, bio=? WHERE id=?",newName,phone,bio,uid);
                require(changed==1,NOT_FOUND,"회원정보를 찾을 수 없습니다.");
                return changingPassword?"개인정보와 비밀번호가 수정되었습니다.":"개인정보 수정 완료";
            }
            default:throw new Failure(FORMAT_ERROR,"지원하지 않는 TYPE");
        }
    }
}
