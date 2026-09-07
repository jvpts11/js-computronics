/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.save;

import dev.jstech.computronics.cannon.run.Snapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * A frozen process, written to a save and read back.
 *
 * <p>The freezing itself is done elsewhere and knows nothing about the game; this is only the writing
 * down, so the part worth testing on its own can be, and the part that has to know about tags stays
 * small enough to read in one sitting.
 *
 * <p>Every kind is written with its name beside it rather than guessed from the shape of what was
 * stored, because a whole number and a truth value look alike once written and a process that came
 * back with one in place of the other would be wrong in a way nothing would catch.
 */
public final class SnapshotTag {

    private static final String KIND = "kind";
    private static final String VALUE = "value";
    private static final String ID = "id";
    private static final String BYTES = "bytes";
    private static final String LINE = "line";
    private static final String FREED = "freed";
    private static final String TYPE = "type";
    private static final String NAME = "name";
    private static final String OWNER = "owner";
    private static final String PARAMETERS = "parameters";
    private static final String FIELDS = "fields";
    private static final String VALUES = "values";
    private static final String KEYS = "keys";
    private static final String ITEMS = "items";
    private static final String CHAIN = "chain";
    private static final String TARGET = "target";
    private static final String METHOD = "method";
    private static final String RETURNS = "returns";
    private static final String SLOTS = "slots";
    private static final String STACK = "stack";
    private static final String SELF = "self";
    private static final String AT = "at";
    private static final String DISCARD = "discard";
    private static final String HELD = "held";
    private static final String FRAMES = "frames";
    private static final String WAITING = "waiting";
    private static final String STATICS = "statics";
    private static final String SCRIPT = "script";
    private static final String CONSOLE = "console";
    private static final String WRITTEN = "written";
    private static final String WATCHES = "watches";
    private static final String THRESHOLD = "threshold";
    private static final String LAST = "last";
    private static final String ARMED = "armed";
    private static final String SEEN = "seen";
    private static final String STATE = "state";
    private static final String MESSAGE = "message";
    private static final String SPENT = "spent";
    private static final String BUDGET = "budget";

    private SnapshotTag() {
    }

    /** Writes a frozen process down. */
    public static CompoundTag write(final Snapshot shot) {
        final CompoundTag tag = new CompoundTag();
        tag.putLong(BUDGET, shot.heapBudget());
        final ListTag held = new ListTag();
        for (final Snapshot.IHeld one : shot.held()) {
            held.add(write(one));
        }
        tag.put(HELD, held);
        tag.put(FRAMES, frames(shot.frames()));
        tag.put(WAITING, frames(shot.waiting()));
        final ListTag statics = new ListTag();
        for (final Map.Entry<String, Map<String, Snapshot.IValue>> entry : shot.statics().entrySet()) {
            final CompoundTag owner = new CompoundTag();
            owner.putString(OWNER, entry.getKey());
            owner.put(FIELDS, fields(entry.getValue()));
            statics.add(owner);
        }
        tag.put(STATICS, statics);
        tag.put(SCRIPT, write(shot.script()));
        final ListTag watches = new ListTag();
        for (final Snapshot.WatchShot watch : shot.watches()) {
            final CompoundTag each = new CompoundTag();
            each.putInt(ID, watch.id());
            each.putString(NAME, watch.item());
            each.putString(KIND, watch.kind());
            each.putLong(THRESHOLD, watch.threshold());
            each.put(METHOD, write(watch.handler()));
            each.put(TARGET, write(watch.token()));
            each.putLong(LAST, watch.last());
            each.putBoolean(ARMED, watch.armed());
            each.putBoolean(SEEN, watch.seen());
            watches.add(each);
        }
        tag.put(WATCHES, watches);
        final ListTag console = new ListTag();
        for (final String line : shot.console()) {
            console.add(StringTag.valueOf(line));
        }
        tag.put(CONSOLE, console);
        tag.putInt(WRITTEN, shot.written());
        tag.putString(STATE, shot.state());
        tag.putString(MESSAGE, shot.message());
        tag.putInt(SPENT, shot.spent());
        return tag;
    }

