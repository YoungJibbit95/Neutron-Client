package me.youngjibbit.module;

import lombok.Getter;
import me.youngjibbit.Pulsar;
import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ColorValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.listener.Subscriber;
import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.Validate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

@Getter
public abstract class Module implements Subscriber {

    private final String id;
    private final String name;
    private final String description;
    private final Category category;
    private final ArrayPriority priority;
    private final List<Value<?>> valueList = new ArrayList<>();
    private boolean toggled;

    private int key;
    private Consumer<Boolean> persistenceListener;
    private boolean configUpdate;
    private final Set<Value<?>> configChanges = new LinkedHashSet<>();

    public void setPersistenceListener(Consumer<Boolean> listener) {
        persistenceListener = listener;
    }

    private void persistentChange(boolean toggleChanged) {
        if (persistenceListener != null) persistenceListener.accept(toggleChanged);
    }

    public void valueChanged(Value<?> value) {
        if (configUpdate) {
            configChanges.add(value);
            return;
        }
        onValueChange();
        value.fireChangeCallback();
        persistentChange(false);
    }

    public void beginConfigUpdate() {
        if (configUpdate) throw new IllegalStateException("Nested module config update.");
        configUpdate = true;
    }

    public void endConfigUpdate() {
        Set<Value<?>> changes = new LinkedHashSet<>(configChanges);
        cancelConfigUpdate();
        if (!changes.isEmpty()) {
            onValueChange();
            for (Value<?> value : changes) value.fireChangeCallback();
            persistentChange(false);
        }
    }

    public void cancelConfigUpdate() {
        configUpdate = false;
        configChanges.clear();
    }

    protected static final Minecraft mc = Minecraft.getMinecraft();
    public static float allowedClickGuiHeight = 300;
    public String info = "";

    protected Module() {
        ModuleInfo info = this.getClass().getAnnotation(ModuleInfo.class);
        Validate.notNull(info, "ModuleInfo annotation is required");

        this.name = info.name();
        String declaredId = info.id();
        this.id = declaredId != null && !declaredId.trim().isEmpty()
                ? normalizeId(declaredId)
                : normalizeId(this.name);
        this.description = info.description();
        this.category = info.category();
        this.priority = info.priority();
    }

    private static String normalizeId(String input) {
        return input
                .toLowerCase(Locale.ROOT)
                .replace(" ", "_")
                .replace("-", "_");
    }

    public String getName(boolean split) {
        return split ? name : name.replace(" ", "");
    }

    public void registerValue(Value<?> value) {
        if (value != null && value.getParent() != this) {
            throw new IllegalArgumentException("A setting must belong to its module.");
        }
        if (value != null && !valueList.contains(value)) {
            valueList.add(value);
        }
    }

    public List<Value<?>> getValues() {
        return Collections.unmodifiableList(valueList);
    }

    /** Retains the original getter without exposing the mutable owning list. */
    public List<Value<?>> getValueList() {
        return getValues();
    }

    @SuppressWarnings("unchecked")
    public <T> Value<T> getValueByName(String nameOrId) {
        if (nameOrId == null) {
            return null;
        }

        for (Value<?> value : valueList) {
            if (value.getName().equalsIgnoreCase(nameOrId) || value.getId().equalsIgnoreCase(nameOrId)) {
                return (Value<T>) value;
            }
        }

        return null;
    }

    public void onEnable() {
        Pulsar.BUS.subscribe(this);
    }

    public void onDisable() {
        Pulsar.BUS.unsubscribe(this);
    }

    public void onToggle() {
    }

    public void onValueChange() {
    }

    protected void onUpdate() {
    }

    protected void onRender() {
    }

    public void toggle() {
        this.setToggled(!this.toggled);
    }

    public void setToggled(boolean toggled) {
        if (this.toggled == toggled) {
            return;
        }

        this.onToggle();
        this.toggled = toggled;

        if (toggled) {
            this.onEnable();
        } else {
            this.onDisable();
        }
        persistentChange(true);
    }

    public void setKey(int key) {
        if (this.key == key) {
            return;
        }

        this.key = key;
        persistentChange(false);
    }

    public static void loadConfig(String configName) {
        if (Pulsar.INSTANCE.getConfigManager() != null) {
            Pulsar.INSTANCE.getConfigManager().loadConfig(configName);
        }
    }

    public static void saveConfig(String configName) {
        if (Pulsar.INSTANCE.getConfigManager() != null) {
            Pulsar.INSTANCE.getConfigManager().saveConfig(configName);
        }
    }

    public SliderValue getNumberSetting(String nameOrId) {
        Value<?> value = getValueByName(nameOrId);
        return value instanceof SliderValue ? (SliderValue) value : null;
    }

    public CheckBoxValue getBooleanSetting(String nameOrId) {
        Value<?> value = getValueByName(nameOrId);
        return value instanceof CheckBoxValue ? (CheckBoxValue) value : null;
    }

    public ComboValue getModeSetting(String nameOrId) {
        Value<?> value = getValueByName(nameOrId);
        return value instanceof ComboValue ? (ComboValue) value : null;
    }

    public ColorValue getColorSetting(String nameOrId) {
        Value<?> value = getValueByName(nameOrId);
        return value instanceof ColorValue ? (ColorValue) value : null;
    }
}
