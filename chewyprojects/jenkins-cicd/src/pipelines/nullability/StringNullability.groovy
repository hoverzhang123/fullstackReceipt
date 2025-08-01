package pipelines.nullability

class StringNullability {
    static String assertNotBlank(String s, Object additionalInfo = null) {
        assert s != null && !s.blank:
        'String must not be null or blank' + (additionalInfo != null ? ": ($additionalInfo)" : '')
        return s
    }
}
