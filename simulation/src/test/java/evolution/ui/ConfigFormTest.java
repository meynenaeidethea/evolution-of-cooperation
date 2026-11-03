package evolution.ui;

import evolution.simulation.*;
import org.junit.jupiter.api.Test;
import java.io.StringReader;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;

class ConfigFormTest {
    @Test void unitsAndPercentagesRoundTripIncludingSavedProperties() throws Exception {
        var c=SimulationConfig.baseline();var values=ConfigForm.values(c);
        assertEquals("100",values.get("initial"));assertEquals("0.2",values.get("weight"));
        assertEquals("1",values.get("mutation"));assertEquals(c,ConfigForm.parse(values));
        assertEquals(c,ConfigProfiles.read(new StringReader(ConfigProfiles.format(c))));
        var changed=new HashMap<>(values);changed.put("initial","12,34");changed.put("mutation","2,5");
        assertEquals(1234,ConfigForm.parse(changed).initialEnergyHundredths());
        assertEquals(.025,ConfigForm.parse(changed).mutationProbability());
    }
    @Test void invalidPrecisionProbabilitiesIntegersAndPopulationAreRejected() {
        var source=ConfigForm.values(SimulationConfig.baseline());
        for(var entry:java.util.Map.of("initial","0.001","spawn","101","mutation","NaN","side","3.2","population","10001").entrySet()) {
            var changed=new HashMap<>(source);changed.put(entry.getKey(),entry.getValue());
            assertThrows(IllegalArgumentException.class,()->ConfigForm.parse(changed),entry.toString());
        }
    }
}
