/*******************************************************************************
 * Copyright (c) 2005-2010 RoSuDa (www.rosuda.org) and others.
 * and others. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * v2.1 or newer, which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 * 
 * Contributors:
 *    RoSuDa, University Augsburg - initial API and implementation
 *    Stephan Wahlbrink - adjustments to RJ
 *******************************************************************************/

package org.rosuda.rj;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


public class JRClassLoader extends URLClassLoader {
	
	private static JRClassLoader instance;
	
	public static JRClassLoader getRJavaClassLoader() throws InstantiationException, IllegalAccessException, ClassNotFoundException {
		synchronized (JRClassLoader.class) {
			if (instance != null) {
				return instance;
			}
			instance = (JRClassLoader) Class.forName("RJavaClassLoader").newInstance();
			return instance;
		}
	}
	
	
	private static final Logger LOGGER = Logger.getLogger("org.rosuda.rjava");
	public static boolean verbose = false;
	
	public static void setDebug(final int level) {
		verbose = (level > 0);
		if (verbose) {
			LOGGER.setLevel(Level.ALL);
		}
	}
	
	
	private static final Pattern PATH_SPLITTER = Pattern.compile(Pattern.quote(File.pathSeparator));
	
	
	/**
	 * Light extension of File that handles file separators and updates
	 */
	private static class UnixFile extends java.io.File {
		
		private static final long serialVersionUID = 6120112960030467453L;
		
		/**
		 * cached "last time modified" stamp
		 */ 
		private long lastModStamp;
		public Object cache;
		
		/**
		 * Constructor. Modifies the path so that the proper path separator is used (most useful on windows)
		 */
		public UnixFile(final String fn) {
			super((separatorChar != '/') ? fn.replace('/', separatorChar) : fn);
			this.lastModStamp = 0;
		}
		
		/**
		 * @return whether the file modified since last time the update method was called
		 */
		public boolean hasChanged() {
			final long curMod = lastModified();
			return (curMod != this.lastModStamp);
		}
		
		/**
		 * Cache the result of the lastModified stamp
		 */
		public void update() {
			this.lastModStamp = lastModified();
		}
		
	}
	
	/**
	 * Specialization of UnixFile that deals with jar files
	 */
	private static class UnixJarFile extends UnixFile {
		
		private static final long serialVersionUID = -956832260008070610L;
		
		/**
		 * The cached jar file
		 */
		private ZipFile zfile ;
		
		/**
		 * common prefix for all URLs within this jar file
		 */
		private String urlPrefix ;
		
		public UnixJarFile(final String filename) {
			super(filename);
		}
		
		@Override
		public void update() {
			try {
				if (this.zfile != null) {
					this.zfile.close();
				}
				this.zfile = new ZipFile(this) ;
			} catch (final Exception tryCloseX) {}
			/* time stamp */
			super.update() ; 
		}
		
		/**
		 * Get an input stream for a resource contained in the jar file
		 * 
		 * @param name file name of the resource within the jar file
		 * @return an input stream representing the resouce if it exists or null
		 */ 
		public InputStream getResourceAsStream(final String name) {
			if (this.zfile==null || hasChanged()) {
				update(); 
			}
			try {
				if (this.zfile == null) {
					return null;
				}
				final ZipEntry e = this.zfile.getEntry(name);
				if (e != null) {
					return this.zfile.getInputStream(e);
				}
			} catch (final Exception e) {
				if (verbose) {
					LOGGER.log(Level.WARNING, "Failed to create resource stream for JAR file.", e);
				}
			}
			return null;
		}
		
		public URL getResource(final String name) {
			if(this.zfile == null  || this.zfile.getEntry(name) == null) {
				return null;
			}
			
			URL u = null;
			if (this.urlPrefix == null) {
				try{
					this.urlPrefix = "jar:" + toURL().toString() + "!";
				} catch(final java.net.MalformedURLException ex) {
				}
			}
			
			try{
				u = new URL(this.urlPrefix + name) ;
			} catch (final java.net.MalformedURLException ex) {
			}
			return u ;
		}
		
	}
	
