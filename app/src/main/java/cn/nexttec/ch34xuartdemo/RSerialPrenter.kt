package cn.nexttec.ch34xuartdemo

import RSerial.RSerialGrpc
import RSerial.RSerialOuterClass
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.protobuf.ByteString
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusRuntimeException
import java.util.*

const val ADDR = "39.108.51.140"
const val PORT = 17626


interface IGRPCPresenter {
    fun onServerStatusUpdate(ret: RSerialOuterClass.ServerStatus?)
    fun onCreateSessionResult(ret: RSerialOuterClass.Ack?)
    fun onJoinSessionResult(ret: RSerialOuterClass.Ack?)
    fun onWriteResult(ret: RSerialOuterClass.Ack?)
    fun onWriteTestResult(ret: RSerialOuterClass.Ack?)
    fun onReadResult(ret: RSerialOuterClass.ByteData?)
    fun onServerUnreachable(reach:Boolean)
    fun onExitSession(sessionId: String?)

}

class RSerialPrenter(val context: Context, val callback: IGRPCPresenter) {

    private lateinit var stub: RSerialGrpc.RSerialBlockingStub
    private var channel: ManagedChannel
    private var sessionId:String ="0"
    private var preferences: SharedPreferences = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE)

    init {
        sessionId = preferences.getString(SESSION,"0")
        channel = ManagedChannelBuilder.forAddress(ADDR, PORT)
                .usePlaintext(true)
                .build()

        synchronized(this) {
            if (channel.isShutdown || channel.isTerminated) {
                channel = ManagedChannelBuilder.forAddress(ADDR, PORT)
                        .usePlaintext(true)
                        .build()
            }

            stub = RSerialGrpc.newBlockingStub(channel)
        }
        //keepAlive()
    }

    fun keepAlive() {
        var isServerReachable = true
        Thread(Runnable {
            while (isServerReachable) {
                try {
                    Log.i("KEEPALIVE", "Send out keepalive message, ${ThisApp.localId}, ${ThisApp.sessionId}")
                    val ret = stub.keepAlive(RSerialOuterClass.ClientStatus.newBuilder()
                            .setId(ThisApp.localId)
                            .setSessionId(ThisApp.sessionId)
                            .build()
                    )

                    callback.onServerStatusUpdate(ret)
                    Thread.sleep(2000)
                } catch (exception: StatusRuntimeException) {
                    Thread.sleep(1500)
                    callback.onServerUnreachable(false)
                    isServerReachable = false
                }
            }
        })
                .start()
    }

    fun createSession(req: RSerialOuterClass.Req) {
        val ret = stub!!.createSession(req)
        callback.onCreateSessionResult(ret)
    }

    fun joinSession(req: RSerialOuterClass.Req) {
        val ret = stub!!.joinSession(req)
        callback.onJoinSessionResult(ret)
    }


    fun write(userId: String, sessionId: String, message: ByteArray) {
        val data = formByteData(userId, sessionId, message)
        val ret = stub.writeBytes(data)
        callback.onWriteResult(ret)
    }

    fun exit(){
        val ret = stub.disconnect(formReq())
        callback.onExitSession(ret.sessionId)
    }


    fun writeTest(data: RSerialOuterClass.ByteData) {
        val ret = stub.writeBytesTest(data)
        callback.onWriteTestResult(ret)

    }

    fun read(req: RSerialOuterClass.Req) {
        val ret = stub.readBytes(req)
        callback.onReadResult(ret)
    }

    fun formByteData(userId: String, sessionId: String, message: ByteArray): RSerialOuterClass.ByteData? {
        return RSerialOuterClass.ByteData.newBuilder()
                .setTimestamp((Date().time / 1000).toInt().toLong())
                .setSessionId(sessionId)
                .setId(userId)
                .setData(ByteString.copyFrom(message))
                .build()
    }



    fun disconnect() {
        ThisApp.sessionId = "0"
    }

    fun restorSession(){

    }
}
