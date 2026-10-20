import socket
import sys
import threading
import time
import paramiko

# --- CONFIGURACIÓN ---
PORT = 2222 # Puerto por defecto para evitar conflictos en Windows
HOST_KEY = paramiko.RSAKey.generate(2048)

class SSHHoneypot(paramiko.ServerInterface):
    """
    Clase que emula la interfaz del servidor SSH utilizando la librería paramiko.
    Captura todos los intentos de inicio de sesión de un adversario.
    """
    def __init__(self, client_addr):
        """
        Inicializa la instancia del honeypot SSH.

        :param client_addr: Tupla con la dirección IP y el puerto del cliente atacante.
        """
        self.client_addr = client_addr

    def check_auth_password(self, username, password):
        """
        Verifica las credenciales SSH (siempre rechaza la conexión). Loguea las credenciales.

        :param username: Usuario intentado por el atacante.
        :param password: Password intentado por el atacante.
        :return: paramiko.AUTH_FAILED siempre para frustrar el login al tiempo que se captura la huella.
        """
        msg = f">>> [COWRIE-SSH] Login intentado: {username}:{password} desde {self.client_addr[0]}"
        print(f"\033[1;31m{msg}\033[0m")
        # Loguear para Sentinel C2
        with open("honeypot_log.txt", "a") as f:
            f.write(msg + "\n")
        return paramiko.AUTH_FAILED

    def check_channel_request(self, kind, chanid):
        """
        Rechaza peticiones de apertura de canales SSH, como ejecución de shell.
        """
        return paramiko.OPEN_FAILED_ADMINISTRATIVELY_PROHIBITED

def handle_client(client, addr):
    """
    Gestiona la conexión de un único cliente atacante en un hilo independiente.

    :param client: El socket cliente conectado.
    :param addr: Dirección IP del atacante.
    """
    try:
        transport = paramiko.Transport(client)
        transport.add_server_key(HOST_KEY)
        server = SSHHoneypot(addr)
        transport.start_server(server=server)
    except Exception as e:
        pass

def main():
    """
    Método principal que despliega el emulador de Honeypot SSH (Cowrie) en el puerto asignado.
    """
    print(f"\n\033[1;36m>>> SENTINEL-COWRIE ACTIVADO\033[0m")
    print(f"\033[1;32m[*] Escuchando SSH en el puerto {PORT}...\033[0m")
    
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        sock.bind(('0.0.0.0', PORT))
        sock.listen(100)
    except Exception as e:
        print(f"\033[1;31m[-] Error al abrir puerto: {e}\033[0m")
        return

    while True:
        try:
            client, addr = sock.accept()
            threading.Thread(target=handle_client, args=(client, addr), daemon=True).start()
        except KeyboardInterrupt:
            break
        except:
            pass

if __name__ == '__main__':
    main()
