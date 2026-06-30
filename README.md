# JSON Form Validator

A lightweight, dependency-minimal Java component for validating a **filled JSON form** against a **JSON form template**. It separates *structural* validation (does the submitted form have the same shape as the template?) from *content* validation (are the submitted values acceptable according to the template's rules?).

Built for Spring Boot and Jackson, but the core logic has no framework-specific dependencies beyond a JSON object mapper.

## The problem it solves

Many applications describe forms as JSON rather than hard-coded UI: a template defines the form (its sections, fields, types, allowed options), and a filled instance carries the same structure plus the values a user entered. A common need is to confirm that a submitted instance genuinely matches the template it claims to follow, and that the values inside it respect the template's rules.

This is **not** classic JSON Schema validation. There is no separate schema document written in the JSON Schema language. Instead, the template *is* the schema: the empty form and the filled form share the same shape, and the only legitimate difference between them is the data in each field's `value`. The validator's job is to confirm exactly that.

A typical template field looks like this (empty `value`):

```json
{ "id": "country", "label": "Country", "type": "select", "options": ["Italy", "Albania", "Montenegro"], "required": true, "value": "" }
```

The corresponding submitted field carries the same structure with a value filled in:

```json
{ "id": "country", "label": "Country", "type": "select", "options": ["Italy", "Albania", "Montenegro"], "required": true, "value": "Italy" }
```

## Form model

The JSON form is a three-level nested structure, mirrored by three DTOs.

```
Form (version, sections[])
 └── Section (title, fields[])
      └── Field (id, label, type, options[], value, required)
```

### Field types

The validator understands six field types, grouped by how their `value` is shaped:

String-valued types carry a single string in `value`: `text` (short free text), `long` (multi-line text), `url` (a single URL), and `select` (one choice out of `options`).

Array-valued types carry a list of strings in `value`: `multi` (several choices out of `options`) and `check` (one or more checkboxes, each an entry in `options`).

### DTO fields

`FormDTO` holds the schema `version` (an `int`, defaulting to `0` when absent) and the ordered list of sections.

`SectionDTO` holds a `title` and the ordered list of fields belonging to it.

`FieldDTO` holds the field's `id` (unique identifier), `label` (human-readable caption, also used in error messages), `type` (one of the six types above), `options` (the list of allowed values, present only for `select`/`multi`/`check`), `value` (the user-entered data, typed as `Object` because it is a string for some types and a list for others), and `required` (a `boolean`, defaulting to `false` when absent, read only from the template).

All three DTOs ignore unknown JSON properties during deserialization, so adding new attributes to the form JSON in the future will not break older consumers.

## Validation

The component exposes its logic through a single service that offers three public entry points plus a convenience check.

### Structural validation

`validateStructure(templateJson, compiledJson)` confirms that the submitted form has the same shape as the template, ignoring all values. It deserializes both JSON strings, then compares the form `version`, the number of sections, and — section by section, field by field — each section's `title` and each field's `id`, `type`, `label`, and `options`. The `value` is deliberately never compared here: it is the one thing the two forms are expected to differ on.

The comparison is **positional**: section *i* of the template is compared with section *i* of the submission, and likewise for fields. If the number of sections (or fields within a section) differs, the validator records the discrepancy and stops descending into that level, since a positional comparison past that point would be meaningless. Any deserialization failure (malformed JSON) is caught and reported as a single error rather than thrown.

It returns a list of error messages, empty when the structure is valid.

### Content validation

`validateContent(templateJson, compiledJson)` confirms that the submitted values respect the rules declared in the template. It assumes the structure has already been validated; to stay safe when called on its own, it walks both forms using the smaller of the two sizes at each level, so it never reads past the end of a list.

The rules applied depend on the field type. For array-valued types (`multi`, `check`), a `required` field whose value list is empty produces a "required" error, and every entry in the value list must appear among the template's `options` or it produces an "invalid value" error. For string-valued types (`text`, `long`, `url`, `select`), a `required` field whose value is empty or absent produces a "required" error; additionally, a non-empty `select` value must appear among the template's `options`, and a non-empty `url` value must be a syntactically valid URL.

A key design point: empty values are checked only for the *required* rule, never for the *content* rule. An optional field left blank is legitimate and must not trigger a "not among the permitted options" or "invalid format" error. This keeps optional fields genuinely optional.

It returns a list of error messages, empty when the content is valid.

### Combined validation

`validateAll(templateJson, compiledJson)` runs structural validation first and, **only if it produces no errors**, proceeds to content validation. It returns the combined list.

The short-circuit is intentional and is the recommended single entry point. If the structure is broken, content validation would be unreliable — fields may no longer line up by position, so its messages could be misleading. Gating content behind a clean structure guarantees that content validation always operates on a well-formed submission.

### Convenience check

`isValid(templateJson, compiledJson)` returns a simple boolean — `true` when structural validation produces no errors — for callers that only need a yes/no answer without the detail.

## Design notes

Errors are **accumulated**, not thrown one at a time. Each validation method collects every problem it finds into a list and returns the whole list, so a caller can show the user everything that needs fixing at once rather than one error per round-trip.

Comparisons use null-safe equality throughout, so fields that legitimately carry no `options` (such as `text`) compare cleanly without special-casing.

The `required` flag lives in the template, not in the code. This means the set of mandatory fields is configuration, editable by whoever maintains the templates, rather than something that requires a code change.

The object mapper is created once and reused. It is comparatively expensive to construct but thread-safe once built, so a single instance serves all validations.

## Usage

```java
List<String> errors = validator.validateAll(templateJson, compiledJson);
if (!errors.isEmpty()) {
    // reject the submission and report the messages back to the caller
}
// otherwise the form is structurally sound and its contents are valid
```

## Requirements

Java 17 or later (the code uses pattern matching for `instanceof` and `List.toList()`), and a Jackson object mapper for deserialization. The service is annotated for Spring component scanning, but the validation logic itself is plain Java and can be lifted out of a Spring context if needed.

## License

Choose a license appropriate for your project (for example, MIT or Apache-2.0) and state it here.
