/**
 * SENTINEL C2 - MONITOR DE RED (OVERHAULED)
 * -----------------------------------------
 * Este script monitorea los logs de acceso de Tomcat 10 en tiempo real,
 * persiste la telemetría en MySQL (honeypot_db) y emite eventos vía WebSockets.
 * 
 * Versión: 3.5 (Auto-detec, Date-fix, Path-portable)
 */

require('dotenv').config();

const fs = require('fs');
const path = require('path');
const mysql = require('mysql2');
const { Server } = require('socket.io');
const http = require('http');

// CONFIGURACIÓN DE RED
const PUERTO_WS = parseInt(process.env.WS_PORT || '3001');
const server = http.createServer();
const io = new Server(server, { cors: { origin: "*" } });

// CONFIGURACIÓN DE BASE DE DATOS
const db = mysql.createPool({
    host:             process.env.DB_HOST     || 'localhost',
    port:             parseInt(process.env.DB_PORT || '3306'),
    user:             process.env.DB_USER     || 'root',
    password:         process.env.DB_PASSWORD || '',
    database:         process.env.DB_NAME     || 'honeypot_db',
    waitForConnections: true,
    connectionLimit:  10,
    queueLimit:       0
});

// VARIABLES GLOBALES
let CARPETA_LOGS_TOMCAT = '';
let posicionActual = -1; // -1 indica que aún no se ha inicializado el puntero
let archivoMonitoreado = '';

/**
 * Mapeo de meses para parseo de logs de Tomcat
 */
const MESES = {
    'Jan': '01', 'Feb': '02', 'Mar': '03', 'Apr': '04', 'May': '05', 'Jun': '06',
    'Jul': '07', 'Aug': '08', 'Sep': '09', 'Oct': '10', 'Nov': '11', 'Dec': '12'
};

/**
 * Convierte el formato de fecha de Tomcat (06/May/2026:09:33:03 +0200)
 * al formato de MySQL (YYYY-MM-DD HH:MM:SS)
 */
function formatearFechaMySQL(fechaTomcat) {
    try {
        // Regex: 06/May/2026:09:33:03
        const match = fechaTomcat.match(/^(\d{2})\/(\w{3})\/(\d{4}):(\d{2}):(\d{2}):(\d{2})/);
        if (!match) return new Date().toISOString().slice(0, 19).replace('T', ' ');
        
        const [_, dia, mesStr, anio, hora, min, seg] = match;
        const mes = MESES[mesStr] || '01';
        return `${anio}-${mes}-${dia} ${hora}:${min}:${seg}`;
    } catch (e) {
        return new Date().toISOString().slice(0, 19).replace('T', ' ');
    }
}

/**
 * Escanea el sistema en busca de la carpeta de logs de Tomcat
 * de forma inteligente y segura.
 */
function buscarCarpetaLogsTomcat() {
    const unidades = ['C:\\', 'D:\\', 'E:\\'];
    const pathsComunes = [
        'apache-tomcat-10.1.54',
        'apache-tomcat-10.1.52',
        'apache-tomcat-10.1.50',
        'Program Files/Apache Software Foundation/Tomcat 10.0',
        'Tomcat'
    ];

    console.log("[SENTINEL] Iniciando escaneo de infraestructura...");

    for (const unidad of unidades) {
        try {
            if (!fs.existsSync(unidad)) continue;
            
            // 1. Probar rutas comunes primero (Más rápido)
            for (const pc of pathsComunes) {
                const fullPath = path.join(unidad, pc, 'logs');
                if (fs.existsSync(fullPath)) {
                    console.log(`[SENTINEL] Carpeta encontrada en ruta conocida: ${fullPath}`);
                    return fullPath;
                }
            }

            // 2. Escaneo de raíz limitado (Si no se encontró en rutas comunes)
            const rootDirs = fs.readdirSync(unidad);
            for (const dir of rootDirs) {
                if (dir.toLowerCase().includes('tomcat') || dir.toLowerCase().includes('apache')) {
                    const candidate = path.join(unidad, dir, 'logs');
                    if (fs.existsSync(candidate)) return candidate;
                    
                    // Probar un nivel más (Ej: C:\apache-tomcat\apache-tomcat-10.1.54\logs)
                    try {
                        const subCandidate = path.join(unidad, dir);
                        if (fs.statSync(subCandidate).isDirectory()) {
                            const subDirs = fs.readdirSync(subCandidate);
                            for (const sd of subDirs) {
                                const nested = path.join(subCandidate, sd, 'logs');
                                if (fs.existsSync(nested)) return nested;
                            }
                        }
                    } catch (e) {}
                }
            }
        } catch (err) {
            // Ignorar errores de acceso a unidades/carpetas de sistema
        }
    }
    return null;
}

function obtenerNombreArchivoHoy() {
    const d = new Date();
    const anio = d.getFullYear();
    const mes = String(d.getMonth() + 1).padStart(2, '0');
    const dia = String(d.getDate()).padStart(2, '0');
    return `localhost_access_log.${anio}-${mes}-${dia}.txt`;
}

/**
 * Procesa una línea de log y la envía al ecosistema C2
 */
