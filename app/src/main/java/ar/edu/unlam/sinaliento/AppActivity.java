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


import ar.edu.unlam.sinaliento.utils.MySharedPreferences;
import ar.edu.unlam.sinaliento.utils.SoaApi;
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

        initializeProximitySensor();
    }

    public void logOut(View view) {
        stopSensors();
        sharedPreferences.setToken("");
        sharedPreferences.setTokenRefresh("");
        Intent login = new Intent(this, MainActivity.class);

        Toast.makeText(this, getString(R.string.finished_session_text), Toast.LENGTH_SHORT).show();

        startActivity(login);
    }

    public void initApp(View view) {

        if (valor == 0) {
            initializeGyroscope();
            isOn = true;
        }
        else {
            Toast.makeText(this, getString(R.string.phone_should_be_closer_text), Toast.LENGTH_SHORT).show();
        }

    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        synchronized (this) {
            Log.d("Sensor", event.sensor.getName());

            if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
                valor = event.values[0];
                Toast.makeText(this, getString(R.string.proximity_text) + valor, Toast.LENGTH_LONG).show();
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
                            stopSensors();
                            Toast.makeText(this, getString(R.string.ambulance_alert_text), Toast.LENGTH_LONG ).show();
                        }
                    }

                    registerGyroscopeEvent(event.values[0], event.values[1], event.values[2]);

                    txtGyroX.setText(Float.toString(event.values[0]));
                    txtGyroY.setText(Float.toString(event.values[1]));
                    txtGyroZ.setText(Float.toString(event.values[2]));
                }
            }
            else if(isOn == true){
                txtGyroX.setText(getString(R.string.unobtained_x_value_text));
                txtGyroY.setText(getString(R.string.unobtained_y_value_text));
                txtGyroZ.setText(getString(R.string.unobtained_z_value_text));
            }

        }
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private void initializeSensor(Sensor sensor) {
        mSensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void initializeProximitySensor() {
        initializeSensor(mSensorProximity);
    }

    private void initializeGyroscope() {
        initializeSensor(mSensorGyroscope);
    }

    private void stopSensor(Sensor sensor) {
        mSensorManager.unregisterListener(this, sensor);
    }

    private void stopSensors()
    {
        stopSensor(mSensorProximity);
        stopSensor(mSensorGyroscope);
    }

    public void refreshToken() {
        int minutesToRefreshToken = 30;
        int millisecondsToRefreshToken = minutesToRefreshToken * 60 * 1000;

        Timer timer = new Timer();
        TimerTask doAsynchronousTask = new TimerTask() {
            public void run() {
                try {
                    Retrofit retrofit = new Retrofit.Builder()
                            .addConverterFactory(GsonConverterFactory.create())
                            .baseUrl(getString(R.string.apiURL))
                            .build();

                    SoaApi apiSOA = retrofit.create(SoaApi.class);

                    Call<RefreshResponse> call = apiSOA.refreshToken("Bearer " + sharedPreferences.getTokenRefresh());
                    call.enqueue(new Callback<RefreshResponse>() {
                        @Override
                        public void onResponse(Call<RefreshResponse> call, Response<RefreshResponse> response) {

                            if (response.isSuccessful()) {
                                sharedPreferences.setToken(response.body().getToken());
                                sharedPreferences.setTokenRefresh(response.body().getTokenRefresh());
                            } else {
                                Toast.makeText(getApplicationContext(), getString(R.string.unsuccessful_refresh_token_text), Toast.LENGTH_LONG).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<RefreshResponse> call, Throwable t) {
                            Log.e(null, t.getMessage());
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

        eventRequest.setEnv(getString(R.string.environment));
        eventRequest.setTypeEvents(getString(R.string.proximity_type_event));
        eventRequest.setDescription(getString(R.string.value_description) + value);

        Retrofit retrofit = new Retrofit.Builder()
                .addConverterFactory(GsonConverterFactory.create())
                .baseUrl(getString(R.string.apiURL))
                .build();

        SoaApi apiRegister = retrofit.create(SoaApi.class);
        Call<EventResponse> call = apiRegister.registerEvent("Bearer " + sharedPreferences.getToken(), eventRequest);
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

        eventRequest.setEnv(getString(R.string.environment));
        eventRequest.setTypeEvents(getString(R.string.gyroscope_type_event));
        eventRequest.setDescription(
                getString(R.string.x_value_description) + valueX +
                getString(R.string.y_value_description) + valueY +
                getString(R.string.z_value_description) + valueZ
        );

        Retrofit retrofit = new Retrofit.Builder()
                .addConverterFactory(GsonConverterFactory.create())
                .baseUrl(getString(R.string.apiURL))
                .build();

        SoaApi apiRegister = retrofit.create(SoaApi.class);
        Call<EventResponse> call = apiRegister.registerEvent("Bearer " + sharedPreferences.getToken(), eventRequest);
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
