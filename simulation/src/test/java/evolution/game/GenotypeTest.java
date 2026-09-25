package evolution.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GenotypeTest {
    @Test
    void selectsGeneForEachPreviousRoundOutcome() {
        Genotype genotype = new Genotype(
                Action.C,
                Action.C,
                Action.D,
                Action.D,
                Action.C
        );

        assertEquals(Action.C, genotype.firstMove());
        assertEquals(Action.C, genotype.actionAfter(Action.C, Action.C));
        assertEquals(Action.D, genotype.actionAfter(Action.C, Action.D));
        assertEquals(Action.D, genotype.actionAfter(Action.D, Action.C));
        assertEquals(Action.C, genotype.actionAfter(Action.D, Action.D));
    }

    @Test
    void rejectMissingGene(){
        assertThrows(
                IllegalArgumentException.class,
                () -> new Genotype(null, Action.C, Action.C, Action.C, Action.C)
        );
    }

}
