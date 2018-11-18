package cn.nexttec.ch34xuartdemo

import RSerial.RSerialOuterClass
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Environment
import android.support.annotation.RequiresApi
import android.support.v7.app.AlertDialog
import android.telephony.TelephonyManager
import android.util.Log
import com.google.gson.Gson
import java.io.*
import java.util.*


infix fun Context.toast(msg: String) = android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()

fun Context.lf(data: String, fileName: String = "log.dat") {
    val filePath = Environment.getExternalStorageDirectory().absolutePath + "/RserialLog"
    val fileName = fileName

    val directory = File(filePath)
    if (!directory.exists()){
        directory.mkdirs()
    }
    val file = File(filePath,fileName)
    if (file.exists())
        file.delete()
    if(!file.exists()){
        file.createNewFile()
    }

    val fos = FileOutputStream(file, true)
    val osw = OutputStreamWriter(fos, Charsets.UTF_8)
    osw.write(data)
    osw.close()
    fos.close()
}

val gson = {obj:Any? ->
    Gson().toJson(obj)
}

fun Context.lo(obj:Any?) = Log.i(this::class.java.simpleName,gson(obj))

fun Context.li(vararg string: String) = Log.i(this::class.java.simpleName,
        string.reduce { acc, s -> "${acc},${s}" })

fun toHexString(arg: ByteArray?, length: Int): String {
    var result = String()
    if (arg != null) {
        for (i in 0 until length) {
            result = (result
                    + (if (Integer.toHexString(
                            if (arg[i] < 0) arg[i] + 256 else arg[i].toInt()).length == 1)
                "0" + Integer.toHexString(if (arg[i] < 0)
                    arg[i] + 256
                else
                    arg[i].toInt())
            else
                Integer.toHexString(if (arg[i] < 0)
                    arg[i] + 256
                else
                    arg[i].toInt())) + " ")
        }
        return result
    }
    return ""
}

fun copyInit(context: Context) {
    val iniFile = "vendor.ini"
    val inputStream:InputStream
    val outputStream:OutputStream
    val path = "${Environment.getExternalStorageDirectory()}/${context.packageName+File.pathSeparator}"
    val file = File(path,iniFile)
    if (!file.exists())
        file.createNewFile()
    outputStream = FileOutputStream(file)
    inputStream = context.assets.open(iniFile)
    copy(inputStream, outputStream)
    inputStream.close()
    outputStream.close()
}

fun copy(ins:InputStream, os:OutputStream){
    var buffer = ByteArray(1024)
    var read = ins.read(buffer)
    while (read != -1){
        os.write(buffer,0,read)
        read = ins.read(buffer)
    }
}

fun readInit(context: Context){
    val path = "${Environment.getExternalStorageDirectory()}/${context.packageName+File.pathSeparator}"
    val filename = "vendor.ini"
}


//todo 简化log输出
infix fun Any.li(msg:String) = Log.i(this::class.java.simpleName, msg)

//todo 获取时间戳
val timestamp = (Date().time/1000).toInt().toLong()

//todo 构成grpc req请求
fun formReq(): RSerialOuterClass.Req {
    return RSerialOuterClass.Req.newBuilder()
            .setId(ThisApp.localId)
            .setSessionId(ThisApp.sessionId)
            .setTimestamp(timestamp)
            .build()
}

val tm = {context:Context->
    context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
}

//todo 读取imei
@RequiresApi(Build.VERSION_CODES.O)
@SuppressLint("MissingPermission")
val imei = { tm: TelephonyManager ->
    tm.imei
}



fun isIniFileExist(context: Context): Boolean {
    val path = Environment.getExternalStorageDirectory().toString() + "/" + context.packageName + "/"
    val fileName = "vendors.ini"
    val file = File(path, fileName)
    return file.exists() && file.isFile

}

