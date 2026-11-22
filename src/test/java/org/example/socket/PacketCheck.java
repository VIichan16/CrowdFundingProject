package org.example.socket;
import java.io.*;
import java.util.HexFormat;
public final class PacketCheck {
 public static void main(String[] args)throws Exception{
  var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
  var first=new Packet(3,0,"한글"); var second=new Packet(10,0,"");
  first.write(out);second.write(out);
  if(!HexFormat.of().formatHex(bytes.toByteArray()).equals("0003000000000006ed959ceab880000a000000000000"))throw new AssertionError("wire bytes");
  var in=new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
  if(!first.equals(Packet.read(in))||!second.equals(Packet.read(in))||Packet.read(in)!=null)throw new AssertionError("round trip");
  for(String hex:new String[]{"0001000000010001","0001000000000002ff","0001000000000001ff","00"}){
   try{Packet.read(new DataInputStream(new ByteArrayInputStream(HexFormat.of().parseHex(hex))));throw new AssertionError("bad frame accepted");}catch(Packet.Format expected){}
  }
  try{Operations.run(2,new String[0],null);throw new AssertionError("unauthenticated request accepted");}catch(Operations.Failure e){if(e.code!=Packet.PERMISSION_DENIED)throw e;}
  System.out.println("PacketCheck OK");
 }
}