	/**
	 * Specialization of UnixFile representing a directory
	 */ 
	private static class UnixDirectory extends UnixFile {
		
		private static final long serialVersionUID = -5697105404215366546L;
		
		public UnixDirectory(final String dirname) {
			super(dirname);
		}
		
	}
	
	
	public static final int OS_WIN = 1;
	public static final int OS_NIX = 2;
	public static final int OS_MAC = 3;
	
	private final String r_home;
	private final String r_arch;
	private final List<String> r_libs;
	private final List<String> r_libs_site;
	private final List<String> r_libs_user;
	
	private final int os;
	private final String arch;
	
	
	/** 
	 * Path of rJava
	 */
	private String rJavaPath;
	
	/**
	 * Map of native libraries (name, path)
	 */
	private final HashMap<String, UnixFile> libMap;
	
	/**
	 * The class path list for alternative class loading
	 */ 
	private final Vector<UnixFile> classPath;
	
	private final Set<String> defaultLibPath;
	
	/**
	 * Should the system class loader be used to resolve classes as well as this class loader
	 */
	private final boolean useSystem = !"false".equalsIgnoreCase(System.getProperty("rjava.classloader.system")); // default true
	
	private final boolean useSecond = "true".equalsIgnoreCase(System.getProperty("rjava.classloader.alternative")); // default false
	
	
	protected JRClassLoader() {
		super(new URL[0], getSystemClassLoader());
		this.libMap = new HashMap<String, UnixFile>();
		this.classPath = new Vector<UnixFile>();
		this.defaultLibPath = new LinkedHashSet<String>();
		
		if (verbose) {
			LOGGER.log(Level.CONFIG, "System URL classloader: " + (this.useSystem ? "enabled" : "disabled"));
			LOGGER.log(Level.CONFIG, "Alternative classloader: " + (this.useSecond ? "enabled" : "disabled"));
		}
		
		this.r_home = checkDirPath(System.getenv("R_HOME"));
		this.r_arch = System.getenv("R_ARCH");
		this.r_libs_site = checkDirPathList(System.getenv("R_LIBS_SITE"));
		this.r_libs = checkDirPathList(System.getenv("R_LIBS"));
		this.r_libs_user = checkDirPathList(System.getenv("R_LIBS_USER"));
		final String osname = System.getProperty("os.name").toLowerCase();
		if (osname.contains("win")) {
			this.os = OS_WIN;
		}
		else if (osname.contains("mac")) {
			this.os = OS_MAC;
		}
		else {
			this.os = OS_NIX;
		}
		this.arch = (this.r_arch != null) ? this.r_arch : System.getProperty("os.arch");
		
		initRLibs();
		
		this.rJavaPath = System.getProperty("rjava.path");
		if (this.rJavaPath == null) {
			this.rJavaPath = searchPackageInLibrary("rJava");
		}
		if (this.rJavaPath == null) {
			final String message = "Path to rJava package not found. Use R_LIBS or java property 'rjava.path' to specify the location.";
			LOGGER.log(Level.SEVERE, message);
			throw new IllegalArgumentException(message);
		}
		
		final String rJavaClassPath = System.getProperty("rjava.class.path");
		if (rJavaClassPath != null) {
			addClassPath(checkDirPathList(rJavaClassPath));
		}
		
		// rJava library
		String rJavaLibPath = System.getProperty("rjava.rjavalibs");
		if (rJavaLibPath == null) {
			rJavaLibPath = this.rJavaPath + "/java";
		}
		addClassPath(this.rJavaPath + "/java");
		final String rJavaDynlibName;
		switch (this.os) {
		case OS_WIN:
			rJavaDynlibName = "rJava.dll";
			break;
		default:
			rJavaDynlibName = "rJava.so";
			break;
		}
		final UnixFile rJavaDynlibFile = new UnixFile(rJavaLibPath + '/' + rJavaDynlibName);
		if (rJavaDynlibFile.exists()) {
			this.libMap.put("rJava", rJavaDynlibFile);
		}
		
		// jri library
		String jriLibPath = System.getProperty("rjava.jrilibs");
		if (jriLibPath == null) {
			jriLibPath = this.rJavaPath + "/jri";
		}
		final String jriDynlibName;
		switch (this.os) {
		case OS_WIN:
			jriDynlibName = "jri.dll";
			break;
		case OS_MAC:
			jriDynlibName = "libjri.jnilib";
			break;
		default:
			jriDynlibName = "libjri.so";
			break;
		}
		final UnixFile jriDynlibFile = searchFile(new String[] {
				jriLibPath + '/' + jriDynlibName,
				jriLibPath + this.arch + '/' + jriDynlibName,
		});
		if (jriDynlibFile != null) {
			this.libMap.put("jri", jriDynlibFile);
			if (verbose) {
				LOGGER.log(Level.CONFIG, "registered JRI: " + jriDynlibFile);
			}
		}
		else {
			if (verbose) {
				LOGGER.log(Level.WARNING, jriDynlibName + " not found");
			}
		}
		
		final UnixFile jriJarFile = searchFile(new String[] {
				jriLibPath + "/JRI.jar",
				this.rJavaPath + "/jri/JRI.jar",
		});
		if (jriJarFile != null) {
			addClassPath(jriJarFile);
		}
		else {
			if (verbose) {
				LOGGER.log(Level.WARNING, "JRI.jar not found and not added to classpath");
			}
		}
	}
	
