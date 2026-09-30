import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Server {
    static void send(com.sun.net.httpserver.HttpExchange e, int code, String body, String type) throws Exception {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        e.sendResponseHeaders(code, data.length);
        e.getResponseBody().write(data);
        e.close();
    }

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8080), 0);

        server.createContext("/health", e -> {
            try {
                send(e, 200, "{\"ok\":true}", "application/json");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        server.createContext("/q/", e -> {
            try {
                String id = e.getRequestURI().getPath().substring(3);

                String html =
                    "<!doctype html><html><head>" +
                    "<meta charset='utf-8'>" +
                    "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
                    "<title>SD-QR</title></head><body>" +
                    "<h1>QR Станки</h1>" +
                    "<h2>Станок: " + id + "</h2>" +
                    "<p>QR-код работает.</p>" +
                    "</body></html>";

                send(e, 200, html, "text/html");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        server.start();
        System.out.println("SD-QR Java Server");
        System.out.println("Listening on port 8080");
    }
}
