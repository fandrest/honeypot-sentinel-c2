/**
 * SCRIPT DE ESPERA - Nodo de Post-Captura
 * Informa al usuario (atacante) que sus acciones están siendo procesadas
 * bajo un supuesto protocolo de seguridad y anonimización.
 */

/**
 * Mensajes técnicos para simular la consolidación de logs y reporte de actividad.
 * Esto ayuda a disuadir al atacante o darle una sensación de que el sistema ha
 * tomado medidas proactivas contra su intrusión.
 */
const texts = [
    "Consolidando logs...",
    "Enviando traza a nodo central...",
    "Generando reporte de infracción...",
    "Finalizando conexión segura..."
];

let i = 0;
const textInterval = setInterval(() => {
    if (i < texts.length) {
        const element = document.getElementById('dynamic-text');
        if (element) {
            element.innerText = texts[i];
            i++;
        }
    } else {
        clearInterval(textInterval);
        // Redirección de respaldo por JS tras 2 segundos del último mensaje
        setTimeout(() => {
            window.location.href = "https://es.wikipedia.org/wiki/Phishing";
        }, 2000);
    }
}, 2000);