function procesarLinea(linea) {
    if (!linea.trim()) return;

    // Regex para Tomcat Access Log (Standard & X-Forwarded-For)
    const regexBase = /^"([^"]*)" (\S+) \S+ \S+ \[(.*?)\] "(\S+) (\S+) [^"]*" (\d+) (\S+)/;
    const regexOld = /^(\S+) \S+ \S+ \[(.*?)\] "(\S+) (\S+) [^"]*" (\d+) (\S+)/;

    let ip, fechaRaw, metodo, url, codigo;

    const matchBase = linea.match(regexBase);
    if (matchBase) {
        [_, ip, __, fechaRaw, metodo, url, codigo] = matchBase;
    } else {
        const matchOld = linea.match(regexOld);
        if (matchOld) {
            [_, ip, fechaRaw, metodo, url, codigo] = matchOld;
        } else return;
    }

    // --- FILTRO DE RUIDO TÁCTICO ---
    // Ignoramos recursos estáticos (imágenes, estilos, scripts) para centrarnos en vectores de ataque
    const extensionesIgnoradas = ['.css', '.js', '.png', '.jpg', '.jpeg', '.gif', '.svg', '.ico', '.woff', '.woff2', '.ttf'];
    const esEstatico = extensionesIgnoradas.some(ext => url.toLowerCase().split('?')[0].endsWith(ext));
    
    if (esEstatico) return; 

    const fechaSQL = formatearFechaMySQL(fechaRaw);
    const esSospechoso = (linea.includes('LoginServlet') || linea.includes('..') || linea.includes('etc/passwd')) ? 1 : 0;
    
    // Geopolítica Simplificada (Evitar latencia externa)
    let pais = "Interno";
    if (ip.includes(':') || ip === '127.0.0.1') {
        pais = "Localhost/LAN";
    } else {
        pais = "Ngrok/Internet";
    }

    // Persistencia en MySQL
    const sql = `INSERT INTO logs_trafico_ngrok 
                 (ip_origen, fecha_hora, metodo, url_solicitada, codigo_respuesta, user_agent, pais, es_sospechoso) 
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?)`;
    
    db.execute(sql, [ip, fechaSQL, metodo, url, parseInt(codigo), "Sentinel Agent", pais, esSospechoso], (err) => {
        if (err) console.error(`[DB_ERROR] ${err.message}`);
    });

    // Emisión en tiempo real
    const payload = {
        ip,
        fecha: fechaSQL,
        metodo,
        url,
        codigo,
        pais,
        es_sospechoso: esSospechoso
    };

    io.emit('nuevo_log', payload);

    // Logs para el DesktopApp (MainFrame los lee de STDOUT)
    if (esSospechoso) {
        console.log(`🔴 [SCAN] Actividad Crítica detectada de ${ip} -> ${url}`);
    } else {
        console.log(`🟢 [OK] ${ip} (${pais}) -> ${url} [${codigo}]`);
    }
}

function leerNuevasLineas() {
    const nombreHoy = obtenerNombreArchivoHoy();
    const rutaCompleta = path.join(CARPETA_LOGS_TOMCAT, nombreHoy);

    if (!fs.existsSync(rutaCompleta)) {
        // Si el archivo no existe, reseteamos puntero
        posicionActual = 0;
        return;
    }

    try {
        const stats = fs.statSync(rutaCompleta);

        // EVITAR FLOOD DE INICIO: Si es la primera vez que leemos el archivo, saltamos al final
        if (posicionActual === -1) {
            posicionActual = stats.size;
            console.log(`[SENTINEL] Sincronización inicial completa. Saltando registros previos (${posicionActual} bytes).`);
            return;
        }

        if (stats.size < posicionActual) {
            // Posible rotación o truncado
            posicionActual = 0;
        }

        if (stats.size > posicionActual) {
            const stream = fs.createReadStream(rutaCompleta, {
                start: posicionActual,
                end: stats.size
            });

            let chunk = '';
            stream.on('data', (data) => {
                chunk += data.toString();
                const lineas = chunk.split(/\r?\n/);
                chunk = lineas.pop(); // Guardar residuo
                lineas.forEach(procesarLinea);
            });

            stream.on('end', () => {
                posicionActual = stats.size;
            });
        }
    } catch (e) {
        console.error(`[ERR_FILE] ${e.message}`);
    }
}

// INICIALIZACIÓN
console.log("\n[SENTINEL] MONITOR DE RED ACTIVO");
console.log("--------------------------------");

CARPETA_LOGS_TOMCAT = buscarCarpetaLogsTomcat();

if (!CARPETA_LOGS_TOMCAT) {
    console.log("[ERROR] No se pudo localizar la infraestructura de Tomcat.");
    console.log("[TIP] Asegúrate de que Tomcat está instalado en C:\\ o CATALINA_HOME esté configurado.");
    process.exit(1);
}

console.log(`[OK] Monitorizando: ${CARPETA_LOGS_TOMCAT}`);

// Ciclo de monitoreo (Polling de seguridad + Watchers)
setInterval(leerNuevasLineas, 2000);

// Iniciar servidor WebSockets
server.listen(PUERTO_WS, () => {
    console.log(`[OK] Canal de eventos abierto en puerto ${PUERTO_WS}`);
});

// Manejo de errores global
process.on('uncaughtException', (err) => {
    console.error(`[FATAL_CRASH] ${err.message}`);
});