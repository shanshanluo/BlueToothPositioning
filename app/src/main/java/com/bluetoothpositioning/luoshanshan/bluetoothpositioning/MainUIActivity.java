package com.bluetoothpositioning.luoshanshan.bluetoothpositioning;

import android.app.ListFragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.Telephony;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Array;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;


public class MainUIActivity extends ActionBarActivity {
    private Handler uiHanldler;
    private myBluetoothLe blueTooth;
    private wifiDoorController wifi = new wifiDoorController();
    private btPositioning positions = new btPositioning();
    Looper looper1, looper2;
    boolean gattConnectReadRssiOn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_ui);

        uiHanldler = new Handler(){
            TextView xText;
            TextView yText;
            @Override
            public void handleMessage(Message msg) {
                xText = (TextView)findViewById(R.id.textx);
 //               yText = (TextView)findViewById(R.id.texty);
                xText.setText("X-Position:" + msg.arg1);
 //               yText.setText("Y-Position:" + msg.arg2);
            }
        };

        /******* start wifi thread *****************************/
        wifi.serverIpAddress = "192.168.43.4";
        wifi.port = 5001;
        Thread wifiThreadInst = new wifiThread();
        wifiThreadInst.start();

        /****** start positioning thread **********************/
        Thread algorithmThread = new positionThread();
        algorithmThread.start();


        /*****  start Bluetooth Rssi reading ************/
        blueTooth = new myBluetoothLe();
        blueTooth.NumOfDevice = 1;
        Thread BTScanThread = new Thread(new Runnable() {
            @Override
            public void run() {
                blueTooth.BTScanStart();
            }
        });
        BTScanThread.start();


        /******* sampling thread get rssi data every 500ms *********/
        Thread sampling = new Thread(new samplingThread());
        sampling.start();
    }

    class wifiThread extends Thread {

        public void run() {
            Looper.prepare();
            looper2 = Looper.myLooper();
            Looper.loop();
        }
    }

    class wifiHandler extends Handler {
        public wifiHandler() {
            super(looper2);
        }
        @Override
        public void handleMessage(Message msg){
            wifi.doorControl(msg.arg1, msg.arg2);
        }
    }

    class positionThread extends Thread {
        public void run() {
            Looper.prepare();
            looper1 = Looper.myLooper();
            Looper.loop();
        }
    }

    class positionsHandler extends Handler {
        wifiHandler handler = new wifiHandler();

        public positionsHandler() {
            super(looper1);
        }
        @Override
        public void handleMessage(Message data) {
            if(positions.AlgPositioning((int [])data.obj)== true) {
                uiHanldler.obtainMessage(0, positions.x, positions.y).sendToTarget();
                handler.obtainMessage(0, positions.x, positions.y).sendToTarget();
            }
        }
    }

    class samplingThread implements Runnable{

        @Override
        public void run() {
            positionsHandler handler = new positionsHandler();
            while(true) {
                handler.obtainMessage(0, blueTooth.rssiList).sendToTarget();

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /*********  positioning algorithm module, suppose max 3 devices ************************/
    /***********************************************/
    class btPositioning{
        private int x, y;

        private boolean AlgPositioning(int[] value)
        {
            int size = value.length;
            int rssi_1 = value[0];
            if(rssi_1 == 0)
                return false;
            if(size >= 2) {
                int rssi_2 = value[1];
                if(rssi_2 == 0)
                    return false;
                if (size > 2) {
                    int rssi_3 = value[2];
                    if(rssi_3 == 0)
                        return false;
                }
            }

            // real positioning algorithm implementation
            if(rssi_1 > -55){
                x = 8;
                y = 8;
            }else if((rssi_1 > -70)&&(rssi_1 <= -55 )){
                x = 12;
                y = 12;
            }else{
                x = 20;
                y = 20;
            }

            return true;
        }

    }

    protected void onDestroy()
    {
        gattConnectReadRssiOn = false;
        /*** make sure all gatt connection is closed ****/
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }


    /*********  Bluetooth module ************************/
    /****************************************************/
    class myBluetoothLe
    {
        private BluetoothManager bluetoothManager;
        private BluetoothAdapter BTAdapter;
        int DeviceCnt = 0;
        private int NumOfDevice;
        Thread Thread;
        int[] rssiList = new int[3];

        public myBluetoothLe()
        {
            bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            BTAdapter = bluetoothManager.getAdapter();
            rssiList[0] = rssiList[1] = rssiList[2] = 0;
        }

        // Start the Bluetooth Scan
        void BTScanStart() {

            if (BTAdapter == null) {
                System.out.println("Bluetooth NOT supported. Aborting.");
                return;
            } else {
                if (BTAdapter.isEnabled()) {
                    System.out.println("Bluetooth is enabled...");
                    BTAdapter.startLeScan(mLeScanCallback);
                }
            }
        }

        // Device scan callback.
        private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
            @Override
            public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {

                if (device.getName().equals("HMSoft")) {
                    if (isAvailableDevice(device.getAddress())) {
                        DeviceCnt++;
                        /**** stop scan and set gattconnectReadRssiOn "true" to start reading Rssi***/
                        if (DeviceCnt == NumOfDevice) {
                            BTAdapter.stopLeScan(mLeScanCallback);
                            gattConnectReadRssiOn = true;
                        }
                        else if (DeviceCnt > NumOfDevice)
                            return;

                        device.connectGatt(null, true, mGattCallback);
                    }
                }

//           System.out.println("scan record:" + scanRecord);
            }
        };

        // Gatt Callback
        private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {

                    Thread rssiRequest = new Thread(new rssiRequestThread(gatt));
                    rssiRequest.start();
                }
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    System.out.println(gatt.getDevice() + ": Disconnected.. ");
                }
            }

            public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status)
            {
                int id = getDeviceId(gatt);
                rssiList[id-1] = rssi;
//            System.out.println(gatt.getDevice().getAddress() + rssi);
            }
        };

        public class rssiRequestThread implements Runnable {
            private BluetoothGatt gatt;

            public rssiRequestThread(BluetoothGatt data) {
                this.gatt = data;
            }

            public void run() {
                /***  send reading Rssi request, when gattConnectReadRssiOn is "true";
                 * otherwise, disconnect gatt profile as requested at onDestroy()
                 */
                while (gattConnectReadRssiOn) {
                    gatt.readRemoteRssi();

                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                gatt.disconnect();
            }
        }

        int getDeviceId(BluetoothGatt gatt) {
            int id = 0;
            switch (gatt.getDevice().getAddress()) {
                case "B4:99:4C:57:B6:E2":
                    id = 1;
                    break;
                case "78:A5:04:3E:C5:8F":
                    id = 2;
                    break;
                case "B4:99:4C:67:8A:E4":
                    id = 3;
                    break;

                default:
                    break;
            }
            return id;
        }

        boolean isAvailableDevice(String s) {
            if (s.equals("B4:99:4C:57:B6:E2")
                    || s.equals("B4:99:4C:67:8A:E4")
                    || s.equals("78:A5:04:3E:C5:8F"))
                return true;
            else
                return false;
        }
    }

    /*********  Wifi module ************************/
    /***********************************************/
    class wifiDoorController{
        private String serverIpAddress;
        private int port;
        int threshold = 10;
        boolean wifiConnected = false;
        boolean isDoorOn = false;

        private void doorControl(int x, int y)
        {
            if(y <= threshold) {
                if (!isDoorOn) {
                    isDoorOn = true;
                    ledCommand("LED ON");
                }
            } else {
                if(isDoorOn){
                    isDoorOn = false;
                    ledCommand("LED OFF");
                }
            }
        }

        private void ledCommand(String cmd)
        {
            if(!wifiConnected){
                if(serverIpAddress != null){
                    sendCommand(cmd);
                }
            }
        }

        private void sendCommand(String cmd)
        {
            BufferedWriter clientOut;
            String outMsg;

            try {
                Socket s = new Socket(serverIpAddress, port);
                wifiConnected = true;
                clientOut = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));
                outMsg = cmd;
                clientOut.write(outMsg);
                clientOut.flush();

                clientOut.close();
                s.close();
                wifiConnected = false;
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
