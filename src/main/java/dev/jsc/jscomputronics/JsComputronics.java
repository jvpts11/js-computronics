package dev.jsc.jscomputronics;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Main mod entry point for J's Computronics.
 */
@Mod(JsComputronics.MODID)
public class JsComputronics {

    public static final String MODID = "jsc";

    public static final Logger LOGGER = LogUtils.getLogger();

    public JsComputronics(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("J's Computronics {} loaded.", modContainer.getModInfo().getVersion());
    }
}