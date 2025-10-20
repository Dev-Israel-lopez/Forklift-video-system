package qnec.dev.printersystem.Controllers;

import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.util.Callback;
import javafx.util.Duration;
import qnec.dev.printersystem.Models.EventRow;
import qnec.dev.printersystem.Models.TimeState;
import qnec.dev.printersystem.Services.EventsApiService;
import qnec.dev.printersystem.Services.ClockService;
import qnec.dev.printersystem.dto.EventsApiResponse;

public class DetectionSystemController {

    private static final Logger log = LogManager.getLogger(DetectionSystemController.class);

    // Header / KPIs
    @FXML private Label critCountLbl, warnCountLbl, normalCountLbl, activeForkliftsLbl;
    @FXML private ImageView logoImage;

    // Pie chart de niveles
    @FXML private PieChart chrtPieNivel;

    // Root
    @FXML private AnchorPane root;

    // Tabla
    @FXML private TableView<EventRow> eventsTable;
    @FXML private TableColumn<EventRow, String> colHora, colEvento, colMontacargas, colNivel, colConfianza;

    // ⏱️ Reloj (si existen en tu FXML)
    @FXML private Label lblTime; // fx:id="lblTime"
    @FXML private Label lblDate; // fx:id="lblDate"
    @FXML private Label lblZone; // fx:id="lblZone"

    private final ObservableList<EventRow> data = FXCollections.observableArrayList();

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final ZoneId ZONE = ZoneId.systemDefault();

    // === API ===
    private final String baseUrl = "http://forklift.test"; // 🔧 cambia a tu host
    private final EventsApiService apiService = new EventsApiService(baseUrl);

