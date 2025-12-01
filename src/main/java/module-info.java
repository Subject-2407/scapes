module com.scapes {
    requires javafx.controls;
    requires javafx.fxml;
    requires transitive javafx.graphics;

    requires java.net.http;
    requires com.google.gson;
    requires com.sun.jna;
    requires com.sun.jna.platform;
    requires org.slf4j;
    requires ch.qos.logback.classic;
    requires java.desktop;

    opens com.scapes to javafx.fxml, com.google.gson;
    opens com.scapes.model to com.google.gson;
    opens com.scapes.controller to javafx.fxml;
    
    exports com.scapes;
}
