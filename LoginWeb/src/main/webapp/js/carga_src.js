/**
 * SCRIPT DE CARGA (Ingeniería Social)
 * Simula procesos de cifrado y verificación para ganar la confianza del atacante
 * mientras los scripts forenses se preparan en segundo plano.
 */

// Validación de flujo de seguridad: evita accesos directos sin consentimiento previo
if (sessionStorage.getItem('terminos_aceptados') !== "true") {
    window.location.href = "index.html";
}

/**
 * Mensajes de estado técnicos diseñados para proyectar una imagen de sistema robusto.
 * Se utilizan para mantener al usuario en la página mientras se recopilan datos iniciales.
 */
const mensajes = [
    "Estableciendo túnel cifrado TLS 1.3...",
    "Verificando integridad del navegador...",
    "Sincronizando con el servidor de autenticación...",
    "Buscando certificados de identidad...",
    "Conexión segura establecida. Redirigiendo..."
];

let currentMsg = 0;
const statusElement = document.getElementById('status');

/**
 * Ciclo de actualización visual que cambia el mensaje de carga cada 1.2 segundos.
 */
const interval = setInterval(() => {
    if (currentMsg < mensajes.length) {
        statusElement.innerText = mensajes[currentMsg];
        currentMsg++;
    } else {
        clearInterval(interval);
        // Salto automático al portal de administrador señuelo
        window.location.href = 'admin.html';
    }
}, 1200);