	private String checkDirPath(String path) {
		if (path != null) {
			path = path.trim();
			if (path.length() > 0) {
				path = path.replace('\\', '/');
				int end = path.length();
				while (end > 0 && path.charAt(end-1) == '/') {
					end--;
				}
				if (end != path.length()) {
					path = path.substring(0, end);
				}
				if (path.length() > 0) {
					return path;
				}
			}
		}
		return null;
	}
	
	private List<String> checkDirPathList(String pathList) {
		if (pathList != null) {
			pathList = pathList.trim();
			if (pathList.length() > 0) {
				final String[] split = PATH_SPLITTER.split(pathList);
				final ArrayList<String> list = new ArrayList<String>(split.length);
				for (int i = 0; i < split.length; i++) {
					final String path = checkDirPath(split[i]);
					if (path != null) {
						list.add(path);
					}
				}
				return list;
			}
		}
		return null;
	}
	
	
	public int getOSType() {
		return this.os;
	}
	
	
	private UnixFile searchFile(final String[] search) {
		for (final String path : search) {
			final UnixFile file = new UnixFile(path);
			if (file.exists()) {
				return file;
			}
		}
		return null;
	}
	
	private void initRLibs() {
		synchronized (this.defaultLibPath) {
			this.defaultLibPath.clear();
			
			// R user libraries (R_LIBS_USER)
			if (this.r_libs_user != null) {
				for (final String l : this.r_libs_user) {
					this.defaultLibPath.add(l);
				}
			}
			
			// R other libraries (R_LIBS)
			if (this.r_libs != null) {
				for (final String l : this.r_libs) {
					this.defaultLibPath.add(l);
				}
			}
			
			// R site libraries (R_LIBS_SITE)
			if (this.r_libs_site != null) {
				for (final String l : this.r_libs_site) {
					this.defaultLibPath.add(l);
				}
			}
			else if (this.r_home != null) {
				if (this.r_home.startsWith("/usr/lib")) {
					this.defaultLibPath.add("/usr/local/lib"+this.r_home.substring(8)+"/site-library");
				}
			}
			// R default library
			if (this.r_home != null) {
				this.defaultLibPath.add(this.r_home+"/library");
			}
			
			if (verbose) {
				final StringBuilder sb = new StringBuilder((1+this.defaultLibPath.size())*32);
				sb.append("JR library path: ");
				final String sep = System.getProperty("line.separator")+"\t";
				for (final String item : this.defaultLibPath) {
					sb.append(sep);
					if (item != null) {
						sb.append(item);
					}
				}
				LOGGER.log(Level.CONFIG, sb.toString());
			}
		}
	}
	