    /** Reads one back. */
    public static Snapshot read(final CompoundTag tag) {
        final List<Snapshot.IHeld> held = new ArrayList<>();
        final ListTag written = tag.getList(HELD, Tag.TAG_COMPOUND);
        for (int i = 0; i < written.size(); i++) {
            held.add(readHeld(written.getCompound(i)));
        }
        final Map<String, Map<String, Snapshot.IValue>> statics = new LinkedHashMap<>();
        final ListTag owners = tag.getList(STATICS, Tag.TAG_COMPOUND);
        for (int i = 0; i < owners.size(); i++) {
            final CompoundTag owner = owners.getCompound(i);
            statics.put(owner.getString(OWNER), readFields(owner.getList(FIELDS, Tag.TAG_COMPOUND)));
        }
        final List<String> console = new ArrayList<>();
        final ListTag lines = tag.getList(CONSOLE, Tag.TAG_STRING);
        for (int i = 0; i < lines.size(); i++) {
            console.add(lines.getString(i));
        }
        final List<Snapshot.WatchShot> watches = new ArrayList<>();
        final ListTag watching = tag.getList(WATCHES, Tag.TAG_COMPOUND);
        for (int i = 0; i < watching.size(); i++) {
            final CompoundTag each = watching.getCompound(i);
            watches.add(new Snapshot.WatchShot(each.getInt(ID), each.getString(NAME),
                    each.getString(KIND), each.getLong(THRESHOLD), readValue(each.getCompound(METHOD)),
                    readValue(each.getCompound(TARGET)), each.getLong(LAST), each.getBoolean(ARMED),
                    each.getBoolean(SEEN)));
        }
        return new Snapshot(tag.getLong(BUDGET), held, readFrames(tag.getList(FRAMES, Tag.TAG_COMPOUND)),
                readFrames(tag.getList(WAITING, Tag.TAG_COMPOUND)), statics,
                readValue(tag.getCompound(SCRIPT)), watches, console, tag.getInt(WRITTEN),
                tag.getString(STATE), tag.getString(MESSAGE), tag.getInt(SPENT));
    }

    // what the program allocated

    private static CompoundTag write(final Snapshot.IHeld one) {
        final CompoundTag tag = new CompoundTag();
        tag.putInt(ID, one.id());
        tag.putLong(BYTES, one.bytes());
        tag.putInt(LINE, one.line());
        tag.putBoolean(FREED, one.freed());
        switch (one) {
            case Snapshot.IHeld.Text text -> {
                tag.putString(KIND, "text");
                tag.putString(VALUE, text.value());
            }
            case Snapshot.IHeld.Object object -> {
                tag.putString(KIND, "object");
                tag.putString(TYPE, object.type());
                tag.put(FIELDS, fields(object.fields()));
            }
            case Snapshot.IHeld.Array array -> {
                tag.putString(KIND, "array");
                tag.putString(TYPE, array.element());
                tag.put(VALUES, values(array.values()));
            }
            case Snapshot.IHeld.Listing list -> {
                tag.putString(KIND, "list");
                tag.put(ITEMS, values(list.items()));
            }
            case Snapshot.IHeld.Keyed keyed -> {
                tag.putString(KIND, "map");
                tag.put(KEYS, values(keyed.keys()));
                tag.put(VALUES, values(keyed.values()));
            }
            case Snapshot.IHeld.Handler handler -> {
                tag.putString(KIND, "handler");
                tag.putString(TYPE, handler.type());
                final ListTag chain = new ListTag();
                for (final Snapshot.BoundShot bound : handler.chain()) {
                    final CompoundTag written = new CompoundTag();
                    written.put(TARGET, write(bound.target()));
                    written.putString(OWNER, bound.owner());
                    written.putString(METHOD, bound.method());
                    written.put(PARAMETERS, names(bound.parameters()));
                    written.putString(RETURNS, bound.returns());
                    chain.add(written);
                }
                tag.put(CHAIN, chain);
            }
        }
        return tag;
    }

    private static Snapshot.IHeld readHeld(final CompoundTag tag) {
        final int id = tag.getInt(ID);
        final long bytes = tag.getLong(BYTES);
        final int line = tag.getInt(LINE);
        final boolean freed = tag.getBoolean(FREED);
        return switch (tag.getString(KIND)) {
            case "text" -> new Snapshot.IHeld.Text(id, bytes, line, freed, tag.getString(VALUE));
            case "object" -> new Snapshot.IHeld.Object(id, bytes, line, freed, tag.getString(TYPE),
                    readFields(tag.getList(FIELDS, Tag.TAG_COMPOUND)));
            case "array" -> new Snapshot.IHeld.Array(id, bytes, line, freed, tag.getString(TYPE),
                    readValues(tag.getList(VALUES, Tag.TAG_COMPOUND)));
            case "list" -> new Snapshot.IHeld.Listing(id, bytes, line, freed,
                    readValues(tag.getList(ITEMS, Tag.TAG_COMPOUND)));
            case "map" -> new Snapshot.IHeld.Keyed(id, bytes, line, freed,
                    readValues(tag.getList(KEYS, Tag.TAG_COMPOUND)),
                    readValues(tag.getList(VALUES, Tag.TAG_COMPOUND)));
            default -> {
                final List<Snapshot.BoundShot> chain = new ArrayList<>();
                final ListTag written = tag.getList(CHAIN, Tag.TAG_COMPOUND);
                for (int i = 0; i < written.size(); i++) {
                    final CompoundTag bound = written.getCompound(i);
                    chain.add(new Snapshot.BoundShot(readValue(bound.getCompound(TARGET)),
                            bound.getString(OWNER), bound.getString(METHOD),
                            readNames(bound.getList(PARAMETERS, Tag.TAG_STRING)),
                            bound.getString(RETURNS)));
                }
                yield new Snapshot.IHeld.Handler(id, bytes, line, freed, tag.getString(TYPE), chain);
            }
        };
    }

    // what each call was doing

