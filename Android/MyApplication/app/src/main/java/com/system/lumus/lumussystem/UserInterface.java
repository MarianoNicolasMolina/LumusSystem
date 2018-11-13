package com.system.lumus.lumussystem;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.fingerprint.FingerprintManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.multidots.fingerprintauth.FingerPrintAuthCallback;
import com.multidots.fingerprintauth.FingerPrintAuthHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.UUID;
public class UserInterface extends AppCompatActivity implements  TimePickerDialog.OnTimeSetListener,FingerPrintAuthCallback {
    Button idBtnSwitchLuces, idBtnSwitchAutomatico,idBtnAlarma,idDesconectar;
    TextView idBufferInMov;
    TextView idBufferInSensorLuz;
    TextView idBufferInPersiana;
    TextView idBufferInEstadoLuz;
    TextView idBufferInHora;
    ListView listViewAlarma;

    private FingerPrintAuthHelper mFingerPrintAuthHelper;
    private int minute,hour;
    private int minuteFinal,hourFinal;
    private AlarmAdapter alarmAdapter;
    private ArrayList<Calendar> calendarArray;

    private static final String SWITCH_LUCES = "0";
    private static final String SWITCH_LDR = "1";
    private static final String SWITCH_PERSIANA = "2";
    private static final String ADD_ALARM_CODE = "3";
    private static final String DELETE_ALARM_CODE = "4";
    private static final String SWITCH_MOVIMIENTO = "5";

    //-------------------------------------------
    Handler bluetoothIn;
    final int handlerState = 0;
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private StringBuilder DataStringIN = new StringBuilder();
    private ConnectedThread MyConexionBT;
    // Identificador unico de servicio - SPP UUID
    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    // String para la direccion MAC
    private static String address = null;

    private SensorManager mSensorManager;
    private ShakeEventListener mSensorListener;

    //-------------------------------------------