    private Timeline poller;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "events-polling-io");
        t.setDaemon(true);
        return t;
    });

    // Servicio de reloj (bajo consumo)
    private final ClockService clockService = new ClockService();
    private final Locale esMX = new Locale("es", "MX");
    private final DateTimeFormatter uiTimeFmt = DateTimeFormatter.ofPattern("HH:mm:ss", esMX);
    private final DateTimeFormatter uiDateFmt = DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy", esMX);

    @FXML
    public void initialize() {
        // KPIs en cero
        if (critCountLbl != null) critCountLbl.setText("0");
        if (warnCountLbl != null) warnCountLbl.setText("0");
        if (normalCountLbl != null) normalCountLbl.setText("0");
        if (activeForkliftsLbl != null) activeForkliftsLbl.setText("0");
        
        // Logo tolerante
        try {
            URL url = getClass().getResource("/images/qnec_logo.png");
            if (url != null && logoImage != null) {
                logoImage.setImage(new Image(url.toExternalForm(), true));
            }
        } catch (Exception ignore) {}

        // Setup tabla
        setup();

        // Setup pie chart base (opcional: título y leyenda)
        if (chrtPieNivel != null) {
            chrtPieNivel.setTitle("Eventos por Nivel");
            chrtPieNivel.setLegendVisible(true);
            chrtPieNivel.setAnimated(false);
            // inicial vacío
            updatePieChart(0, 0, 0);
        }

        // Primera carga
        loadFromApiOnce();

        // Polling periódico
        startPolling(Duration.seconds(3));

        // ⏱️ Iniciar reloj (solo si existen los labels)
        startClockIfPresent();
    }

    /** Configura columnas, celdas y binding de datos. */
    public void setup() {
        eventsTable.setItems(data);

        colHora.setCellValueFactory(c -> c.getValue().horaProperty());
        colEvento.setCellValueFactory(c -> c.getValue().eventoProperty());
        colMontacargas.setCellValueFactory(c -> c.getValue().montacargasProperty());
        colNivel.setCellValueFactory(c -> c.getValue().nivelProperty());
        colConfianza.setCellValueFactory(c -> c.getValue().confianzaProperty());

        colNivel.setCellFactory(levelBadgeCellFactory());

        eventsTable.getSortOrder().setAll(Collections.singletonList(colHora));
        colHora.setSortType(TableColumn.SortType.DESCENDING);
    }

    // ---------- CLOCK ----------

    private void startClockIfPresent() {
        // Si la vista no tiene los labels, no hacemos nada.
        if (lblTime == null || lblDate == null || lblZone == null) {
            log.info("Reloj no inicializado: labels no presentes en el FXML.");
            return;
        }

        clockService.setZone(ZONE);
        clockService.start(this::onClockTick);

        // Pintado inicial
        onClockTick(new TimeState(java.time.ZonedDateTime.now(ZONE)));
        log.info("ClockService iniciado a 1 Hz, zona={}", ZONE);
    }

    private void onClockTick(TimeState ts) {
        if (lblTime == null || lblDate == null || lblZone == null) return;
        Platform.runLater(() -> {
            var zdt = ts.now();
            lblTime.setText(zdt.format(uiTimeFmt));
            lblDate.setText(capitalize(zdt.format(uiDateFmt)));
            lblZone.setText(zdt.getZone().toString());
        });
    }

    private static String capitalize(String s) {
        return (s == null || s.isEmpty()) ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Llamar esto cuando cierres la escena o cambies de vista. */
    public void dispose() {
        try { clockService.close(); } catch (Exception ignored) {}
        if (poller != null) poller.stop();
        ioExecutor.shutdownNow();
    }

    // ---------- POLLING ----------

    private void startPolling(Duration every) {
        if (poller != null) poller.stop();
        poller = new Timeline(new KeyFrame(every, e -> loadFromApiOnce()));
        poller.setCycleCount(Timeline.INDEFINITE);
        poller.play();
        log.info("Iniciado polling cada {} ms a {}/api/events", (long) every.toMillis(), baseUrl);
    }

    private void loadFromApiOnce() {
        final long start = System.currentTimeMillis();
        log.info("Solicitando información al servicio REST: {}/api/events", baseUrl);
        System.out.println("[INFO] solicitando " + baseUrl + "/api/events");

        CompletableFuture
            .supplyAsync(() -> {
                try {
                    return apiService.fetchEvents();
                } catch (Exception ex) {
                    throw new CompletionException(ex);
                }
            }, ioExecutor)
            .whenComplete((resp, err) -> {
                final long took = System.currentTimeMillis() - start;

                if (err != null) {
                    Throwable cause = (err instanceof CompletionException && err.getCause()!=null) ? err.getCause() : err;
                    log.error("Fallo al consultar /api/events ({} ms): {}", took, cause.toString());
                    System.err.println("[ERROR] fallo consultando /api/events en " + took + " ms: " + cause);
                    return;
                }

                if (resp == null) {
                    log.warn("Respuesta nula ({} ms).", took);
                    System.out.println("[WARN] respuesta nula en " + took + " ms");
                    return;
                }

                log.info("Conectado OK. data.size={} ({} ms).",
                        resp.data != null ? resp.data.size() : 0, took);
                System.out.println("[OK] conectó. registros=" +
                        (resp.data != null ? resp.data.size() : 0) + " (" + took + " ms)");

                Platform.runLater(() -> applyApiData(resp));
            });
    }

    // ---------- APLICAR DATA A LA UI ----------

    private void applyApiData(EventsApiResponse resp) {
        // 1) Tabla
        data.clear();
        if (resp.data != null) {
            for (var ev : resp.data) {
                String hora = toLocalTime(ev);
                String evento = ev.event != null ? ev.event : "";
                String montacargas = ev.forklift_name != null ? ev.forklift_name : "";
                String nivelUi = mapLevel(ev.level);
                String conf = toPercent(ev.confidence);

                data.add(new EventRow(hora, evento, montacargas, nivelUi, conf));
            }
        }

        // 2) KPIs desde meta.stats (o fallback)
        int sCrit  = -1, sWarn = -1, sNorm = -1;
        if (resp.meta != null && resp.meta.stats != null) {
            if (resp.meta.stats.critical != null) sCrit = resp.meta.stats.critical;
            if (resp.meta.stats.warning  != null) sWarn = resp.meta.stats.warning;
            if (resp.meta.stats.normal   != null) sNorm = resp.meta.stats.normal;
        }

        if (sCrit >= 0 && sWarn >= 0 && sNorm >= 0) {
            critCountLbl.setText(Integer.toString(sCrit));
            warnCountLbl.setText(Integer.toString(sWarn));
            normalCountLbl.setText(Integer.toString(sNorm));
            updatePieChart(sCrit, sWarn, sNorm);
        } else {
            long crit = data.stream().filter(r -> "CRÍTICO".equalsIgnoreCase(r.getNivel())).count();
            long warn = data.stream().filter(r -> "ADVERTENCIA".equalsIgnoreCase(r.getNivel())).count();
            long norm = data.stream().filter(r -> "NORMAL".equalsIgnoreCase(r.getNivel())).count();
            critCountLbl.setText(Long.toString(crit));
            warnCountLbl.setText(Long.toString(warn));
            normalCountLbl.setText(Long.toString(norm));
            updatePieChart((int)crit, (int)warn, (int)norm);
        }

        // 3) Montacargas activos (distintos en la lista actual)
        long activos = data.stream()
                .map(EventRow::getMontacargas)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        activeForkliftsLbl.setText(Long.toString(activos));

        eventsTable.sort();
    }

    // ---------- PieChart helpers ----------

    /** Refresca el pie chart con los valores dados. */
    private void updatePieChart(int critical, int warning, int normal) {
        if (chrtPieNivel == null) return;

        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList(
            new PieChart.Data("Crítico",     Math.max(0, critical)),
            new PieChart.Data("Advertencia", Math.max(0, warning)),
            new PieChart.Data("Normal",      Math.max(0, normal))
        );

        chrtPieNivel.setData(pieData);

        // Estilos por color + tooltips (al crear los nodos)
        for (PieChart.Data d : pieData) {
            d.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) {
                    applySliceStyle(d, newNode);
                    installSliceTooltip(d, newNode);
                }
            });
        }

        // Si ya están creados (primer refresh tras layout)
        Platform.runLater(() -> {
            for (PieChart.Data d : chrtPieNivel.getData()) {
                Node n = d.getNode();
                if (n != null) {
                    applySliceStyle(d, n);
                    installSliceTooltip(d, n);
                }
            }
        });
    }

    private void applySliceStyle(PieChart.Data d, Node node) {
        String name = d.getName().toLowerCase(Locale.ROOT);
        String color;
        switch (name) {
            case "crítico":
            case "critico":
                color = "#e74c3c"; // rojo
                break;
            case "advertencia":
                color = "#f1c40f"; // amarillo
                break;
            default:
                color = "#2ecc71"; // verde
                break;
        }
        node.setStyle("-fx-pie-color: " + color + ";");
    }

    private void installSliceTooltip(PieChart.Data d, Node node) {
        Tooltip tp = new Tooltip(d.getName() + ": " + (int)d.getPieValue());
        // para que actualice el texto cuando cambie el valor
        d.pieValueProperty().addListener((o, ov, nv) -> tp.setText(d.getName() + ": " + nv.intValue()));
        Tooltip.install(node, tp);
    }

    // ---------- Helpers de mapeo ----------

    private static String toPercent(Double v) {
        if (v == null) return "";
        int pct = (int)Math.round(v * 100.0);
        return pct + "%";
    }

    private static String mapLevel(String lvl) {
        if (lvl == null) return "NORMAL";
        switch (lvl.toLowerCase(Locale.ROOT)) {
            case "red":    return "CRÍTICO";
            case "yellow": return "ADVERTENCIA";
            case "green":  return "NORMAL";
            default:       return lvl.toUpperCase(Locale.ROOT);
        }
    }

    private static String toLocalTime(qnec.dev.printersystem.dto.EventsApiResponse.EventData ev) {
        try {
            if (ev.created_at != null && !ev.created_at.isBlank()) {
                Instant utc = Instant.parse(ev.created_at.replace("000000Z", "Z"));
                return LocalDateTime.ofInstant(utc, ZONE).toLocalTime().format(TIME_FMT);
            }
        } catch (Exception ignore) {}
        try {
            if (ev.timestamp_str != null && ev.timestamp_str.length() >= 15) {
                String s = ev.timestamp_str.replace("_", "");
                int hh = Integer.parseInt(s.substring(8, 10));
                int mm = Integer.parseInt(s.substring(10, 12));
                int ss = Integer.parseInt(s.substring(12, 14));
                return LocalTime.of(hh, mm, ss).format(TIME_FMT);
            }
        } catch (Exception ignore) {}
        return LocalTime.now().format(TIME_FMT);
    }

    /** Badges por nivel con CSS. */
    private Callback<TableColumn<EventRow, String>, TableCell<EventRow, String>> levelBadgeCellFactory() {
        return col -> new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    getStyleClass().removeAll("badge-critical","badge-warning","badge-normal");
                    return;
                }
                setText(value);
                getStyleClass().removeAll("badge-critical","badge-warning","badge-normal");
                switch (value.toUpperCase(Locale.ROOT)) {
                    case "CRÍTICO":     getStyleClass().add("badge-critical"); break;
                    case "ADVERTENCIA": getStyleClass().add("badge-warning");  break;
                    default:            getStyleClass().add("badge-normal");   break;
                }
            }
        };
    }
}
