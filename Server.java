import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Server {

    static final java.util.concurrent.ConcurrentHashMap<String, Integer> OPEN_COUNTS = new java.util.concurrent.ConcurrentHashMap<>();
    static final java.util.concurrent.ConcurrentHashMap<String, Long> FIRST_OPEN = new java.util.concurrent.ConcurrentHashMap<>();
    static void send(com.sun.net.httpserver.HttpExchange e, int code, String body, String type) throws Exception {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        e.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        e.sendResponseHeaders(code, data.length);
        e.getResponseBody().write(data);
        e.close();
    }

    static String dec(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return s;
        }
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

        server.createContext("/health", e -> {
            try {
                send(e, 200, "{\"ok\":true}", "application/json");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        server.createContext("/machines", e -> {
            try {
                if ("POST".equalsIgnoreCase(e.getRequestMethod())) {
                    String body = new String(
                        e.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8
                    );

                    Files.writeString(
                        Path.of("machines.txt"),
                        body,
                        StandardCharsets.UTF_8
                    );

                    send(e, 200, "{\"ok\":true}", "application/json");
                } else {
                    send(e, 405, "{\"error\":\"POST only\"}", "application/json");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                try {
                    send(e, 500, "{\"error\":\"server error\"}", "application/json");
                } catch (Exception ignored) {}
            }
        });

        server.createContext("/debug", e -> {
            try {
                Path f = Path.of("machines.txt");
                String body = Files.exists(f) ? Files.readString(f, StandardCharsets.UTF_8) : "NO machines.txt";
                send(e, 200, body, "text/plain");
            } catch (Exception ex) {
                try { send(e, 500, ex.toString(), "text/plain"); } catch (Exception ignored) {}
            }
        });

        server.createContext("/q/", e -> {
            try {
                String id = dec(e.getRequestURI().getPath().substring(3));

                Path file = Path.of("machines.txt");

                if (!Files.exists(file)) {
                    send(e, 404,
                        "<!doctype html><html><head><meta charset='utf-8'>" +
                        "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
                        "<title>SD-QR</title></head><body>" +
                        "<h1>SD-QR</h1><p>Данные станка пока не загружены.</p>" +
                        "</body></html>",
                        "text/html");
                    return;
                }

                String[] lines = Files.readString(file, StandardCharsets.UTF_8).split("\\R");
                String[] found = null;

                for (String line : lines) {
                    String[] p = line.split("\\|", -1);
                    if (p.length >= 3 && dec(p[0]).equals(id)) {
                        found = p;
                        break;
                    }
                }

                if (found == null) {
                    send(e, 404,
                        "<!doctype html><html><head><meta charset='utf-8'>" +
                        "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
                        "<title>SD-QR</title></head><body>" +
                        "<h1>SD-QR</h1><p>QR-код не найден: " + esc(id) + "</p>" +
                        "</body></html>",
                        "text/html");
                    return;
                }

                String name = found.length > 1 ? dec(found[1]) : "";
                String status = found.length > 2 ? dec(found[2]) : "";
                String contentType = found.length > 3 ? dec(found[3]) : "";
                String title = found.length > 4 ? dec(found[4]) : "";
                String note = found.length > 5 ? dec(found[5]) : "";
        String pageBg = found.length > 11 ? dec(found[11]) : "#0b0f14"; String cardBg = found.length > 12 ? dec(found[12]) : "#ffffff"; String textColor = found.length > 13 ? dec(found[13]) : "#111111";
                String resource = found.length > 6 ? dec(found[6]) : "";
                String extra = found.length > 7 ? dec(found[7]) : "";
        int used = 0, limit = 0, durationMinutes = 0;
        try { if (found.length > 8 && !found[8].isBlank()) used = Integer.parseInt(found[8]); } catch(Exception ignored) {}
        try { if (found.length > 9 && !found[9].isBlank()) limit = Integer.parseInt(found[9]); } catch(Exception ignored) {}
        try { if (found.length > 10 && !found[10].isBlank()) durationMinutes = Integer.parseInt(found[10]); } catch(Exception ignored) {}

                StringBuilder html = new StringBuilder();
                html.append("<!doctype html><html><head>");
            if (!"Работает".equalsIgnoreCase(status)) { send(e, 403, "QR-код не активен. Доступ отключён.", "text/plain"); return; }
            int opens = OPEN_COUNTS.getOrDefault(id, 0);
            long now = System.currentTimeMillis();
            long firstOpen = FIRST_OPEN.computeIfAbsent(id, k -> now);
            if (durationMinutes > 0 && now - firstOpen >= durationMinutes * 60000L) { send(e, 403, "Время доступа к QR-коду истекло.", "text/plain"); return; }
            if (limit > 0 && opens >= limit) { send(e, 403, "Лимит открытий QR-кода исчерпан.", "text/plain"); return; }
            OPEN_COUNTS.put(id, opens + 1);
                html.append("<meta charset='utf-8'>");
                html.append("<meta name='viewport' content='width=device-width,initial-scale=1'>");
                html.append("<title>SD-QR</title>");
                html.append("<style>");
        html.append("body{font-family:Arial,sans-serif;max-width:760px;margin:0 auto;padding:24px;background:").append(esc(pageBg)).append(";color:#f5f7fa}");
        html.append(".card{color:").append(esc(textColor)).append(";background:").append(esc(cardBg)).append(";padding:22px;border-radius:16px;box-shadow:0 2px 12px #0002}");
                html.append(".status{font-weight:bold}");
                html.append("a{word-break:break-all}");
                html.append("</style></head><body><div class='card'>");

                html.append("<h1>SD-QR</h1>");
                html.append("<p><b>ID:</b> ").append(esc(id)).append("</p>");
                html.append("<p><b>Статус:</b> <span class='status'>").append(esc(status)).append("</span></p>");

                if (!contentType.isBlank())
                    html.append("<p><b>Тип:</b> ").append(esc(contentType)).append("</p>");

                if (!title.isBlank())
                    html.append("<h3>").append(esc(title)).append("</h3>");

                if (!note.isBlank())
                    html.append("<p>").append(esc(note).replace("\n", "<br>")).append("</p>");

                if (!resource.isBlank()) {
                    String safeResource = esc(resource);
                    if (resource.startsWith("http://") || resource.startsWith("https://")) {
                        html.append("<p><b>Ссылка:</b> <a href='")
                            .append(safeResource)
                            .append("'>")
                            .append(safeResource)
                            .append("</a></p>");
                    } else {
                        html.append("<p><b>Ресурс:</b> ").append(safeResource).append("</p>");
                    }
                }

                if (!extra.isBlank())
                    html.append("<p><b>Дополнительно:</b> ")
                        .append(esc(extra).replace("\n", "<br>"))
                        .append("</p>");

                html.append("</div></body></html>");

                send(e, 200, html.toString(), "text/html");

            } catch (Exception ex) {
                ex.printStackTrace();
                try {
                    send(e, 500, "Ошибка сервера", "text/plain");
                } catch (Exception ignored) {}
            }
        });

        server.start();
        System.out.println("SD-QR Java Server");
        System.out.println("Listening on port " + port);
    }
}