    private static ListTag frames(final List<Snapshot.FrameShot> shots) {
        final ListTag written = new ListTag();
        for (final Snapshot.FrameShot frame : shots) {
            final CompoundTag tag = new CompoundTag();
            tag.putString(OWNER, frame.owner());
            tag.putString(NAME, frame.name());
            tag.put(PARAMETERS, names(frame.parameters()));
            tag.putInt(AT, frame.at());
            tag.put(SELF, write(frame.self()));
            tag.put(SLOTS, values(frame.slots()));
            tag.put(STACK, values(frame.stack()));
            tag.putBoolean(DISCARD, frame.discard());
            written.add(tag);
        }
        return written;
    }

    private static List<Snapshot.FrameShot> readFrames(final ListTag written) {
        final List<Snapshot.FrameShot> shots = new ArrayList<>();
        for (int i = 0; i < written.size(); i++) {
            final CompoundTag tag = written.getCompound(i);
            shots.add(new Snapshot.FrameShot(tag.getString(OWNER), tag.getString(NAME),
                    readNames(tag.getList(PARAMETERS, Tag.TAG_STRING)), tag.getInt(AT),
                    readValue(tag.getCompound(SELF)), readValues(tag.getList(SLOTS, Tag.TAG_COMPOUND)),
                    readValues(tag.getList(STACK, Tag.TAG_COMPOUND)), tag.getBoolean(DISCARD)));
        }
        return shots;
    }

    // single values

    private static CompoundTag write(final Snapshot.IValue value) {
        final CompoundTag tag = new CompoundTag();
        switch (value) {
            case Snapshot.IValue.Nothing ignored -> tag.putString(KIND, "none");
            case Snapshot.IValue.I4 number -> {
                tag.putString(KIND, "i4");
                tag.putInt(VALUE, number.value());
            }
            case Snapshot.IValue.I8 number -> {
                tag.putString(KIND, "i8");
                tag.putLong(VALUE, number.value());
            }
            case Snapshot.IValue.R4 number -> {
                tag.putString(KIND, "r4");
                tag.putFloat(VALUE, number.value());
            }
            case Snapshot.IValue.R8 number -> {
                tag.putString(KIND, "r8");
                tag.putDouble(VALUE, number.value());
            }
            case Snapshot.IValue.Bool flag -> {
                tag.putString(KIND, "bool");
                tag.putBoolean(VALUE, flag.value());
            }
            case Snapshot.IValue.Ch letter -> {
                tag.putString(KIND, "char");
                tag.putInt(VALUE, letter.value());
            }
            case Snapshot.IValue.Ref reference -> {
                tag.putString(KIND, "ref");
                tag.putInt(VALUE, reference.id());
            }
        }
        return tag;
    }

    private static Snapshot.IValue readValue(final CompoundTag tag) {
        return switch (tag.getString(KIND)) {
            case "i4" -> new Snapshot.IValue.I4(tag.getInt(VALUE));
            case "i8" -> new Snapshot.IValue.I8(tag.getLong(VALUE));
            case "r4" -> new Snapshot.IValue.R4(tag.getFloat(VALUE));
            case "r8" -> new Snapshot.IValue.R8(tag.getDouble(VALUE));
            case "bool" -> new Snapshot.IValue.Bool(tag.getBoolean(VALUE));
            case "char" -> new Snapshot.IValue.Ch((char) tag.getInt(VALUE));
            case "ref" -> new Snapshot.IValue.Ref(tag.getInt(VALUE));
            default -> new Snapshot.IValue.Nothing();
        };
    }

    private static ListTag values(final List<Snapshot.IValue> values) {
        final ListTag written = new ListTag();
        for (final Snapshot.IValue value : values) {
            written.add(write(value));
        }
        return written;
    }

    private static List<Snapshot.IValue> readValues(final ListTag written) {
        final List<Snapshot.IValue> values = new ArrayList<>();
        for (int i = 0; i < written.size(); i++) {
            values.add(readValue(written.getCompound(i)));
        }
        return values;
    }

    private static ListTag fields(final Map<String, Snapshot.IValue> fields) {
        final ListTag written = new ListTag();
        for (final Map.Entry<String, Snapshot.IValue> field : fields.entrySet()) {
            final CompoundTag tag = write(field.getValue());
            tag.putString(NAME, field.getKey());
            written.add(tag);
        }
        return written;
    }

    private static Map<String, Snapshot.IValue> readFields(final ListTag written) {
        final Map<String, Snapshot.IValue> fields = new LinkedHashMap<>();
        for (int i = 0; i < written.size(); i++) {
            final CompoundTag tag = written.getCompound(i);
            fields.put(tag.getString(NAME), readValue(tag));
        }
        return fields;
    }

    private static ListTag names(final List<String> names) {
        final ListTag written = new ListTag();
        for (final String name : names) {
            written.add(StringTag.valueOf(name));
        }
        return written;
    }

    private static List<String> readNames(final ListTag written) {
        final List<String> names = new ArrayList<>();
        for (int i = 0; i < written.size(); i++) {
            names.add(written.getString(i));
        }
        return names;
    }
}
