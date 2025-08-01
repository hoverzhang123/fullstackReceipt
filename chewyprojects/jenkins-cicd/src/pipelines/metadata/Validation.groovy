package pipelines.metadata


class Validation {
    List<String> success
    List<String> failure

    @Override
    String toString() {
        return "success:${success.toListString()}\nfailure:${failure.toListString()}"
    }
}
