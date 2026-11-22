package org.example.socket;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
public final class SocketClient {
 static final String[] INPUT={"","아이디 | 비밀번호","제목 | 카테고리 | 설명 | 목표금액 | 시작일 | 종료일","프로젝트ID / 빈 줄(목록) / 제목·카테고리·상태 중 하나 | 검색값","프로젝트ID | 리워드ID | 수량","프로젝트ID","프로젝트ID | 별점 | 내용","프로젝트ID | 제목 | 카테고리 | 설명 | 목표금액 | 시작일 | 종료일","프로젝트ID | 리워드명 | 설명 | 금액 | 수량 (무제한 0; 리워드 항목 반복 가능)","프로젝트ID","빈 줄","아이디 | 비밀번호 | 이름 | 전화번호","프로젝트ID","프로젝트ID | 승인 또는 거절 | 사유 (승인은 빈 필드)","빈 줄(전체), 또는 프로젝트ID(후원자 상세)","빈 줄"};
 public static void main(String[] args)throws IOException{
  String role="";
  int port=args.length==0?9090:Integer.parseInt(args[0]);
  try(var socket=new Socket(InetAddress.getLoopbackAddress(),port);var in=new DataInputStream(socket.getInputStream());var out=new DataOutputStream(socket.getOutputStream());var scanner=new Scanner(System.in,StandardCharsets.UTF_8)){
   socket.setSoTimeout(120000);
   while(true){
    System.out.println(switch(role){
     case "MAKER" -> "2 프로젝트 등록 / 3 조회 / 7 수정 / 8 리워드 등록 / 9 승인 신청 / 12 내 프로젝트 현황 / 15 내 계정";
     case "SUPPORTER" -> "3 조회 / 4 후원 / 5 좋아요 / 6 리뷰 / 10 후원 내역 / 15 내 계정";
     case "ADMIN" -> "3 조회 / 13 승인·거절 / 14 전체 펀딩·승인 현황 / 15 내 계정";
     default -> "11 후원자 회원가입";
    });
    System.out.println("1 로그인·계정 전환 / 0 종료");
    if(!scanner.hasNextLine())return;
    int t;try{t=Integer.parseInt(scanner.nextLine().trim());}catch(NumberFormatException e){continue;}
    if(t==0)return;if(t<1||t>15)continue;
    System.out.println(INPUT[t]+" (구분자 |, 날짜 YYYY-MM-DD)");
    if(!scanner.hasNextLine())return;
    new Packet(t,Packet.REQUEST,scanner.nextLine().replaceAll(" *\\| *","\t")).write(out);
    Packet p=Packet.read(in);if(p==null){System.out.println("서버 연결 종료");return;}
    if(p.type()!=t)throw new IOException("응답 TYPE 불일치");
    if(t==1)role=p.code()==Packet.SUCCESS?p.body().split("\t",2)[0]:"";
    System.out.printf("TYPE=0x%04X CODE=0x%04X%n%s%n",p.type(),p.code(),p.body());
   }
  }
 }
}
