import argparse, base64, socket, threading

def relay(src, dst):
    try:
        while True:
            data = src.recv(32768)
            if not data: break
            dst.sendall(data)
    except OSError: pass
    finally:
        for s in (src, dst):
            try: s.shutdown(socket.SHUT_RDWR)
            except OSError: pass

def handle(client, phone, port, auth):
    upstream = None
    try:
        upstream = socket.create_connection((phone, port), timeout=15)
        first = client.recv(32768)
        if not first: return
        head, sep, body = first.partition(b"\r\n\r\n")
        lines = [x for x in head.split(b"\r\n") if not x.lower().startswith(b"proxy-authorization:")]
        lines.append(b"Proxy-Authorization: Basic " + auth)
        upstream.sendall(b"\r\n".join(lines) + sep + body)
        threading.Thread(target=relay, args=(client, upstream), daemon=True).start()
        relay(upstream, client)
    except OSError:
        try: client.sendall(b"HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
        except OSError: pass
    finally:
        for s in (client, upstream):
            if s:
                try: s.close()
                except OSError: pass

def main():
    p = argparse.ArgumentParser(description="Local HTTP/HTTPS proxy bridge; not a VPN.")
    p.add_argument("--phone", required=True)
    p.add_argument("--password", required=True)
    p.add_argument("--port", type=int, default=8787)
    args = p.parse_args()
    auth = base64.b64encode(("phone:" + args.password).encode())
    srv = socket.socket()
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("127.0.0.1", 8899)); srv.listen(32)
    print("Proxy bridge on 127.0.0.1:8899. Ctrl+C to stop.")
    try:
        while True:
            c, _ = srv.accept()
            threading.Thread(target=handle, args=(c,args.phone,args.port,auth), daemon=True).start()
    except KeyboardInterrupt: pass
    finally: srv.close()
if __name__ == "__main__": main()
