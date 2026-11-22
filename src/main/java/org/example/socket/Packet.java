package org.example.socket;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
record Packet(int type,int code,String body) {
 static final int REQUEST=0,SUCCESS=1,PERMISSION_DENIED=2,INVALID_INPUT=3,REWARD_CONDITION=4,DATABASE_ERROR=5,NOT_FOUND=6,INVALID_STATE=7,FORMAT_ERROR=8, MAX=3 * 1024 * 1024;
 static final class Format extends IOException { final int type; Format(int type){this.type=type;} }
 static Packet read(DataInputStream in)throws IOException {
  int first=in.read(); if(first<0)return null;
  int t=0;
  try { t=(first<<8)|in.readUnsignedByte(); int c=in.readUnsignedShort(), n=in.readInt();
   if(n<0||n>MAX)throw new Format(t);
   byte[] b=new byte[n]; in.readFully(b);
   return new Packet(t,c,StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(b)).toString());
  }catch(EOFException|CharacterCodingException e){throw new Format(t);}
 }
 void write(DataOutputStream out)throws IOException {
  byte[] b=body.getBytes(StandardCharsets.UTF_8); if(b.length>MAX)throw new Format(type);
  out.writeShort(type);out.writeShort(code);out.writeInt(b.length);out.write(b);out.flush();
 }
}
