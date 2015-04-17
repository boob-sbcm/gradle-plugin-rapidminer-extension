## Introduction

The 'com.rapidminer.extension' plugin is designed to ease the process of creating a RapidMiner extension.
The plugin...
* ... applies and configures the RapidMiner Gradle plugins rapidminer-java-basics, rapidminer-code-quality, and rapidminer-release as well as a plugin for creating a shaded/shadow Jar (link).
* ... adds the extensionJar Maven publication which contains the pure extension jar as well as the shaded/shadow jar.
* ... adds RapidMiner and configured RapidMiner extensions as provided dependencies 
* ... ensures that the created extension jars MANIFEST.MF contains valid and correct entries

## How to use (requires Gradle 2.3+)
	plugins {
		id 'com.rapidminer.extension' version <plugin version>
	}
	
	extensionConfig {
		/*
		 * Mandatory parameter that defines the extension name.
		 */
		name = 'Web Mining'
		
		/*
		 * Optional parameter that defines the extension namespace. The namespace is also used as artifactId.
		 * If not defined the namespace will be infered from the extension name by replacing whitespace characters 
		 * by an underscore and converting the name to lower case (e.g. 'Web Mining' becomes 'web_mining'). 
		 */
		namespace = 'web'
		
		/*
		 * The artefact group. By default it is 'com.rapidminer.extension'.
		 */
		groupId = 'com.rapidminer.extension'
		
		/*
		 * The extension vendor. By default it is 'RapidMiner GmbH'.
		 */
		vendor = "RapidMiner GmbH"
		
		/*
		 * The vendor homepage. By default it is 'www.rapidminer.com'.
		 */
		homepage = "www.rapidminer.com"
		
		/*
		 * The path to RapidMiner Studio. It is used to deploy the extension to '$rapidminerHome/lib/plugins'.
		 */
		rapidminerHome = "../rapidminer-studio"
		
		/*
		 * The Gradle Wrapper version to be installed when invoking the 'wrapper' task.
		 */
		wrapperVersion = '2.0'
		
		/*
		 * Optional block which allows to define the version of RapidMiner to compile against and 
		 * other RapidMiner extension dependencies. 
		 */
		dependencies {
			
			/*
			 * Defines the minimum version of RapidMiner the extension needs to be loaded. 
			 * This version will be added to the compile classpath as provided dependency. 
			 * Default is '6.0.000'.
			 */
			rapidminer "5.3.000"
			
			/*
			 * Defines whether the old Ant artifact (com.rapidminer.studio:rapidmimer) should be
			 * used of if the new Maven artifact (com.rapidminer.studio:rapidmimer-studio-core),
			 * which is available since version 6.0.9, should be used. 
			 * By default it is set to 'false'.
			 */
			useAntArtifact true
			
			/*
			 * Syntax to add dependencies to other RapidMiner extensions.
			 * Each defined extension will be added as provided dependency.
			 */
			extension namespace: "text", version: "5.3.3-SNAPSHOT"
		}
		
		/*
		 * Optional block to define resource files. 
		 * If not defined the resource files will be guessed by a heuristics.
		 */
		resource {
			
			/*
			 * Optional parameter that allows to specify the class used for initialization. 
			 * If not specified the plugin checks if a class named 'PluginInit%EXTENSION_NAME%' exists
			 * where %EXTENSION_NAME% is the extension name without whitespaces (e.g. 'Web Mining' -> 'PluginInitWebMining').
			 */
			initClass "com.rapidminer.PluginInitWebMining"
			
			/*
			 * Optional parameter that allows to specify the operator definition XML file. 
			 * If not specified the plugin checks if a resource file named 'Operators%EXTENSION_NAME%.xml' exists
			 * where %EXTENSION_NAME% is the extension name without whitespaces (e.g. 'Web Mining' -> 'OperatorsWebMining.xml').
			 */
			operatorDefinition "/com/rapidminer/resources/OperatorsWebMining.xml"
			
			/*
			 * Optional parameter that allows to specify the IO Object definition XML file. 
			 * If not specified the plugin checks if a resource file named 'ioobjects%EXTENSION_NAME%.xml' exists
			 * where %EXTENSION_NAME% is the extension name without whitespaces (e.g. 'Web Mining' -> 'ioobjectsWebMining.xml').
			 */
			objectDefinition "/com/rapidminer/resources/ioobjectsWebMining.xml"
			
			/*
			 * Optional parameter that allows to specify the parse rule definition XML file. 
			 * If not specified the plugin checks if a resource file named 'parserules%EXTENSION_NAME%.xml' exists
			 * where %EXTENSION_NAME% is the extension name without whitespaces (e.g. 'Web Mining' -> 'parserulesWebMining.xml').
			 */
			parseRuleDefinition "/com/rapidminer/resources/parserulesWebMining.xml"
			
			/*
			 * Optional parameter that allows to specify the groups properties file. 
			 * If not specified the plugin checks if a resource file named 'groups%EXTENSION_NAME%.properties' exists
			 * where %EXTENSION_NAME% is the extension name without whitespaces (e.g. 'Web Mining' -> 'groupsWebMining.properties').
			 */
			groupProperties "/com/rapidminer/resources/groupsWebMining.properties"
			
			/*
			 * Optional parameter that allows to specify the errors properties file. 
			 * If not specified the plugin checks if a resource file named 'Errors%EXTENSION_NAME%.properties' exists
			 * where %EXTENSION_NAME% is the extension name without whitespaces (e.g. 'Web Mining' -> 'ErrorsWebMining.properties').
			 */
			errorDescription "/com/rapidminer/resources/i18n/ErrorsWebMining.properties"
			
			/*
			 * Optional parameter that allows to specify the user errors properties file. 
			 * If not specified the plugin checks if a resource file named 'UserErrorMessages%EXTENSION_NAME%.properties' exists
			 * where %EXTENSION_NAME% is the extension name without whitespaces (e.g. 'Web Mining' -> 'UserErrorMessagesWebMining.properties').
			 */
			userErrors "/com/rapidminer/resources/i18n/UserErrorMessagesWebMining.properties"
			
			/*
			 * Optional parameter that allows to specify the GUI properties file. 
			 * If not specified the plugin checks if a resource file named 'GUI%EXTENSION_NAME%.properties' exists
			 * where %EXTENSION_NAME% is the extension name without whitespaces (e.g. 'Web Mining' -> 'GUIWebMining.properties').
			 */
			guiDescription "/com/rapidminer/resources/i18n/GUIWebMining.properties"
			
			/*
			 * Optional parameter that allows to specify the settings configuration XML file. 
			 * If not specified the plugin checks if a resource file named 'settings%EXTENSION_NAME%.xml' exists
			 * where %EXTENSION_NAME% is the extension name without whitespaces (e.g. 'Web Mining' -> 'settingsWebMining.xml').
			 */
			settingsDescriptor "/com/rapidminer/resouces/settingsWebMining.xml"
			
			/*
			 * Optional parameter that allows to specify the settings properties file. 
			 * If not specified the plugin checks if a resource file named 'Settings%EXTENSION_NAME%.properties' exists
			 * where %EXTENSION_NAME% is the extension name without whitespaces (e.g. 'Web Mining' -> 'SettingsWebMining.properties').
			 */
			settingsStructureDescriptor "/com/rapidminer/resources/i18n/SettingsWebMining.properties"
		}
	}

## Applied Plugins
* com.rapidminer.java-basics
* com.rapidminer.code-quality
* com.rapidminer.release
* com.github.johnrengelman.shadow

## Added Tasks
Apart from the tasks added by the applied plugins, following tasks are added:

##### installExtension
This task depends on the _shadow_ task, which creates a Jar containing the extension source code as well as all dependencies (shaded/shadow jar). This jar is copied to %rapidminerHome%/lib/plugins.

##### publishExtension _(incubating)_
This task depends on the publication tasks of the extensionJar Maven publication.
It publishes the extension .jar and the shaded extension .jar to the configured Maven repository.

##### wrapper
This tasks downloads and installs the Gradle wrapper with the specified Gradle version.
