import socket
import sys
import os
import threading
import time
import base64

# --- CONFIGURACIÓN ---
VERSION = "2.0.1"
BANNER = f"""
\033[1;36m
  ____  _____ _   _ _____ ___ _   _ _____ _     
 / ___|| ____| \ | |_   _|_ _| \ | | ____| |    
 \___ \|  _| |  \| | | |  | ||  \| |  _| | |    
  ___) | |___| |\  | | |  | || |\  | |___| |___ 
 |____/|_____|_| \_| |_| |___|_| \_|_____|_____|
                                                 
  SENTINEL - TACTICAL HONEYPOT SUITE (PENTBOX)
  Version: {VERSION} | Portable Edition
\033[0m
"""

def clear_screen():
    """
    Limpia la pantalla de la consola dependiendo del sistema operativo subyacente.
    Usa 'cls' para Windows (nt) y 'clear' para sistemas basados en Unix.
    """
    os.system('cls' if os.name == 'nt' else 'clear')

def honey_pot_module():
    """
    Despliega un Honeypot HTTP básico en un puerto específico (por defecto 80).
    Captura y registra los intentos de conexión en el archivo 'honeypot_log.txt', 
    enviando un falso error 403 al atacante para frustrar sus operaciones y 
    simular un servicio restringido.
    """
    print("\n\033[1;33m[+] MÓDULO HONEYPOT ACTIVADO\033[0m")
    port = input("Puerto a monitorizar (def: 80): ") or "80"
    try:
        port = int(port)
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.bind(("0.0.0.0", port))
        s.listen(100)
        print(f"\033[1;32m[*] Escuchando en el puerto {port}... Presiona Ctrl+C para detener.\033[0m")
        
        while True:
            client, addr = s.accept()
            msg = f">>> INTRUSIÓN DETECTADA: {addr[0]}:{addr[1]} a las {time.ctime()}"
            print(f"\033[1;31m{msg}\033[0m")
            # Loguear a un archivo para que Sentinel lo lea si es necesario
            with open("honeypot_log.txt", "a") as f:
                f.write(msg + "\n")
            
            client.send(b"HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n<h1>403 Forbidden - Sentinel Monitoring</h1>")
            client.close()
    except Exception as e:
        print(f"\033[1;31m[-] Error: {e}\033[0m")
    except KeyboardInterrupt:
        print("\n\033[1;33m[!] Honeypot detenido.\033[0m")

def stress_tool():
    """
    Inicia una herramienta de estrés de red (TCP Flood) que lanza múltiples hilos
    para inundar un objetivo (IP y puerto) con conexiones persistentes.
    Funciona como componente de pruebas de carga de la suite Pentbox nativa.
    """
    print("\n\033[1;33m[+] MÓDULO STRESS TOOL (TCP FLOOD)\033[0m")
    target = input("IP Objetivo: ")
    port = int(input("Puerto Objetivo: "))
    print(f"\033[1;31m[*] Iniciando ataque de stress hacia {target}:{port}...\033[0m")
    
    def flood():
        while True:
            try:
                s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                s.connect((target, port))
                s.send(b"SENTINEL_STRESS_TEST")
                s.close()
            except:
                pass

    for i in range(50):
        threading.Thread(target=flood, daemon=True).start()
    
    input("\033[1;32m[!] Stress en curso. Pulsa ENTER para detener.\033[0m")

def net_info():
    """
    Recopila y muestra información crítica de la red del sistema operativo host,
    incluyendo Hostname, IP Local y dirección IP Pública solicitada a una API externa.
    """
    print("\n\033[1;33m[+] MÓDULO NETWORK INFO\033[0m")
    print(f"Hostname: {socket.gethostname()}")
    print(f"IP Local: {socket.gethostbyname(socket.gethostname())}")
    try:
        import requests
        ext_ip = requests.get("https://api.ipify.org").text
        print(f"IP Pública: {ext_ip}")
    except:
        print("IP Pública: No se pudo obtener (instala 'requests')")
    input("\nPresiona ENTER para volver...")

def main_menu():
    """
    Ejecuta el menú principal interactivo de la Suite de Sentinel (Terminal UI),
    proporcionando opciones para desplegar honeypots lógicos, simulaciones de SSH y
    auditoría de red.
    """
    while True:
        clear_screen()
        print(BANNER)
        print("1. Honeypot (Detección de Intrusos)")
        print("2. Stress Tool (Prueba de Carga)")
        print("3. Network Info (Guerra de Información)")
        print("4. Cowrie Lite (SSH Honeypot - Requiere Paramiko)")
        print("5. Salir")
        
        choice = input("\nSelecciona una opción: ")
        
        if choice == "1":
            honey_pot_module()
        elif choice == "2":
            stress_tool()
        elif choice == "3":
            net_info()
        elif choice == "4":
            print("\033[1;33m[*] Lanzando Sentinel-Cowrie...\033[0m")
            os.system("python sentinel_ssh.py")
        elif choice == "5":
            print("Saliendo de la Suite...")
            break
        else:
            print("Opción inválida.")
            time.sleep(1)

if __name__ == "__main__":
    main_menu()
