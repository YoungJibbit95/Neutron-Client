package me.youngjibbit.value.impl;

import me.youngjibbit.module.Module;
import me.youngjibbit.value.Value;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A disclosure value owned by a UI, never registered or serialized as a gameplay setting. */
public final class SubcategoryValue extends Value<Boolean> {
    private final Module owner;
    private final String description;
    private final List<Value<?>> children;

    public SubcategoryValue(String name, String description, Module owner, List<Value<?>> children) {
        super(name, null, false);
        this.owner = owner;
        this.description = description;
        this.children = Collections.unmodifiableList(new ArrayList<>(children));
    }
    public Module owner() { return owner; }
    public String description() { return description; }
    public List<Value<?>> children() { return children; }
    public int visibleCount() {
        int count = 0;
        for (Value<?> child : children) if (child.isVisible()) count++;
        return count;
    }
    @Override public boolean isVisible() { return visibleCount() > 0; }
}
