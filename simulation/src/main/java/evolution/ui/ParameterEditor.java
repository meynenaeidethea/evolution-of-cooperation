package evolution.ui;

import evolution.simulation.SimulationConfig;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import java.util.*;

final class ParameterEditor extends Dialog<SimulationConfig> {
    ParameterEditor(Window owner,SimulationConfig config) {
        initOwner(owner);setTitle("Параметры нового запуска");setResizable(true);
        var apply=new ButtonType("Применить и начать заново",ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(apply,new ButtonType("Отмена",ButtonBar.ButtonData.CANCEL_CLOSE));
        var form=new GridPane();form.setHgap(16);form.setVgap(8);form.setPadding(new Insets(12));
        var error=new Label();error.setStyle("-fx-text-fill: #b42318;");error.setWrapText(true);error.setMaxWidth(520);
        var fields=new LinkedHashMap<String,TextField>();var values=ConfigForm.values(config);int row=0;
        for(var f:ConfigForm.FIELDS) {
            var input=new TextField(values.get(f.key()));input.setId(f.key());input.setPrefColumnCount(10);input.setTooltip(new Tooltip(f.hint()));
            input.textProperty().addListener((o,a,b)->error.setText(""));
            fields.put(f.key(),input);var label=new Label(f.label());label.setTooltip(new Tooltip(f.hint()));
            form.addRow(row++,label,input);
        }
        var explanation=new Label("Энергия указана в обычных единицах, вероятности — в процентах.\nПрименение создаёт новый запуск; текущая запись остаётся на диске.");
        explanation.setWrapText(true);
        var content=new VBox(10,explanation,form,error);content.setPadding(new Insets(12));
        var scroll=new ScrollPane(content);scroll.setFitToWidth(true);scroll.setPrefViewportHeight(590);
        getDialogPane().setContent(scroll);
        SimulationConfig[] result={null};
        getDialogPane().lookupButton(apply).addEventFilter(ActionEvent.ACTION,event->{
            try {var inputs=new LinkedHashMap<String,String>();fields.forEach((key,value)->inputs.put(key,value.getText()));result[0]=ConfigForm.parse(inputs);}
            catch(IllegalArgumentException e) {error.setText("Проверьте параметры: "+e.getMessage());event.consume();}
        });
        setResultConverter(button->button==apply?result[0]:null);
    }
}
