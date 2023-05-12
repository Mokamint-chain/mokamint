module io.mokamint.tools {
	exports io.mokamint.tools;
	opens io.mokamint.tools to info.picocli; // for injecting CLI options
	opens io.mokamint.tools.internal to info.picocli; // for accessing the POMVersionProvider

	requires transitive info.picocli;
	requires java.logging;
}