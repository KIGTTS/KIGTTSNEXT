package com.kigtts.next.media

import java.io.File
import java.io.RandomAccessFile
import java.io.StringReader
import org.xmlpull.v1.XmlPullParser

/** Google Motion Photo container and legacy MicroVideo. Verify a real ISO-BMFF clip before treating a still as live. */
object MotionPhoto {
    data class Clip(val offset: Long, val length: Long)
    fun locate(file: File): Clip? = runCatching {
        val head = file.inputStream().use { input ->
            val bytes=ByteArray(minOf(file.length(),4L*1024*1024).toInt()); var offset=0
            while(offset<bytes.size) { val read=input.read(bytes,offset,bytes.size-offset); if(read<0)break; offset+=read }; String(bytes,0,offset,Charsets.ISO_8859_1)
        }
        val begin=head.indexOf("<x:xmpmeta"); val end=head.indexOf("</x:xmpmeta>",begin)
        if(begin<0 || end<begin) return@runCatching null
        val xml=head.substring(begin,end+12)
        if(xml.contains("<!DOCTYPE",ignoreCase=true)||xml.contains("<!ENTITY",ignoreCase=true))return@runCatching null
        val parser=android.util.Xml.newPullParser().apply { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);setInput(StringReader(xml)) }
        var modern: Long?=null; var legacy: Long?=null; var motion: Int?=null
        val camera="http://ns.google.com/photos/1.0/camera/";val item="http://ns.google.com/photos/1.0/container/item/"
        while(parser.eventType!=XmlPullParser.END_DOCUMENT) {
            if(parser.eventType==XmlPullParser.START_TAG) {
                var semantic="";var length:Long?=null
                for(a in 0 until parser.attributeCount) {
                    val name=parser.getAttributeName(a);val namespace=parser.getAttributeNamespace(a);val value=parser.getAttributeValue(a)
                    if(namespace==camera) when(name) { "MotionPhoto" -> motion=value.toIntOrNull();"MicroVideoOffset" -> legacy=value.toLongOrNull() }
                    if(namespace==item) when(name) { "Semantic" -> semantic=value;"Length" -> length=value.toLongOrNull() }
                }
                if(semantic=="MotionPhoto")modern=length
                if(parser.namespace==camera&&parser.name in setOf("MotionPhoto","MicroVideoOffset")) {
                    val name=parser.name;val value=parser.nextText().trim()
                    if(name=="MotionPhoto")motion=value.toIntOrNull() else legacy=value.toLongOrNull()
                }
            }
            parser.next()
        }
        if(motion!=null && motion!=1) return@runCatching null
        val size=modern ?: legacy ?: return@runCatching null
        if(size<16 || size>=file.length()) return@runCatching null
        val clip=Clip(file.length()-size,size)
        RandomAccessFile(file,"r").use { input -> input.seek(clip.offset); val box=input.readInt().toLong() and 0xffffffffL
            val type=ByteArray(4).also(input::readFully); if(String(type,Charsets.US_ASCII)!="ftyp" || box !in 8..size) return@runCatching null }
        clip
    }.getOrNull()
    fun extract(file: File, destination: File): Boolean {
        val clip=locate(file) ?: return false
        RandomAccessFile(file,"r").use { input -> input.seek(clip.offset); destination.outputStream().use { output ->
            val buffer=ByteArray(64*1024); var remaining=clip.length
            while(remaining>0) { val count=input.read(buffer,0,minOf(buffer.size.toLong(),remaining).toInt()); check(count>0); output.write(buffer,0,count);remaining-=count }
        } }; return true
    }
}
