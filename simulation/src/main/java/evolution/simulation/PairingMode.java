package evolution.simulation;

/** Experimental choice of game partners; movement and resources remain spatial in both modes. */
public enum PairingMode {
    LOCAL, GLOBAL;

    public String csvValue() { return name().toLowerCase(java.util.Locale.ROOT); }
}
