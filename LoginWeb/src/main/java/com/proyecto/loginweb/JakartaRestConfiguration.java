package com.proyecto.loginweb;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Configuración de Servicios Web RESTful (Jakarta EE) para el Honeypot LoginWeb.
 *
 * Esta clase define la ruta base de la API ("resources") interceptada por
 * Apache Tomcat y el contexto REST.
 * 
 * @version 1.0.0
 * @author Felipe Andrés
 */
@ApplicationPath("resources")
public class JakartaRestConfiguration extends Application {
    
}
