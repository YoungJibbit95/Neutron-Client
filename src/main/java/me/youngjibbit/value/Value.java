package me.youngjibbit.value;

import lombok.Getter;
import lombok.Setter;
import me.youngjibbit.module.Module;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Getter
@Setter
public class Value<T> {
    private final String id;
    private final String name;
    private final Module parent;
    private final T defaultValue;
    private T value;
    private Predicate<Module> visibility = module -> true;
    private Consumer<T> onChange;

    public Value(String name, Module parent, T defaultValue) {
        this.name = name;
        this.parent = parent;
        this.id = normalizeId(name);
        this.defaultValue = defaultValue;
        this.value = defaultValue;

        if (this.parent != null) {
            this.parent.registerValue(this);
        }
    }

    private String normalizeId(String rawName) {
        return rawName
                .toLowerCase(Locale.ROOT)
                .replace(" ", "_")
                .replace("-", "_");
    }

    public boolean isVisible() {
        return visibility == null || visibility.test(parent);
    }

    public void reset() {
        setValue(defaultValue);
    }

    public void setValue(T value) {
        T previous = this.value;
        this.value = value;

        if (!Objects.equals(previous, value)) {
            notifyChanged();
        }
    }

    protected void notifyChanged() {
        if (parent != null) parent.valueChanged(this);
        else fireChangeCallback();
    }

    public void fireChangeCallback() {
        if (onChange != null) onChange.accept(getValue());
    }

    public void setOnChange(Consumer<T> onChange) {
        this.onChange = onChange;
    }

    public void setVisibility(Predicate<Module> visibility) {
        this.visibility = visibility;
    }
}
