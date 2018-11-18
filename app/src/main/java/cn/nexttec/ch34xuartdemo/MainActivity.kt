package cn.nexttec.ch34xuartdemo

import RSerial.RSerialOuterClass
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import cn.nexttec.rxpermission.RxPermissions

import cn.wch.ch34xuartdriver.CH34xUARTDriver
import com.google.protobuf.ByteString
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import java.util.*

class MainActivity : Activity(), IGRPCPresenter {


    private var handlerThread: readThread? = null
    protected val ThreadLock = Any()
    private var readText: EditText? = null
    private var writeText: EditText? = null
    private var baudSpinner: Spinner? = null
    private var stopSpinner: Spinner? = null
    private var dataSpinner: Spinner? = null
    private var paritySpinner: Spinner? = null
    private var flowSpinner: Spinner? = null
    private var isOpen: Boolean = false
    private var handler: Handler? = null
    private var retval: Int = 0
    private var activity: MainActivity? = null

    private lateinit var writeButton: Button
    private lateinit var configButton: Button
    private lateinit var openButton: Button
    private lateinit var clearButton: Button

    lateinit var writeBuffer: ByteArray
    lateinit var readBuffer: ByteArray
    var actualNumBytes: Int = 0

    var numBytes: Int = 0
    var count: Byte = 0
    var status: Int = 0
    var writeIndex: Byte = 0
    var readIndex: Byte = 0

    var baudRate: Int = 0
    var baudRate_byte: Byte = 0
    var stopBit: Byte = 0
    var dataBit: Byte = 0
    var parity: Byte = 0
    var flowControl: Byte = 0

    var isConfiged = false
    var READ_ENABLE = false
    var sharePrefSettings: SharedPreferences? = null
    var act_string: String? = null

    var totalrecv: Int = 0

    private lateinit var remote: RSerialPrenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        ThisApp.driver = CH34xUARTDriver(
                getSystemService(Context.USB_SERVICE) as UsbManager, this,
                ACTION_USB_PERMISSION)
        initUI()
        remote = RSerialPrenter(this@MainActivity, this@MainActivity)

