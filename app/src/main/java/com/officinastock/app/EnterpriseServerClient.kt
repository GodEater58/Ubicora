package com.officinastock.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.UUID
import javax.net.ssl.HttpsURLConnection

/** Explicit opt-in sync transport, no background uploads or saved credentials.
 * Server MUST authenticate each user and authorize tenant/warehouse/resource permissions.
 * Never treat local staff-directory role labels as permissions.
 */
object EnterpriseSyncProtocol {
    const val VERSION=1
    const val ENDPOINT="/v1/sync/events"
    data class Target(val baseUrl:String,val tenantUuid:String) {
        fun validatedBase():String {
            val uri=URI(baseUrl.trim())
            require(uri.scheme.equals("https",true) && !uri.host.isNullOrBlank() &&
                uri.userInfo==null && uri.query==null && uri.fragment==null &&
                uri.port in -1..65535 && uri.port!=0) {
                "Server non valido: è richiesto HTTPS, senza credenziali nell'indirizzo"
            }
            require(UUID.fromString(tenantUuid).toString().equals(tenantUuid,true)){"Identificativo azienda non valido"}
            return uri.toString().trimEnd('/')
        }
    }
    fun batch(target:Target,events:List<PendingSyncEvent>):ByteArray {
        target.validatedBase()
        require(events.isNotEmpty() && events.size<=100){"Inviare da 1 a 100 eventi"}
        require(events.map{it.profileId}.distinct().size==1){"Non mischiare profili locali"}
        val payload=JSONObject().put("protocol_version",VERSION).put("tenant_uuid",target.tenantUuid)
        val list=JSONArray()
        events.forEach {e->
            require(UUID.fromString(e.id).toString().equals(e.id,true)){"Evento non valido"}
            require(UUID.fromString(e.entityUuid).toString().equals(e.entityUuid,true)){"Entità non valida"}
            require(e.operation in setOf("CREATE","UPDATE","DELETE")){"Operazione non valida"}
            list.put(JSONObject().put("event_id",e.id).put("entity_uuid",e.entityUuid)
                .put("resource",e.resource).put("operation",e.operation)
                .put("created_at_ms",e.createdAt).put("data",JSONObject(e.payload)))
        }
        val bytes=payload.put("events",list).toString().toByteArray(Charsets.UTF_8)
        require(bytes.size<=512*1024){"Batch di sincronizzazione eccessivo"}
        return bytes
    }
}

/** HTTPS client adapter only. No endpoint is configured in the app yet.
 * Caller owns auth token in memory, invokes this on IO dispatcher, then acknowledges
 * only IDs explicitly returned by the authenticated server.
 */
class EnterpriseServerClient {
    fun push(target:EnterpriseSyncProtocol.Target,bearerToken:String,
             events:List<PendingSyncEvent>):List<String> {
        require(bearerToken.length in 20..8192 &&
            bearerToken.none{it=='\r'||it=='\n'}){"Token di sessione mancante o non valido"}
        val body=EnterpriseSyncProtocol.batch(target,events)
        val url=java.net.URL(target.validatedBase()+EnterpriseSyncProtocol.ENDPOINT)
        val conn=url.openConnection() as HttpsURLConnection
        conn.instanceFollowRedirects=false
        conn.connectTimeout=10_000
        conn.readTimeout=15_000
        conn.requestMethod="POST"
        conn.setRequestProperty("Accept","application/json")
        conn.setRequestProperty("Content-Type","application/json; charset=utf-8")
        conn.setRequestProperty("Authorization","Bearer $bearerToken")
        conn.doOutput=true
        return try {
            conn.outputStream.use{it.write(body)}
            require(conn.responseCode==200){"Server non ha confermato la sincronizzazione (HTTP ${conn.responseCode})"}
            val response=ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val block=ByteArray(4096)
                var size=0
                while(true){
                    val n=input.read(block)
                    if(n<0)break
                    size+=n
                    require(size<=64*1024){"Risposta server troppo grande"}
                    response.write(block,0,n)
                }
            }
            val result=JSONObject(response.toString(Charsets.UTF_8.name()))
            require(result.optInt("protocol_version")==EnterpriseSyncProtocol.VERSION){
                "Versione protocollo server non compatibile"
            }
            val confirmed=result.getJSONArray("accepted_event_ids")
            val valid=events.mapTo(HashSet()){it.id}
            val ack=buildList{
                for(i in 0 until confirmed.length()){
                    val id=confirmed.getString(i)
                    require(id in valid && id !in this){"Conferma server non valida"}
                    add(id)
                }
            }
            ack
        } finally { conn.disconnect() }
    }
}
