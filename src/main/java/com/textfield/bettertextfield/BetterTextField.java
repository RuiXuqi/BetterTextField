package com.textfield.bettertextfield;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = Tags.MOD_ID,
        name = Tags.MOD_NAME,
        version = Tags.VERSION,
        clientSideOnly = true,
        acceptableRemoteVersions = "*",
        dependencies = "required-after:mixinbooter@[8.0,)",
        customProperties = {
                @Mod.CustomProperty(k = "license", v = "MIT"),
                @Mod.CustomProperty(k = "issueTrackerUrl", v = "https://github.com/RuiXuqi/BetterTextField/issues")
        }
)
public class BetterTextField {
    public static final Logger LOGGER = LogManager.getLogger(Tags.MOD_NAME);
}
