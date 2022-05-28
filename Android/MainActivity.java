package com.feche.ardsensor;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ClipDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class MainActivity extends AppCompatActivity
{
    static boolean _DEBUG = false;

    int gBtStatus = 0;
    int loginStatus = 0;
    
    long gLiveTick = System.currentTimeMillis();

    boolean gCheckSwitch = false;
    boolean gCooldown = false;
    boolean gCompOn = false;
    int gCAlert = 0;
    boolean gAutoLogin = false;
    boolean BT_Ready = false;

    BluetoothSocket gBtSocket = null;
    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    Timer timer = new Timer();
    
    Toast gToast = null;

    TextView gPairStatus = null;
    TextView psiOnTxt;
    TextView psiOffTxt;
    TextView onTimeTxt;
    TextView offTimeTxt;
    TextView gCAlertTxt;

    TextView txt1;
    TextView txt2;
    TextView txt3;
    TextView txt4;
    TextView txt5;
    TextView txt6;
    TextView txt7;
    TextView txt8;

    ImageView imgCooldown;
    ImageView imgCompOn;
    ImageView imgAlert;
    ImageView imgTank1;
    ImageView imgTank2;

    Switch switch1;

    EditText loginPwd;
    EditText psiOn;
    EditText psiOff;
    EditText onTime;
    EditText offTime;

    Button loginButton;
    Button saveButton;
    Button onButton;
    Button offButton;

    int color_hide = Color.argb(255, 0, 0,0);
    int color_show = Color.argb(0, 0, 0, 0);

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        gPairStatus = (TextView)findViewById(R.id.tank_psi);
        psiOnTxt = (TextView) findViewById(R.id.psion);
        psiOffTxt = (TextView) findViewById(R.id.psioff);
        onTimeTxt = (TextView) findViewById(R.id.ontime);
        offTimeTxt = (TextView) findViewById(R.id.offtime);
        gCAlertTxt = (TextView) findViewById(R.id.alert_text);

        txt1 = (TextView)findViewById(R.id.compressor_label);
        txt2 = (TextView)findViewById(R.id.manual_label);
        txt3 = (TextView)findViewById(R.id.auto_label);
        txt4 = (TextView)findViewById(R.id.settings_label);
        txt5 = (TextView)findViewById(R.id.psion_label);
        txt6 = (TextView)findViewById(R.id.psioff_label);
        txt7 = (TextView)findViewById(R.id.ontime_label);
        txt8 = (TextView)findViewById(R.id.offtime_label);

        imgCooldown = (ImageView)findViewById(R.id.cold);
        imgCompOn = (ImageView)findViewById(R.id.on);
        imgAlert = (ImageView)findViewById(R.id.alert);
        imgTank1 = (ImageView)findViewById(R.id.imageView4);
        imgTank2 = (ImageView)findViewById(R.id.tank_pressure_img);

        switch1 = (Switch) findViewById(R.id.switch1);

        loginPwd = (EditText)findViewById(R.id.login_pwd);
        psiOn = (EditText)findViewById(R.id.psion);
        psiOff = (EditText)findViewById(R.id.psioff);
        onTime = (EditText)findViewById(R.id.ontime);
        offTime = (EditText)findViewById(R.id.offtime);

        loginButton = (Button)findViewById(R.id.login_button);
        saveButton = (Button)findViewById(R.id.save_button);
        onButton = (Button)findViewById(R.id.on_button);
        offButton = (Button)findViewById(R.id.off_button);

        imgCooldown.setColorFilter(Color.argb(255, 0, 0, 0));
        imgCompOn.setColorFilter(Color.argb(255, 0, 0, 0));
        imgAlert.setColorFilter(Color.argb(255, 0, 0, 0));

        Debug("STATUS", "App starting..");
        endBTConnection(); // Used to set UI to nil values
        startTimer();
        showLogin(false);
        showMainUI(false);
        initializeBluetoothOrRequestPermission();

        ////////////////////////////////////////////////////////////////////////////////////////////

        //
        // MANUAL - AUTO SWITCH
        //
        switch1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                if(!gCheckSwitch) return;

                switch1.setEnabled(false);

                if (isChecked)
                    sendData("comp_auto");
                else
                    sendData("comp_manual");
            }
        });

        //
        // LOGIN BUTTON
        //
        loginButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                String pwd = loginPwd.getText().toString();

                if(pwd.length() != 4)
                {
                    showToast("La contraseña debe ser de 4 digitos.");
                    return;
                }

                sendData(pwd + (loginButton.getText().toString().matches("Guardar") ? ",register" : ",login"));
                loginButton.setEnabled(false);
            }
        });

        //
        // SAVE BUTTON
        //
        saveButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if(gBtStatus != 5) return;

                sendData(psiOn.getText().toString() + "," + psiOff.getText().toString() + "," + onTime.getText().toString() + "," + offTime.getText().toString() + "," + (switch1.isChecked() ? "1" : "0").toString() + ",save");
                saveButton.setEnabled(false);
            }
        });

        //
        // COMPRESSOR ON BUTTON
        //
        onButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                sendData("comp_on");
            }
        });

        //
        // COMPRESSOR OFF BUTTON
        //
        offButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                sendData("comp_off");
            }
        });

        //
        // RECEIVE BT DATA THREAD
        //
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                while(true)
                {
                    try
                    {
                        Thread.sleep(100);
                    }
                    catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }

                    if(gBtStatus >= 4)
                    {
                        if(gBtSocket != null)
                        {
                            byte b;
                            byte buffer[] = new byte[1024];
                            int idx = 0;
                            String readMessage = null;
                            InputStream tmpIn = null;

                            try
                            {
                                tmpIn = gBtSocket.getInputStream();
                            }
                            catch (IOException e)
                            {
                                e.printStackTrace();
                            }

                            while(true)
                            {
                                try
                                {
                                    b = (byte) tmpIn.read();
                                    if(b == '\n')
                                    {
                                        readMessage = new String(buffer, 0, idx);
                                        onReceiveData(readMessage);
                                        idx = 0;
                                        break;
                                    }
                                    else
                                    {
                                        buffer[idx] = b;
                                        idx++;
                                    }
                                }
                                catch (IOException e)
                                {
                                    Debug("BLUETOOTH", "Error receiving data");
                                    currentStatus("Dispositivo desconectado.");
                                    endBTConnection();
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }).start();
    }

    public void initializeBluetoothOrRequestPermission()
    {
        if(Build.VERSION.SDK_INT <= 30)
        {
            BT_Ready = true;
            return;
        }

        if(ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(MainActivity.this, new String[] { Manifest.permission.BLUETOOTH_CONNECT }, 1);
            //showToast("Solicitando permiso para acceder al Bluetooth..");
        }
        else
        {
            BT_Ready = true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(requestCode == 1)
        {
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            {
                BT_Ready = true;
            }
            else
            {
                currentStatus("El acceso al Bluetooth ha sido denegado");
                BT_Ready = false;
            }
        }
    }

    public void showMainUI(boolean state)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                psiOnTxt.setVisibility(state ? View.VISIBLE : View.INVISIBLE);
                psiOffTxt.setVisibility(state ? View.VISIBLE : View.INVISIBLE);
                onTimeTxt.setVisibility(state ? View.VISIBLE : View.INVISIBLE);
                offTimeTxt.setVisibility(state ? View.VISIBLE : View.INVISIBLE);

                txt1.setVisibility(state ? View.VISIBLE : View.INVISIBLE);
                txt2.setVisibility(state ? View.VISIBLE : View.INVISIBLE);
                txt3.setVisibility(state ? View.VISIBLE : View.INVISIBLE);
                txt4.setVisibility(state ? View.VISIBLE : View.INVISIBLE);
                txt5.setVisibility(state ? View.VISIBLE : View.INVISIBLE);
                txt6.setVisibility(state ? View.VISIBLE : View.INVISIBLE);
                txt7.setVisibility(state ? View.VISIBLE : View.INVISIBLE);
                txt8.setVisibility(state ? View.VISIBLE : View.INVISIBLE);

                imgCooldown.setVisibility(state ? View.VISIBLE : View.INVISIBLE);
                imgCompOn.setVisibility(state ? View.VISIBLE : View.INVISIBLE);
                imgAlert.setVisibility(state ? View.VISIBLE : View.INVISIBLE);
                imgTank1.setVisibility(state ? View.VISIBLE : View.INVISIBLE);
                imgTank2.setVisibility(state ? View.VISIBLE : View.INVISIBLE);

                switch1.setVisibility(state ? View.VISIBLE : View.INVISIBLE);

                psiOn.setVisibility(state ? View.VISIBLE : View.INVISIBLE);
                psiOff.setVisibility(state ? View.VISIBLE : View.INVISIBLE);
                onTime.setVisibility(state ? View.VISIBLE : View.INVISIBLE);
                offTime.setVisibility(state ? View.VISIBLE : View.INVISIBLE);

                saveButton.setVisibility(state ? View.VISIBLE : View.INVISIBLE);
                onButton.setVisibility(state ? View.VISIBLE : View.INVISIBLE);
                offButton.setVisibility(state ? View.VISIBLE : View.INVISIBLE);
            }
        });
    }

    public void showLogin(boolean state)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                loginPwd.setVisibility(state ? View.VISIBLE : View.INVISIBLE);
                loginButton.setVisibility(state ? View.VISIBLE : View.INVISIBLE);
            }
        });
    }

    //
    // DATA RECEIVED OVER BLUETOOTH CONNECTION
    //
    public void onReceiveData(String data)
    {
        Debug("DATA", "Received: (" + data + ")");

        if(data.contains("register_start"))
        {
            showMainUI(false);
            showLogin(true);
            currentStatus("Genere su contraseña");
            runOnUiThread(new Runnable()
            {
                  @Override
                  public void run()
                  {
                      loginButton.setText("Guardar");
                      loginButton.setEnabled(true);
                      loginPwd.setText("");
                  }
            });

            Debug("LOGIN", "Password not created, showing login");
        }
        else if(data.contains("login_start"))
        {
            SharedPreferences prefs = getSharedPreferences("ARDSENSOR", MODE_PRIVATE);
            int pwd = prefs.getInt("pwd", 0);
            if(pwd == 0)
            {
                showMainUI(false);
                showLogin(true);
                currentStatus("Digite su contraseña");
                gAutoLogin = false;
                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        loginButton.setText("Login");
                        loginButton.setEnabled(true);
                        loginPwd.setText("");
                    }
                });

                Debug("LOGIN", "Password not saved, showing login");
            }
            else
            {
                sendData(pwd + ",login");
                gAutoLogin = true;
            }
        }
        else if(data.contains("register_ok") || data.contains("login_ok"))
        {
            if(!gAutoLogin)
            {
                int pwd = Integer.parseInt(loginPwd.getText().toString());
                SharedPreferences.Editor editor = getSharedPreferences("ARDSENSOR", MODE_PRIVATE).edit();
                editor.putInt("pwd", pwd);
                editor.apply();

                InputMethodManager Keyboard = (InputMethodManager) getSystemService(this.INPUT_METHOD_SERVICE);
                if(Keyboard.isActive())
                    Keyboard.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
            }

            gBtStatus = 5;
            gLiveTick = System.currentTimeMillis();
            sendData("start");

            showLogin(false);
            showMainUI(true);

            if(data.contains("register_ok"))
                showToast("Su contraseña ha sido guardada.");
        }
        else if(data.contains("login_fail"))
        {
            if(gAutoLogin)
            {
                SharedPreferences.Editor editor = getSharedPreferences("ARDSENSOR", MODE_PRIVATE).edit();
                editor.putInt("pwd", 0);
                editor.apply();
                return;
            }

            showToast("Contraseña incorrecta, intente nuevamente.");
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    loginButton.setEnabled(true);
                    loginPwd.setText("");
                }
            });
        }
        //
        // SCREEN UI UPDATE
        //
        else if(data.contains(",psi"))
        {
            gLiveTick = System.currentTimeMillis();

            if(gBtStatus != 5) return;

            String[] split = data.split(",");
            int currPsi = Integer.parseInt(split[0]);
            int maxPsi = Integer.parseInt(split[1]);
            int compOn = Integer.parseInt(split[2]);
            int compCooldownTime = Integer.parseInt(split[3]);
            int compAlert = Integer.parseInt(split[4]);

            maxPsi = maxPsi == 0 ? 1 : maxPsi; // Prevent NaN crash

            currentStatus(currPsi + " psi");
            ImageView img = (ImageView) findViewById(R.id.tank_pressure_img);
            ClipDrawable mImageDrawable = (ClipDrawable) img.getDrawable();

            mImageDrawable.setLevel((10000 / maxPsi) * currPsi);
            mImageDrawable.setAlpha(175);

            gCompOn = (compOn == 1 ? true : false);
            gCooldown = (compCooldownTime == 0 ? false : true);
            gCAlert = compAlert;

            if(gCooldown)
            {
                if((int)imgCooldown.getTag() == 0)
                {
                    imgCooldown.setColorFilter(color_hide);
                    imgCooldown.setTag(color_hide);
                }
                else
                {
                    imgCooldown.setColorFilter(color_show);
                    imgCooldown.setTag(color_show);
                }

                int sec = compCooldownTime % 60;
                int min = (compCooldownTime / 60) % 60;

                String sec_str = sec < 10 ? "0" + sec : String.valueOf(sec);

                gCAlertTxt.setText("Compresor en enfriamiento [" + min + ":" + sec_str + "]");
                gCAlertTxt.setTextColor(Color.rgb(47, 125, 237));
            }
            else
            {
                imgCooldown.setColorFilter(color_hide);
                imgCooldown.setTag(color_hide);
                gCAlertTxt.setText("");
            }

            if(gCompOn)
                imgCompOn.setColorFilter(color_show);
            else
                imgCompOn.setColorFilter(color_hide);

            if(gCAlert >= 1)
            {
                if((int)imgAlert.getTag() == 0)
                {
                    imgAlert.setColorFilter(color_hide);
                    imgAlert.setTag(color_hide);
                }
                else
                {
                    imgAlert.setColorFilter(color_show);
                    imgAlert.setTag(color_show);
                }

                //Log.d("gCAlert", "gCAlert = " + gCAlert);

                // Alert label
                switch(gCAlert)
                {
                    case 1:
                        gCAlertTxt.setText("Fuga de aire o falla compresor [ERR42]");
                        gCAlertTxt.setTextColor(Color.rgb(255, 0, 0));
                        break;
                    case 2:
                        gCAlertTxt.setText("Presión excedida en tanque [ERR40]");
                        gCAlertTxt.setTextColor(Color.rgb(255, 0, 0));
                        break;
                    case 3:
                        gCAlertTxt.setText("No se detecta el sensor [ERR44]");
                        gCAlertTxt.setTextColor(Color.rgb(255, 0, 0));
                        break;
                    default:
                        gCAlertTxt.setText("");
                        break;
                }
            }
            else
            {
                imgAlert.setColorFilter(color_hide);
                imgAlert.setTag(color_hide);
            }
        }

        //
        // RECEIVE DATA ON APP START
        //
        else if(data.contains(",start"))
        {
            String[] strArray = data.split(",");

            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    // Compressor mode
                    switch1.setChecked(Integer.parseInt(strArray[0]) == 1 ? true : false);
                    switch1.setEnabled(true);
                    gCheckSwitch = true;
                    // On Off buttons
                    onButton.setEnabled(Integer.parseInt(strArray[0]) == 1 ? false : true);
                    offButton.setEnabled(Integer.parseInt(strArray[0]) == 1 ? false : true);
                    // Psi on
                    psiOnTxt.setText(strArray[1]);
                    // Psi off
                    psiOffTxt.setText(strArray[2]);
                    // On time
                    onTimeTxt.setText(strArray[3]);
                    // Off time
                    offTimeTxt.setText(strArray[4]);
                }
            });
        }
        //
        // NOTIFICATIONS
        //
        else if(data.contains(",not"))
        {
            showToast(data.replace(",not", ""));

            if(data.contains("Datos guardados") || data.contains("La presión") || data.contains("El tiempo maximo") || data.contains("El tiempo minimo"))
            {
                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        saveButton.setEnabled(true);
                    }
                });
            }
        }
        //
        // COMPRESSOR AUTOMATIC
        //
        else if(data.contains("comp_auto"))
        {
            showToast("Compresor: automatico");
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    onButton.setEnabled(false);
                    offButton.setEnabled(false);
                    switch1.setEnabled(true);
                }
            });
        }
        //
        // COMPRESSOR MANUAL
        //
        else if(data.contains("comp_manual"))
        {
            showToast("Compresor: manual");
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    onButton.setEnabled(true);
                    offButton.setEnabled(true);
                    switch1.setEnabled(true);
                }
            });
        }
        //
        // ERROR HANDLING
        //
        else if(data.matches("err"))
        {
            showToast("Error, reintente.");
            runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    switch1.setEnabled(true);
                    saveButton.setEnabled(true);
                }
            });
        }
    }

    //
    // CORE FUNCTIONS
    //

    public void startTimer()
    {
        timer.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                if(BT_Ready != true) return;

                if(gBtStatus >= 4)
                {
                    sendData("get");
                    //imgTank1.setColorFilter(Color.argb(100,255, 255, 0));
                }

                Debug("TIMER", "Checking bluetooth status..");
                checkBluetoothStatus(); // Check for timeouts or bt disabled by user
            }
        }, 0, 500);
    }

    public void checkBluetoothStatus()
    {
        if(System.currentTimeMillis() - gLiveTick >= 2500 && gBtStatus == 5)
        {
            endBTConnection();
            Debug("BLUETOOTH", "Bluetooth disconnected from timeout");
            currentStatus("Dispositivo desconectado.");

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return;
        }

        if(gBtStatus >= 3) return;

        if (!mBluetoothAdapter.isEnabled())
        {
            currentStatus("Por favor, enciende el bluetooth.");

            // Prevent bt spam from timer
            if(gBtStatus != 1)
            {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }

            gBtStatus = 1; // bt OFF
        }
        else
        {
            boolean bFound = false;
            String btMAC = "";
            String btName = "";
            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

            for (BluetoothDevice bt : pairedDevices)
            {
                btName = bt.getName();
                btMAC = bt.getAddress();

                if (btName.indexOf("ARDSENSOR") >= 0)
                {
                    Debug("BLUETOOTH", "Device is paired!");
                    bFound = true;
                    break;
                }
            }

            // Check if device is found..
            if (!bFound)
            {
                currentStatus("Por favor, empareje su dispositivo.");
                gBtStatus = 2; // not paired
            }
            else
            {
                gBtStatus = 3; // connecting..
                currentStatus("Conectando con el dispositivo...");
                connectToBTDevice(btMAC, btName);
            }
        }
    }

    public void connectToBTDevice(String address, String name)
    {
        BluetoothDevice hc = mBluetoothAdapter.getRemoteDevice(address);
        UUID myUUID = hc.getUuids()[0].getUuid();

        try
        {
            gBtSocket = hc.createInsecureRfcommSocketToServiceRecord(myUUID);
            gBtSocket.connect();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            endBTConnection();
            Debug("BLUETOOTH", "Failed to connect to to bluetooth device!");
            return;
        }

        Debug("BLUETOOTH", "Succesfully connected to device " + address);
        showToast("Conectado al dispositivo\n" + name + " (" + address + ")");

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        gBtStatus = 4;
    }

    public void endBTConnection()
    {
        gBtStatus = 0;
        gCheckSwitch = false;

        if(gBtSocket != null)
        {
            try {
                gBtSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            gBtSocket = null;
        }

        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                // Compressor mode
                switch1.setChecked(false);
                switch1.setEnabled(false);
                // On Off buttons
                onButton.setEnabled(true);
                offButton.setEnabled(true);
                // Psi on
                psiOnTxt.setText("-");
                // Psi off
                psiOffTxt.setText("-");
                // On time
                onTimeTxt.setText("-");
                // Off time
                offTimeTxt.setText("-");
                // Alert
                gCAlertTxt.setText("");

                imgCooldown.setColorFilter(color_hide);
                imgCooldown.setTag(color_hide);
                imgCompOn.setColorFilter(color_hide);
                imgAlert.setColorFilter(color_hide);
                imgAlert.setTag(color_hide);

                showLogin(false);
                showMainUI(false);
            }
        });
    }

    public void sendData(String data)
    {
        data = data + "\n";
        try
        {
            gBtSocket.getOutputStream().write(data.getBytes());
            Debug("DATA", "Sending data to Arduino (" + data + ")");
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    //
    // USEFUL FUNCTIONS
    //
    void showToast(String str)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                if(gToast != null)
                    gToast.cancel();

                gToast = Toast.makeText(getApplicationContext(), str, Toast.LENGTH_LONG);
                gToast.setGravity(Gravity.BOTTOM | Gravity.CENTER, 0, 250);
                gToast.show();
            }
        });
    }

    public void currentStatus(String str)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                gPairStatus.setText(str);
            }
        });
    }

    public void Debug(String str1, String str2)
    {
        if (_DEBUG)
            Log.d(str1, str2);
    }
}