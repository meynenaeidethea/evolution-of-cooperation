package evolution.simulation;

/** Experimental choice of game partners; movement and resources remain spatial in both modes. */
public enum PairingMode {
    LOCAL, GLOBAL, GLOBAL_MATCHED;

    public String csvValue() { return name().toLowerCase(java.util.Locale.ROOT); }
}
