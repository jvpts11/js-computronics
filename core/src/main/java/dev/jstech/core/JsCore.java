/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core;

import com.mojang.logging.LogUtils;
import dev.jstech.core.config.CoreConfigBridge;
import dev.jstech.core.event.CoreEventDispatcher;
import dev.jstech.core.operation.OperationTypeRegistry;
import dev.jstech.core.registry.CoreItems;
import dev.jstech.core.registry.CoreAttachments;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * The shared library of the J's Tech Series. It has no gameplay of its own: it carries what every mod of
 * the series is built on, and every one of them requires it. The one thing it adds to the game is the
 * material catalogue, the items several mods process and trade.
 */
@Mod(JsCore.MODID)
public final class JsCore {

    public static final String MODID = "jscore";

    public static final Logger LOGGER = LogUtils.getLogger();

    private static final CoreEventDispatcher EVENTS = new CoreEventDispatcher();
    private static final OperationTypeRegistry OPERATIONS = new OperationTypeRegistry();
    private static final dev.jstech.core.language.LanguageRegistry LANGUAGES =
            new dev.jstech.core.language.LanguageRegistry();

    /**
     * The series' internal event bus: an orchestrator posts the lifecycle of its Operations here and any mod
     * subscribes, without a class dependency between them. Posted on the server thread.
     */
    public static CoreEventDispatcher events() {
        return EVENTS;
    }

    /** The Operation types the mods of the series (and addons) declare, by id such as {@code jsc:select}. */
    public static OperationTypeRegistry operations() {
        return OPERATIONS;
    }

    /**
     * The languages the computers of this world can be programmed in.
     *
     * <p>An addon may add one and may take one away, this series' own included: everything that deals in
     * programs resolves through here by extension rather than by naming a language.
     */
    public static dev.jstech.core.language.LanguageRegistry languages() {
        return LANGUAGES;
    }

    public JsCore(final IEventBus modEventBus, final ModContainer modContainer) {
        LOGGER.info("J's Core {} loaded.", modContainer.getModInfo().getVersion());
        // The data network lives on the level and the chunks as attachments the core owns.
        CoreAttachments.register(modEventBus);
        CoreItems.register(modEventBus);
        // The balance of the Operations engine is series-wide: the core owns the server config file.
        CoreConfigBridge.register(modEventBus, modContainer);
    }
}
