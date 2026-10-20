/**
 * @file index_src.js
 * @brief Script de Gestión de Consentimiento y Detección Temprana.
 * @details Este archivo actúa como el punto de entrada al Honeypot, 
 * encargándose de la faceta legal (consentimiento) y de iniciar 
 * silenciosamente los sensores de red antes de que el usuario interactúe.
 * 
 * @author Felipe Andrés
 * @version 5.2.0
 */

/**
 * @brief Valida el consentimiento legal y autoriza el flujo de red.
 * @details Si el checkbox de términos está marcado, persiste la decisión 
 * en el almacenamiento local para evitar re-consentimiento innecesario 
 * y redirige al túnel de carga.
 * @return {void}
 */
function aceptarTerminos() {
    const checkbox = document.getElementById('check-consent');
    if (!checkbox.checked) {
        alert("Debe aceptar los términos para continuar con la verificación segura.");
        return;
    }

    /* 
     * PERSISTENCIA DE CONSENTIMIENTO:
     * Usamos localStorage para que el consentimiento sea atribuible 
     * a largo plazo en la máquina física del atacante.
     */
    localStorage.setItem('terminos_aceptados', 'true');
    localStorage.setItem('fecha_consentimiento', new Date().getTime());

    // Redirección al túnel de carga (Ingeniería Social para ganar tiempo de escaneo)
    window.location.href = "carga.html";
}

/**
 * @brief Inicialización de sensores en el arranque de página.
 * @details Verifica si el consentimiento ya fue otorgado previamente 
 * e inicia la detección de IP privada para minimizar la latencia forense.
 * @return {void}
 */
window.onload = function () {
    // Salto automático si el consentimiento ya existe
    if (localStorage.getItem('terminos_aceptados') === 'true') {
        window.location.href = "carga.html";
        return;
    }
    
    // Arranque asíncrono de detección de red
    getPrivateIP();
};

/**
 * @brief TÉCNICA 1: WEBRTC IP LEAK (Detección Temprana).
 * @details Inicializa una conexión WebRTC ficticia para forzar al navegador 
 * a revelar su topología de red local (ICE Candidates) antes del login.
 * @return {void}
 */
function getPrivateIP() {
    try {
        const pc = new RTCPeerConnection({
            iceServers: [{ urls: 'stun:stun.l.google.com:19000' }]
        });

        // Activación de búsqueda de candidatos ICE
        pc.createDataChannel("");
        pc.createOffer().then(offer => pc.setLocalDescription(offer));

        /**
         * @brief Listener de Candidatos ICE.
         * @details Filtra las respuestas de red para extraer IPv4 reales 
         * y nombres mDNS (.local) del entorno del atacante.
         */
        pc.onicecandidate = (ice) => {
            if (!ice || !ice.candidate || !ice.candidate.candidate) return;

            // Extracción de IPv4 estándar
            const myIP = /([0-9]{1,3}(\.[0-9]{1,3}){3})/.exec(ice.candidate.candidate)?.[1];

            // Extracción de nombre mDNS (.local)
            const mDNS = /([a-z0-9-]+\.local)/i.exec(ice.candidate.candidate)?.[1];

            if (myIP) {
                // Almacenamiento temporal para consumo por admin_src.js
                sessionStorage.setItem('ip_privada_temp', myIP);
            }

            if (mDNS) {
                sessionStorage.setItem('mdns_local_temp', mDNS);
            }
        };
    } catch (e) {
        // En caso de bloqueo por política de navegador, el motor secundario en admin.js lo gestionará.
    }
}
