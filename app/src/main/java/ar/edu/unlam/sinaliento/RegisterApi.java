package ar.edu.unlam.sinaliento;

import ar.edu.unlam.sinaliento.dto.EventRequest;
import ar.edu.unlam.sinaliento.dto.EventResponse;
import ar.edu.unlam.sinaliento.dto.Login;
import ar.edu.unlam.sinaliento.dto.Post;
import ar.edu.unlam.sinaliento.dto.RefreshResponse;
import ar.edu.unlam.sinaliento.dto.Response;
import ar.edu.unlam.sinaliento.dto.Session;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.PUT;

public interface RegisterApi {

    @POST("api/register")
    Call<Response> register(@Body Post request);
    @POST("api/login")
    Call<Session> login(@Body Login request);

    @Headers({"Content-Type: application/json"})
    @POST("api/event")
    Call<EventResponse> registrarEvento(@Header("Authorization") String token, @Body EventRequest request);

    @PUT("api/refresh")
    Call<RefreshResponse> refreshToken(@Header("Authorization") String token_refresh);
}
