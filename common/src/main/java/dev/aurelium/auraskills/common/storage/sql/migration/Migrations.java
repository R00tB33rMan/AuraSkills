package dev.aurelium.auraskills.common.storage.sql.migration;

import java.util.Locale;

public enum Migrations {

    V1__MODIFIERS_TABLE,
    V2__LAST_UPDATED_COL;

    // Excluding .sql
    private final String fileName;

    Migrations() {
        this.fileName = this.name().toLowerCase(Locale.ROOT);
    }

    public String getFileName() {
        return fileName;
    }

}
