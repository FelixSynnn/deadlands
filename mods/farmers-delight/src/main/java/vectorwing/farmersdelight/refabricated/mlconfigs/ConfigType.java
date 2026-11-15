package vectorwing.farmersdelight.refabricated.mlconfigs;

public enum ConfigType {
    COMMON,
    COMMON_SYNCED,
    CLIENT;

    public boolean isSynced() {
        return this == COMMON_SYNCED;
    }

    public String getDefaultName() {
        return this == CLIENT ? "client" : "common";
    }
}
