#!/usr/bin/env -S java --source 11
class Scratch {
    public static void main(String[] args) {
        // The sentence is intentionally split, so we can be sure that the complete sentence can only appear in STDOUT,
        // when Java shebang worked.
        System.out.printf("%s %s works", "Java", "shebang");
    }
}
