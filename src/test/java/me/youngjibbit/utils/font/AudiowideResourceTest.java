package me.youngjibbit.utils.font;

import java.awt.Font;
import java.io.*;
import java.security.MessageDigest;
import org.junit.Test;
import static org.junit.Assert.*;

public class AudiowideResourceTest {
    private static byte[] resource(String path)throws Exception {
        try(InputStream in=AudiowideResourceTest.class.getResourceAsStream(path)){
            assertNotNull(path,in);ByteArrayOutputStream out=new ByteArrayOutputStream();
            byte[] buffer=new byte[4096];int length;while((length=in.read(buffer))!=-1)out.write(buffer,0,length);
            return out.toByteArray();
        }
    }
    private static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format("%02x",b&255));return s.toString();}
    @Test public void originalStaticFontLoadsWithAwtAndContainsNeutron() throws Exception {
        byte[] bytes=resource("/assets/minecraft/Audiowide-Regular.ttf");
        assertEquals(69916,bytes.length);
        assertEquals("c7c0f2b0f6fad8c623e31772ce79f94a4edb9321ffce9fce978ea892d20ae730",hex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        Font face=Font.createFont(Font.TRUETYPE_FONT,new ByteArrayInputStream(bytes));
        assertEquals("Audiowide-Regular",face.getPSName());assertEquals(-1,face.canDisplayUpTo("Neutron"));
        DataInputStream sfnt=new DataInputStream(new ByteArrayInputStream(bytes));
        sfnt.readInt();int count=sfnt.readUnsignedShort();sfnt.skipBytes(6);
        for(int i=0;i<count;i++){assertNotEquals("Variable-font axis table",0x66766172,sfnt.readInt());sfnt.skipBytes(12);}
    }
    @Test public void originalOflAndReservedFontNoticeArePackaged() throws Exception {
        byte[] bytes=resource("/META-INF/licenses/Audiowide-OFL.txt");
        assertEquals(4430,bytes.length);
        MessageDigest sha=MessageDigest.getInstance("SHA-1");
        sha.update(("blob "+bytes.length+"\0").getBytes("UTF-8"));sha.update(bytes);
        assertEquals("19bb4adffab57778a892f98e9552441208896226",hex(sha.digest()));
        String license=new String(bytes,"UTF-8");
        assertTrue(license.contains("SIL OPEN FONT LICENSE Version 1.1"));
        assertTrue(license.contains("Reserved Font Names \"Audiowide\""));
    }
    @Test public void brandingDoesNotReplaceBodyFontsAndMissingAssetFallsBack() {
        try(FontManager fonts=new FontManager()){
            TTFFontRenderer body=fonts.getFont("DmSansMedium 18");
            assertSame(fonts.getLocalFont("Audiowide-Regular 18"),fonts.getWordmarkFont("Neutron","DmSansMedium 18"));
            assertSame(body,fonts.getWordmarkFont("Session","DmSansMedium 18"));
            assertSame(body,fonts.getWordmarkFont("Привет","DmSansMedium 18"));
        }
        try(FontManager missing=new FontManager(){@Override protected InputStream openFont(String file){return file.equals("Audiowide-Regular.ttf")?null:super.openFont(file);}}){
            assertSame(missing.getFont("DmSansMedium 18"),missing.getWordmarkFont("Neutron","DmSansMedium 18"));
        }
    }
}
