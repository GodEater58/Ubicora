package com.officinastock.app

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** AES-256-GCM a blocchi autenticati. Non bufferizza l'intero backup in RAM. */
object BackupCipher {
    private val signature="OSTOCKB2".toByteArray(Charsets.US_ASCII)
    private const val CHUNK=65536
    private const val ROUNDS=220_000
    private const val TAG=16
    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        require(password.length>=8) {"Password backup: almeno 8 caratteri"}
        val spec=PBEKeySpec(password.toCharArray(),salt,ROUNDS,256)
        return try {
            val material=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(material,"AES").also {material.fill(0)}
        } finally {spec.clearPassword()}
    }
    private fun nonce(prefix:ByteArray,sequence:Int):ByteArray = ByteArray(12).also {
        prefix.copyInto(it)
        it[8]=(sequence ushr 24).toByte()
        it[9]=(sequence ushr 16).toByte()
        it[10]=(sequence ushr 8).toByte()
        it[11]=sequence.toByte()
    }
    private fun cipher(mode:Int,key:SecretKeySpec,prefix:ByteArray,sequence:Int,length:Int):Cipher {
        val result=Cipher.getInstance("AES/GCM/NoPadding")
        result.init(mode,key,GCMParameterSpec(128,nonce(prefix,sequence)))
        result.updateAAD(signature)
        result.updateAAD(byteArrayOf((sequence ushr 24).toByte(),(sequence ushr 16).toByte(),(sequence ushr 8).toByte(),sequence.toByte()))
        result.updateAAD(byteArrayOf((length ushr 24).toByte(),(length ushr 16).toByte(),(length ushr 8).toByte(),length.toByte()))
        return result
    }
    fun encrypt(output:OutputStream,password:String):OutputStream {
        val salt=ByteArray(16).also{SecureRandom().nextBytes(it)}
        val prefix=ByteArray(8).also{SecureRandom().nextBytes(it)}
        val secret=key(password,salt)
        val wire=DataOutputStream(output)
        wire.write(signature);wire.write(salt);wire.write(prefix)
        return object:OutputStream() {
            private val buffer=ByteArray(CHUNK)
            private var used=0
            private var sequence=0
            private var closed=false
            private fun frame(length:Int) {
                if(sequence==Int.MAX_VALUE)throw IOException("Limite backup superato")
                val block=cipher(Cipher.ENCRYPT_MODE,secret,prefix,sequence++,length).doFinal(buffer,0,length)
                wire.writeInt(length)
                wire.write(block)
                used=0
            }
            override fun write(b:Int) {
                buffer[used++]=b.toByte()
                if(used==CHUNK)frame(used)
            }
            override fun write(b:ByteArray,off:Int,len:Int) {
                if(off<0||len<0||off>b.size-len)throw IndexOutOfBoundsException()
                var start=off;var remaining=len
                while(remaining>0) {
                    val n=minOf(remaining,CHUNK-used)
                    b.copyInto(buffer,used,start,start+n)
                    used+=n;start+=n;remaining-=n
                    if(used==CHUNK)frame(used)
                }
            }
            override fun flush(){wire.flush()}
            override fun close() {
                if(closed)return
                try {
                    if(used>0)frame(used)
                    frame(0) // Blocco di fine stream autenticato: rileva archivi troncati.
                    wire.flush()
                } finally {closed=true;wire.close()}
            }
        }
    }
    /** Ogni blocco viene verificato PRIMA di essere consegnato al chiamante. */
    fun decrypt(input:InputStream,password:String):InputStream {
        val wire=DataInputStream(input)
        val header=ByteArray(8);wire.readFully(header)
        require(header.contentEquals(signature)) {"Backup incompatibile o non riconosciuto"}
        val salt=ByteArray(16);wire.readFully(salt)
        val prefix=ByteArray(8);wire.readFully(prefix)
        val secret=key(password,salt)
        return object:InputStream() {
            private var buffer=ByteArray(0)
            private var pos=0
            private var sequence=0
            private var finished=false
            private fun next():Boolean {
                if(finished)return false
                if(sequence==Int.MAX_VALUE)throw IOException("Limite backup superato")
                val length=try {wire.readInt()}catch(e:Exception){throw IOException("Backup incompleto",e)}
                require(length in 0..CHUNK){"Blocco del backup non valido"}
                val ciphertext=ByteArray(length+TAG)
                wire.readFully(ciphertext)
                try {
                    buffer=cipher(Cipher.DECRYPT_MODE,secret,prefix,sequence++,length).doFinal(ciphertext)
                } catch(e:Exception){throw IOException("Password errata o backup danneggiato",e)}
                pos=0
                if(length==0) {
                    require(wire.read()==-1){"Dati aggiuntivi non validi dopo il backup"}
                    finished=true
                    return false
                }
                return true
            }
            override fun read():Int {
                if(pos>=buffer.size&&!next())return -1
                return buffer[pos++].toInt() and 255
            }
            override fun read(b:ByteArray,off:Int,len:Int):Int {
                if(off<0||len<0||off>b.size-len)throw IndexOutOfBoundsException()
                if(len==0)return 0
                if(pos>=buffer.size&&!next())return -1
                val n=minOf(len,buffer.size-pos)
                buffer.copyInto(b,off,pos,pos+n)
                pos+=n
                return n
            }
            override fun close(){wire.close()}
        }
    }
}
