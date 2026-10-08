import com.officinastock.app.BackupCipher
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream

fun main() {
    val sample=ByteArray(160_000) { (it*37+17).toByte() }
    val output=ByteArrayOutputStream()
    BackupCipher.encrypt(output,"PasswordDiProva-123").use { it.write(sample) }
    val encoded=output.toByteArray()
    check(!encoded.copyOfRange(0,8).contentEquals(sample.copyOfRange(0,8)))
    val decoded=BackupCipher.decrypt(ByteArrayInputStream(encoded),"PasswordDiProva-123").readBytes()
    check(sample.contentEquals(decoded))
    val bad=encoded.clone().apply {this[this.size/2]=(this[this.size/2].toInt() xor 4).toByte()}
    check(runCatching {BackupCipher.decrypt(ByteArrayInputStream(bad),"PasswordDiProva-123").readBytes()}.isFailure)
    check(runCatching {BackupCipher.decrypt(ByteArrayInputStream(encoded),"PasswordSbagliata").readBytes()}.isFailure)
    check(runCatching {BackupCipher.decrypt(ByteArrayInputStream(encoded.copyOf(encoded.size-8)),"PasswordDiProva-123").readBytes()}.isFailure)
    println("PASS: AES-GCM roundtrip, wrong password, tampered file, truncated file")
}
