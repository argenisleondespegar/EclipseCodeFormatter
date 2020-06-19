package krasa.formatter.eclipse;

import com.intellij.openapi.diagnostic.Logger;
import krasa.formatter.exception.FormattingFailedException;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

public class ConfigurableEclipseLocation {
	private static final Logger LOG = Logger.getInstance(ConfigurableEclipseLocation.class.getName());

	private static final int TIMEOUT = 500;

	//@formatter:off
	String[] JAR_NAMES = {
			"org.eclipse.core.contenttype",
			"org.eclipse.core.jobs",
			"org.eclipse.core.resources",
			"org.eclipse.core.runtime",
			"org.eclipse.equinox.app",//probably useless
			"org.eclipse.equinox.common",
			"org.eclipse.equinox.preferences",
			"org.eclipse.jdt.core",
			"org.eclipse.osgi",
			"org.eclipse.text"
	};
	//@formatter:on

	public Set<String> jarNames;

	public ConfigurableEclipseLocation() {
		jarNames = new HashSet<String>();
		jarNames.addAll(Arrays.asList(JAR_NAMES));

	}

	public static void main(String[] args) throws IOException {
		List<URL> urlList = new ConfigurableEclipseLocation().run("F:\\workspace\\eclipse-jee 4.5.2");
		for (URL jar : urlList) {
			System.out.println(jar);
		}
	}

	public List<URL> run(String from) {
		long start = System.currentTimeMillis();
		List<URL> jars = null;
		try {
			File root = new File(from);
			root = findEclipseRoot(root, start);

			if (root == null || !root.exists()) {
				throw new IllegalStateException("Invalid Eclipse location");
			}

			LOG.info("found root=" + root.getAbsolutePath() + " in" + (System.currentTimeMillis() - start) + "ms");

			jars = findJars(root);

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		if (!jarNames.isEmpty()) {
			throw new FormattingFailedException("Required jars not found in '" + from + "': " + jarNames.toString(), true);
		}

		long total = System.currentTimeMillis() - start;
		LOG.info("found " + jars.size() + " jars in " + total + "ms, (" + from + ")");
		return jars;
	}

	private File findEclipseRoot(File root, long start) {
		if (System.currentTimeMillis() - start > TIMEOUT) {
			throw new FormattingFailedException("Timeout, Eclipse installation containing '.eclipseproduct' not found.", true);
		}
		File stick = FileUtils.getFile(root, ".eclipseproduct");
		if (!stick.exists()) {
			File[] files = root.listFiles();
			if (files != null) {
				for (File childDir : files) {
					if (childDir.isDirectory()) {
						stick = findEclipseRoot(childDir, start);
						if (stick != null) {
							return stick;
						}
					}
				}
			}
			return null;
		} else {
			return root;
		}
	}

	@NotNull
	private List<URL> findJars(File from) throws IOException {
		File plugins = FileUtils.getFile(from, "plugins");

		List<URL> files = findJarsFromPackagedInstall(plugins);

		if (files.isEmpty()) {
			File bundlesInfo = FileUtils.getFile(from, "configuration", "org.eclipse.equinox.simpleconfigurator", "bundles.info");
			files = findJarsFromRepository(bundlesInfo);
		}
		return files;
	}

	private List<URL> findJarsFromRepository(File bundlesInfo) throws IOException {
		long start = System.currentTimeMillis();
		List<URL> files = new ArrayList<>();
		List<String> strings = FileUtils.readLines(bundlesInfo, "UTF-8");
		for (String string : strings) {
			String[] split = string.split(",");
			if (split.length >= 3) {
				String name = split[0];
				String path = split[2];
				if (jarNames.contains(name)) {
					jarNames.remove(name);
					files.add(new URL(path));
				}
			}
		}
		LOG.info("#findJarsFromRepository took " + (System.currentTimeMillis() - start) + "ms");
		return files;
	}

	@NotNull
	private List<URL> findJarsFromPackagedInstall(File from) throws IOException {
		long start = System.currentTimeMillis();
		List<URL> files = new ArrayList<URL>();
		File[] listFiles = from.listFiles();
		if (listFiles != null) {
			for (File next : listFiles) {
				String name = next.getName();
				if (name.endsWith(".jar")) {
					int i = name.indexOf("_");
					if (i <= 0)
						continue;
					String jarName = name.substring(0, i);
					if (jarNames.contains(jarName)) {
						jarNames.remove(jarName);
						files.add(next.toURI().toURL());
					}
				}
			}
		}
		LOG.info("#findJarsFromPackagedInstall took " + (System.currentTimeMillis() - start) + "ms");
		return files;
	}
}
