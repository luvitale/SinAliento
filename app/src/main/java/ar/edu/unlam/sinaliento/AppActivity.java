package ar.edu.unlam.sinaliento;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Timer;
import java.util.TimerTask;

import ar.edu.unlam.sinaliento.dto.EventRequest;
import ar.edu.unlam.sinaliento.dto.EventResponse;
import ar.edu.unlam.sinaliento.dto.RefreshResponse;


import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class AppActivity extends AppCompatActivity implements SensorEventListener {

    private static final int ROTATION_WAIT_TIME_MS = 500;
    private long mGyroTime = 0;

    private SensorManager mSensorManager;
    private Sensor mSensorGyroscope;
    private Sensor mSensorProximity;

    private TextView txtGyroX;
    private TextView txtGyroY;
    private TextView txtGyroZ;

    private double valor;
    private boolean isOn;

    private TimerTask doAsynchronousTask;

    private final int minutesToRefreshToken = 30;
    private final int millisecondsToRefreshToken = minutesToRefreshToken * 60 * 1000;

    MySharedPreferences sharedPreferences = MySharedPreferences.getSharedPreferences(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensorGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensorProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        refreshToken();

        txtGyroX = findViewById(R.id.tvGyroX);
        txtGyroY = findViewById(R.id.tvGyroY);
        txtGyroZ = findViewById(R.id.tvGyroZ);
        isOn = false;
        valor = 10;
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY), SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void ToLogin(View view) {
        StopSensors();
        sharedPreferences.setToken("");
        sharedPreferences.setTokenRefresh("");
        Intent login = new Intent(this, MainActivity.class);

        Toast.makeText(this, "Sesión finalizada", Toast.LENGTH_SHORT).show();

        startActivity(login);
    }

    public void InitApp(View view) {

        if (valor == 0) {
            InitializeSensors();
            isOn = true;
        }
        else {
            Toast.makeText(this, "Debe aproximar el celular", Toast.LENGTH_SHORT).show();
        }

    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        synchronized (this) {
            Log.d("Sensor", event.sensor.getName());

            if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
                valor = event.values[0];
                Toast.makeText(this, "Proximidad: " + valor, Toast.LENGTH_LONG).show();
                registerProximityEvent(valor);
            }

            if(valor == 0 && isOn == true) {
                if(event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {

                    long now = System.currentTimeMillis();

                    long totalGyro = (long)(event.values[0] + event.values[1] + event.values[2]);

                    if(totalGyro == 0) {
                        mGyroTime = System.currentTimeMillis();
                        if ((now - mGyroTime) < ROTATION_WAIT_TIME_MS) {
                            mGyroTime = System.currentTimeMillis();
                        }
                        else {
                            StopSensors();
                            Toast.makeText(this, "Se ha enviado una ambulancia a su ubicacion", Toast.LENGTH_LONG ).show();
                        }
                    }

                    registerGyroscopeEvent(event.values[0], event.values[1], event.values[2]);

                    txtGyroX.setText(Float.toString(event.values[0]));
                    txtGyroY.setText(Float.toString(event.values[1]));
                    txtGyroZ.setText(Float.toString(event.values[2]));
                }
            }
            else if(isOn == true){
                txtGyroX.setText("No puede obtenerse el valor");
                txtGyroY.setText("No puede obtenerse el valor");
                txtGyroZ.setText("No puede obtenerse el valor");
            }

        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    protected void InitializeSensors () {
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void StopSensors()
    {
        mSensorManager.unregisterListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE));
        mSensorManager.unregisterListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY));
    }

    public void refreshToken() {
        Timer timer = new Timer();
        doAsynchronousTask = new TimerTask() {
            public void run() {
                try {
                    Retrofit retrofit = new Retrofit.Builder()
                            .addConverterFactory(GsonConverterFactory.create())
                            .baseUrl("http://so-unlam.net.ar/api/")
                            .build();

                    RegisterApi apiRegister = retrofit.create(RegisterApi.class);

                    Call<RefreshResponse> call = apiRegister.refreshToken("Bearer " + sharedPreferences.getTokenRefresh());
                    call.enqueue(new Callback<RefreshResponse>() {
                        @Override
                        public void onResponse(Call<RefreshResponse> call, retrofit2.Response<RefreshResponse> response) {

                            if(response.isSuccessful()) {
                                sharedPreferences.setToken(response.body().getToken());
                                sharedPreferences.setTokenRefresh(response.body().getTokenRefresh());
                            }
                            else{
                                Toast.makeText(getApplicationContext(), "No anduvo bien", Toast.LENGTH_LONG).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<RefreshResponse> call, Throwable t) {
                            Log.e(null,t.getMessage());
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        timer.schedule(doAsynchronousTask, 0, millisecondsToRefreshToken);

    }

    private void registerProximityEvent(double value) {
        EventRequest eventRequest = new EventRequest();

        eventRequest.setEnv("PROD");
        eventRequest.setTypeEvents("Medición de sensor de proximidad");
        eventRequest.setDescription("Valor: " + value);

        Retrofit retrofit = new Retrofit.Builder()
                .addConverterFactory(GsonConverterFactory.create())
                .baseUrl("http://so-unlam.net.ar/api/")
                .build();

        RegisterApi apiRegister = retrofit.create(RegisterApi.class);
        Call<EventResponse> call = apiRegister.registrarEvento("Bearer " + sharedPreferences.getToken(), eventRequest);
        call.enqueue(new Callback<EventResponse>() {
            @Override
            public void onResponse(Call<EventResponse> call, retrofit2.Response<EventResponse> response) {

                if(response.isSuccessful()) {
                    Log.e("Evento Proximidad", "Evento Registrado");
                }

                else {
                    Log.e("Evento Proximidad", "Evento No Registrado");
                }
            }

            @Override
            public void onFailure(Call<EventResponse> call, Throwable t) {
                Log.e(null,t.getMessage());
            }
        });
    }

    private void registerGyroscopeEvent(float valueX, float valueY, float valueZ) {
        EventRequest eventRequest = new EventRequest();

        eventRequest.setEnv("PROD");
        eventRequest.setTypeEvents("Medición de giroscopio");
        eventRequest.setDescription("Valor X: " + valueX + ", Valor Y: " + valueY + ", Valor Z: " + valueZ);

        Retrofit retrofit = new Retrofit.Builder()
                .addConverterFactory(GsonConverterFactory.create())
                .baseUrl("http://so-unlam.net.ar/api/")
                .build();

        RegisterApi apiRegister = retrofit.create(RegisterApi.class);
        Call<EventResponse> call = apiRegister.registrarEvento("Bearer " + sharedPreferences.getToken(), eventRequest);
        call.enqueue(new Callback<EventResponse>() {
            @Override
            public void onResponse(Call<EventResponse> call, Response<EventResponse> response) {

                if(response.isSuccessful()) {
                    Log.e("Evento Giroscopio", "Evento Registrado");
                }

                else {
                    Log.e("Evento Giroscopio", "Evento No Registrado");
                }
            }

            @Override
            public void onFailure(Call<EventResponse> call, Throwable t) {
                Log.e(null,t.getMessage());
            }
        });
    }

}