    @RequiresApi(api = Build.VERSION_CODES.M)
    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_interface);
        //2)
        //Enlaza los controles con sus respectivas vistas
        idBtnSwitchLuces =  findViewById(R.id.idBtnSwitchLuces);
        idBtnSwitchAutomatico =  findViewById(R.id.idBtnSwitchAutomatico);
        idBtnAlarma =  findViewById(R.id.idBtnAlarma);
        idDesconectar =  findViewById(R.id.idDesconectar);
        idBufferInMov =  findViewById(R.id.idBufferInMov);
        idBufferInSensorLuz =  findViewById(R.id.idBufferInSensorLuz);
        idBufferInPersiana =  findViewById(R.id.idBufferInPersiana);
        idBufferInEstadoLuz =  findViewById(R.id.idBufferInEstadoLuz);
        idBufferInHora =  findViewById(R.id.idBufferInHora);
        listViewAlarma = findViewById(R.id.listViewAlarma);


        bluetoothIn = new Handler() {
            public void handleMessage(android.os.Message msg) {
                if (msg.what == handlerState) {
                    String readMessage = (String) msg.obj;
                    DataStringIN.append(readMessage);

                    int endOfLineIndex = DataStringIN.indexOf("#");

                    if (endOfLineIndex > 0) {
                        String dataInPrint = DataStringIN.substring(0, endOfLineIndex);
                        String[] dataInPrintSeparated = dataInPrint.split("\\|");

                        idBufferInMov.setText("Sensor Mov: " + dataInPrintSeparated[0]);
                        idBufferInSensorLuz.setText("Sensor Luz: " + dataInPrintSeparated[1]);
                        idBufferInPersiana.setText("Persiana: " + dataInPrintSeparated[2]);
                        idBufferInEstadoLuz.setText("Estado Luz: " + dataInPrintSeparated[3]);
                        idBufferInHora.setText("Hora: " + dataInPrintSeparated[4]);
                        DataStringIN.delete(0, DataStringIN.length());
                    }
                }
            }
        };

        btAdapter = BluetoothAdapter.getDefaultAdapter(); // get Bluetooth adapter

        VerificarEstadoBT();

        calendarArray = new ArrayList<Calendar>();
        cargarAlarmas();
        alarmAdapter = new AlarmAdapter(this, calendarArray);

        listViewAlarma.setAdapter(alarmAdapter);
        listViewAlarma.setClickable(true);
        alarmAdapter.setAlarmaInterface(new AlarmAdapter.AlarmaInterface() {

            @Override
            public void sendAlarmaOn(int pos) {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
                String alarmaAEnviar = sdf.format(calendarArray.get(pos).getTime());

                Toast.makeText(UserInterface.this, "Alarma Encendida a las" + alarmaAEnviar, Toast.LENGTH_SHORT).show(); //Activar alarma en el arduino
                MyConexionBT.write(ADD_ALARM_CODE + "|" + alarmaAEnviar.substring(0,2) + alarmaAEnviar.substring(3,5) );

            }

            @Override
            public void sendAlarmaOff(int pos) {
                Toast.makeText(UserInterface.this, "Alarma Apagada", Toast.LENGTH_SHORT).show(); //Desactivar alarma en el arduino
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
                String alarmaAEnviar = sdf.format(calendarArray.get(pos).getTime());
                MyConexionBT.write(DELETE_ALARM_CODE + "|" + alarmaAEnviar.substring(0,2) + alarmaAEnviar.substring(3,5));
            }
        });

        listViewAlarma.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {

            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                AlertDialog.Builder adb=new AlertDialog.Builder(UserInterface.this);
                adb.setTitle("Eliminar");
                adb.setMessage("Estas seguro que desas eliminar la alarma?");
                final int positionToRemove = position;
                adb.setNegativeButton("Cancel", null);
                adb.setPositiveButton("Ok", new AlertDialog.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        calendarArray.remove(positionToRemove);
                        Toast.makeText(UserInterface.this, "Alarma Eliminada", Toast.LENGTH_SHORT).show(); //Eliminar parte del arduino
                        alarmAdapter.notifyDataSetChanged();
                    }});
                adb.show();
                return true;
            }
        });

        // Configuracion onClick listeners para los botones 
        // para indicar que se realizara cuando se detecte 
        // el evento de Click 
        idBtnSwitchLuces.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v)
            {
                MyConexionBT.write(SWITCH_LUCES);
                Toast.makeText(UserInterface.this, "Switch luces", Toast.LENGTH_SHORT).show(); // Hacer parte del sensor de luz
            }
        });

        idBtnSwitchAutomatico.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                MyConexionBT.write(SWITCH_LDR);
                Toast.makeText(UserInterface.this, "Switch sensor de luz", Toast.LENGTH_SHORT).show(); // Hacer parte del sensor de luz
            }
        });

        idBtnAlarma.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Calendar c = Calendar.getInstance();
                hour = c.get(Calendar.HOUR_OF_DAY);
                minute = c.get(Calendar.MINUTE);

                TimePickerDialog timePickerDialog = new TimePickerDialog(UserInterface.this,UserInterface.this,hour,minute, DateFormat.is24HourFormat(UserInterface.this));

                timePickerDialog.show();
            }
        });
        idDesconectar.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (btSocket!=null)
                {
                    try {btSocket.close();}
                    catch (IOException e)
                    { Toast.makeText(getBaseContext(), "Error", Toast.LENGTH_SHORT).show();}
                }
                finish();
            }
        });


        //Parte sensores

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensorListener = new ShakeEventListener();

        mSensorListener.setOnShakeListener(new ShakeEventListener.OnShakeListener() {

            public void onShake() {
                Toast.makeText(UserInterface.this, "Switch luces", Toast.LENGTH_SHORT).show(); // Hacer parte del sensor de luz
                MyConexionBT.write(SWITCH_LUCES);
            }

            public void onGettingCloser(){
                Toast.makeText(UserInterface.this, "Switch sensor de movimiento", Toast.LENGTH_SHORT).show(); // Hacer parte del sensor de proximidad
                MyConexionBT.write(SWITCH_MOVIMIENTO);
            }
        });

        mFingerPrintAuthHelper = FingerPrintAuthHelper.getHelper(this, this);

    }



    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException
    {
        //crea un conexion de salida segura para el dispositivo 
        //usando el servicio UUID 
        return device.createRfcommSocketToServiceRecord(BTMODULEUUID);
    }


    @Override
    public void onResume()
    {
        super.onResume();
        //Consigue la direccion MAC desde DeviceListActivity via intent 
        Intent intent = getIntent();
        //Consigue la direccion MAC desde DeviceListActivity via EXTRA 
        address = intent.getStringExtra(DispositivosBT.EXTRA_DEVICE_ADDRESS);//<-<- PARTE A MODIFICAR >->-> 
        //Setea la direccion MAC 
        BluetoothDevice device = btAdapter.getRemoteDevice(address);

        try
        {
            btSocket = createBluetoothSocket(device);
        } catch (IOException e) {
            Toast.makeText(getBaseContext(), "La creacción del Socket fallo", Toast.LENGTH_LONG).show();
        }
        // Establece la conexión con el socket Bluetooth. 
        try
        {
            btSocket.connect();
        } catch (IOException e) {
            try {
                btSocket.close();
            } catch (IOException e2) {}
        }
        MyConexionBT = new ConnectedThread(btSocket);
        MyConexionBT.start();

        mSensorManager.registerListener(mSensorListener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(mSensorListener, mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY),       SensorManager.SENSOR_DELAY_NORMAL);
        mFingerPrintAuthHelper.startAuth();

    }
    @Override
    protected void onStop()
    {
        super.onStop();
    }

    @Override
    public void onPause()
    {
        mSensorManager.unregisterListener(mSensorListener);
        super.onPause();
        try
        { // Cuando se sale de la aplicación esta parte permite 
            // que no se deje abierto el socket 
            btSocket.close();
        } catch (IOException e2) {}
        mFingerPrintAuthHelper.stopAuth();
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        guardarAlarmas();
    }

    private void cargarAlarmas() {
        SharedPreferences sharedPreferences = this.getSharedPreferences("ALARMAS",Context.MODE_PRIVATE);

        try {
            calendarArray = (ArrayList<Calendar>) ObjectSerializer.deserialize(sharedPreferences.getString("ALARMAS",ObjectSerializer.serialize(new ArrayList<Calendar>())));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void guardarAlarmas() {
        SharedPreferences sharedPreferences = this.getSharedPreferences("ALARMAS",Context.MODE_PRIVATE);
        try{
            sharedPreferences.edit().putString("ALARMAS",ObjectSerializer.serialize(calendarArray)).apply();
        } catch(IOException e){
            e.printStackTrace();
        }
    }




    //Comprueba que el dispositivo Bluetooth está disponible y solicita que se active si está desactivado
    private void VerificarEstadoBT() {

        if(btAdapter==null) {
            Toast.makeText(getBaseContext(), "El dispositivo no soporta bluetooth", Toast.LENGTH_LONG).show();
        } else {
            if (btAdapter.isEnabled()) {
            } else {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
        }
    }

    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        hourFinal = hourOfDay;
        minuteFinal = minute;

        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hourFinal);
        c.set(Calendar.MINUTE, minuteFinal);
        if(calendarArray.size() < 5){
            calendarArray.add(c);
            listViewAlarma.invalidateViews();
        }else{
            Toast.makeText(UserInterface.this, "Solo puedes agregar un maximo de 5 alarmas, elimina alguna de las anteriores para agregar otra.", Toast.LENGTH_SHORT).show();
        }

    }

    @Override
    public void onNoFingerPrintHardwareFound() {
        Toast.makeText(UserInterface.this, "No tenés sensor de huella digital.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onNoFingerPrintRegistered() {

    }

    @Override
    public void onBelowMarshmallow() {
        Toast.makeText(UserInterface.this, "Tu dispositivo debe tener una versión de andriod mayor o igual a android M", Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onAuthSuccess(FingerprintManager.CryptoObject cryptoObject) {
        Toast.makeText(UserInterface.this, "Switch Persiana", Toast.LENGTH_SHORT).show();
        MyConexionBT.write(SWITCH_PERSIANA);
        mFingerPrintAuthHelper.startAuth();
    }

    @Override
    public void onAuthFailed(int errorCode, String errorMessage) {
        MyConexionBT.write(SWITCH_PERSIANA);
        Toast.makeText(UserInterface.this, "Switch Persiana", Toast.LENGTH_SHORT).show();
    }


    //Crea la clase que permite crear el evento de conexion 
    private class ConnectedThread extends Thread
    {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket)
        {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            try
            {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run()
        {
            byte[] buffer = new byte[256];
            int bytes;

            // Se mantiene en modo escucha para determinar el ingreso de datos 
            while (true) {
                try {
                    bytes = mmInStream.read(buffer);
                    String readMessage = new String(buffer, 0, bytes);
                    // Envia los datos obtenidos hacia el evento via handler 
                    bluetoothIn.obtainMessage(handlerState, bytes, -1, readMessage).sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }

        public void write(String input)
        {
            try {
                mmOutStream.write(input.getBytes());
            }
            catch (IOException e)
            {
                //si no es posible enviar datos se cierra la conexión 
                Toast.makeText(getBaseContext(), "La Conexión fallo", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
} 