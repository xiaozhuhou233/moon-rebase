package dev.pier.lua;

import org.luaj.vm2.LuaValue;

/**
 * One setting owned by a script module.
 *
 * Values are stored as Java objects so they can be written to a plain
 * properties file without pulling in another serialization dependency.
 */
public final class LuaSetting {

    public enum Type {
        BOOLEAN,
        INTEGER,
        FLOAT,
        COLOR,
        TEXT
    }

    private final String id;
    private final String name;
    private final Type type;
    private Object value;

    public LuaSetting(String id, String name, Type type, Object value) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.value = coerce(type, value);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }

    public void setFromLua(LuaValue value) {
        switch (type) {
            case BOOLEAN:
                this.value = value.toboolean();
                break;
            case INTEGER:
                this.value = value.toint();
                break;
            case FLOAT:
                this.value = value.tofloat();
                break;
            case COLOR:
                this.value = colorBits(value.todouble());
                break;
            case TEXT:
                this.value = value.tojstring();
                break;
        }
    }

    public LuaValue toLua() {
        switch (type) {
            case BOOLEAN:
                return LuaValue.valueOf((Boolean) value);
            case INTEGER:
                return LuaValue.valueOf((Integer) value);
            case FLOAT:
                return LuaValue.valueOf((Float) value);
            case COLOR:
                return LuaValue.valueOf((Integer) value);
            case TEXT:
                return LuaValue.valueOf((String) value);
            default:
                return LuaValue.NIL;
        }
    }

    public String toConfigString() {
        switch (type) {
            case COLOR:
                return Long.toString(((long) (Integer) value) & 0xFFFFFFFFL);
            default:
                return String.valueOf(value);
        }
    }

    public void setFromConfigString(String raw) {
        if (raw == null) {
            return;
        }
        try {
            switch (type) {
                case BOOLEAN:
                    value = Boolean.parseBoolean(raw);
                    break;
                case INTEGER:
                    value = Integer.parseInt(raw);
                    break;
                case FLOAT:
                    value = Float.parseFloat(raw);
                    break;
                case COLOR:
                    value = (int) Long.parseLong(raw);
                    break;
                case TEXT:
                    value = raw;
                    break;
            }
        } catch (NumberFormatException ignored) {
            // Keep the script-provided default when the config is corrupt.
        }
    }

    private static Object coerce(Type type, Object value) {
        switch (type) {
            case BOOLEAN:
                return value instanceof Boolean ? value : Boolean.parseBoolean(String.valueOf(value));
            case INTEGER:
                return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value));
            case FLOAT:
                return value instanceof Number ? ((Number) value).floatValue() : Float.parseFloat(String.valueOf(value));
            case COLOR:
                return value instanceof Number ? ((Number) value).intValue() : (int) Long.parseLong(String.valueOf(value));
            case TEXT:
                return String.valueOf(value);
            default:
                return value;
        }
    }

    private static int colorBits(double raw) {
        // Lua numbers are doubles. 0xFFFFFFFF and 0xAARRGGBB literals are
        // larger than Integer.MAX_VALUE, so never use LuaValue.toint() here:
        // LuaJ saturates that conversion and would turn white into 0x7FFFFFFF.
        return (int) ((long) raw);
    }
}
