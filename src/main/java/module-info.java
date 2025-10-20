module qnec.dev.printersystem {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.net.http;
    requires java.desktop;
    requires org.apache.logging.log4j;

    requires org.yaml.snakeyaml;
    requires com.google.gson;
        
    opens qnec.dev.printersystem to javafx.fxml;
    opens qnec.dev.printersystem.Controllers to javafx.fxml;
    opens qnec.dev.printersystem.Models to javafx.base;
    opens qnec.dev.printersystem.dto to com.google.gson;
  
    exports qnec.dev.printersystem;
    exports qnec.dev.printersystem.Controllers;
}