	private String searchPackageInLibrary(final String name) {
		synchronized (this.defaultLibPath) {
			for (final String l : this.defaultLibPath) {
				try {
					final String p = l+'/'+name;
					final File dir = new File(p);
					if (dir.exists() && dir.isDirectory()) {
						return p;
					}
				}
				catch (final Exception e) {}
			}
			return null;
		}
	}
	
	private String classNameToFile(final String cls) {
		// convert . to /
		return cls.replace('.', '/');
	}
	
	@Override
	protected Class<?> findClass(final String name) throws ClassNotFoundException {
		if (verbose) {
			LOGGER.entering("RJavaClassLoader", "findClass", name);
		}
		Class cl = null;
		if ("RJavaClassLoader".equals(name)) {
			return getClass();
		}
		if (this.useSystem) {
			try {
				cl = super.findClass(name);
				if (cl != null) {
					if (verbose) {
						LOGGER.log(Level.FINE, "Found class '" + name + "' using URL loader");
					}
					return cl;
				}
			}
			catch (final Exception e) {
				if (verbose) {
					LOGGER.log(Level.FINE, "URL Loader could not find class", e);
				}
				if (!this.useSecond && e instanceof ClassNotFoundException) {
					throw (ClassNotFoundException) e;
				}
			}
		}
		if (this.useSecond) {
			if (verbose) {
				System.out.println("RJavaClassLoader.findClass(\"" + name + "\")");
			}
			
			InputStream ins = null;
			
			for (final Enumeration<UnixFile> e = this.classPath.elements(); e.hasMoreElements();) {
				final UnixFile cp = e.nextElement();
				
				if (verbose) {
					System.out.println(" - trying class path \"" + cp + "\"");
				}
				try {
					ins = null;
					if (cp instanceof UnixJarFile) {
						ins = ((UnixJarFile) cp).getResourceAsStream(classNameToFile(name) + ".class");
					} else if (cp instanceof UnixDirectory) {
						final UnixFile class_f = new UnixFile(cp.getPath()+"/"+classNameToFile(name)+".class");
						if (class_f.isFile()) {
							ins = new FileInputStream(class_f);
						}
					}
					if (ins != null) {
						int al = 128 * 1024;
						byte fc[] = new byte[al];
						int n = ins.read(fc);
						int rp = n;
						// System.out.println(" loading class file, initial n =
						// "+n);
						while (n > 0) {
							if (rp == al) {
								int nexa = al * 2;
								if (nexa < 512 * 1024) {
									nexa = 512 * 1024;
								}
								final byte la[] = new byte[nexa];
								System.arraycopy(fc, 0, la, 0, al);
								fc = la;
								al = nexa;
							}
							n = ins.read(fc, rp, fc.length - rp);
							// System.out.println(" next n = "+n+" (rp="+rp+",
							// al="+al+")");
							if (n > 0) {
								rp += n;
							}
						}
						ins.close();
						n = rp;
						if (verbose) {
							System.out.println("RJavaClassLoader: loaded class "
									+ name + ", " + n + " bytes");
						}
						cl = defineClass(name, fc, 0, n);
						// System.out.println(" - class = "+cl);
						return cl;
					}
				} catch (final Exception ex) {
					// System.out.println(" * won't work: "+ex.getMessage());
				}
			}
		}
		// System.out.println("=== giving up");
		if (cl == null) {
			throw (new ClassNotFoundException(name));
		}
		return cl;
	}
	
