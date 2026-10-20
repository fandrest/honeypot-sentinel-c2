package com.proyecto.loginweb.resources;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

/**
 * Recurso REST de prueba (Ping) para el subsistema Jakarta EE 10.
 * 
 * Permite instrumentación y monitoreo de salud del servidor a nivel de red
 * comprobando que el path /resources/jakartaee10 responda un HTTP 200 OK.
 *
 * @version 1.0.0
 * @author Felipe Andrés
 */
@Path("jakartaee10")
public class JakartaEE10Resource {
    
    @GET
    public Response ping(){
        return Response
                .ok("ping Jakarta EE")
                .build();
    }
}
