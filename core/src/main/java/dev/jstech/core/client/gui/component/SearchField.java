/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.component;

import java.util.Locale;

/**
 * A text field that filters something as it is typed: its {@link #query()} is the live text, lower-cased
 * and trimmed, whether or not the keyboard is still in it.
 */
public final class SearchField extends TextField {

    public SearchField(final int maxLength) {
        super(maxLength);
        setPlaceholder("Search");
    }

    /** The text to filter by right now. */
    public String query() {
        return edit().trim().toLowerCase(Locale.ROOT);
    }

    /** Empties the field. */
    public SearchField reset() {
        sync("");
        return this;
    }
}
