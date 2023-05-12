module io.mokamint.tools {
	exports io.mokamint.tools;
	opens io.mokamint.tools to info.picocli; // for injecting CLI options

	requires transitive info.picocli;
	requires java.logging;
}