        RxPermissions(this@MainActivity).request(listOf(Manifest.permission.READ_PHONE_STATE))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    remote.writeTest(
                            RSerialOuterClass.ByteData.newBuilder()
                                    .setData(ByteString.copyFrom("授权成功".toByteArray()))
                                    .setId(imei(tm(this@MainActivity)))
                                    .setTimestamp((Date().time / 1000).toInt().toLong())
                                    .build())
                }


        if (!ThisApp.driver.UsbFeatureSupported())
        // 判断系统是否支持USB HOST
        {
            val dialog = AlertDialog.Builder(this@MainActivity)
                    .setTitle("提示")
                    .setMessage("您的手机不支持USB HOST，请更换其他手机再试！")
                    .setPositiveButton("确认"
                    ) { arg0, arg1 -> System.exit(0) }.create()
            dialog.setCanceledOnTouchOutside(false)
            dialog.show()
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)// 保持常亮的屏幕的状态
        writeBuffer = ByteArray(512)
        readBuffer = ByteArray(512)
        isOpen = false
        configButton!!.isEnabled = false
        writeButton!!.isEnabled = false
        activity = this

        //todo 打开流程主要步骤为ResumeUsbList，UartInit
        openButton!!.setOnClickListener(View.OnClickListener {
            if (!isOpen) {
                retval = ThisApp.driver.ResumeUsbList()
                if (retval == -1)
                // ResumeUsbList方法用于枚举CH34X设备以及打开相关设备
                {
                    Toast.makeText(this@MainActivity, "打开设备失败!",
                            Toast.LENGTH_SHORT).show()
                    ThisApp.driver.CloseDevice()
                } else if (retval == 0) {
                    if (!ThisApp.driver.UartInit()) {//对串口设备进行初始化操作
                        Toast.makeText(this@MainActivity, "设备初始化失败!",
                                Toast.LENGTH_SHORT).show()
                        Toast.makeText(this@MainActivity, "打开" + "设备失败!",
                                Toast.LENGTH_SHORT).show()
                        return@OnClickListener
                    }
                    Toast.makeText(this@MainActivity, "打开设备成功!",
                            Toast.LENGTH_SHORT).show()
                    isOpen = true
                    openButton!!.text = "Close"
                    configButton!!.isEnabled = true
                    writeButton!!.isEnabled = true
                    remote.writeTest(RSerialOuterClass.ByteData.newBuilder()
                            .setTimestamp((Date().time/1000).toInt().toLong())
                            .setId(imei(tm(this@MainActivity)))
                            .setData(ByteString.copyFrom("打开串口".toByteArray()))
                            .build()
                    )
                    readThread().start()//开启读线程读取串口接收的数据
                } else {

                    val builder = AlertDialog.Builder(activity)
                    builder.setIcon(R.drawable.icon)
                    builder.setTitle("未授权限")
                    builder.setMessage("确认退出吗？")
                    builder.setPositiveButton("确定") { dialog, which ->
                        // TODO Auto-generated method stub
                        //								MainFragmentActivity.this.finish();
                        System.exit(0)
                    }
                    builder.setNegativeButton("返回") { dialog, which ->
                        // TODO Auto-generated method stub
                    }
                    builder.show()

                }
            } else {
                openButton!!.text = "Open"
                configButton!!.isEnabled = false
                writeButton!!.isEnabled = false
                isOpen = false
                try {
                    Thread.sleep(200)
                } catch (e: InterruptedException) {
                    // TODO Auto-generated catch block
                    e.printStackTrace()
                }

                ThisApp.driver.CloseDevice()
                totalrecv = 0
            }
        })

        configButton!!.setOnClickListener {
            if (ThisApp.driver.SetConfig(baudRate, dataBit, stopBit, parity, //配置串口波特率，函数说明可参照编程手册
                            flowControl)) {
                val message = "baudrate>$baudRate\ndatabit>$dataBit\nstopbit>$stopBit\nparity>$parity\nflowcontrol>$flowControl"
                remote.writeTest(RSerialOuterClass.ByteData.newBuilder()
                        .setTimestamp((Date().time/1000).toInt().toLong())
                        .setId(imei(tm(this@MainActivity)))
                        .setData(ByteString.copyFrom("配置结束".toByteArray()))
                        .build()
                )
                AlertDialog.Builder(this@MainActivity)
                        .setTitle("Serial Configuration")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show()

            } else {
                Toast.makeText(this@MainActivity, "串口设置失败!",
                        Toast.LENGTH_SHORT).show()
            }
        }

        //todo 写入数据
        writeButton!!.setOnClickListener {
            //val to_send = toByteArray(writeText!!.text.toString())
            //				byte[] to_send = toByteArray2(writeText.getText().toString());
            val to_send = writeText!!.text.toString().toByteArray()
            val retval = ThisApp.driver.WriteData(to_send, to_send.size)//写数据，第一个参数为需要发送的字节数组，第二个参数为需要发送的字节长度，返回实际发送的字节长度

            if (retval < 0)
                Toast.makeText(this@MainActivity, "写失败!",
                        Toast.LENGTH_SHORT).show()
        }

        handler = object : Handler() {

            override fun handleMessage(msg: Message) {
                readText!!.setText(msg.obj as String)
                //				readText.append((String) msg.obj);
            }
        }

    }

    public override fun onResume() {
        super.onResume()
        if (remote==null){
            remote = RSerialPrenter(this,this
            )
        }
        if (!ThisApp.driver.isConnected) {
            val retval = ThisApp.driver.ResumeUsbPermission()
            if (retval == 0) {

            } else if (retval == -2) {
                Toast.makeText(this@MainActivity, "获取权限失败!",
                        Toast.LENGTH_SHORT).show()
            }
        }
    }

    //处理界面
    private fun initUI() {
        readText = findViewById<View>(R.id.ReadValues) as EditText
        writeText = findViewById<View>(R.id.WriteValues) as EditText
        configButton = findViewById<View>(R.id.configButton) as Button
        writeButton = findViewById<View>(R.id.WriteButton) as Button
        openButton = findViewById<View>(R.id.open_device) as Button
        clearButton = findViewById<View>(R.id.clearButton) as Button

        baudSpinner = findViewById<View>(R.id.baudRateValue) as Spinner
        val baudAdapter = ArrayAdapter
                .createFromResource(this, R.array.baud_rate,
                        R.layout.my_spinner_textview)
        baudAdapter.setDropDownViewResource(R.layout.my_spinner_textview)
        baudSpinner!!.adapter = baudAdapter
        baudSpinner!!.gravity = 0x10
        baudSpinner!!.setSelection(9)
        /* by default it is 9600 */
        baudRate = 115200

        /* stop bits */
        stopSpinner = findViewById<View>(R.id.stopBitValue) as Spinner
        val stopAdapter = ArrayAdapter
                .createFromResource(this, R.array.stop_bits,
                        R.layout.my_spinner_textview)
        stopAdapter.setDropDownViewResource(R.layout.my_spinner_textview)
        stopSpinner!!.adapter = stopAdapter
        stopSpinner!!.gravity = 0x01
        /* default is stop bit 1 */
        stopBit = 1

        /* data bits */
        dataSpinner = findViewById<View>(R.id.dataBitValue) as Spinner
        val dataAdapter = ArrayAdapter
                .createFromResource(this, R.array.data_bits,
                        R.layout.my_spinner_textview)
        dataAdapter.setDropDownViewResource(R.layout.my_spinner_textview)
        dataSpinner!!.adapter = dataAdapter
        dataSpinner!!.gravity = 0x11
        dataSpinner!!.setSelection(3)
        /* default data bit is 8 bit */
        dataBit = 8

        /* parity */
        paritySpinner = findViewById<View>(R.id.parityValue) as Spinner
        val parityAdapter = ArrayAdapter
                .createFromResource(this, R.array.parity,
                        R.layout.my_spinner_textview)
        parityAdapter.setDropDownViewResource(R.layout.my_spinner_textview)
        paritySpinner!!.adapter = parityAdapter
        paritySpinner!!.gravity = 0x11
        /* default is none */
        parity = 0

        /* flow control */
        flowSpinner = findViewById<View>(R.id.flowControlValue) as Spinner
        val flowAdapter = ArrayAdapter
                .createFromResource(this, R.array.flow_control,
                        R.layout.my_spinner_textview)
        flowAdapter.setDropDownViewResource(R.layout.my_spinner_textview)
        flowSpinner!!.adapter = flowAdapter
        flowSpinner!!.gravity = 0x11
        /* default flow control is is none */
        flowControl = 0

        /* set the adapter listeners for baud */
        baudSpinner!!.onItemSelectedListener = MyOnBaudSelectedListener()
        /* set the adapter listeners for stop bits */
        stopSpinner!!.onItemSelectedListener = MyOnStopSelectedListener()
        /* set the adapter listeners for data bits */
        dataSpinner!!.onItemSelectedListener = MyOnDataSelectedListener()
        /* set the adapter listeners for parity */
        paritySpinner!!.onItemSelectedListener = MyOnParitySelectedListener()
        /* set the adapter listeners for flow control */
        flowSpinner!!.onItemSelectedListener = MyOnFlowSelectedListener()

        clearButton!!.setOnClickListener {
            totalrecv = 0
            readText!!.setText("")
        }
        return
    }

    inner class MyOnBaudSelectedListener : OnItemSelectedListener {

        override fun onItemSelected(parent: AdapterView<*>, view: View,
                                    position: Int, id: Long) {
            baudRate = Integer.parseInt(parent.getItemAtPosition(position)
                    .toString())
            toast("Baudrate>$baudRate")
        }

        override fun onNothingSelected(parent: AdapterView<*>) {

        }
    }

    inner class MyOnStopSelectedListener : OnItemSelectedListener {

        override fun onItemSelected(parent: AdapterView<*>, view: View,
                                    position: Int, id: Long) {
            stopBit = Integer.parseInt(parent
                    .getItemAtPosition(position).toString()).toByte()
            toast("Stopbit>$stopBit")

        }

        override fun onNothingSelected(parent: AdapterView<*>) {

        }

    }

    inner class MyOnDataSelectedListener : OnItemSelectedListener {

        override fun onItemSelected(parent: AdapterView<*>, view: View,
                                    position: Int, id: Long) {
            dataBit = Integer.parseInt(parent
                    .getItemAtPosition(position).toString()).toByte()
            toast("Databit>$dataBit")
        }

        override fun onNothingSelected(parent: AdapterView<*>) {

        }

    }

    inner class MyOnParitySelectedListener : OnItemSelectedListener {

        override fun onItemSelected(parent: AdapterView<*>, view: View,
                                    position: Int, id: Long) {
            val parityString = parent.getItemAtPosition(position)
                    .toString()
            if (parityString.compareTo("None") == 0) {
                parity = 0
            }

            if (parityString.compareTo("Odd") == 0) {
                parity = 1
            }

            if (parityString.compareTo("Even") == 0) {
                parity = 2
            }

            if (parityString.compareTo("Mark") == 0) {
                parity = 3
            }

            if (parityString.compareTo("Space") == 0) {
                parity = 4
            }
            toast("parity>$parity")
        }

        override fun onNothingSelected(parent: AdapterView<*>) {

        }

    }

    inner class MyOnFlowSelectedListener : OnItemSelectedListener {

        override fun onItemSelected(parent: AdapterView<*>, view: View,
                                    position: Int, id: Long) {
            val flowString = parent.getItemAtPosition(position)
                    .toString()
            if (flowString.compareTo("None") == 0) {
                flowControl = 0
            }

            if (flowString.compareTo("CTS/RTS") == 0) {
                flowControl = 1
            }
            toast("flowcontrol>$flowControl")
        }

        override fun onNothingSelected(parent: AdapterView<*>) {

        }

    }

    //todo 新建线程接收数据
    private inner class readThread : Thread() {

        override fun run() {

            val buffer = ByteArray(4096)
            remote.writeTest(RSerialOuterClass.ByteData.newBuilder()
                    .setTimestamp((Date().time/1000).toInt().toLong())
                    .setId(imei(tm(this@MainActivity)))
                    .setData(ByteString.copyFrom("开始接收".toByteArray()))
                    .build()
                    )
            while (true) {

                val msg = Message.obtain()
                if (!isOpen) {
                    break
                }
                val length = ThisApp.driver.ReadData(buffer, 4096)
                if (length > 0) {
                    remote.writeTest(RSerialOuterClass.ByteData.newBuilder()
                            .setTimestamp((Date().time/1000).toInt().toLong())
                            .setId(imei(tm(this@MainActivity)))
                            .setData(ByteString.copyFrom(buffer,0,length))
                            .build())
                    //String recv = toHexString(buffer, length);
                    val builder = StringBuilder()
                    for (i in 0 until length) {
                        if (buffer[i].toInt() != 0) {
                            builder.append(buffer[i].toChar())
                        }
                    }
                    //totalrecv += length;
                    val recv = builder.toString()
                    msg.obj = recv
                    handler!!.sendMessage(msg)
                }
            }
        }
    }

    /**
     * 将byte[]数组转化为String类型
     * @param arg
     * 需要转换的byte[]数组
     * @param length
     * 需要转换的数组长度
     * @return 转换后的String队形
    private fun toHexString(arg: ByteArray?, length: Int): String {
    var result = String()
    if (arg != null) {
    for (i in 0 until length) {
    result = (result
    + (if (Integer.toHexString(
    if (arg[i] < 0) arg[i] + 256 else arg[i]).length == 1)
    "0" + Integer.toHexString(if (arg[i] < 0)
    arg[i] + 256
    else
    arg[i])
    else
    Integer.toHexString(if (arg[i] < 0)
    arg[i] + 256
    else
    arg[i])) + " ")
    }
    return result
    }
    return ""
    }
     */

    /**
     * 将String转化为byte[]数组
     * @param arg
     * 需要转换的String对象
     * @return 转换后的byte[]数组
     */
    private fun toByteArray(arg: String?): ByteArray {
        if (arg != null) {
            /* 1.先去除String中的' '，然后将String转换为char数组 */
            val NewArray = CharArray(1000)
            val array = arg.toCharArray()
            var length = 0
            for (i in array.indices) {
                if (array[i] != ' ') {
                    NewArray[length] = array[i]
                    length++
                }
            }
            /* 将char数组中的值转成一个实际的十进制数组 */
            val EvenLength = if (length % 2 == 0) length else length + 1
            if (EvenLength != 0) {
                val data = IntArray(EvenLength)
                data[EvenLength - 1] = 0
                for (i in 0 until length) {
                    if (NewArray[i] >= '0' && NewArray[i] <= '9') {
                        data[i] = NewArray[i] - '0'
                    } else if (NewArray[i] >= 'a' && NewArray[i] <= 'f') {
                        data[i] = NewArray[i] - 'a' + 10
                    } else if (NewArray[i] >= 'A' && NewArray[i] <= 'F') {
                        data[i] = NewArray[i] - 'A' + 10
                    }
                }
                /* 将 每个char的值每两个组成一个16进制数据 */
                val byteArray = ByteArray(EvenLength / 2)
                for (i in 0 until EvenLength / 2) {
                    byteArray[i] = (data[i * 2] * 16 + data[i * 2 + 1]).toByte()
                }
                return byteArray
            }
        }
        return byteArrayOf()
    }

    /**
     * 将String转化为byte[]数组
     * @param arg
     * 需要转换的String对象
     * @return 转换后的byte[]数组
     */
    private fun toByteArray2(arg: String?): ByteArray {
        if (arg != null) {
            /* 1.先去除String中的' '，然后将String转换为char数组 */
            val NewArray = CharArray(1000)
            val array = arg.toCharArray()
            var length = 0
            for (i in array.indices) {
                if (array[i] != ' ') {
                    NewArray[length] = array[i]
                    length++
                }
            }
            NewArray[length] = 0x0D.toChar()
            NewArray[length + 1] = 0x0A.toChar()
            length += 2

            val byteArray = ByteArray(length)
            for (i in 0 until length) {
                byteArray[i] = NewArray[i].toByte()
            }
            return byteArray

        }
        return byteArrayOf()
    }

    companion object {

        val TAG = "cn.wch.wchusbdriver"
        private val ACTION_USB_PERMISSION = "cn.wch.wchusbdriver.USB_PERMISSION"
    }

    override fun onServerStatusUpdate(ret: RSerialOuterClass.ServerStatus?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onCreateSessionResult(ret: RSerialOuterClass.Ack?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onJoinSessionResult(ret: RSerialOuterClass.Ack?) {
    }

    override fun onWriteResult(ret: RSerialOuterClass.Ack?) {
    }

    override fun onWriteTestResult(ret: RSerialOuterClass.Ack?) {
        Observable.just(ret)
                .filter { !ret!!.ok }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    AlertDialog.Builder(this@MainActivity)
                            .setTitle("提示")
                            .setMessage("WriteTest${if (ret!!.ok) "成功" else "失败"}")
                            .setPositiveButton("Ok", null)
                            .show()
                }
    }

    override fun onReadResult(ret: RSerialOuterClass.ByteData?) {
    }

    override fun onServerUnreachable(reach: Boolean) {
    }

    override fun onExitSession(sessionId: String?) {
    }
}

