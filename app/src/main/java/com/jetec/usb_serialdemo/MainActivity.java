package com.jetec.usb_serialdemo;

import androidx.appcompat.app.AppCompatActivity;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private USBStatus status = new USBStatus();
    public static final String TAG = MainActivity.class.getSimpleName() + "My";
    private static final String USB_PERMISSION = "USB_Demo";
    TextView tvStatus, tvInfo, tvRes;
    UsbManager manager;
    List<UsbSerialDriver> drivers;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        /*註冊廣播*/
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(USB_PERMISSION);
        registerReceiver(status, filter);
        /*Find UIs*/
        tvStatus = findViewById(R.id.textView_Status);
        tvInfo = findViewById(R.id.textView_Info);
        tvRes = findViewById(R.id.textView_Respond);
        Button btSend = findViewById(R.id.button_Send);
        btSend.setOnClickListener(v -> {
            /*對裝置送出指令*/
            sendValue(drivers);
        });
        /*偵測是否正在有裝置插入*/
        detectUSB();
    }

    @Override
    protected void onStop() {
        super.onStop();
        /*反註冊廣播*/
        unregisterReceiver(status);
    }

    private class USBStatus extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            switch (action) {
                /**當Check完授權狀態後進入此處*/
                case USB_PERMISSION:
                    if (drivers.size() == 0) return;
                    boolean hasPermission = manager.hasPermission(drivers.get(0).getDevice());
                    tvStatus.setText("授權狀態: " + hasPermission);
                    if (!hasPermission) {
                        getPermission(drivers);
                        return;
                    }
                    Toast.makeText(context, "已獲取權限", Toast.LENGTH_SHORT).show();
                    break;
                /**偵測USB裝置插入*/
                case UsbManager.ACTION_USB_DEVICE_ATTACHED:
                    Toast.makeText(context, "USB裝置插入", Toast.LENGTH_SHORT).show();
                    detectUSB();
                    break;
                /**偵測USB裝置拔出*/
                case UsbManager.ACTION_USB_DEVICE_DETACHED:
                    Toast.makeText(context, "USB裝置拔出", Toast.LENGTH_SHORT).show();
                    tvInfo.setText("TextView");
                    tvRes.setText("TextView");
                    tvStatus.setText("授權狀態: false");
                    break;
            }
        }
    }
    /**偵測裝置*/
    private void detectUSB() {
        manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (manager == null) return;
        if (manager.getDeviceList().size() == 0)return;
        tvStatus.setText("授權狀態: false");
        /*取得目前插在USB-OTG上的裝置*/
        drivers = getDeviceInfo();
        /*確認使用者是否有同意使用OTG(權限)*/
        getPermission(drivers);
    }
    /**取得目前插在USB-OTG上的裝置列表，並取得"第一個"裝置的資訊*/
    private List<UsbSerialDriver> getDeviceInfo() {
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        Log.d(TAG, "裝置資訊列表:\n " + deviceList);
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        ProbeTable customTable = new ProbeTable();
        List<UsbSerialDriver> drivers = null;
        String info = "";
        while (deviceIterator.hasNext()) {
            /*取得裝置資訊*/
            UsbDevice device = deviceIterator.next();
            info = "Vendor ID: " + device.getVendorId()
                    + "\nProduct Id: " + device.getDeviceId()
                    + "\nManufacturerName: " + device.getManufacturerName()
                    + "\nProduceName: " + device.getProductName();
            /*設置驅動*/
            customTable.addProduct(
                    device.getVendorId(),
                    device.getProductId(),
                    CdcAcmSerialDriver.class
                    /*我的設備Diver是CDC，另有
                     * CP21XX, CH34X, FTDI, Prolific 等等可以使用*/
            );
            /*將驅動綁定給此裝置*/
            UsbSerialProber prober = new UsbSerialProber(customTable);
            drivers = prober.findAllDrivers(manager);
        }
        /*更新UI*/
        tvInfo.setText(info);
        return drivers;
    }
    /**確認OTG使用權限，此處為顯示詢問框*/
    private void getPermission(List<UsbSerialDriver> drivers) {
        if (PendingIntent.getBroadcast(this, 0, new Intent(USB_PERMISSION), 0) != null) {
            manager.requestPermission(drivers.get(0).getDevice(), PendingIntent.getBroadcast(
                    this, 0, new Intent(USB_PERMISSION), 0)); }
    }
    /**送出資訊*/
    private void sendValue(List<UsbSerialDriver> drivers) {
        if (drivers == null) return;
        /*初始化整個發送流程*/
        UsbDeviceConnection connect = manager.openDevice(drivers.get(0).getDevice());
        /*取得此USB裝置的PORT*/
        UsbSerialPort port = drivers.get(0).getPorts().get(0);
        try {
            /*開啟port*/
            port.open(connect);
            /*取得要發送的字串*/
            EditText edInput = findViewById(R.id.editText_Input);
            String s = edInput.getText().toString();
            if (s.length() == 0) return;
            /*設定胞率、資料長度、停止位元、檢查位元*/
            port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            /*寫出資訊*/
            port.write(s.getBytes(), 200);
            /*設置回傳執行緒*/
            SerialInputOutputManager.Listener serialInputOutputManager = getRespond;
            SerialInputOutputManager sL = new SerialInputOutputManager(port, serialInputOutputManager);
            ExecutorService service = Executors.newSingleThreadExecutor();
            service.submit(sL);
        } catch (IOException e) {
            try {
                /*如果Port是開啟狀態，則關閉；再使用遞迴法重複呼叫並嘗試*/
                port.close();
                sendValue(drivers);
            } catch (IOException ex) {
                ex.printStackTrace();
                Log.e(TAG, "送出失敗，原因: " + ex);
            }
        }
    }
    /**接收回傳*/
    private SerialInputOutputManager.Listener getRespond = new SerialInputOutputManager.Listener() {
        @Override
        public void onNewData(byte[] data) {
            String res = "字串回傳： "+new String(data)+"\nByteArray回傳： "+byteArrayToHexStr(data);
            Log.d(TAG, "回傳: " + res);
            runOnUiThread(() -> {
                tvRes.setText(res);
            });
        }
        @Override
        public void onRunError(Exception e) {
        }
    };
    /**將ByteArray轉成字串可顯示的ASCII*/
    private String byteArrayToHexStr(byte[] byteArray) {
        if (byteArray == null) {
            return null;
        }
        StringBuilder hex = new StringBuilder(byteArray.length * 2);
        for (byte aData : byteArray) {
            hex.append(String.format("%02X ", aData));
        }
        String gethex = hex.toString();
        return gethex;
    }
}