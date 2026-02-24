package com.textfield.bettertextfield.mixin;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@IFMLLoadingPlugin.Name("EarlyMixin")
@IFMLLoadingPlugin.MCVersion("1.12.2")
public class EarlyMixin implements IFMLLoadingPlugin, IEarlyMixinLoader {
    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.bettertextfield.early.json");
    }

    @Nullable
    @Override
    public String[] getASMTransformerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getModContainerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Nullable
    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
