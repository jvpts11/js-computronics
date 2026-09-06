/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon;

/**
 * Every message the front end can produce, with the code a player quotes when asking for help.
 *
 * <p>The numbering is by stage, so a code says where the compiler gave up: C1xxx while reading the
 * characters, C2xxx while reading the grammar. Later stages take the ranges above them.
 */
public enum CannonError {

    UNTERMINATED_STRING("C1001", "the string was never closed before the end of the line"),
    UNTERMINATED_COMMENT("C1002", "the comment was never closed before the end of the file"),
    UNEXPECTED_CHARACTER("C1003", "'%s' does not begin anything the language knows"),
    MALFORMED_NUMBER("C1004", "'%s' is not a number the language can read"),
    INVALID_CHARACTER_LITERAL("C1005", "a character literal holds exactly one character"),
    UNKNOWN_ESCAPE("C1006", "'\\%s' is not an escape the language knows"),

    EXPECTED_TOKEN("C2001", "expected %s but found %s"),
    EXPECTED_TYPE("C2002", "expected a type but found %s"),
    EXPECTED_EXPRESSION("C2003", "expected an expression but found %s"),
    EXPECTED_MEMBER("C2004", "expected a field, a method, a property or an event but found %s"),
    EXPECTED_TYPE_DECLARATION("C2005", "expected a class, an interface, an enum or a delegate but found %s"),
    NOT_A_STATEMENT("C2006", "only a call, an assignment, an increment, a decrement or a new object "
            + "can be used as a statement"),
    DUPLICATE_MODIFIER("C2007", "'%s' was given twice"),
    INVALID_ASSIGNMENT_TARGET("C2008", "the left side of an assignment must be a variable, a field, "
            + "a property or an element"),

    UNKNOWN_NAME("C3001", "'%s' does not name anything here"),
    DUPLICATE_DECLARATION("C3002", "'%s' is already declared here"),
    CANNOT_CONVERT("C3003", "cannot convert '%s' to '%s'"),
    NO_SUCH_MEMBER("C3004", "'%s' has no member called '%s'"),
    NO_MATCHING_OVERLOAD("C3005", "no version of '%s' takes those arguments"),
    AMBIGUOUS_CALL("C3006", "the call to '%s' fits more than one version of it"),
    OPERATOR_ON_TYPES("C3007", "'%s' cannot be applied to '%s' and '%s'"),
    OPERATOR_ON_TYPE("C3008", "'%s' cannot be applied to '%s'"),
    CONDITION_MUST_BE_BOOL("C3009", "a condition is a bool, not '%s'"),
    BREAK_OUTSIDE_LOOP("C3010", "'break' only means something inside a loop or a switch"),
    CONTINUE_OUTSIDE_LOOP("C3011", "'continue' only means something inside a loop"),
    MISSING_RETURN_VALUE("C3012", "a method that gives back '%s' must return a value"),
    UNEXPECTED_RETURN_VALUE("C3013", "a method that gives back nothing cannot return a value"),
    THIS_IN_STATIC("C3014", "'%s' is not available in a static member"),
    NO_BASE_CLASS("C3015", "'%s' has no base class"),
    CANNOT_ASSIGN_READONLY("C3016", "'%s' is readonly, so it can only be written where it is declared "
            + "or in a constructor"),
    ENTRY_POINT("C3017", "a program needs exactly one class that implements IScript, and this one has %s"),
    MISSING_INTERFACE_MEMBER("C3018", "'%s' says it is a '%s' but does not have '%s'"),
    WRONG_TYPE_ARGUMENT_COUNT("C3019", "'%s' takes %s type arguments"),
    STATIC_THROUGH_INSTANCE("C3020", "'%s' belongs to the type, not to one of its objects"),
    INSTANCE_THROUGH_TYPE("C3021", "'%s' belongs to an object, not to the type"),
    CANNOT_DISPOSE("C3022", "only an object can be disposed, not '%s'"),
    NOT_A_COLLECTION("C3023", "foreach walks an array or a list, not '%s'"),
    CANNOT_INDEX("C3024", "'%s' cannot be indexed by '%s'"),
    CANNOT_CALL("C3025", "'%s' is not something that can be called"),
    CANNOT_CREATE("C3026", "'%s' cannot be made with new"),
    LAMBDA_SHAPE("C3027", "this lambda does not have the shape '%s' takes"),
    NOT_A_TYPE("C3028", "'%s' does not name a type"),
    DUPLICATE_SWITCH_LABEL("C3029", "this switch already has that label"),
    EVENT_OUTSIDE_ITS_TYPE("C3030", "an event can only be raised inside the type that declares it"),
    INVALID_BASE("C3031", "only a class or an interface can stand behind the colon, and '%s' is neither"),
    EVENT_NEEDS_DELEGATE("C3032", "an event's type must be a delegate, and '%s' is not one"),
    METHOD_AS_VALUE("C3033", "'%s' is a method: call it, or hand it over where a delegate of its shape "
            + "is wanted"),
    OUT_ARGUMENT_EXPECTED("C3034", "'%s' is filled in by the method, so the argument is written with out"),
    OUT_ARGUMENT_UNEXPECTED("C3035", "'%s' is read by the method, so the argument cannot be written with out"),
    OUT_TYPE_MUST_MATCH("C3036", "an out argument is exactly '%s', with no conversion on the way"),
    OUT_NOT_ASSIGNED("C3037", "'%s' is filled in by this method, so it must be given a value before "
            + "every way out"),
    OUT_NOT_A_PLACE("C3038", "'%s' is not somewhere a method can write"),
    ENUM_VALUE_MUST_BE_WRITTEN("C3039", "an enum's number has to be written as a number"),

    MISSING_VERSION_LINE("C4001", "the listing has to begin with a version line"),
    VERSION_TOO_NEW("C4002", "this runtime reads assembly version %s, and this listing is version %s"),
    UNKNOWN_INSTRUCTION("C4003", "'%s' is not an instruction"),
    MISSING_OPERAND("C4004", "'%s' needs something after it"),
    UNEXPECTED_OPERAND("C4005", "'%s' takes nothing after it"),
    MALFORMED_OPERAND("C4006", "'%s' is not what '%s' takes"),
    UNKNOWN_DIRECTIVE("C4007", "'%s' is not a line this format has"),
    INSTRUCTION_OUTSIDE_METHOD("C4008", "an instruction has to be inside a method"),
    DIRECTIVE_OUTSIDE_TYPE("C4009", "'%s' has to be inside a type"),
    UNKNOWN_LABEL("C4010", "nothing in this method is labelled '%s'"),
    NOT_YET_BUILT("C4011", "%s is not built yet");

    private final String code;
    private final String template;

    CannonError(final String code, final String template) {
        this.code = code;
        this.template = template;
    }

    /** The code as it appears in a diagnostic, for example {@code C2001}. */
    public String code() {
        return this.code;
    }

    /** The message with its placeholders filled in. */
    public String message(final Object... arguments) {
        return arguments.length == 0 ? this.template : String.format(this.template, arguments);
    }
}