	@Override
	public URL findResource(final String name) {
		if (verbose) {
			LOGGER.entering("RJavaClassLoader", "findResource", name);
		}
		if (this.useSystem) {
			try {
				final URL u = super.findResource(name);
				if (u != null) {
					if (verbose) {
						LOGGER.log(Level.FINE, "Found resource '"+name+"' at '"+ u + "' using URL loader.");
					}
					return u;
				}
			}
			catch (final Exception e) {
			}
		}
		if (this.useSecond) {
			for (final Enumeration<UnixFile> e = this.classPath.elements(); e.hasMoreElements();) {
				final UnixFile cp = e.nextElement();
				
				try {
					if (cp instanceof UnixJarFile) {
						final URL u = ((UnixJarFile) cp).getResource(name) ;
						if (u != null) {
							if (verbose) {
								System.out.println(" - found in a JAR file, URL " + u);
							}
							return u;
						}
					} else if (cp instanceof UnixDirectory) {
						final UnixFile res_f = new UnixFile(cp.getPath() + "/" + name);
						if (res_f.isFile()) {
							if (verbose) {
								System.out.println(" - find as a file: "+res_f);
							}
							return res_f.toURL();
						}
					}
				} catch (final Exception iox) {
				}
			}
		}
		return null;
	}
	
	/** add a library to path mapping for a native library */
	public void addRLibrary(final String name, final String path) {
		this.libMap.put(name, new UnixFile(path));
	}
	
	public void addClassPath(final String cp) {
		final UnixFile f = new UnixFile(cp);
		addClassPath(f);
	}
	
	public void addClassPath(final UnixFile f) {
		if (this.useSystem) {
			try {
				addURL(f.toURI().toURL());
				if (verbose) {
					LOGGER.log(Level.FINE, "Added '"+f.getPath()+"' to classpath of URL loader");
				}
				// return; // we need to add it anyway
			}
			catch (final Exception e) {
			}
		}
		if (this.useSecond) {
			UnixFile g = null;
			if (f.isFile() && f.getName().endsWith(".jar")) {
				g = new UnixJarFile(f.getPath());
			} else if (f.isDirectory()) {
				g = new UnixDirectory(f.getPath());
			}
			
			if (g != null && !this.classPath.contains(g)) {
				this.classPath.add(g);
				System.setProperty("java.class.path",
						System.getProperty("java.class.path")+File.pathSeparator+g.getPath());
			}
		}
	}
	
	/**
	 * Adds multiple entries to the class path
	 */
	public void addClassPath(final String[] cp) {
		int i = 0;
		while (i < cp.length) {
			addClassPath(cp[i++]);
		}
	}
	
	public void addClassPath(final List<String> cpList) {
		for (final String path : cpList) {
			addClassPath(path);
		}
	}
	
	/**
	 * @return the array of class paths used by this class loader
	 */
	public String[] getClassPath() {
		final List<String> list = new ArrayList<String>();
		if (this.useSystem) {
			final URL[] urls = getURLs();
			for (final URL url : urls) {
				list.add(url.toString());
			}
		}
		if (this.useSecond) {
			final UnixFile[] files = this.classPath.toArray(new UnixFile[list.size()]);
			for (final UnixFile file : files) {
				list.add(file.getPath());
			}
		}
		return list.toArray(new String[list.size()]);
	}
	
	@Override
	protected String findLibrary(final String name) {
		if (verbose) {
			LOGGER.entering("RJavaClassLoader", "findLibrary", name);
		}
		
		// if (name.equals("rJava"))
		// return rJavaLibPath+"/"+name+".so";
		
		final UnixFile u = this.libMap.get(name);
		String s = null;
		if (u != null && u.exists()) {
			s = u.getPath();
		}
		if (verbose) {
			LOGGER.log(Level.FINE, "Mapping to " + ((s == null) ? "<none>" : s));
		}
		
		return s;
	}
	
	public Class<?> loadRJavaClass(final String name) throws ClassNotFoundException {
		final Class<?> clazz = findClass(name);
		resolveClass(clazz);
		return clazz;
	}
	
}
