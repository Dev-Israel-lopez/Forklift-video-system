package qnec.dev.printersystem.Models;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class EventRow {
    private final StringProperty hora       = new SimpleStringProperty();
    private final StringProperty evento     = new SimpleStringProperty();
    private final StringProperty montacargas= new SimpleStringProperty();
    private final StringProperty nivel      = new SimpleStringProperty();
    private final StringProperty confianza  = new SimpleStringProperty();

    public EventRow(String hora, String evento, String montacargas, String nivel, String confianza) {
        setHora(hora);
        setEvento(evento);
        setMontacargas(montacargas);
        setNivel(nivel);
        setConfianza(confianza);
    }

    // Getters/Setters/Properties
    public String getHora() { return hora.get(); }
    public void setHora(String v) { hora.set(v); }
    public StringProperty horaProperty() { return hora; }

    public String getEvento() { return evento.get(); }
    public void setEvento(String v) { evento.set(v); }
    public StringProperty eventoProperty() { return evento; }

    public String getMontacargas() { return montacargas.get(); }
    public void setMontacargas(String v) { montacargas.set(v); }
    public StringProperty montacargasProperty() { return montacargas; }

    public String getNivel() { return nivel.get(); }
    public void setNivel(String v) { nivel.set(v); }
    public StringProperty nivelProperty() { return nivel; }

    public String getConfianza() { return confianza.get(); }
    public void setConfianza(String v) { confianza.set(v); }
    public StringProperty confianzaProperty() { return confianza; }
}
